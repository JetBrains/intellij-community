package com.intellij.ide;

import com.intellij.ide.presentation.Presentation;
import com.intellij.ide.presentation.PresentationTemplate;
import com.intellij.ide.presentation.PresentationTemplateImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class TypePresentationService {

  @Nullable
  public Icon getTypeIcon(Class type) {
    Set<PresentationTemplate> templates = mySuperClasses.get(type);
    for (PresentationTemplate template : templates) {
      Icon icon = template.getIcon(null, 0);
      if (icon != null) return icon;
    }
    return null;
  }

  @Nullable
  public String getTypePresentableName(Class type) {
    Set<PresentationTemplate> templates = mySuperClasses.get(type);
    for (PresentationTemplate template : templates) {
      String typeName = template.getTypeName();
      if (typeName != null) return typeName;
    }
    return null;
  }

  public static TypePresentationService getService() {
    return ourInstance;
  }

  public TypePresentationService() {
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
      };
    }
    return null;
  }

  private static final TypePresentationService ourInstance = new TypePresentationService();

  private final Map<String, NullableLazyValue<Icon>> myIcons = new HashMap<String, NullableLazyValue<Icon>>();
  private final Map<String, NullableLazyValue<String>> myNames = new HashMap<String, NullableLazyValue<String>>();
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final FactoryMap<Class, Set<PresentationTemplate>> mySuperClasses = new ConcurrentFactoryMap<Class, Set<PresentationTemplate>>() {
    @Override
    protected Set<PresentationTemplate> create(Class key) {
      LinkedHashSet<PresentationTemplate> templates = new LinkedHashSet<PresentationTemplate>();
      walkSupers(key, new LinkedHashSet<Class>(), templates);
      return templates;
    }

    private void walkSupers(Class aClass, Set<Class> classes, Set<PresentationTemplate> templates) {
      if (!classes.add(aClass)) {
        return;
      }
      ContainerUtil.addIfNotNull(createPresentationTemplate(aClass), templates);
      final Class superClass = aClass.getSuperclass();
      if (superClass != null) {
        walkSupers(superClass, classes, templates);
      }

      for (Class intf : aClass.getInterfaces()) {
        walkSupers(intf, classes, templates);
      }
    }
  };

}
