// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.api;

import com.intellij.AbstractBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ResourceBundle;

/**
 * A base class for resource bundles used in JPS build process
 * This class provides support for "dynamic" resource bundles provided by {@link com.intellij.DynamicBundle.LanguageBundleEP} extension.
 * if JPS plugin inherits the ResourceBundle from this class, IDE's language pack's localized resources will be automatically available for the JPS process launched by the IDE.
 */
public abstract class JpsDynamicBundle extends AbstractBundle {
  private static final Logger LOG = Logger.getInstance(JpsDynamicBundle.class);

  private static final Method SET_PARENT = getSetParentMethod();
  private static final ClassLoader ourLangBundleLoader;
  static {
    ClassLoader loader = null;
    try {
      final String bundlePath = System.getProperty(GlobalOptions.LANGUAGE_BUNDLE, null);
      if (bundlePath != null) {
        loader = new URLClassLoader(new URL[] {new File(bundlePath).toURI().toURL()}, null);
      }
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    finally {
      ourLangBundleLoader = loader;
    }
  }
  private static Method getSetParentMethod() {
    try {
      return ReflectionUtil.getDeclaredMethod(ResourceBundle.class, "setParent", ResourceBundle.class);
    }
    catch (Throwable e) {
      return null;
    }
  }

  protected JpsDynamicBundle(@NonNls @NotNull String pathToBundle) {
    super(pathToBundle);
  }

  @Override
  protected ResourceBundle findBundle(@NotNull @NonNls String pathToBundle, @NotNull ClassLoader loader, @NotNull ResourceBundle.Control control) {
    final ResourceBundle base = super.findBundle(pathToBundle, loader, control);
    final ClassLoader languageBundleLoader = ourLangBundleLoader;
    if (languageBundleLoader != null) {
      ResourceBundle languageBundle = super.findBundle(pathToBundle, languageBundleLoader, control);
      if (languageBundle != null) {
        try {
          if (SET_PARENT != null) {
            SET_PARENT.invoke(languageBundle, base);
          }
          return languageBundle;
        }
        catch (Throwable e) {
          LOG.warn(e);
        }
      }
    }
    
    return base;
  }

}
