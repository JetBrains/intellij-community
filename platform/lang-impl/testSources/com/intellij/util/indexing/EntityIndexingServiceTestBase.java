// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.VersionedStorageChange;
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.IndexableIteratorPresentation;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public abstract class EntityIndexingServiceTestBase extends HeavyPlatformTestCase {

  private VirtualFileUrlManager fileUrlManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    fileUrlManager = WorkspaceModel.getInstance(myProject).getVirtualFileUrlManager();
  }

  @Override
  public void tearDown() throws Exception {
    fileUrlManager = null;
    super.tearDown();
  }

  @NotNull
  protected VirtualFileUrl getUrl(@NotNull VirtualFile file) {
    return fileUrlManager.getOrCreateFromUrl(file.getUrl());
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
        Iterator<EntityChange<?>> iterator = ((VersionedStorageChangeInternal)event).getAllChanges().iterator();
        while (iterator.hasNext()) {
          EntityChange<?> next = iterator.next();
          changes.add(next);
        }
      }
      iterators = ProjectEntityIndexingService.Companion.getIterators(getProject(), changes);
      Collection<IndexableFilesIterator> expectedIterators = expectedIteratorsProducer.apply(createdEntities);

      assertSameIterators(iterators, expectedIterators);
    }
    finally {
      WriteAction.run(() -> remover.consume(createdEntities));
    }

    new UnindexedFilesScanner(getProject(), iterators, getTestName(false)).queue();
  }

  private static void assertSameIterators(List<IndexableFilesIterator> actualIterators,
                                          Collection<IndexableFilesIterator> expectedIterators) {
    assertEquals(expectedIterators.size(), actualIterators.size());
    Collection<Pair<IndexableSetOrigin, IndexableIteratorPresentation>> expectedOrigins = collectOriginsAndPresentations(expectedIterators);
    Collection<Pair<IndexableSetOrigin, IndexableIteratorPresentation>> actualOrigins = collectOriginsAndPresentations(actualIterators);
    assertSameElements(actualOrigins, expectedOrigins);
  }

  private static Collection<Pair<IndexableSetOrigin, IndexableIteratorPresentation>> collectOriginsAndPresentations(Collection<IndexableFilesIterator> iterators) {
    Set<IndexableSetOrigin> origins = new HashSet<>();
    Set<Pair<IndexableSetOrigin, IndexableIteratorPresentation>> result = new HashSet<>();
    for (IndexableFilesIterator iterator : iterators) {
      IndexableSetOrigin origin = iterator.getOrigin();
      assertTrue("Origins should be unique", origins.add(origin));
      IndexableIteratorPresentation presentation = IndexableIteratorPresentation.create(iterator.getDebugName(),
                                                                                        iterator.getIndexingProgressText(),
                                                                                        iterator.getRootsScanningProgressText());
      result.add(new Pair<>(origin, presentation));
    }
    return result;
  }

  private static class MyWorkspaceModelChangeListener implements WorkspaceModelChangeListener {
    final List<VersionedStorageChange> myEvents = new ArrayList<>();

    @Override
    public void changed(@NotNull VersionedStorageChange event) {
      myEvents.add(event);
    }
  }
}
