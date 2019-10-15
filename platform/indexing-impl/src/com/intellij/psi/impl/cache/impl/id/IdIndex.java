// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.lang.cacheBuilder.CacheBuilderRegistry;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.InlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class IdIndex extends FileBasedIndexExtension<IdIndexEntry, Integer> implements DocumentChangeDependentIndex {
  @NonNls public static final ID<IdIndexEntry, Integer> NAME = ID.create("IdIndex");

  private final FileBasedIndex.InputFilter myInputFilter = file -> isIndexable(file.getFileType());

  private final DataExternalizer<Integer> myValueExternalizer = new DataExternalizer<Integer>() {
    @Override
    public void save(@NotNull final DataOutput out, final Integer value) throws IOException {
      out.write(value.intValue() & UsageSearchContext.ANY);
    }

    @Override
    public Integer read(@NotNull final DataInput in) throws IOException {
      return Integer.valueOf(in.readByte() & UsageSearchContext.ANY);
    }
  };

  private final KeyDescriptor<IdIndexEntry> myKeyDescriptor = new InlineKeyDescriptor<IdIndexEntry>() {
    @Override
    public IdIndexEntry fromInt(int n) {
      return new IdIndexEntry(n);
    }

    @Override
    public int toInt(IdIndexEntry idIndexEntry) {
      return idIndexEntry.getWordHashCode();
    }
  };

  private final DataIndexer<IdIndexEntry, Integer, FileContent> myIndexer = new DataIndexer<IdIndexEntry, Integer, FileContent>() {
    @Override
    @NotNull
    public Map<IdIndexEntry, Integer> map(@NotNull final FileContent inputData) {
      final IdIndexer indexer = IdTableBuilding.getFileTypeIndexer(inputData.getFileType());
      if (indexer != null) {
        return indexer.map(inputData);
      }

      return Collections.emptyMap();
    }
  };

  @Override
  public int getVersion() {
    return 16 + (FileBasedIndex.ourSnapshotMappingsEnabled ? 0xFF:0); // TODO: version should enumerate all word scanner versions and build version upon that set
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @NotNull
  @Override
  public ID<IdIndexEntry,Integer> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<IdIndexEntry, Integer, FileContent> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public DataExternalizer<Integer> getValueExternalizer() {
    return myValueExternalizer;
  }

  @NotNull
  @Override
  public KeyDescriptor<IdIndexEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public static boolean isIndexable(FileType fileType) {
    return fileType instanceof LanguageFileType ||
           fileType instanceof CustomSyntaxTableFileType ||
           IdTableBuilding.isIdIndexerRegistered(fileType) ||
           CacheBuilderRegistry.getInstance().getCacheBuilder(fileType) != null;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  public static boolean hasIdentifierInFile(@NotNull PsiFile file, @NotNull String name) {
    PsiUtilCore.ensureValid(file);
    if (file.getVirtualFile() == null || DumbService.isDumb(file.getProject())) {
      return StringUtil.contains(file.getViewProvider().getContents(), name);
    }

    GlobalSearchScope scope = GlobalSearchScope.fileScope(file);
    return !FileBasedIndex.getInstance().getContainingFiles(NAME, new IdIndexEntry(name, true), scope).isEmpty();
  }
}
