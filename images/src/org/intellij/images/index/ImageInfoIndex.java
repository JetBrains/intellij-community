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
package org.intellij.images.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.util.ImageInfoReader;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author spleaner
 */
public class ImageInfoIndex extends SingleEntryFileBasedIndexExtension<ImageInfoIndex.ImageInfo> {
  public static final ID<Integer, ImageInfo> INDEX_ID = ID.create("ImageFileInfoIndex");

  private final FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      return (file.getFileSystem() == LocalFileSystem.getInstance() || file.getFileSystem() instanceof TempFileSystem) &&
             file.getFileType() == ImageFileTypeManager.getInstance().getImageFileType();
    }
  };

  private final DataExternalizer<ImageInfo> myValueExternalizer = new DataExternalizer<ImageInfo>() {
    public void save(final DataOutput out, final ImageInfo info) throws IOException {
      out.writeInt(info.width);
      out.writeInt(info.height);
      out.writeInt(info.bpp);
    }

    public ImageInfo read(final DataInput in) throws IOException {
      return new ImageInfo(in.readInt(), in.readInt(), in.readInt());
    }
  };

  private final SingleEntryIndexer<ImageInfo> myDataIndexer = new SingleEntryIndexer<ImageInfo>(false) {
    protected ImageInfo computeValue(@NotNull FileContent inputData) {
      final ImageInfoReader.Info info = ImageInfoReader.getInfo(inputData.getContent());
      return info != null? new ImageInfo(info.width, info.height, info.bpp) : null;
    }
  };

  public ID<Integer, ImageInfo> getName() {
    return INDEX_ID;
  }

  public SingleEntryIndexer<ImageInfo> getIndexer() {
    return myDataIndexer;
  }

  public static void processValues(VirtualFile virtualFile, FileBasedIndex.ValueProcessor<ImageInfo> processor, Project project) {
    FileBasedIndex.getInstance().processValues(INDEX_ID, Math.abs(FileBasedIndex.getFileId(virtualFile)), virtualFile, processor, GlobalSearchScope.fileScope(project, virtualFile));
  }

  public DataExternalizer<ImageInfo> getValueExternalizer() {
    return myValueExternalizer;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public int getVersion() {
    return 2;
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
