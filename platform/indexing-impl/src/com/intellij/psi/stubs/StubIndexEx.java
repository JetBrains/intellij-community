// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.KeyValueUpdateProcessor;
import com.intellij.util.indexing.impl.RemovedKeyProcessor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

@ApiStatus.Internal
public abstract class StubIndexEx extends StubIndex {
  static void initExtensions() {
    // initialize stub index keys
    for (StubIndexExtension<?, ?> extension : StubIndexExtension.EP_NAME.getExtensionList()) {
      extension.getKey();
    }
  }

  public <K> void updateIndex(@NotNull StubIndexKey<K, ?> stubIndexKey,
                              int fileId,
                              @NotNull Set<? extends K> oldKeys,
                              @NotNull Set<? extends K> newKeys) {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      try {
        if (FileBasedIndexEx.DO_TRACE_STUB_INDEX_UPDATE) {
          getLogger().info("stub index '" + stubIndexKey + "' update: " + fileId +
                   " old = " + Arrays.toString(oldKeys.toArray()) +
                   " new  = " + Arrays.toString(newKeys.toArray()) +
                   " updated_id = " + System.identityHashCode(newKeys));
        }
        final UpdatableIndex<K, Void, FileContent> index = getIndex(stubIndexKey);
        if (index == null) return;
        index.updateWithMap(new AbstractUpdateData<>(fileId) {
          @Override
          protected boolean iterateKeys(@NotNull KeyValueUpdateProcessor<? super K, ? super Void> addProcessor,
                                        @NotNull KeyValueUpdateProcessor<? super K, ? super Void> updateProcessor,
                                        @NotNull RemovedKeyProcessor<? super K> removeProcessor) throws StorageException {
            boolean modified = false;

            for (K oldKey : oldKeys) {
              if (!newKeys.contains(oldKey)) {
                removeProcessor.process(oldKey, fileId);
                if (!modified) modified = true;
              }
            }

            for (K oldKey : newKeys) {
              if (!oldKeys.contains(oldKey)) {
                addProcessor.process(oldKey, null, fileId);
                if (!modified) modified = true;
              }
            }

            if (FileBasedIndexEx.DO_TRACE_STUB_INDEX_UPDATE) {
              getLogger().info("keys iteration finished updated_id = " + System.identityHashCode(newKeys) + "; modified = " + modified);
            }

            return modified;
          }
        });
      }
      catch (StorageException e) {
        getLogger().info(e);
        forceRebuild(e);
      }
    });
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  @NotNull
  public abstract Logger getLogger();

  @ApiStatus.Experimental
  @ApiStatus.Internal
  protected abstract <Key> UpdatableIndex<Key, Void, FileContent> getIndex(@NotNull StubIndexKey<Key, ?> indexKey);

  @ApiStatus.Internal
  public static @NotNull <K> FileBasedIndexExtension<K, Void> wrapStubIndexExtension(StubIndexExtension<K, ?> extension) {
    return new FileBasedIndexExtension<>() {
      @Override
      public @NotNull ID<K, Void> getName() {
        @SuppressWarnings("unchecked") ID<K, Void> key = (ID<K, Void>)extension.getKey();
        return key;
      }

      @Override
      public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return f -> {
          throw new UnsupportedOperationException();
        };
      }

      @Override
      public boolean dependsOnFileContent() {
        return true;
      }

      @Override
      public boolean needsForwardIndexWhenSharing() {
        return false;
      }

      @Override
      public @NotNull DataIndexer<K, Void, FileContent> getIndexer() {
        return i -> {
          throw new AssertionError();
        };
      }

      @Override
      public @NotNull KeyDescriptor<K> getKeyDescriptor() {
        return extension.getKeyDescriptor();
      }

      @Override
      public @NotNull DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
      }

      @Override
      public int getVersion() {
        return extension.getVersion();
      }

      @Override
      public boolean traceKeyHashToVirtualFileMapping() {
        return extension instanceof StringStubIndexExtension && ((StringStubIndexExtension<?>)extension).traceKeyHashToVirtualFileMapping();
      }
    };
  }
}
