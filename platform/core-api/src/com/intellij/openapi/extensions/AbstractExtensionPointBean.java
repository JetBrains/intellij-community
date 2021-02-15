// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * @deprecated Use {@link com.intellij.serviceContainer.LazyExtensionInstance}.
 */
@Deprecated
public abstract class AbstractExtensionPointBean implements PluginAware {
  private static final Logger LOG = Logger.getInstance(AbstractExtensionPointBean.class);

  protected PluginDescriptor myPluginDescriptor;

  @Transient
  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public @Nullable PluginId getPluginId() {
    return myPluginDescriptor == null ? null : myPluginDescriptor.getPluginId();
  }

  /**
   * @deprecated use {@link #findExtensionClass(String)} instead. It'll throw {@link PluginException} instead of
   * {@link ClassNotFoundException}, which contains information about the plugin which registers the problematic extension so error reporters
   * will be able to report such exception as a plugin problem, not core problem. Also it isn't a checked exception so you won't need to wrap
   * it to unchecked exception in your code.
   */
  @Deprecated
  public final @NotNull <T> Class<T> findClass(@NotNull String className) throws ClassNotFoundException {
    return findClass(className, myPluginDescriptor);
  }

  public final @NotNull <T> Class<T> findExtensionClass(@NotNull String className) {
    try {
      return findClass(className, myPluginDescriptor);
    }
    catch (Throwable t) {
      throw new PluginException(t, getPluginId());
    }
  }

  private static @NotNull <T> Class<T> findClass(@NotNull String className, @Nullable PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
    ClassLoader classLoader = pluginDescriptor == null ? AbstractExtensionPointBean.class.getClassLoader() : pluginDescriptor.getPluginClassLoader();
    //noinspection unchecked
    return (Class<T>)Class.forName(className, true, classLoader);
  }

  public final @Nullable <T> Class<T> findClassNoExceptions(String className) {
    try {
      return findClass(className, myPluginDescriptor);
    }
    catch (Throwable t) {
      LOG.error(new PluginException(t, getPluginId()));
      return null;
    }
  }

  public @NotNull ClassLoader getLoaderForClass() {
    return myPluginDescriptor == null ? getClass().getClassLoader() : myPluginDescriptor.getPluginClassLoader();
  }

  /**
   * @deprecated use {@link #instantiateClass(String, PicoContainer)} instead. It'll throw {@link PluginException} instead of
   * {@link ClassNotFoundException}, which contains information about the plugin which registers the problematic extension so error reporters
   * will be able to report such exception as a plugin problem, not core problem. Also it isn't a checked exception so you won't need to wrap
   * it to unchecked exception in your code.
   */
  @Deprecated
  public final @NotNull <T> T instantiate(@NotNull String className, @NotNull PicoContainer container) throws ClassNotFoundException {
    return instantiate(findClass(className, myPluginDescriptor), container, true);
  }

  public final @NotNull <T> T instantiateClass(@NotNull String className, @NotNull PicoContainer container) {
    return instantiate(findExtensionClass(className), container, true);
  }

  public static @NotNull <T> T instantiate(@NotNull Class<T> aClass, @NotNull PicoContainer container) {
    return instantiate(aClass, container, true);
  }

  public static @NotNull <T> T instantiate(@NotNull Class<T> aClass, @NotNull PicoContainer container, @SuppressWarnings("unused") boolean allowNonPublicClasses) {
    //noinspection unchecked
    return (T)CachingConstructorInjectionComponentAdapter.instantiateGuarded(null, container, aClass);
  }
}