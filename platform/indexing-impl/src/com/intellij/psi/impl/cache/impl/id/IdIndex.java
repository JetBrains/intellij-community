// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.fileTypes.FileType;
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

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * An implementation of identifier index where the key is an identifier hash,
 * and the value is an occurrence mask ({@link UsageSearchContext}).
 *<p>
 * Consider usage of {@link com.intellij.psi.search.PsiSearchHelper} or {@link com.intellij.psi.impl.cache.CacheManager} instead of direct index access.
 */
@ApiStatus.Internal
public class IdIndex extends FileBasedIndexExtension<IdIndexEntry, Integer> {
  private static final Logger LOG = Logger.getInstance(IdIndex.class);
  private static final ThrottledLogger THROTTLED_LOGGER = new ThrottledLogger(LOG, MINUTES.toMillis(1));

  public static final @NonNls ID<IdIndexEntry, Integer> NAME = ID.create("IdIndex");

  private static final FileBasedIndex.InputFilter INPUT_FILES_FILTER = new IdIndexFilter();

  private static final KeyDescriptor<IdIndexEntry> KEY_DESCRIPTOR = new InlineKeyDescriptor<>() {
    @Override
    public IdIndexEntry fromInt(int n) {
      return new IdIndexEntry(n);
    }

    @Override
    public int toInt(IdIndexEntry idIndexEntry) {
      return idIndexEntry.getWordHashCode();
    }
  };

  private static final DataExternalizer<Integer> VALUE_EXTERNALIZER = new DataExternalizer<>() {
    @Override
    public void save(@NotNull DataOutput out, Integer value) throws IOException {
      out.write(value.intValue() & UsageSearchContext.ANY);
    }

    @Override
    public Integer read(@NotNull DataInput in) throws IOException {
      return Integer.valueOf(in.readByte() & UsageSearchContext.ANY);
    }
  };

  @Override
  public int getVersion() {
    return 21 + IdIndexEntry.getUsedHashAlgorithmVersion();
  }

  @Override
  public @NotNull ID<IdIndexEntry,Integer> getName() {
    return NAME;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILES_FILTER;
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
          Map<IdIndexEntry, Integer> idsMap = subIndexerType.map(inputData);
          if (!(idsMap instanceof IdEntryToScopeMapImpl) && !idsMap.isEmpty() ) {
            //RC: it is strongly recommended for all the IdIndexer implementations to use IdDataConsumer helper to
            //    collect IDs and occurrence masks. Such a helper class returns IdEntryToScopeMapImpl instance,
            //    which is  optimized for memory consumption and serialization. All the implementations in intellij
            //    follow that rule.
            //    But if there are some implementations outside our control that doesn't follow, we 'correct' it by
            //    wrapping the map into IdEntryToScopeMapImpl -- with the associated costs -- and log a warning so
            //    devs could fix it later:
            THROTTLED_LOGGER.warn( () ->
              subIndexerType.getClass() + " for [" + inputData.getFile().getPath() + "] returned non-IdEntryToScopeMapImpl " +
              "map (" + idsMap.getClass() +")." +
              "This is not incorrect, but ineffective -- it is strongly recommended to use " +
              "com.intellij.psi.impl.cache.impl.id.IdDataConsumer helper class to collect IDs " +
              "and occurrence masks (instead of plain Map impl) in your IdIndexer implementation "
            );
            return new IdEntryToScopeMapImpl(idsMap);
          }
          return idsMap;
        }
        catch (Exception e) {
          if (e instanceof ControlFlowException) throw e;
          throw new MapReduceIndexMappingException(e, subIndexerType.getClass());
        }
      }
    };
  }

  @Override
  public @NotNull KeyDescriptor<IdIndexEntry> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  @Override
  public @NotNull DataExternalizer<Integer> getValueExternalizer() {
    return VALUE_EXTERNALIZER;
  }

  @Override
  public int getCacheSize() {
    return 64 * super.getCacheSize();
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
