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
package com.intellij.openapi.roots;

/**
 * @author nik
 */
public class NativeLibraryOrderRootType extends PersistentOrderRootType {
  public static OrderRootType getInstance() {
    return getOrderRootType(NativeLibraryOrderRootType.class);
  }

  public NativeLibraryOrderRootType() {
    super("NATIVE", null, null, null);
  }

  @Override
  public boolean skipWriteIfEmpty() {
    return true;
  }
}
