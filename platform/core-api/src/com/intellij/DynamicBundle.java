// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.DefaultBundleService;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class DynamicBundle extends AbstractBundle {
  private static final Logger LOG = Logger.getInstance(DynamicBundle.class);

  private static @NotNull String ourLangTag = Locale.ENGLISH.toLanguageTag();
  private static final Map<String, DynamicBundle> ourBundlesForForms = CollectionFactory.createConcurrentSoftValueMap();

  public DynamicBundle(@NotNull String pathToBundle) {
    super(pathToBundle);
  }

  // see BundleUtil
  @Override
  protected ResourceBundle findBundle(@NotNull String pathToBundle,
                                      @NotNull ClassLoader baseLoader,
                                      @NotNull ResourceBundle.Control control) {
    ResourceBundle base = super.findBundle(pathToBundle, baseLoader, control);
    if (!DefaultBundleService.isDefaultBundle()) {
      LanguageBundleEP langBundle = findLanguageBundle();
      if (langBundle != null) {
        PluginDescriptor pluginDescriptor = langBundle.pluginDescriptor;
        ResourceBundle pluginBundle = super.findBundle(pathToBundle, pluginDescriptor == null ? getClass().getClassLoader() : pluginDescriptor.getPluginClassLoader(), control);
        if (pluginBundle != null) {
          try {
            if (DynamicBundleInternal.SET_PARENT != null) {
              DynamicBundleInternal.SET_PARENT.bindTo(pluginBundle).invoke(base);
            }
            return pluginBundle;
          }
          catch (Throwable e) {
            LOG.warn(e);
          }
        }
      }
    }
    return base;
  }

  /**
   * "SET_PARENT" has been temporary moved into the internal class to fix Kotlin compiler.
   * It's to be refactored with "ResourceBundleProvider" since 'core-api' module will use java 1.9+
   */
 private static class DynamicBundleInternal {
   private static final MethodHandle SET_PARENT;

   static {
     try {
       Method method = ResourceBundle.class.getDeclaredMethod("setParent", ResourceBundle.class);
       method.setAccessible(true);
       SET_PARENT = MethodHandles.lookup().unreflect(method);
     }
     catch (NoSuchMethodException | IllegalAccessException e) {
       throw new RuntimeException(e);
     }
   }
 }

  // todo: one language per application
  public static @Nullable LanguageBundleEP findLanguageBundle() {
    try {
      if (!LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
        return null;
      }

      Application app = ApplicationManager.getApplication();
      if (app == null || !app.getExtensionArea().hasExtensionPoint(LanguageBundleEP.EP_NAME)) {
        return null;
      }
      return LanguageBundleEP.EP_NAME.findExtension(LanguageBundleEP.class);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  public static final DynamicBundle INSTANCE = new DynamicBundle("") { };

  @ApiStatus.Internal
  public static final class LanguageBundleEP implements PluginAware {
    public static final ExtensionPointName<LanguageBundleEP> EP_NAME = new ExtensionPointName<>("com.intellij.languageBundle");

    @Attribute("locale")
    public String locale = Locale.ENGLISH.getLanguage();
    public PluginDescriptor pluginDescriptor;

    @Override
    public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
      this.pluginDescriptor = pluginDescriptor;
    }
  }

  /** @deprecated used only by GUI form builder */
  @Deprecated
  public static ResourceBundle getBundle(@NotNull String baseName) {
    Class<?> callerClass = ReflectionUtil.findCallerClass(2);
    return getBundle(baseName, callerClass == null ? DynamicBundle.class : callerClass);
  }

  /** @deprecated used only by GUI form builder */
  @Deprecated
  public static ResourceBundle getBundle(@NotNull String baseName, @NotNull Class<?> formClass) {
    DynamicBundle dynamic = ourBundlesForForms.computeIfAbsent(baseName, s -> new DynamicBundle(s) { });
    ResourceBundle rb = dynamic.getResourceBundle(formClass.getClassLoader());
    if (!BundleBase.SHOW_LOCALIZED_MESSAGES) {
      return rb;
    }

    return new ResourceBundle() {
      @Override
      protected Object handleGetObject(@NotNull String key) {
        Object get = rb.getObject(key);
        assert get instanceof String : "Language bundles should contain only strings";
        return BundleBase.appendLocalizationSuffix((String)get, BundleBase.L10N_MARKER);
      }

      @Override
      public @NotNull Enumeration<String> getKeys() {
        return rb.getKeys();
      }
    };
  }

  public static void loadLocale(@Nullable LanguageBundleEP langBundle) {
    if (langBundle != null) {
      ourLangTag = langBundle.locale;
      clearGlobalLocaleCache();
      ourBundlesForForms.clear();
    }
  }

  public static @NotNull Locale getLocale() {
    return Locale.forLanguageTag(ourLangTag);
  }
}
