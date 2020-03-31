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
package com.intellij.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class MixinEP<T> extends AbstractExtensionPointBean {

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;

  @Attribute("implementationClass")
  public String implementationClass;

  private final NotNullLazyValue<Class> myKey = new NotNullLazyValue<Class>() {
    @Override
    @NotNull
    protected Class compute() {
      if (key == null) {
        String error = "No key specified for mixin with implementation class " + implementationClass;
        if (myPluginDescriptor != null) {
          throw new PluginException(error, myPluginDescriptor.getPluginId());
        }
        throw new IllegalArgumentException(error);
      }
      return findExtensionClass(key);
    }
  };

  private final LazyInstance<T> myHandler = new LazyInstance<T>() {
    @Override
    protected Class<T> getInstanceClass() {
      return findExtensionClass(implementationClass);
    }
  };

  public Class getKey() {
    return myKey.getValue();
  }

  public T getInstance() {
    return myHandler.getValue();
  }
}