// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.include;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Dmitry Avdeev
 */
public final class FileIncludeIndex extends FileBasedIndexExtension<String, List<FileIncludeInfoImpl>> {
  public static final ID<String,List<FileIncludeInfoImpl>> INDEX_ID = ID.create("fileIncludes");

  private static final int BASE_VERSION = 6;

  @NotNull
  public static List<FileIncludeInfo> getIncludes(@NotNull VirtualFile file, @NotNull Project project) {
    Map<String, List<FileIncludeInfoImpl>> data = FileBasedIndex.getInstance().getFileData(INDEX_ID, file, project);
    return ContainerUtil.flatten(data.values());
  }

  @NotNull
  public static MultiMap<VirtualFile, FileIncludeInfoImpl> getIncludingFileCandidates(String fileName, @NotNull GlobalSearchScope scope) {
    final MultiMap<VirtualFile, FileIncludeInfoImpl> result = new MultiMap<>();
    FileBasedIndex.getInstance().processValues(INDEX_ID, fileName, null, (file, value) -> {
      result.put(file, value);
      return true;
    }, scope);
    return result;
  }

  @NotNull
  @Override
  public ID<String, List<FileIncludeInfoImpl>> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<String, List<FileIncludeInfoImpl>, FileContent> getIndexer() {
    return new CompositeDataIndexer<String, List<FileIncludeInfoImpl>, Set<FileIncludeProvider>, Set<String>>() {
      @NotNull
      @Override
      public Set<FileIncludeProvider> calculateSubIndexer(@NotNull IndexedFile file) {
        return
          FileIncludeProvider
            .EP_NAME
            .getExtensionList()
            .stream()
            .filter(provider -> provider.acceptFile(file.getFile()))
            .collect(Collectors.toSet());
      }

      @NotNull
      @Override
      public Set<String> getSubIndexerVersion(@NotNull Set<FileIncludeProvider> providers) {
        return ContainerUtil.map2Set(providers, provider -> provider.getId() + ":" + provider.getVersion());
      }

      @NotNull
      @Override
      public KeyDescriptor<Set<String>> getSubIndexerVersionDescriptor() {
        return new StringSetDescriptor();
      }

      @NotNull
      @Override
      public Map<String, List<FileIncludeInfoImpl>> map(@NotNull FileContent inputData, @NotNull Set<FileIncludeProvider> providers) {
        Map<String, List<FileIncludeInfoImpl>> map = FactoryMap.create(key -> new ArrayList<>());
        for (FileIncludeProvider provider : providers) {
          FileIncludeInfo[] includeInfos;
          try {
            includeInfos = provider.getIncludeInfos(inputData);
          } catch (Exception e) {
            if (e instanceof ControlFlowException) throw e;
            throw new MapReduceIndexMappingException(e, provider.getClass());
          }
          for (FileIncludeInfo info : includeInfos) {
            FileIncludeInfoImpl impl = new FileIncludeInfoImpl(info.path, info.offset, info.runtimeOnly, provider.getId());
            map.get(info.fileName).add(impl);
          }
        }
        return map;
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<List<FileIncludeInfoImpl>> getValueExternalizer() {
    return new DataExternalizer<>() {
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
    return new DefaultFileTypeSpecificWithProjectInputFilter() {
      @Override
      public boolean acceptInput(@NotNull IndexedFile indexedFile) {
        VirtualFile file = indexedFile.getFile();
        if (file.getFileSystem() == JarFileSystem.getInstance()) {
          return false;
        }
        for (FileIncludeProvider provider : FileIncludeProvider.EP_NAME.getExtensionList()) {
          if (provider.acceptFile(file, indexedFile.getProject())) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink) {
        for (FileIncludeProvider provider : FileIncludeProvider.EP_NAME.getExtensionList()) {
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
    // composite indexer
    return BASE_VERSION;
  }

  private static class StringSetDescriptor implements KeyDescriptor<Set<String>> {
    @Override
    public int getHashCode(Set<String> value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(Set<String> val1, Set<String> val2) {
      return val1.equals(val2);
    }

    @Override
    public void save(@NotNull DataOutput out, Set<String> value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.size());
      for (String s : value) {
        IOUtil.writeUTF(out, s);
      }
    }

    @Override
    public Set<String> read(@NotNull DataInput in) throws IOException {
      int size = DataInputOutputUtil.readINT(in);
      Set<String> result = new HashSet<>(size);
      for (int i = 0; i < size; i++) {
        result.add(IOUtil.readUTF(in));
      }
      return result;
    }
  }

}


