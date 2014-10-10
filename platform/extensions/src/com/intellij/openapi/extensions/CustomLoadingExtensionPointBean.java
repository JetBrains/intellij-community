/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

/**
 * @author yole
 */
public class CustomLoadingExtensionPointBean extends AbstractExtensionPointBean {
  @Attribute("factoryClass")
  public String factoryClass;

  @Attribute("factoryArgument")
  public String factoryArgument;

  @NotNull
  protected Object instantiateExtension(String implementationClass, @NotNull PicoContainer picoContainer) throws ClassNotFoundException {
    if (factoryClass != null) {
      ExtensionFactory factory = instantiate(factoryClass, picoContainer);
      return factory.createInstance(factoryArgument, implementationClass);
    }
    else {
      if (implementationClass == null) {
        throw new RuntimeException("implementation class is not specified for unknown language extension point, " +
                                   "plugin id: " +
                                   (myPluginDescriptor == null ? "<not available>" : myPluginDescriptor.getPluginId()) + ". " +
                                   "Check if 'implementationClass' attribute is specified");
      }
      //noinspection unchecked
      return instantiate(implementationClass, picoContainer);
    }
  }
}
