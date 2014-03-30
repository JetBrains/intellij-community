/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class KeyedLazyInstanceEP<T> extends AbstractExtensionPointBean implements KeyedLazyInstance<T> {

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;
  
  @Attribute("implementationClass")
  public String implementationClass;

  private final LazyInstance<T> myHandler = new LazyInstance<T>() {
    @Override
    protected Class<T> getInstanceClass() throws ClassNotFoundException {
      return findClass(implementationClass);
    }
  };

  @Override
  public T getInstance() {
    return myHandler.getValue();
  }

  @Override
  public String getKey() {
    return key;
  }
}