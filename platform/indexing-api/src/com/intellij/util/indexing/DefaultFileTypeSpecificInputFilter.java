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
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public class DefaultFileTypeSpecificInputFilter implements FileBasedIndex.FileTypeSpecificInputFilter {
  private final FileType[] myFileTypes;

  public DefaultFileTypeSpecificInputFilter(@NotNull FileType... fileTypes) {
    myFileTypes = fileTypes;
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<FileType> fileTypeSink) {
    for(FileType ft:myFileTypes) fileTypeSink.consume(ft);
  }

  @Override
  public boolean acceptInput(@NotNull VirtualFile file) {
    return true;
  }
}
