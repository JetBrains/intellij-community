// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.PicoContainer;

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

  @Nullable
  public PluginId getPluginId() {
    return myPluginDescriptor == null ? null : myPluginDescriptor.getPluginId();
  }

  /**
   * @deprecated use {@link #findExtensionClass(String)} instead. It'll throw {@link ExtensionInstantiationException} instead of
   * {@link ClassNotFoundException}, which contains information about the plugin which registers the problematic extension so error reporters
   * will be able to report such exception as a plugin problem, not core problem. Also it isn't a checked exception so you won't need to wrap
   * it to unchecked exception in your code.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  public final <T> Class<T> findClass(@NotNull String className) throws ClassNotFoundException {
    return findClass(className, myPluginDescriptor);
  }

  @NotNull
  public final <T> Class<T> findExtensionClass(@NotNull String className) {
    try {
      return findClass(className, myPluginDescriptor);
    }
    catch (ClassNotFoundException e) {
      throw new ExtensionInstantiationException(e, myPluginDescriptor);
    }
  }

  @NotNull
  public static <T> Class<T> findClass(@NotNull String className, @Nullable PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
    ClassLoader classLoader = pluginDescriptor == null ? AbstractExtensionPointBean.class.getClassLoader() : pluginDescriptor.getPluginClassLoader();
    //noinspection unchecked
    return (Class<T>)Class.forName(className, true, classLoader);
  }

  @Nullable
  public final <T> Class<T> findClassNoExceptions(String className) {
    try {
      return findClass(className);
    }
    catch (ClassNotFoundException e) {
      LOG.error(new ExtensionInstantiationException(e, myPluginDescriptor));
      return null;
    }
  }

  @NotNull
  public ClassLoader getLoaderForClass() {
    return myPluginDescriptor == null ? getClass().getClassLoader() : myPluginDescriptor.getPluginClassLoader();
  }

  /**
   * @deprecated use {@link #instantiateClass(String, PicoContainer)} instead. It'll throw {@link ExtensionInstantiationException} instead of
   * {@link ClassNotFoundException}, which contains information about the plugin which registers the problematic extension so error reporters
   * will be able to report such exception as a plugin problem, not core problem. Also it isn't a checked exception so you won't need to wrap
   * it to unchecked exception in your code.
   */
  @Deprecated
  @NotNull
  public final <T> T instantiate(@NotNull String className, @NotNull PicoContainer container) throws ClassNotFoundException {
    return instantiate(findClass(className), container);
  }

  @NotNull
  public final <T> T instantiateClass(@NotNull String className, @NotNull PicoContainer container) {
    return instantiate(findExtensionClass(className), container);
  }

  @NotNull
  public static <T> T instantiate(@NotNull Class<T> aClass, @NotNull PicoContainer container) {
    return instantiate(aClass, container, true);
  }

  @NotNull
  public static <T> T instantiate(@NotNull Class<T> aClass, @NotNull PicoContainer container, boolean allowNonPublicClasses) {
    ComponentAdapter adapter = new CachingConstructorInjectionComponentAdapter(aClass.getName(), aClass, null, allowNonPublicClasses);
    @SuppressWarnings("unchecked") T t = (T)adapter.getComponentInstance(container);
    return t;
  }
}