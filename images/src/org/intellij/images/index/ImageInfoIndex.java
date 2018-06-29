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
package org.intellij.images.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.fileTypes.impl.SvgFileType;
import org.intellij.images.util.ImageInfoReader;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author spleaner
 */
public class ImageInfoIndex extends SingleEntryFileBasedIndexExtension<ImageInfoIndex.ImageInfo> {
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
      final ImageInfoReader.Info info = ImageInfoReader.getInfo(inputData.getContent());
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

  public static void processValues(VirtualFile virtualFile, FileBasedIndex.ValueProcessor<ImageInfo> processor, Project project) {
    FileBasedIndex.getInstance().processValues(INDEX_ID, Math.abs(FileBasedIndex.getFileId(virtualFile)), virtualFile, processor, GlobalSearchScope
        .fileScope(project, virtualFile));
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

  public static class ImageInfo {
    public int width;
    public int height;
    public int bpp;

    public ImageInfo(int width, int height, int bpp) {
      this.width = width;
      this.height = height;
      this.bpp = bpp;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ImageInfo imageInfo = (ImageInfo)o;

      if (bpp != imageInfo.bpp) return false;
      if (height != imageInfo.height) return false;
      if (width != imageInfo.width) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = width;
      result = 31 * result + height;
      result = 31 * result + bpp;
      return result;
    }
  }
}
