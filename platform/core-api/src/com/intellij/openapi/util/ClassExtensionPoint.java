/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.openapi.extensions.RequiredElement;
import org.jetbrains.annotations.NotNull;

public class ClassExtensionPoint<T> extends AbstractExtensionPointBean implements KeyedLazyInstance<T> {
  // these must be public for scrambling compatibility
  @Attribute("forClass")
  @RequiredElement
  public String psiElementClass;

  @Attribute("implementationClass")
  @RequiredElement
  public String implementationClass;

  private final LazyInstance<T> myHandler = new LazyInstance<T>() {
    @Override
    protected Class<T> getInstanceClass() {
      return findExtensionClass(implementationClass);
    }
  };

  @NotNull
  @Override
  public T getInstance() {
    return myHandler.getValue();
  }

  @Override
  public String getKey() {
    return psiElementClass;
  }

}