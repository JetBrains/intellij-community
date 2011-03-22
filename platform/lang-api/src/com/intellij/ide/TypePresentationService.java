package com.intellij.ide;

import com.intellij.ide.presentation.Presentation;
import com.intellij.ide.presentation.PresentationTemplate;
import com.intellij.ide.presentation.PresentationTemplateImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.containers.ConcurrentFactoryMap;
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
    PresentationTemplate template = mySuperClasses.get(type);
    return template == null ? null : template.getIcon(null, 0);
  }

  @Nullable
  public String getTypePresentableName(Class type) {
    PresentationTemplate template = mySuperClasses.get(type);
    return template == null ? null : template.getTypeName();
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
  private final FactoryMap<Class, PresentationTemplate> mySuperClasses = new ConcurrentFactoryMap<Class, PresentationTemplate>() {
    @Override
    protected PresentationTemplate create(Class key) {
      return walkSupers(key, new LinkedHashSet<Class>());
    }

    @Nullable
    private PresentationTemplate walkSupers(Class aClass, Set<Class> result) {
      if (!result.add(aClass)) {
        return null;
      }
      PresentationTemplate template = createPresentationTemplate(aClass);
      if (template != null) return template;
      final Class superClass = aClass.getSuperclass();
      if (superClass != null) {
        template = walkSupers(superClass, result);
        if (template != null) return template;
      }

      for (Class intf : aClass.getInterfaces()) {
        template = walkSupers(intf, result);
        if (template != null) return template;
      }
      return null;
    }
  };

}
