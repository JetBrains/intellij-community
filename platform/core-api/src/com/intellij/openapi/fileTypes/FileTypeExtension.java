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

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FileTypeExtension<T> extends KeyedExtensionCollector<T, FileType> {
  public FileTypeExtension(@NonNls final String epName) {
    super(epName);
  }

  @NotNull
  @Override
  protected String keyToString(@NotNull final FileType key) {
    return key.getName();
  }

  @NotNull
  public List<T> allForFileType(@NotNull FileType t) {
    return forKey(t);
  }

  public T forFileType(@NotNull FileType t) {
    final List<T> all = allForFileType(t);
    return all.isEmpty() ? null : all.get(0);
  }
}