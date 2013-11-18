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

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.util.KeyedExtensionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FileTypeExtensionFactory<T> extends KeyedExtensionFactory<T, FileType> {
  public FileTypeExtensionFactory(@NotNull final Class<T> interfaceClass, @NonNls @NotNull final ExtensionPointName<KeyedFactoryEPBean> epName) {
    super(interfaceClass, epName, ApplicationManager.getApplication().getPicoContainer());
  }

  @Override
  public String getKey(@NotNull final FileType key) {
    return key.getName();
  }
}