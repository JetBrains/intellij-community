// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.gist;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.storage.GistStorage;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@ApiStatus.Internal
public final class GistManagerImpl extends GistManager {
  private static final Logger LOG = Logger.getInstance(GistManagerImpl.class);

  private static final int INTERNAL_VERSION = 2;

  private static final String GIST_REINDEX_COUNT_PROPERTY_NAME = "file.gist.reindex.count";
  private static final Key<AtomicInteger> GIST_INVALIDATION_COUNT_KEY = Key.create("virtual.file.gist.invalidation.count");

  private static final Map<String, VirtualFileGist<?>> ourGists = CollectionFactory.createConcurrentWeakValueMap();

  private final AtomicInteger myReindexCount = new AtomicInteger(
    PropertiesComponent.getInstance().getInt(GIST_REINDEX_COUNT_PROPERTY_NAME, 0)
  );

  private final MergingUpdateQueue myDropCachesQueue;
  private final AtomicInteger myMergingDropCachesRequestors = new AtomicInteger();

  private final GistStorage gistStorage;

  static final class MyBulkFileListener implements BulkFileListener {
    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
      if (ContainerUtil.exists(events, MyBulkFileListener::shouldDropCache)) {
        GistManagerImpl gistManager = (GistManagerImpl)getInstance();
        if (events.size() < 100) {
          List<VirtualFile> files = events.stream()
            .filter(MyBulkFileListener::shouldDropCache)
            .map(VFileEvent::getFile)
            .filter(Objects::nonNull)
            .toList();

          if (ContainerUtil.exists(files, VirtualFile::isDirectory)) {
            gistManager.invalidateGists();
          }
          else {
            for (VirtualFile file : files) {
              gistManager.invalidateGist(file);
            }
          }
        }
        else {
          // give up and drop everything
          gistManager.invalidateGists();
        }
      }
    }

    private static boolean shouldDropCache(VFileEvent e) {
      if (!(e instanceof VFilePropertyChangeEvent)) return false;

      String propertyName = ((VFilePropertyChangeEvent)e).getPropertyName();
      return propertyName.equals(VirtualFile.PROP_NAME) || propertyName.equals(VirtualFile.PROP_ENCODING);
    }
  }

  public GistManagerImpl(@NotNull CoroutineScope coroutineScope) {
    gistStorage = GistStorage.getInstance();
    myDropCachesQueue = MergingUpdateQueue.Companion.edtMergingUpdateQueue("gist-manager-drop-caches", 500, coroutineScope)
      .setRestartTimerOnAdd(true);
  }

  @Override
  public @NotNull <Data> VirtualFileGist<Data> newVirtualFileGist(@NotNull String id,
                                                                  int version,
                                                                  @NotNull DataExternalizer<Data> externalizer,
                                                                  @NotNull VirtualFileGist.GistCalculator<Data> calcData) {
    if (ourGists.get(id) != null) {
      throw new IllegalArgumentException("Gist '" + id + "' is already registered");
    }

    //noinspection unchecked
    return (VirtualFileGist<Data>)ourGists.computeIfAbsent(
      id,
      __ -> new VirtualFileGistOverGistStorage<>(gistStorage.newGist(id, version, externalizer), calcData)
    );
  }

  @Override
  public @NotNull <Data> PsiFileGist<Data> newPsiFileGist(@NotNull String id,
                                                          int version,
                                                          @NotNull DataExternalizer<Data> externalizer,
                                                          @NotNull NullableFunction<? super PsiFile, ? extends Data> calculator) {
    return new PsiFileGistImpl<>(id, version, externalizer, calculator);
  }

  @VisibleForTesting
  public int getReindexCount() {
    return myReindexCount.get();
  }

  @Override
  public void invalidateData() {
    invalidateGists();
    invalidateDependentCaches();
  }

  @Override
  public void invalidateData(@NotNull VirtualFile file) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Invalidating gist " + file);
    }
    invalidateGist(file);
    invalidateDependentCaches();
  }

  private void invalidateGists() {
    if (LOG.isTraceEnabled()) {
      LOG.trace(new Throwable("Invalidating gists"));
    }
    // Clear all cache at once to simplify and speedup this operation.
    // It can be made per-file if cache recalculation ever becomes an issue.
    PropertiesComponent.getInstance().setValue(GIST_REINDEX_COUNT_PROPERTY_NAME, myReindexCount.incrementAndGet(), 0);
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void invalidateGist(@NotNull VirtualFile file) {
    file.putUserDataIfAbsent(GIST_INVALIDATION_COUNT_KEY, new AtomicInteger()).incrementAndGet();
  }

  private void invalidateDependentCaches() {
    Runnable dropCaches = () -> {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        PsiManager.getInstance(project).dropPsiCaches();
      }
    };
    if (myMergingDropCachesRequestors.get() == 0) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), dropCaches);
    }
    else {
      myDropCachesQueue.queue(Update.create(this, dropCaches));
    }
  }

  @Contract(" -> new")
  @ApiStatus.Internal
  public @NotNull AccessToken mergeDependentCacheInvalidations() {
    myMergingDropCachesRequestors.incrementAndGet();
    return new AccessToken() {
      private final AtomicBoolean alreadyFinished = new AtomicBoolean(false);

      @Override
      public void finish() {
        if (alreadyFinished.compareAndSet(false, true)) {
          if (myMergingDropCachesRequestors.decrementAndGet() == 0) {
            myDropCachesQueue.sendFlush();
          }
        }
      }
    };
  }

  public void runWithMergingDependentCacheInvalidations(@NotNull Runnable runnable) {
    try (AccessToken ignored = mergeDependentCacheInvalidations()) {
      runnable.run();
    }
  }

  static int getGistStamp(@NotNull VirtualFile file) {
    AtomicInteger invalidationCount = file.getUserData(GIST_INVALIDATION_COUNT_KEY);
    int reindexCount = ((GistManagerImpl)getInstance()).getReindexCount();
    long fileModificationCount = file.getModificationCount();
    //mix the bits in all 4 components so that there is little chance change in one counter
    //  'compensate' change in another, and the resulting stamp happens to be the same:
    return mixBits(
      mixBits(Long.hashCode(fileModificationCount), reindexCount),
      mixBits(invalidationCount != null ? invalidationCount.get() : 0, INTERNAL_VERSION)
    );
  }

  @TestOnly
  public void clearQueueInTests() {
    myDropCachesQueue.cancelAllUpdates();
  }

  @TestOnly
  public void resetReindexCount() {
    //this changes getGistStamp() thus invalidating .stamps of all currently cached Gists
    myReindexCount.set(0);
    PropertiesComponent.getInstance().unsetValue(GIST_REINDEX_COUNT_PROPERTY_NAME);
  }

  private static final int INT_PHI = 0x9E3779B9;

  /** aka 'fibonacci hashing' */
  private static int mixBits(int x) {
    final int h = x * INT_PHI;
    return h ^ (h >>> 16);
  }

  private static int mixBits(int x, int y) {
    return mixBits(mixBits(x) + y);
  }
}
