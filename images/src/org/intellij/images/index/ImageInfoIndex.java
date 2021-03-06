// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.VirtualFileGist;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.fileTypes.impl.SvgFileType;
import org.intellij.images.util.ImageInfo;
import org.intellij.images.util.ImageInfoReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class ImageInfoIndex {
  @Nullable
  public static ImageInfo getInfo(@NotNull VirtualFile file, @NotNull Project project) {
    return ourGist.getFileData(project, file);
  }

  private static final long ourMaxImageSize = (long)(Registry.get("ide.index.image.max.size").asDouble() * 1024 * 1024);

  private static final DataExternalizer<ImageInfo> ourValueExternalizer = new DataExternalizer<>() {
    @Override
    public void save(@NotNull final DataOutput out, final ImageInfo info) throws IOException {
      DataInputOutputUtil.writeINT(out, info.width);
      DataInputOutputUtil.writeINT(out, info.height);
      DataInputOutputUtil.writeINT(out, info.bpp);
    }

    @Override
    public ImageInfo read(@NotNull final DataInput in) throws IOException {
      return new ImageInfo(DataInputOutputUtil.readINT(in),
                           DataInputOutputUtil.readINT(in),
                           DataInputOutputUtil.readINT(in));
    }
  };

  private static final VirtualFileGist<ImageInfo> ourGist =
    GistManager.getInstance().newVirtualFileGist("ImageInfo", 1, ourValueExternalizer, (project, file) -> {
      if (!file.isInLocalFileSystem() || file.getLength() > ourMaxImageSize) {
        return null;
      }

      FileType fileType = file.getFileType();
      if (fileType != SvgFileType.INSTANCE && fileType != ImageFileTypeManager.getInstance().getImageFileType()) {
        return null;
      }

      byte[] content;
      try {
        content = file.contentsToByteArray();
      }
      catch (IOException e) {
        Logger.getInstance(ImageInfoIndex.class).error(e);
        return null;
      }
      ImageInfoReader.Info info = ImageInfoReader.getInfo(content);
      return info == null ? null : new ImageInfo(info.width, info.height, info.bpp);
    });
}
