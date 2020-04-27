// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
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

public final class ImageInfoIndex extends SingleEntryFileBasedIndexExtension<ImageInfo> {
  private static final long ourMaxImageSize = (long)(Registry.get("ide.index.image.max.size").asDouble() * 1024 * 1024);

  public static final ID<Integer, ImageInfo> INDEX_ID = ID.create("ImageFileInfoIndex");

  private final DataExternalizer<ImageInfo> myValueExternalizer = new DataExternalizer<ImageInfo>() {
    @Override
    public void save(@NotNull final DataOutput out, final ImageInfo info) throws IOException {
      DataInputOutputUtil.writeINT(out, info.width);
      DataInputOutputUtil.writeINT(out, info.height);
      DataInputOutputUtil.writeINT(out, info.bpp);
    }

    @Override
    public ImageInfo read(@NotNull final DataInput in) throws IOException {
      return new ImageInfo(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in));
    }
  };

  private final SingleEntryIndexer<ImageInfo> myDataIndexer = new SingleEntryIndexer<ImageInfo>(false) {
    @Override
    protected ImageInfo computeValue(@NotNull FileContent inputData) {
      ImageInfoReader.Info info = ImageInfoReader.getInfo(inputData.getContent(), inputData.getFileName());
      return info != null? new ImageInfo(info.width, info.height, info.bpp) : null;
    }
  };

  @Override
  @NotNull
  public ID<Integer, ImageInfo> getName() {
    return INDEX_ID;
  }

  @Override
  @NotNull
  public SingleEntryIndexer<ImageInfo> getIndexer() {
    return myDataIndexer;
  }

  @Nullable
  public static ImageInfo getInfo(@NotNull VirtualFile file, @NotNull Project project) {
    return ContainerUtil.getFirstItem(FileBasedIndex.getInstance().getFileData(INDEX_ID, file, project).values());
  }

  @NotNull
  @Override
  public DataExternalizer<ImageInfo> getValueExternalizer() {
    return myValueExternalizer;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(ImageFileTypeManager.getInstance().getImageFileType(), SvgFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return file.isInLocalFileSystem() && file.getLength() < ourMaxImageSize;
      }
    };
  }

  @Override
  public int getVersion() {
    return 8;
  }
}
