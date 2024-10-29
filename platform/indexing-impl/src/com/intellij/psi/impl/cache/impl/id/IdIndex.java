// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.hints.FileTypeInputFilterPredicate;
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

import static com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION;

/**
 * An implementation of identifier index where the key is an identifier hash,
 * and the value is an occurrence mask ({@link UsageSearchContext}).
 *<p>
 * Consider usage of {@link com.intellij.psi.search.PsiSearchHelper} or {@link com.intellij.psi.impl.cache.CacheManager} instead of direct index access.
 */
@ApiStatus.Internal
public class IdIndex extends FileBasedIndexExtension<IdIndexEntry, Integer> {
  public static final @NonNls ID<IdIndexEntry, Integer> NAME = ID.create("IdIndex");

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

  @Override
  public int getCacheSize() {
    return 64 * super.getCacheSize();
  }

  @Override
  public @NotNull ID<IdIndexEntry,Integer> getName() {
    return NAME;
  }

  @Override
  public @NotNull DataIndexer<IdIndexEntry, Integer, FileContent> getIndexer() {
    return new CompositeDataIndexer<IdIndexEntry, Integer, FileTypeSpecificSubIndexer<IdIndexer>, String>() {
      @Override
      public @Nullable FileTypeSpecificSubIndexer<IdIndexer> calculateSubIndexer(@NotNull IndexedFile file) {
        FileType type = file.getFileType();
        IdIndexer indexer = IdTableBuilding.getFileTypeIndexer(type);
        return indexer == null ? null : new FileTypeSpecificSubIndexer<>(indexer, file.getFileType());
      }

      @Override
      public @NotNull String getSubIndexerVersion(@NotNull FileTypeSpecificSubIndexer<IdIndexer> indexer) {
        return indexer.getSubIndexerType().getClass().getName() + ":" +
               indexer.getSubIndexerType().getVersion() + ":" +
               indexer.getFileType().getName();
      }

      @Override
      public @NotNull KeyDescriptor<String> getSubIndexerVersionDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
      }

      @Override
      public @NotNull Map<IdIndexEntry, Integer> map(@NotNull FileContent inputData,
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

  @Override
  public @NotNull DataExternalizer<Integer> getValueExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(final @NotNull DataOutput out, final Integer value) throws IOException {
        out.write(value.intValue() & UsageSearchContext.ANY);
      }

      @Override
      public Integer read(final @NotNull DataInput in) throws IOException {
        return Integer.valueOf(in.readByte() & UsageSearchContext.ANY);
      }
    };
  }

  @Override
  public @NotNull KeyDescriptor<IdIndexEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new FileTypeInputFilterPredicate(BEFORE_SUBSTITUTION, fileType -> isIndexable(fileType));
  }

  public static boolean isIndexable(FileType fileType) {
    return (fileType instanceof LanguageFileType && (fileType != PlainTextFileType.INSTANCE || !FileBasedIndex.IGNORE_PLAIN_TEXT_FILES)) ||
           IdTableBuilding.getFileTypeIndexer(fileType) != null;
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
