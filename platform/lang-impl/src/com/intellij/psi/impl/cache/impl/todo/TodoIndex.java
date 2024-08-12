// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.impl.cache.impl.id.PlatformIdTableBuilding;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.ThreeState;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.hints.BaseFileTypeInputFilter;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy.AFTER_SUBSTITUTION;

/**
 * @author Eugene Zhuravlev
 */
public final class TodoIndex extends SingleEntryFileBasedIndexExtension<Map<TodoIndexEntry, Integer>> {
  /** @deprecated Use {@link com.intellij.psi.impl.cache.TodoCacheManager} methods instead **/
  @Deprecated
  @ApiStatus.Internal
  public static final ID<Integer, Map<TodoIndexEntry, Integer>> NAME = ID.create("TodoIndex");

  public TodoIndex() {
    ApplicationManager.getApplication().getMessageBus().simpleConnect()
      .subscribe(IndexPatternProvider.INDEX_PATTERNS_CHANGED, __ -> FileBasedIndex.getInstance().requestRebuild(NAME));
  }

  private final DataExternalizer<Map<TodoIndexEntry, Integer>> myValueExternalizer = new DataExternalizer<>() {
    @Override
    public void save(@NotNull DataOutput out,
                     @NotNull Map<TodoIndexEntry, Integer> value) throws IOException {
      int size = value.size();
      DataInputOutputUtil.writeINT(out, size);
      if (size == 0) return;
      for (TodoIndexEntry entry : value.keySet()) {
        IOUtil.writeUTF(out, entry.pattern);
        out.writeBoolean(entry.caseSensitive);
        DataInputOutputUtil.writeINT(out, value.get(entry));
      }
    }

    @Override
    public @NotNull Map<TodoIndexEntry, Integer> read(@NotNull DataInput in) throws IOException {
      int size = DataInputOutputUtil.readINT(in);
      if (size == 0) return Collections.emptyMap();
      Map<TodoIndexEntry, Integer> map = new HashMap<>(size);
      for (int i = 0; i < size; i++) {
        String pattern = IOUtil.readUTF(in);
        boolean caseSensitive = in.readBoolean();
        TodoIndexEntry entry = new TodoIndexEntry(pattern, caseSensitive);
        map.put(entry, DataInputOutputUtil.readINT(in));
      }
      return map;
    }
  };

  private final SingleEntryIndexer<Map<TodoIndexEntry, Integer>> myIndexer =
    new SingleEntryCompositeIndexer<Map<TodoIndexEntry, Integer>, DataIndexer<TodoIndexEntry, Integer, FileContent>, String>(false) {
      @Override
      public @Nullable DataIndexer<TodoIndexEntry, Integer, FileContent> calculateSubIndexer(@NotNull IndexedFile file) {
        return PlatformIdTableBuilding.getTodoIndexer(file.getFileType());
      }

      @Override
      public @NotNull String getSubIndexerVersion(@NotNull DataIndexer<TodoIndexEntry, Integer, FileContent> indexer) {
        int version = indexer instanceof VersionedTodoIndexer ? (((VersionedTodoIndexer)indexer).getVersion()) : 0xFF;
        return indexer.getClass().getName() + ":" + version;
      }

      @Override
      public @NotNull KeyDescriptor<String> getSubIndexerVersionDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
      }

      @Override
      protected @Nullable Map<TodoIndexEntry, Integer> computeValue(@NotNull FileContent inputData,
                                                                    @NotNull DataIndexer<TodoIndexEntry, Integer, FileContent> indexer) {
        try {
          Map<TodoIndexEntry, Integer> result = indexer.map(inputData);
          return result.isEmpty() ? null : result;
        }
        catch (Exception e) {
          if (e instanceof ControlFlowException) throw e;
          throw new MapReduceIndexMappingException(e, indexer.getClass());
        }
      }
    };

  @Override
  public int getVersion() {
    return 13;
  }

  @Override
  public @NotNull ID<Integer, Map<TodoIndexEntry, Integer>> getName() {
    return NAME;
  }

  @Override
  public @NotNull SingleEntryIndexer<Map<TodoIndexEntry, Integer>> getIndexer() {
    return myIndexer;
  }

  @Override
  public @NotNull DataExternalizer<Map<TodoIndexEntry, Integer>> getValueExternalizer() {
    return myValueExternalizer;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new BaseFileTypeInputFilter(AFTER_SUBSTITUTION) {
      @Override
      public boolean slowPathIfFileTypeHintUnsure(@NotNull IndexedFile file) {
        return TodoIndexers.needsTodoIndex(file);
      }

      @Override
      public @NotNull ThreeState acceptFileType(@NotNull FileType fileType) {
        DataIndexer<TodoIndexEntry, Integer, FileContent> indexer = PlatformIdTableBuilding.getTodoIndexer(fileType);
        if (indexer == null) return ThreeState.NO;
        else return ThreeState.UNSURE;
      }
    };
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }
}
