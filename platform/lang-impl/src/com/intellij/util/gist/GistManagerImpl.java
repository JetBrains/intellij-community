// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.gist;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class GistManagerImpl extends GistManager {
  private static final Logger LOG = Logger.getInstance(GistManagerImpl.class);
  private static final Map<String, VirtualFileGist<?>> ourGists = ContainerUtil.createConcurrentWeakValueMap();
  private static final String ourPropertyName = "file.gist.reindex.count";
  private final AtomicInteger myReindexCount = new AtomicInteger(PropertiesComponent.getInstance().getInt(ourPropertyName, 0));
  private final MergingUpdateQueue myDropCachesQueue = new MergingUpdateQueue("gist-manager-drop-caches", 500, true, null).setRestartTimerOnAdd(true);
  private final AtomicInteger myMergingDropCachesRequestors = new AtomicInteger();

  static final class MyBulkFileListener implements BulkFileListener {
    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
      if (events.stream().anyMatch(MyBulkFileListener::shouldDropCache)) {
        ((GistManagerImpl)GistManager.getInstance()).invalidateGists();
      }
    }

    private static boolean shouldDropCache(VFileEvent e) {
      if (!(e instanceof VFilePropertyChangeEvent)) return false;

      String propertyName = ((VFilePropertyChangeEvent)e).getPropertyName();
      return propertyName.equals(VirtualFile.PROP_NAME) || propertyName.equals(VirtualFile.PROP_ENCODING);
    }
  }

  @NotNull
  @Override
  public <Data> VirtualFileGist<Data> newVirtualFileGist(@NotNull String id,
                                                         int version,
                                                         @NotNull DataExternalizer<Data> externalizer,
                                                         @NotNull VirtualFileGist.GistCalculator<Data> calcData) {
    if (ourGists.get(id) != null) {
      throw new IllegalArgumentException("Gist '" + id + "' is already registered");
    }

    //noinspection unchecked
    return (VirtualFileGist<Data>)ourGists.computeIfAbsent(id, __ -> new VirtualFileGistImpl<>(id, version, externalizer, calcData));
  }

  @NotNull
  @Override
  public <Data> PsiFileGist<Data> newPsiFileGist(@NotNull String id,
                                                 int version,
                                                 @NotNull DataExternalizer<Data> externalizer,
                                                 @NotNull NullableFunction<? super PsiFile, ? extends Data> calculator) {
    return new PsiFileGistImpl<>(id, version, externalizer, calculator);
  }

  int getReindexCount() {
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
    file.putUserDataIfAbsent(VirtualFileGistImpl.GIST_INVALIDATION_COUNT_KEY, new AtomicInteger()).incrementAndGet();
    invalidateDependentCaches();
  }

  private void invalidateGists() {
    if (LOG.isTraceEnabled()) {
      LOG.trace(new Throwable("Invalidating gists"));
    }
    // Clear all cache at once to simplify and speedup this operation.
    // It can be made per-file if cache recalculation ever becomes an issue.
    PropertiesComponent.getInstance().setValue(ourPropertyName, myReindexCount.incrementAndGet(), 0);
  }

  private void invalidateDependentCaches() {
    Runnable dropCaches = () -> {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        PsiManager.getInstance(project).dropPsiCaches();
      }
    };
    if (myMergingDropCachesRequestors.get() == 0) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, dropCaches);
    }
    else {
      myDropCachesQueue.queue(Update.create(this, dropCaches));
    }
  }

  public void runWithMergingDependentCacheInvalidations(@NotNull Runnable runnable) {
    myMergingDropCachesRequestors.incrementAndGet();
    try {
      runnable.run();
    }
    finally {
      if (myMergingDropCachesRequestors.decrementAndGet() == 0) {
        myDropCachesQueue.sendFlush();
      }
    }
  }

  @TestOnly
  public void clearQueueInTests() {
    myDropCachesQueue.cancelAllUpdates();
  }

  @TestOnly
  public void resetReindexCount() {
    myReindexCount.set(0);
    PropertiesComponent.getInstance().unsetValue(ourPropertyName);
  }
}
