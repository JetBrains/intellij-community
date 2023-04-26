// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.Function;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.workspaceModel.ide.VirtualFileUrlManagerUtil;
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener;
import com.intellij.workspaceModel.ide.WorkspaceModelTopics;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.VersionedStorageChange;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class EntityIndexingServiceTestBase extends HeavyPlatformTestCase {

  private VirtualFileUrlManager fileUrlManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    fileUrlManager = VirtualFileUrlManagerUtil.getInstance(VirtualFileUrlManager.Companion, myProject);
  }

  @Override
  public void tearDown() throws Exception {
    fileUrlManager = null;
    super.tearDown();
  }

  @NotNull
  protected VirtualFileUrl getUrl(@NotNull VirtualFile file) {
    return fileUrlManager.fromUrl(file.getUrl());
  }

  @NotNull
  protected List<VirtualFileUrl> getUrls(@NotNull VirtualFile file) {
    return Collections.singletonList(getUrl(file));
  }

  protected <T> void doTest(ThrowableComputable<? extends T, ? extends Exception> generator,
                            ThrowableConsumer<? super T, ? extends Exception> remover,
                            Function<T, ? extends Collection<IndexableFilesIterator>> expectedIteratorsProducer) throws Exception {
    MyWorkspaceModelChangeListener listener = new MyWorkspaceModelChangeListener();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(WorkspaceModelTopics.CHANGED, listener);
    T createdEntities = WriteAction.compute(generator);

    List<IndexableFilesIterator> iterators;
    try {
      List<EntityChange<?>> changes = new ArrayList<>();
      for (VersionedStorageChange event : listener.myEvents) {
        Iterator<EntityChange<?>> iterator = event.getAllChanges().iterator();
        while (iterator.hasNext()) {
          EntityChange<?> next = iterator.next();
          changes.add(next);
        }
      }
      iterators = EntityIndexingServiceImpl.getIterators(getProject(), changes);
      Collection<IndexableFilesIterator> expectedIterators = expectedIteratorsProducer.fun(createdEntities);

      assertSameIterators(iterators, expectedIterators);
    }
    finally {
      WriteAction.run(() -> remover.consume(createdEntities));
    }

    new UnindexedFilesUpdater(getProject(), iterators, null, getTestName(false)).queue();
  }

  private static void assertSameIterators(List<IndexableFilesIterator> actualIterators,
                                          Collection<IndexableFilesIterator> expectedIterators) {
    assertEquals(expectedIterators.size(), actualIterators.size());
    Collection<IndexableSetOrigin> expectedOrigins = collectOrigins(expectedIterators);
    Collection<IndexableSetOrigin> actualOrigins = collectOrigins(actualIterators);
    assertSameElements(actualOrigins, expectedOrigins);
  }

  private static Collection<IndexableSetOrigin> collectOrigins(Collection<IndexableFilesIterator> iterators) {
    Set<IndexableSetOrigin> origins = new HashSet<>();
    for (IndexableFilesIterator iterator : iterators) {
      IndexableSetOrigin origin = iterator.getOrigin();
      assertTrue("Origins should be unique", origins.add(origin));
    }
    return origins;
  }

  private static class MyWorkspaceModelChangeListener implements WorkspaceModelChangeListener {
    final List<VersionedStorageChange> myEvents = new ArrayList<>();

    @Override
    public void changed(@NotNull VersionedStorageChange event) {
      myEvents.add(event);
    }
  }
}
