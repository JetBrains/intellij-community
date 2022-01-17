// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.psi.impl.cache.impl.id.PlatformIdTableBuilding;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public final class TodoIndex extends SingleEntryFileBasedIndexExtension<Map<TodoIndexEntry, Integer>> {
  @NonNls
  public static final ID<Integer, Map<TodoIndexEntry, Integer>> NAME = ID.create("TodoIndex");

  public TodoIndex() {
    ApplicationManager.getApplication().getMessageBus().connect()
      .subscribe(IndexPatternProvider.INDEX_PATTERNS_CHANGED, new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          FileBasedIndex.getInstance().requestRebuild(NAME);
        }
      });
  }

  private final DataExternalizer<Map<TodoIndexEntry, Integer>> myValueExternalizer = new DataExternalizer<>() {
    @Override
    public void save(@NotNull DataOutput out,
                     @NotNull Map<TodoIndexEntry, Integer> value) throws IOException {
      int size = value.size();
      DataInputOutputUtil.writeINT(out, size);
      if (size <= 0) return;
      for (TodoIndexEntry entry : value.keySet()) {
        out.writeUTF(entry.pattern);
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
        String pattern = in.readUTF();
        boolean caseSensitive = in.readBoolean();
        TodoIndexEntry entry = new TodoIndexEntry(pattern, caseSensitive);
        map.put(entry, DataInputOutputUtil.readINT(in));
      }
      return map;
    }
  };

  private final SingleEntryIndexer<Map<TodoIndexEntry, Integer>> myIndexer =
    new SingleEntryCompositeIndexer<Map<TodoIndexEntry, Integer>, DataIndexer<TodoIndexEntry, Integer, FileContent>, String>(false) {
      @Nullable
      @Override
      public DataIndexer<TodoIndexEntry, Integer, FileContent> calculateSubIndexer(@NotNull IndexedFile file) {
        return PlatformIdTableBuilding.getTodoIndexer(file.getFileType());
      }

      @NotNull
      @Override
      public String getSubIndexerVersion(@NotNull DataIndexer<TodoIndexEntry, Integer, FileContent> indexer) {
        int version = indexer instanceof VersionedTodoIndexer ? (((VersionedTodoIndexer)indexer).getVersion()) : 0xFF;
        return indexer.getClass().getName() + ":" + version;
      }

      @NotNull
      @Override
      public KeyDescriptor<String> getSubIndexerVersionDescriptor() {
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
    // composite indexer
    return 12;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @NotNull
  @Override
  public ID<Integer, Map<TodoIndexEntry, Integer>> getName() {
    return NAME;
  }

  @Override
  public @NotNull SingleEntryIndexer<Map<TodoIndexEntry, Integer>> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public DataExternalizer<Map<TodoIndexEntry, Integer>> getValueExternalizer() {
    return myValueExternalizer;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new FileBasedIndex.ProjectSpecificInputFilter() {
      @Override
      public boolean acceptInput(@NotNull IndexedFile file) {
        if (!TodoIndexers.needsTodoIndex(file)) return false;

        final FileType fileType = file.getFileType();

        if (fileType instanceof LanguageFileType) {
          return LanguageParserDefinitions.INSTANCE.forLanguage(((LanguageFileType)fileType).getLanguage()) != null;
        }

        return PlatformIdTableBuilding.isTodoIndexerRegistered(fileType) ||
               fileType instanceof CustomSyntaxTableFileType;
      }
    };
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }
}
