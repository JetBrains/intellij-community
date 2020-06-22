// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.presentation.Presentation;
import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.ClassExtension;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class TypePresentationServiceImpl extends TypePresentationService {
  private static final ExtensionPointName<PresentationProvider<?>> PROVIDER_EP = new ExtensionPointName<>("com.intellij.presentationProvider");
  private static final ClassExtension<PresentationProvider<?>> PROVIDERS = new ClassExtension<>(PROVIDER_EP.getName());

  @Nullable
  @Override
  public Icon getIcon(@NotNull Object o) {
    return getIcon(o.getClass(), o);
  }

  @Nullable
  @Override
  public Icon getTypeIcon(Class type) {
    return getIcon(type, null);
  }

  private @Nullable Icon getIcon(Class<?> type, Object o) {
    return findFirst(type, template -> template.getIcon(o, 0));
  }

  @Nullable
  @Override
  public String getTypePresentableName(Class type) {
    String typeName = findFirst(type, template -> template.getTypeName());
    return typeName != null ? typeName : getDefaultTypeName(type);
  }

  @Nullable
  @Override
  public String getTypeName(@NotNull Object o) {
    return findFirst(o.getClass(), template -> template.getTypeName(o));
  }

  @Nullable
  @Override
  public String getObjectName(@NotNull Object o) {
    return findFirst(o.getClass(), template -> template.getName(o));
  }

  @Nullable
  private <T> T findFirst(Class<?> clazz, @NotNull Function<? super PresentationTemplate, ? extends T> f) {
    Set<PresentationTemplate> templates = mySuperClasses.get(clazz);
    for (PresentationTemplate template : templates) {
      T result = f.apply(template);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  public TypePresentationServiceImpl() {
    for (TypeIconEP ep : TypeIconEP.EP_NAME.getExtensionList()) {
      myIcons.put(ep.className, ep.getIcon());
    }
    TypeIconEP.EP_NAME.addExtensionPointListener(new ExtensionPointListener<TypeIconEP>() {
      @Override
      public void extensionAdded(@NotNull TypeIconEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        myIcons.put(extension.className, extension.getIcon());
      }

      @Override
      public void extensionRemoved(@NotNull TypeIconEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        myIcons.remove(extension.className);
      }
    }, ApplicationManager.getApplication());

    for (TypeNameEP ep : TypeNameEP.EP_NAME.getExtensionList()) {
      myNames.put(ep.className, ep.getTypeName());
    }
    TypeNameEP.EP_NAME.addExtensionPointListener(new ExtensionPointListener<TypeNameEP>() {
      @Override
      public void extensionAdded(@NotNull TypeNameEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        myNames.put(extension.className, extension.getTypeName());
      }

      @Override
      public void extensionRemoved(@NotNull TypeNameEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        myNames.remove(extension.className);
      }
    }, ApplicationManager.getApplication());

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        mySuperClasses.clear();
      }
    });
  }

  @Nullable
  private PresentationTemplate createPresentationTemplate(Class<?> type) {
    Presentation presentation = type.getAnnotation(Presentation.class);
    if (presentation != null) {
      return new AnnotationBasedTemplate(presentation, type);
    }
    PresentationProvider<?> provider = PROVIDERS.forClass(type);
    if (provider != null) {
      return new ProviderBasedTemplate(provider);
    }
    final NullableLazyValue<Icon> icon = myIcons.get(type.getName());
    final NullableLazyValue<String> typeName = myNames.get(type.getName());
    if (icon != null || typeName != null) {
      return new PresentationTemplate() {
        @Nullable
        @Override
        public Icon getIcon(Object o, int flags) {
          return icon == null ? null : icon.getValue();
        }

        @Nullable
        @Override
        public String getName(Object o) {
          return null;
        }

        @Nullable
        @Override
        public String getTypeName() {
          return typeName == null ? null : typeName.getValue();
        }

        @Nullable
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
  private final Map<Class<?>, Set<PresentationTemplate>> mySuperClasses = ConcurrentFactoryMap.createMap(key -> {
    Set<PresentationTemplate> templates = new LinkedHashSet<>();
    walkSupers(key, new LinkedHashSet<>(), templates);
    return templates;
  });

  private void walkSupers(Class<?> aClass, Set<? super Class<?>> classes, Set<? super PresentationTemplate> templates) {
    if (!classes.add(aClass)) {
      return;
    }
    ContainerUtil.addIfNotNull(templates, createPresentationTemplate(aClass));
    Class<?> superClass = aClass.getSuperclass();
    if (superClass != null) {
      walkSupers(superClass, classes, templates);
    }

    for (Class<?> intf : aClass.getInterfaces()) {
      walkSupers(intf, classes, templates);
    }
  }

  public static class ProviderBasedTemplate implements PresentationTemplate {
    private final PresentationProvider myProvider;

    public ProviderBasedTemplate(PresentationProvider<?> provider) {
      myProvider = provider;
    }

    @Nullable
    @Override
    public Icon getIcon(Object o, int flags) {
      //noinspection unchecked
      return myProvider instanceof PresentationTemplate ? ((PresentationTemplate)myProvider).getIcon(o, flags) : myProvider.getIcon(o);
    }

    @Nullable
    @Override
    public String getName(Object o) {
      //noinspection unchecked
      return myProvider.getName(o);
    }

    @Nullable
    @Override
    public String getTypeName() {
      return myProvider instanceof PresentationTemplate ? ((PresentationTemplate)myProvider).getTypeName() : null;
    }

    @Nullable
    @Override
    public String getTypeName(Object o) {
      //noinspection unchecked
      return myProvider.getTypeName(o);
    }
  }

  public static class PresentationTemplateImpl extends ProviderBasedTemplate {

    public PresentationTemplateImpl(Presentation presentation, Class<?> aClass) {
      super(new AnnotationBasedTemplate(presentation, aClass));
    }
  }

  @SuppressWarnings("unchecked")
  private static class AnnotationBasedTemplate extends PresentationProvider<Object> implements PresentationTemplate {
    private final Presentation myPresentation;
    private final Class<?> myClass;

    AnnotationBasedTemplate(Presentation presentation, Class<?> aClass) {
      myPresentation = presentation;
      myClass = aClass;
    }

    @Nullable
    @Override
    public Icon getIcon(Object o) {
      return getIcon(o, 0);
    }

    @Nullable
    @Override
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

    @Nullable
    @Override
    public String getTypeName() {
      return StringUtil.isEmpty(myPresentation.typeName()) ? null : myPresentation.typeName();
    }

    @Nullable
    @Override
    public String getTypeName(Object o) {
      PresentationProvider provider = myPresentationProvider.getValue();
      if (provider != null) {
        String typeName = provider.getTypeName(o);
        if (typeName != null) return typeName;
      }
      return getTypeName();
    }

    @Nullable
    @Override
    public String getName(Object o) {
      PresentationProvider namer = myPresentationProvider.getValue();
      return namer == null ? null : namer.getName(o);
    }

    private final NullableLazyValue<Icon> myIcon = new NullableLazyValue<Icon>() {
      @Override
      protected Icon compute() {
        if (StringUtil.isEmpty(myPresentation.icon())) return null;
        return IconLoader.getIcon(myPresentation.icon(), myClass);
      }
    };

    private final NullableLazyValue<PresentationProvider<?>> myPresentationProvider = new NullableLazyValue<PresentationProvider<?>>() {
      @Override
      protected PresentationProvider<?> compute() {
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

    @Nullable
    String getTypeName(Object o);
  }
}
