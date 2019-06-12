// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.impl.id.PlatformIdTableBuilding;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IntInlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class TodoIndex extends FileBasedIndexExtension<TodoIndexEntry, Integer> {
  @NonNls
  public static final ID<TodoIndexEntry, Integer> NAME = ID.create("TodoIndex");

  public TodoIndex() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(IndexPatternProvider.INDEX_PATTERNS_CHANGED, new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        FileBasedIndex.getInstance().requestRebuild(NAME);
      }
    });
  }

  private final KeyDescriptor<TodoIndexEntry> myKeyDescriptor = new KeyDescriptor<TodoIndexEntry>() {
    @Override
    public int getHashCode(final TodoIndexEntry value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(final TodoIndexEntry val1, final TodoIndexEntry val2) {
      return val1.equals(val2);
    }

    @Override
    public void save(@NotNull final DataOutput out, final TodoIndexEntry value) throws IOException {
      out.writeUTF(value.pattern);
      out.writeBoolean(value.caseSensitive);
    }

    @Override
    public TodoIndexEntry read(@NotNull final DataInput in) throws IOException {
      final String pattern = in.readUTF();
      final boolean caseSensitive = in.readBoolean();
      return new TodoIndexEntry(pattern, caseSensitive);
    }
  };

  private final DataExternalizer<Integer> myValueExternalizer = new IntInlineKeyDescriptor() {
    @Override
    protected boolean isCompactFormat() {
      return true;
    }
  };

  private final DataIndexer<TodoIndexEntry, Integer, FileContent> myIndexer = new DataIndexer<TodoIndexEntry, Integer, FileContent>() {
    @Override
    @NotNull
    public Map<TodoIndexEntry, Integer> map(@NotNull final FileContent inputData) {
      final VirtualFile file = inputData.getFile();
      final DataIndexer<TodoIndexEntry, Integer, FileContent> indexer = PlatformIdTableBuilding
        .getTodoIndexer(inputData.getFileType(), file);
      if (indexer != null) {
        return indexer.map(inputData);
      }
      return Collections.emptyMap();
    }
  };

  protected final FileBasedIndex.InputFilter myInputFilter = file -> {
    if (!TodoIndexers.needsTodoIndex(file)) return false;

    final FileType fileType = file.getFileType();

    if (fileType instanceof LanguageFileType) {
      return LanguageParserDefinitions.INSTANCE.forLanguage(((LanguageFileType)fileType).getLanguage()) != null;
    }

    return PlatformIdTableBuilding.isTodoIndexerRegistered(fileType) ||
           fileType instanceof CustomSyntaxTableFileType;
  };

  @Override
  public int getVersion() {
    int version = 11;
    Map<FileType, DataIndexer<TodoIndexEntry, Integer, FileContent>> extensions = TodoIndexers.INSTANCE.getAllRegisteredExtensions();
    List<FileType> types = new ArrayList<>(extensions.keySet());
    types.sort((o1, o2) -> Comparing.compare(o1.getName(), o2.getName()));

    for (FileType fileType : types) {
      DataIndexer<TodoIndexEntry, Integer, FileContent> indexer = extensions.get(fileType);

      int versionFromIndexer = indexer instanceof VersionedTodoIndexer ? (((VersionedTodoIndexer)indexer).getVersion()) : 0xFF;
      version = version * 31 + (versionFromIndexer ^ indexer.getClass().getName().hashCode());
    }
    return version;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @NotNull
  @Override
  public ID<TodoIndexEntry, Integer> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<TodoIndexEntry, Integer, FileContent> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<TodoIndexEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @NotNull
  @Override
  public DataExternalizer<Integer> getValueExternalizer() {
    return myValueExternalizer;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }
}
