/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.annotations.NotNull;

/**
 * Image file type manager.
 */
final class ImageFileTypeManagerImpl extends ImageFileTypeManager {
  @Override
  public boolean isImage(@NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, ImageFileType.INSTANCE) || file.getFileType() instanceof SvgFileType;
  }

  @Override
  public @NotNull FileType getImageFileType() {
    return ImageFileType.INSTANCE;
  }
}
