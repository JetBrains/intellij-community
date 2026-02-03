// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * A JPS plugin should create an instance of this class and use its {@link #getMessage} method.
 * In that case, IDE's language pack's localized resources will be automatically used for the JPS process launched by the IDE.
 */
public class JpsDynamicBundle extends AbstractBundle {
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

  /**
   * Creates a new instance of the message bundle. It's usually stored in a private static final field, and static methods delegating
   * to its {@link #getMessage} and {@link #getLazyMessage} methods are added.
   *
   * @param bundleClass  any class from the module containing the bundle, it's used to locate the file with the messages
   * @param pathToBundle qualified name of the file with the messages (without the extension, with slashes replaced by dots)
   */
  public JpsDynamicBundle(@NotNull Class<?> bundleClass, @NotNull String pathToBundle) {
    super(bundleClass, pathToBundle);
  }

  /**
   * @deprecated create an instance using {@link #JpsDynamicBundle(Class, String)} and delegate to it instead of using inheritance 
   */
  @Deprecated
  protected JpsDynamicBundle(@NonNls @NotNull String pathToBundle) {
    super(pathToBundle);
  }

  @Override
  protected @NotNull ResourceBundle findBundle(
    @NotNull @NonNls String pathToBundle,
    @NotNull ClassLoader loader,
    @NotNull ResourceBundle.Control control
  ) {
    final ResourceBundle base = super.findBundle(pathToBundle, loader, control);
    final ClassLoader languageBundleLoader = ourLangBundleLoader;
    if (languageBundleLoader != null) {
      ResourceBundle languageBundle = super.findBundle(pathToBundle, languageBundleLoader, control);
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
    
    return base;
  }

}
