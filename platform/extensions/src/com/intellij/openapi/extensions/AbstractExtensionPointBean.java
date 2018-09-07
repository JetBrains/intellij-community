// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * @author peter
 */
public abstract class AbstractExtensionPointBean implements PluginAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.extensions.AbstractExtensionPointBean");
  protected PluginDescriptor myPluginDescriptor;

  @Transient
  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @Override
  public final void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  @Nullable
  public PluginId getPluginId() {
    return myPluginDescriptor == null ? null : myPluginDescriptor.getPluginId();
  }

  @NotNull
  public final <T> Class<T> findClass(@NotNull String className) throws ClassNotFoundException {
    return (Class<T>)Class.forName(className, true, getLoaderForClass());
  }

  @Nullable
  public final <T> Class<T> findClassNoExceptions(final String className) {
    try {
      return findClass(className);
    }
    catch (ClassNotFoundException e) {
      LOG.error("Problem loading class " + className + " from plugin " + myPluginDescriptor.getPluginId().getIdString(), e);
      return null;
    }
  }

  @NotNull
  public ClassLoader getLoaderForClass() {
    return myPluginDescriptor == null ? getClass().getClassLoader() : myPluginDescriptor.getPluginClassLoader();
  }

  @NotNull
  public final <T> T instantiate(final String className, @NotNull final PicoContainer container) throws ClassNotFoundException {
    return instantiate(this.findClass(className), container);
  }

  @NotNull
  public static <T> T instantiate(@NotNull final Class<T> aClass, @NotNull final PicoContainer container) {
    return instantiate(aClass, container, false);
  }

  @NotNull
  public static <T> T instantiate(@NotNull final Class<T> aClass,
                                  @NotNull final PicoContainer container,
                                  final boolean allowNonPublicClasses) {
    return (T)new CachingConstructorInjectionComponentAdapter(aClass.getName(), aClass, null, allowNonPublicClasses).getComponentInstance(container);
  }

}
