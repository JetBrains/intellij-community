/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.options.colors;

/**
 * @author gregsh
 */
public abstract class AbstractKeyDescriptor<T> {

  private final String myDisplayName;
  private final T myKey;

  protected AbstractKeyDescriptor(String displayName, T key) {
    myKey = key;
    myDisplayName = displayName;
  }

  /**
   * Returns the name of the attribute shown in the colors settings page.
   *
   * @return the name of the attribute.
   */
  public String getDisplayName() {
    return myDisplayName;
  }

  /**
   * Returns the text attributes or color key for which the colors are specified.
   *
   * @return the attributes key.
   */
  public T getKey() {
    return myKey;
  }
}
