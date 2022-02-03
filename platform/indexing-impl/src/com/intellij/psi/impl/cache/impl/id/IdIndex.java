// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.lang.cacheBuilder.CacheBuilderRegistry;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.InlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

/**
 * An implementation of identifier index where key is a identifier hash and value is occurrence mask {@link UsageSearchContext}.
 *
 * Consider usage of {@link com.intellij.psi.search.PsiSearchHelper} or {@link com.intellij.psi.impl.cache.CacheManager} instead direct index access.
 */
@ApiStatus.Internal
public class IdIndex extends FileBasedIndexExtension<IdIndexEntry, Integer> {
  @NonNls public static final ID<IdIndexEntry, Integer> NAME = ID.create("IdIndex");

  private final KeyDescriptor<IdIndexEntry> myKeyDescriptor = new InlineKeyDescriptor<>() {
    @Override
    public IdIndexEntry fromInt(int n) {
      return new IdIndexEntry(n);
    }

    @Override
    public int toInt(IdIndexEntry idIndexEntry) {
      return idIndexEntry.getWordHashCode();
    }
  };

  @Override
  public int getVersion() {
    return 21 + IdIndexEntry.getUsedHashAlgorithmVersion();
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
    return new CompositeDataIndexer<IdIndexEntry, Integer, FileTypeSpecificSubIndexer<IdIndexer>, String>() {
      @Nullable
      @Override
      public FileTypeSpecificSubIndexer<IdIndexer> calculateSubIndexer(@NotNull IndexedFile file) {
        FileType type = file.getFileType();
        IdIndexer indexer = IdTableBuilding.getFileTypeIndexer(type);
        return indexer == null ? null : new FileTypeSpecificSubIndexer<>(indexer, file.getFileType());
      }

      @NotNull
      @Override
      public String getSubIndexerVersion(@NotNull FileTypeSpecificSubIndexer<IdIndexer> indexer) {
        return indexer.getSubIndexerType().getClass().getName() + ":" +
               indexer.getSubIndexerType().getVersion() + ":" +
               indexer.getFileType().getName();
      }

      @NotNull
      @Override
      public KeyDescriptor<String> getSubIndexerVersionDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
      }

      @NotNull
      @Override
      public Map<IdIndexEntry, Integer> map(@NotNull FileContent inputData,
                                            @NotNull FileTypeSpecificSubIndexer<IdIndexer> indexer) throws MapReduceIndexMappingException {
        IdIndexer subIndexerType = indexer.getSubIndexerType();
        try {
          return subIndexerType.map(inputData);
        }
        catch (Exception e) {
          if (e instanceof ControlFlowException) throw e;
          throw new MapReduceIndexMappingException(e, subIndexerType.getClass());
        }
      }
    };
  }

  @NotNull
  @Override
  public DataExternalizer<Integer> getValueExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(@NotNull final DataOutput out, final Integer value) throws IOException {
        out.write(value.intValue() & UsageSearchContext.ANY);
      }

      @Override
      public Integer read(@NotNull final DataInput in) throws IOException {
        return Integer.valueOf(in.readByte() & UsageSearchContext.ANY);
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<IdIndexEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> isIndexable(file.getFileType());
  }

  public static boolean isIndexable(FileType fileType) {
    return (fileType instanceof LanguageFileType && (fileType != PlainTextFileType.INSTANCE || !FileBasedIndex.IGNORE_PLAIN_TEXT_FILES)) ||
           fileType instanceof CustomSyntaxTableFileType ||
           IdTableBuilding.isIdIndexerRegistered(fileType) ||
           CacheBuilderRegistry.getInstance().getCacheBuilder(fileType) != null;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  @Override
  public boolean needsForwardIndexWhenSharing() {
    return false;
  }
}
