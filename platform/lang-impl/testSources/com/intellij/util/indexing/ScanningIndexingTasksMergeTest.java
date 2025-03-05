// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.indexing.diagnostic.ScanningType;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import kotlinx.coroutines.CompletableDeferredKt;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScanningIndexingTasksMergeTest extends LightPlatformTestCase {
  private IndexableFilesIterator iter1;
  private IndexableFilesIterator iter2;

  private UnindexedFilesIndexer task1;
  private UnindexedFilesIndexer task2;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    iter1 = new FakeIndexableFilesIterator();
    iter2 = new FakeIndexableFilesIterator();

    task1 = new UnindexedFilesIndexer(getProject(), "test task1");
    task2 = new UnindexedFilesIndexer(getProject(), "test task2");
  }

  public void testTryMergeScanningTasks() {
    UnindexedFilesScanner t1 = createScanningTask(iter1, "reason 1", ScanningType.PARTIAL);
    UnindexedFilesScanner t2 = createScanningTask(iter2, "reason 2", ScanningType.PARTIAL);

    assertSameElements(t1.tryMergeWith(t2, GlobalScope.INSTANCE).getPredefinedIndexableFileIteratorsBlocking(), Arrays.asList(iter1, iter2));
    assertSameElements(t2.tryMergeWith(t1, GlobalScope.INSTANCE).getPredefinedIndexableFileIteratorsBlocking(), Arrays.asList(iter1, iter2));
  }

  public void testMergeScanningTasksWithFullScan() {
    UnindexedFilesScanner t1 = createScanningTask(iter1, "reason 1", ScanningType.PARTIAL);
    UnindexedFilesScanner full = createScanningTask(null, "full", ScanningType.FULL);

    List<UnindexedFilesScanner> mergedVariants = Arrays.asList(
      t1.tryMergeWith(full, GlobalScope.INSTANCE),
      full.tryMergeWith(t1, GlobalScope.INSTANCE)
    );

    for (UnindexedFilesScanner merged : mergedVariants) {
      assertNull(merged.getPredefinedIndexableFileIteratorsBlocking());
      assertEquals(ScanningType.FULL, merged.getScanningTypeBlocking());
    }
  }

  // we don't care which exact reason will be after merge. We only care that we don't have hundreds of "On refresh of files" in it
  public void testVFSRefreshIndexingTasksReasonsDoNotAccumulate() {
    String[][][] situations = {
      {{"On refresh of files in awesome.project"}, {"On refresh of files in awesome.project"}, {"On refresh of files in awesome.project (x2)"}},
      {{"On refresh of A"}, {"On refresh of B"}, {"Merged On refresh of A with On refresh of B"}},
      {{"A", "B"}, {"Merged A with B"}},
      {{"A", "B"}, {"A", "B"}, {"Merged A (x2) with B (x2)"}},
      {{"A"}, {"B", "C"}, {"Merged A with B with C"}},
      {{"A", "B"}, {"B", "C"}, {"Merged A with B (x2) with C"}},
      {{"A", "B", "C"}, {"B", "C"}, {"Merged A with B (x2) with C (x2)"}},
      {{"On refresh of A", "On refresh of B"}, {"On refresh of B"}, {"Merged On refresh of A with On refresh of B (x2)"}}
    };


    for (String[][] situation : situations) {
      var tasks = situation.length - 1;
      var answer = situation[tasks][0];

      UnindexedFilesIndexer merged = null;
      for (int i = 0; i < tasks; i++) {
        UnindexedFilesIndexer subtaskRes = null;
        for (String subtask : situation[i]) {
          if (subtaskRes == null) {
            subtaskRes = new UnindexedFilesIndexer(getProject(), subtask);
          }
          else {
            subtaskRes = subtaskRes.tryMergeWith(new UnindexedFilesIndexer(getProject(), subtask));
          }
        }

        if (merged == null) {
          merged = subtaskRes;
        }
        else {
          merged = merged.tryMergeWith(subtaskRes);
        }
      }
      assertEquals(answer, merged.getIndexingReason());
    }
  }

  public void testReasonCountersNotRemoved() {
    UnindexedFilesIndexer merged = task1.tryMergeWith(task2);
    String task1Reason = task1.getIndexingReason();
    String task2Reason = task2.getIndexingReason();
    assertEquals("Merged " + task1Reason + " with " + task2Reason, merged.getIndexingReason());

    merged = merged.tryMergeWith(task2);
    assertEquals("Merged " + task1Reason + " with " + task2Reason + " (x2)", merged.getIndexingReason());
  }

  @NotNull
  private UnindexedFilesScanner createScanningTask(IndexableFilesIterator iter, String reason, ScanningType type) {
    List<IndexableFilesIterator> iterators = iter == null ? null : Collections.singletonList(iter);
    var parameters = CompletableDeferredKt.CompletableDeferred(new ScanningIterators(reason, iterators, null, type));
    return new UnindexedFilesScanner(
      getProject(),
      false,
      false,
      null,
      null,
      null,
      false,
      parameters);
  }

  private static final AtomicInteger idCounter = new AtomicInteger(0);

  private static class FakeVirtualFileWithId extends FakeVirtualFile implements VirtualFileWithId {
    private final int id;

    FakeVirtualFileWithId(@NotNull VirtualFile parent, @NotNull String name, int id) {
      super(parent, name);
      this.id = id;
    }

    @Override
    public int getId() {
      return id;
    }
  }

  private static List<VirtualFile> createFiles(VirtualFile root, String... names) {
    List<VirtualFile> files = new ArrayList<>();
    for (String name : names) {
      files.add(new FakeVirtualFileWithId(root, name, idCounter.incrementAndGet()));
    }
    return files;
  }

  private static class FakeIndexableSetOrigin implements IndexableSetOrigin {

  }

  private static class FakeIndexableFilesIterator implements IndexableFilesIterator {
    IndexableSetOrigin origin = new FakeIndexableSetOrigin();

    @Override
    public String getDebugName() {
      return "FakeIndexableFileIterator";
    }

    @Override
    public String getIndexingProgressText() {
      return "Indexing FakeIndexableFileIterator";
    }

    @Override
    public String getRootsScanningProgressText() {
      return "Scanning FakeIndexableFileIterator";
    }

    @Override
    public @NotNull IndexableSetOrigin getOrigin() {
      return origin;
    }

    @Override
    public boolean iterateFiles(@NotNull Project project, @NotNull ContentIterator fileIterator, @NotNull VirtualFileFilter fileFilter) {
      return false;
    }

    @Override
    public @NotNull Set<String> getRootUrls(@NotNull Project project) {
      return Collections.emptySet();
    }
  }
}