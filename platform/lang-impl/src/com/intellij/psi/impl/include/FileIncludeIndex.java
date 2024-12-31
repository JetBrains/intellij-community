// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.include;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.extensions.ExtensionPointName;
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
import org.jetbrains.annotations.Unmodifiable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FileIncludeIndex extends FileBasedIndexExtension<String, List<FileIncludeInfoImpl>> {
  private static final ID<String,List<FileIncludeInfoImpl>> INDEX_ID = ID.create("fileIncludes");
  public static final ExtensionPointName<FileIncludeProvider>
    FILE_INCLUDE_PROVIDER_EP_NAME = ExtensionPointName.create("com.intellij.include.provider");

  public static @Unmodifiable @NotNull Stream<FileIncludeInfo> getIncludes(@NotNull VirtualFile file, @NotNull Project project) {
    Map<String, List<FileIncludeInfoImpl>> data = FileBasedIndex.getInstance().getFileData(INDEX_ID, file, project);
    return data.values().stream().flatMap(Collection::stream);
  }

  public static @NotNull MultiMap<VirtualFile, FileIncludeInfoImpl> getIncludingFileCandidates(String fileName, @NotNull GlobalSearchScope scope) {
    final MultiMap<VirtualFile, FileIncludeInfoImpl> result = new MultiMap<>();
    FileBasedIndex.getInstance().processValues(INDEX_ID, fileName, null, (file, value) -> {
      result.put(file, value);
      return true;
    }, scope);
    return result;
  }

  @Override
  public @NotNull ID<String, List<FileIncludeInfoImpl>> getName() {
    return INDEX_ID;
  }

  @Override
  public @NotNull DataIndexer<String, List<FileIncludeInfoImpl>, FileContent> getIndexer() {
    return new CompositeDataIndexer<String, List<FileIncludeInfoImpl>, Set<FileIncludeProvider>, Set<String>>() {
      @Override
      public @NotNull Set<FileIncludeProvider> calculateSubIndexer(@NotNull IndexedFile file) {
        return FILE_INCLUDE_PROVIDER_EP_NAME
            .getExtensionList()
            .stream()
            .filter(provider -> provider.acceptFile(file.getFile()))
            .collect(Collectors.toSet());
      }

      @Override
      public @Unmodifiable @NotNull Set<String> getSubIndexerVersion(@NotNull Set<FileIncludeProvider> providers) {
        return ContainerUtil.map2Set(providers, provider -> provider.getId() + ":" + provider.getVersion());
      }

      @Override
      public @NotNull KeyDescriptor<Set<String>> getSubIndexerVersionDescriptor() {
        return new StringSetDescriptor();
      }

      @Override
      public @NotNull Map<String, List<FileIncludeInfoImpl>> map(@NotNull FileContent inputData, @NotNull Set<FileIncludeProvider> providers) {
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

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull DataExternalizer<List<FileIncludeInfoImpl>> getValueExternalizer() {
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

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificWithProjectInputFilter() {
      @Override
      public boolean acceptInput(@NotNull IndexedFile indexedFile) {
        VirtualFile file = indexedFile.getFile();
        if (file.getFileSystem() == JarFileSystem.getInstance()) {
          return false;
        }
        for (FileIncludeProvider provider : FILE_INCLUDE_PROVIDER_EP_NAME.getExtensionList()) {
          if (provider.acceptFile(file, indexedFile.getProject())) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink) {
        for (FileIncludeProvider provider : FILE_INCLUDE_PROVIDER_EP_NAME.getExtensionList()) {
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
    return 6;
  }

  private static final class StringSetDescriptor implements KeyDescriptor<Set<String>> {
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


