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

package com.intellij.psi.impl.include;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class FileIncludeIndex extends FileBasedIndexExtension<FileIncludeIndex.Key, List<FileIncludeInfoImpl>> {

  public static final ID<Key,List<FileIncludeInfoImpl>> INDEX_ID = ID.create("fileIncludes");

  private static final int BASE_VERSION = 5;

  public static List<FileIncludeInfoImpl> getIncludes(VirtualFile file, GlobalSearchScope scope) {
    final List<FileIncludeInfoImpl> result = new ArrayList<>();
    FileBasedIndex.getInstance().processValues(INDEX_ID, new FileKey(file), file, new FileBasedIndex.ValueProcessor<List<FileIncludeInfoImpl>>() {
      @Override
      public boolean process(VirtualFile file, List<FileIncludeInfoImpl> value) {
        result.addAll(value);
        return true;
      }
    }, scope);
    return result;
  }

  public static MultiMap<VirtualFile, FileIncludeInfoImpl> getIncludingFileCandidates(String fileName, GlobalSearchScope scope) {
    final MultiMap<VirtualFile, FileIncludeInfoImpl> result = new MultiMap<>();
    FileBasedIndex.getInstance().processValues(INDEX_ID, new IncludeKey(fileName), null, new FileBasedIndex.ValueProcessor<List<FileIncludeInfoImpl>>() {
      @Override
      public boolean process(VirtualFile file, List<FileIncludeInfoImpl> value) {
        result.put(file, value);
        return true;
      }
    }, scope);
    return result;
  }

  private static class Holder {
    private static final FileIncludeProvider[] myProviders = Extensions.getExtensions(FileIncludeProvider.EP_NAME);
  }

  @NotNull
  @Override
  public ID<Key, List<FileIncludeInfoImpl>> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<Key, List<FileIncludeInfoImpl>, FileContent> getIndexer() {
    return new DataIndexer<Key, List<FileIncludeInfoImpl>, FileContent>() {
      @Override
      @NotNull
      public Map<Key, List<FileIncludeInfoImpl>> map(@NotNull FileContent inputData) {

        Map<Key, List<FileIncludeInfoImpl>> map = new FactoryMap<Key, List<FileIncludeInfoImpl>>() {
          @Override
          protected List<FileIncludeInfoImpl> create(Key key) {
            return new ArrayList<>();
          }
        };

        for (FileIncludeProvider provider : Holder.myProviders) {
          if (!provider.acceptFile(inputData.getFile())) continue;
          FileIncludeInfo[] infos = provider.getIncludeInfos(inputData);
          if (infos.length  == 0) continue;
          List<FileIncludeInfoImpl> infoList = map.get(new FileKey(inputData.getFile()));

          for (FileIncludeInfo info : infos) {
            FileIncludeInfoImpl impl = new FileIncludeInfoImpl(info.path, info.offset, info.runtimeOnly, provider.getId());
            map.get(new IncludeKey(info.fileName)).add(impl);
            infoList.add(impl);
          }
        }
        return map;
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<Key> getKeyDescriptor() {
    return new KeyDescriptor<Key>() {
      @Override
      public int getHashCode(Key value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(Key val1, Key val2) {
        return val1.equals(val2);
      }

      @Override
      public void save(@NotNull DataOutput out, Key value) throws IOException {
        out.writeBoolean(value.isInclude());
        value.writeValue(out);
      }

      @Override
      public Key read(@NotNull DataInput in) throws IOException {
        boolean isInclude = in.readBoolean();
        return isInclude ? new IncludeKey(IOUtil.readUTF(in)) : new FileKey(in.readInt());
      }
    };
  }

  @NotNull
  @Override
  public DataExternalizer<List<FileIncludeInfoImpl>> getValueExternalizer() {
    return new DataExternalizer<List<FileIncludeInfoImpl>>() {
      @Override
      public void save(@NotNull DataOutput out, List<FileIncludeInfoImpl> value) throws IOException {
        out.writeInt(value.size());
        for (FileIncludeInfoImpl info : value) {
          IOUtil.writeUTF(out, info.path);
          out.writeInt(info.offset);
          out.writeBoolean(info.runtimeOnly);
          IOUtil.writeUTF(out, info.providerId);
        }
      }

      @Override
      public List<FileIncludeInfoImpl> read(@NotNull DataInput in) throws IOException {
        int size = in.readInt();
        ArrayList<FileIncludeInfoImpl> infos = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
          infos.add(new FileIncludeInfoImpl(IOUtil.readUTF(in), in.readInt(), in.readBoolean(), IOUtil.readUTF(in)));
        }
        return infos;
      }
    };
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new FileBasedIndex.FileTypeSpecificInputFilter() {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        if (file.getFileSystem() == JarFileSystem.getInstance()) {
          return false;
        }
        for (FileIncludeProvider provider : Holder.myProviders) {
          if (provider.acceptFile(file)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void registerFileTypesUsedForIndexing(@NotNull Consumer<FileType> fileTypeSink) {
        for (FileIncludeProvider provider : Holder.myProviders) {
          provider.registerFileTypesUsedForIndexing(fileTypeSink);
        }
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    int version = BASE_VERSION;
    for (FileIncludeProvider provider : Holder.myProviders) {
      version = version * 31 + (provider.getVersion() ^ provider.getClass().getName().hashCode());
    }
    return version;
  }

  interface Key {
    boolean isInclude();

    void writeValue(DataOutput out) throws IOException;
  }

  private static class IncludeKey implements Key {
    private final String myFileName;

    public IncludeKey(String fileName) {
      myFileName = fileName;
    }

    @Override
    public boolean isInclude() {
      return true;
    }

    @Override
    public void writeValue(DataOutput out) throws IOException {
      IOUtil.writeUTF(out, myFileName);
    }

    @Override
    public int hashCode() {
      return myFileName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof IncludeKey && ((IncludeKey)obj).myFileName.equals(myFileName);
    }
  }

  private static class FileKey implements Key {
    private final int myFileId;

    private FileKey(int fileId) {
      myFileId = fileId;
    }

    private FileKey(VirtualFile file) {
      myFileId = FileBasedIndex.getFileId(file);
    }

    @Override
    public boolean isInclude() {
      return false;
    }

    @Override
    public void writeValue(DataOutput out) throws IOException {
      out.writeInt(myFileId);
    }

    @Override
    public int hashCode() {
      return myFileId;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof FileKey && ((FileKey)obj).myFileId == myFileId;
    }
  }
}


