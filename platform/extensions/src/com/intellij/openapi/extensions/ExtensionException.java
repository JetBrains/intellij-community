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
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;

public class ExtensionException extends RuntimeException{
  private final Class<?> myExtensionClass;

  public ExtensionException(@NotNull Class<?> extensionClass) {
    super(extensionClass.getCanonicalName());

    myExtensionClass = extensionClass;
  }

  public ExtensionException(@NotNull Class<?> extensionClass, @NotNull Throwable cause) {
    super(extensionClass.getCanonicalName(), cause);

    myExtensionClass = extensionClass;
  }

  @NotNull
  public Class<?> getExtensionClass() {
    return myExtensionClass;
  }
}
