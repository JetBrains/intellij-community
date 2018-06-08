/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.extensions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * @author peter
 */
public abstract class AbstractExtensionPointBean implements PluginAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.extensions.AbstractExtensionPointBean");
  protected PluginDescriptor myPluginDescriptor;

  @Override
  public final void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
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
    return instantiate(this.<T>findClass(className), container);
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
