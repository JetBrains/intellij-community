/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.ide.presentation.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author peter
 */
public class TypePresentationServiceImpl extends TypePresentationService {

  @Override
  public Icon getIcon(Object o) {
    return getIcon(o.getClass(), o);
  }

  @Override@Nullable
  public Icon getTypeIcon(Class type) {
    return getIcon(type, null);
  }

  private Icon getIcon(Class type, Object o) {
    Set<PresentationTemplate> templates = mySuperClasses.get(type);
    for (PresentationTemplate template : templates) {
      Icon icon = template.getIcon(o, 0);
      if (icon != null) return icon;
    }
    return null;
  }

  @Override @Nullable
  public String getTypePresentableName(Class type) {
    Set<PresentationTemplate> templates = mySuperClasses.get(type);
    for (PresentationTemplate template : templates) {
      String typeName = template.getTypeName();
      if (typeName != null) return typeName;
    }
    return getDefaultTypeName(type);
  }

  @Override
  public String getTypeName(Object o) {
    Set<PresentationTemplate> templates = mySuperClasses.get(o.getClass());
    for (PresentationTemplate template : templates) {
      String typeName = template.getTypeName(o);
      if (typeName != null) return typeName;
    }
    return null;
  }

  public TypePresentationServiceImpl() {
    for(TypeIconEP ep: Extensions.getExtensions(TypeIconEP.EP_NAME)) {
      myIcons.put(ep.className, ep.getIcon());
    }
    for(TypeNameEP ep: Extensions.getExtensions(TypeNameEP.EP_NAME)) {
      myNames.put(ep.className, ep.getTypeName());
    }
  }

  @Nullable
  private PresentationTemplate createPresentationTemplate(Class<?> type) {
    Presentation presentation = type.getAnnotation(Presentation.class);
    if (presentation != null) {
      return new PresentationTemplateImpl(presentation, type);
    }
    final NullableLazyValue<Icon> icon = myIcons.get(type.getName());
    final NullableLazyValue<String> typeName = myNames.get(type.getName());
    if (icon != null || typeName != null) {
      return new PresentationTemplate() {
        @Override
        public Icon getIcon(Object o, int flags) {
          return icon == null ? null : icon.getValue();
        }

        @Override
        public String getName(Object o) {
          return null;
        }

        @Override
        public String getTypeName() {
          return typeName == null ? null : typeName.getValue();
        }

        @Override
        public String getTypeName(Object o) {
          return getTypeName();
        }
      };
    }
    return null;
  }

  private final Map<String, NullableLazyValue<Icon>> myIcons = new HashMap<>();
  private final Map<String, NullableLazyValue<String>> myNames = new HashMap<>();
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final FactoryMap<Class, Set<PresentationTemplate>> mySuperClasses = new ConcurrentFactoryMap<Class, Set<PresentationTemplate>>() {
    @Override
    protected Set<PresentationTemplate> create(Class key) {
      LinkedHashSet<PresentationTemplate> templates = new LinkedHashSet<>();
      walkSupers(key, new LinkedHashSet<>(), templates);
      return templates;
    }

    private void walkSupers(Class aClass, Set<Class> classes, Set<PresentationTemplate> templates) {
      if (!classes.add(aClass)) {
        return;
      }
      ContainerUtil.addIfNotNull(templates, createPresentationTemplate(aClass));
      final Class superClass = aClass.getSuperclass();
      if (superClass != null) {
        walkSupers(superClass, classes, templates);
      }

      for (Class intf : aClass.getInterfaces()) {
        walkSupers(intf, classes, templates);
      }
    }
  };

  /**
   * @author Dmitry Avdeev
   */
  public static class PresentationTemplateImpl implements PresentationTemplate {

    @Override
    @Nullable
    public Icon getIcon(Object o, int flags) {
      if (o == null) return myIcon.getValue();
      PresentationProvider provider = myPresentationProvider.getValue();
      if (provider == null) {
        return myIcon.getValue();
      }
      else {
        Icon icon = provider.getIcon(o);
        return icon == null ? myIcon.getValue() : icon;
      }
    }

    @Override
    @Nullable
    public String getTypeName() {
      return StringUtil.isEmpty(myPresentation.typeName()) ? null : myPresentation.typeName();
    }

    @Override
    public String getTypeName(Object o) {
      PresentationProvider provider = myPresentationProvider.getValue();
      if (provider != null) {
        String typeName = provider.getTypeName(o);
        if (typeName != null) return typeName;
      }
      return getTypeName();
    }

    @Override
    @Nullable
    public String getName(Object o) {
      PresentationProvider namer = myPresentationProvider.getValue();
      return namer == null ? null : namer.getName(o);
    }

    public PresentationTemplateImpl(Presentation presentation, Class<?> aClass) {
      this.myPresentation = presentation;
      myClass = aClass;
    }

    private final Presentation myPresentation;
    private final Class<?> myClass;

    private final NullableLazyValue<Icon> myIcon = new NullableLazyValue<Icon>() {
      @Override
      protected Icon compute() {
        if (StringUtil.isEmpty(myPresentation.icon())) return null;
        return IconLoader.getIcon(myPresentation.icon(), myClass);
      }
    };

    private final NullableLazyValue<PresentationProvider> myPresentationProvider = new NullableLazyValue<PresentationProvider>() {
      @Override
      protected PresentationProvider compute() {
        Class<? extends PresentationProvider> aClass = myPresentation.provider();

        try {
          return aClass == PresentationProvider.class ? null : aClass.newInstance();
        }
        catch (Exception e) {
          return null;
        }
      }
    };
  }

  interface PresentationTemplate {

    @Nullable
    Icon getIcon(Object o, int flags);

    @Nullable
    String getName(Object o);

    @Nullable
    String getTypeName();

    String getTypeName(Object o);
  }
}
