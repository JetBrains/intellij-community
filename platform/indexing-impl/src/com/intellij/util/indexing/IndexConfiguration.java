// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public final class IndexConfiguration {
  private final Int2ObjectMap<Pair<UpdatableIndex<?, ?, FileContent, ?>, FileBasedIndex.InputFilter>> myIndices =
    new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<Throwable> myInitializationProblems = new Int2ObjectOpenHashMap<>();
  private final List<ID<?, ?>> myIndexIds = new ArrayList<>();
  private final Object2IntMap<ID <?, ?>> myIndexIdToVersionMap = new Object2IntOpenHashMap<>();
  private final List<ID<?, ?>> myIndicesWithoutFileTypeInfo = new ArrayList<>();
  private final Map<FileType, List<ID<?, ?>>> myFileType2IndicesWithFileTypeInfoMap = CollectionFactory.createSmallMemoryFootprintMap();
  private volatile boolean myFreezed;

  @ApiStatus.Internal
  public @Nullable <K, V> UpdatableIndex<K, V, FileContent, ?> getIndex(@NotNull ID<K, V> indexId) {
    assert myFreezed;
    final Pair<UpdatableIndex<?, ?, FileContent, ?>, FileBasedIndex.InputFilter> pair = myIndices.get(indexId.getUniqueId());

    //noinspection unchecked
    return (UpdatableIndex<K, V, FileContent, ?>)Pair.getFirst(pair);
  }

  @Nullable
  @ApiStatus.Internal
  public Throwable getInitializationProblem(@NotNull ID<?, ?> indexId) {
    return myInitializationProblems.get(indexId.getUniqueId());
  }

  @ApiStatus.Internal
  public @NotNull FileBasedIndex.InputFilter getInputFilter(@NotNull ID<?, ?> indexId) {
    assert myFreezed;
    final Pair<UpdatableIndex<?, ?, FileContent, ?>, FileBasedIndex.InputFilter> pair = myIndices.get(indexId.getUniqueId());

    assert pair != null : "Index data is absent for index " + indexId;

    return pair.getSecond();
  }

  @ApiStatus.Internal
  public void freeze() {
    myFreezed = true;
  }

  @ApiStatus.Internal
  public void registerIndexInitializationProblem(@NotNull ID<?, ?> indexId, @NotNull Throwable problemTrace) {
    assert !myFreezed;

    synchronized (myInitializationProblems) {
      myInitializationProblems.put(indexId.getUniqueId(), problemTrace);
    }
  }

  @ApiStatus.Internal
  public <K, V> void registerIndex(@NotNull ID<K, V> indexId,
                            @NotNull UpdatableIndex<K, V, FileContent, ?> index,
                            @NotNull FileBasedIndex.InputFilter inputFilter,
                            int version) {
    assert !myFreezed;

    synchronized (myIndices) {
      myIndexIds.add(indexId);
      myIndexIdToVersionMap.put(indexId, version);

      Collection<FileType> associatedFileTypes = getAssociatedFileTypes(inputFilter);
      if (associatedFileTypes != null) {
        for (FileType fileType : associatedFileTypes) {
          List<ID<?, ?>> ids = myFileType2IndicesWithFileTypeInfoMap.computeIfAbsent(fileType, __ -> new ArrayList<>(5));
          ids.add(indexId);
        }
      }
      else {
        myIndicesWithoutFileTypeInfo.add(indexId);
      }

      Pair<UpdatableIndex<?, ?, FileContent, ?>, FileBasedIndex.InputFilter> old =
        myIndices.put(indexId.getUniqueId(), new Pair<>(index, inputFilter));
      if (old != null) {
        throw new IllegalStateException("Index " + old.first + " already registered for the name '" + indexId + "'");
      }
    }
  }

  private static @Nullable Collection<FileType> getAssociatedFileTypes(@NotNull FileBasedIndex.InputFilter inputFilter) {
    Set<FileType> addedTypes;
    if (inputFilter instanceof FileBasedIndex.FileTypeSpecificInputFilter) {
      addedTypes = new HashSet<>();
      ((FileBasedIndex.FileTypeSpecificInputFilter)inputFilter).registerFileTypesUsedForIndexing(type -> {
        if (type != null) addedTypes.add(type);
      });
    }
    else {
      addedTypes = null;
    }
    return addedTypes;
  }

  @ApiStatus.Internal
  public @NotNull List<ID<?, ?>> getFileTypesForIndex(@NotNull FileType fileType) {
    assert myFreezed;
    List<ID<?, ?>> ids = myFileType2IndicesWithFileTypeInfoMap.get(fileType);
    if (ids == null) ids = myIndicesWithoutFileTypeInfo;
    return ids;
  }

  @ApiStatus.Internal
  public void finalizeFileTypeMappingForIndices() {
    assert !myFreezed;
    synchronized (myIndices) {
      for (List<ID<?, ?>> value : myFileType2IndicesWithFileTypeInfoMap.values()) {
        value.addAll(myIndicesWithoutFileTypeInfo);
      }
    }
  }

  @ApiStatus.Internal
  public @NotNull Collection<ID<?, ?>> getIndexIDs() {
    assert myFreezed;
    return myIndexIds;
  }

  @ApiStatus.Internal
  public int getIndexVersion(@NotNull ID<?, ?> id) {
    assert myFreezed;
    return myIndexIdToVersionMap.getInt(id);
  }
}
