/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class AbstractExtensionPointBean implements PluginAware {
  private PluginDescriptor myPluginDescriptor;

  public final void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  @NotNull
  public final <T> Class<T> findClass(final String className) throws ClassNotFoundException {
    return (Class<T>)Class.forName(className, true, myPluginDescriptor == null ? getClass().getClassLoader() : myPluginDescriptor.getPluginClassLoader());
  }

  @NotNull
  public final <T> T instantiate(final String className, @NotNull final PicoContainer container) throws ClassNotFoundException {
    return instantiate(this.<T>findClass(className), container);
  }

  @NotNull
  public static <T> T instantiate(@NotNull final Class<T> aClass, @NotNull final PicoContainer container) {
    return (T)new ConstructorInjectionComponentAdapter(aClass.getName(), aClass).getComponentInstance(container);
  }

}
