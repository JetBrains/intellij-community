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

import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import icons.ImagesIcons;
import org.intellij.images.ImagesBundle;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.util.Set;

/**
 * Image file type manager.
 */
final class ImageFileTypeManagerImpl extends ImageFileTypeManager {
  private static final String IMAGE_FILE_TYPE_NAME = "Image";
  private static final String IMAGE_FILE_TYPE_DESCRIPTION = ImagesBundle.message("images.filetype.description");
  private static final UserFileType IMAGE_FILE_TYPE = new ImageFileType();

  @Override
  public boolean isImage(@NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, IMAGE_FILE_TYPE) || file.getFileType() instanceof SvgFileType;
  }

  @Override
  @NotNull
  public FileType getImageFileType() {
    return IMAGE_FILE_TYPE;
  }

  public static final class ImageFileType extends UserBinaryFileType {
    private ImageFileType() {
      setName(IMAGE_FILE_TYPE_NAME);
      setDescription(IMAGE_FILE_TYPE_DESCRIPTION);
    }

    @Override
    public Icon getIcon() {
      return ImagesIcons.ImagesFileType;
    }
  }

  @Override
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    final Set<String> processed = new THashSet<>();
    for (String format : ImageIO.getReaderFormatNames()) {
      processed.add(StringUtil.toLowerCase(format));
    }
    processed.add(StringUtil.toLowerCase(IfsUtil.ICO_FORMAT));

    consumer.consume(IMAGE_FILE_TYPE, StringUtil.join(processed, FileTypeConsumer.EXTENSION_DELIMITER));
  }
}
