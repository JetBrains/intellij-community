// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.indexing.diagnostic.ScanningType;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ScanningIndexingTasksMergeTest extends LightPlatformTestCase {
  private List<VirtualFile> f1;
  private List<VirtualFile> fShared;
  private List<VirtualFile> f2;

  private IndexableFilesIterator iter1;
  private IndexableFilesIterator iter2;

  private UnindexedFilesIndexer task1;
  private UnindexedFilesIndexer task2;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    VirtualFile root = getSourceRoot();

    f1 = createFiles(root, "a1", "a2");
    fShared = createFiles(root, "s1", "s2", "s3");
    f2 = createFiles(root, "b1");

    iter1 = new FakeIndexableFilesIterator();
    iter2 = new FakeIndexableFilesIterator();

    Set<VirtualFile> set1 = new HashSet<>(f1);
    set1.addAll(fShared.subList(0, 2));

    Set<VirtualFile> set2 = new HashSet<>(f2);
    set2.addAll(fShared.subList(1, 3));

    task1 = new UnindexedFilesIndexer(getProject(), set1, "test task1", LongSet.of());
    task2 = new UnindexedFilesIndexer(getProject(), set2, "test task2", LongSet.of());
  }

  public void testTryMergeIndexingTasks() {
    assertMergedStateInvariants(task1.tryMergeWith(task2));
    assertMergedStateInvariants(task2.tryMergeWith(task1));
  }

  public void testTryMergeScanningTasks() {
    UnindexedFilesScanner t1 = createScanningTask(iter1, "reason 1", ScanningType.PARTIAL);
    UnindexedFilesScanner t2 = createScanningTask(iter2, "reason 2", ScanningType.PARTIAL);

    assertSameElements(t1.tryMergeWith(t2).getPredefinedIndexableFilesIterators(), Arrays.asList(iter1, iter2));
    assertSameElements(t2.tryMergeWith(t1).getPredefinedIndexableFilesIterators(), Arrays.asList(iter1, iter2));
  }

  public void testMergeScanningTasksWithFullScan() {
    UnindexedFilesScanner t1 = createScanningTask(iter1, "reason 1", ScanningType.PARTIAL);
    UnindexedFilesScanner full = createScanningTask(null, "full", ScanningType.FULL);

    List<UnindexedFilesScanner> mergedVariants = Arrays.asList(
      t1.tryMergeWith(full),
      full.tryMergeWith(t1)
    );

    for (UnindexedFilesScanner merged : mergedVariants) {
      assertNull(merged.getPredefinedIndexableFilesIterators());
      assertEquals(ScanningType.FULL, merged.getScanningType());
    }
  }

  // we don't care which exact reason will be after merge. We only care that we don't have hundreds of "On refresh of files" in it
  public void testVFSRefreshIndexingTasksReasonsDoNotAccumulate() {
    String[][] situations = {
      {"On refresh of files in awesome.project", "On refresh of files in awesome.project", "On refresh of files in awesome.project"},
      {"On refresh of A", "On refresh of B", "Merged On refresh of A with On refresh of B"},
      {"Merged A with B", "Merged A with B", "Merged A with B"},
      {"Merged A with B", "Merged B with C", "Merged A with B with B with C"},
      {"Merged A with B with C", "Merged B with C", "Merged A with B with C"},
      {"Merged On refresh of A with On refresh of B", "On refresh of B", "Merged On refresh of A with On refresh of B"}
    };


    for (String[] situation : situations) {
      UnindexedFilesIndexer t1 = new UnindexedFilesIndexer(getProject(), situation[0]);
      UnindexedFilesIndexer t2 = new UnindexedFilesIndexer(getProject(), situation[1]);
      UnindexedFilesIndexer merged = t1.tryMergeWith(t2);
      assertEquals(situation[2], merged.getIndexingReason());
    }
  }

  public void testNonVFSRefreshIndexingTasksReasonsNotRemoved() {
    UnindexedFilesIndexer merged = task1.tryMergeWith(task2);
    String task1Reason = task1.getIndexingReason();
    String task2Reason = task2.getIndexingReason();
    assertEquals("Merged " + task1Reason + " with " + task2Reason, merged.getIndexingReason());

    merged = merged.tryMergeWith(task2);
    assertEquals("Merged " + task1Reason + " with " + task2Reason + " with " + task2Reason, merged.getIndexingReason());
  }

  @NotNull
  private UnindexedFilesScanner createScanningTask(IndexableFilesIterator iter, String reason, ScanningType type) {
    List<IndexableFilesIterator> iterators = iter == null ? null : Collections.singletonList(iter);
    return new UnindexedFilesScanner(getProject(), false, false, iterators, null, reason, type, null);
  }

  private void assertMergedStateInvariants(UnindexedFilesIndexer mergedTask) {
    Set<VirtualFile> merged = mergedTask.getFiles();

    assertEquals(merged.toString(), f1.size() + f2.size() + fShared.size(), merged.size());
    assertTrue(merged.containsAll(f1));
    assertTrue(merged.containsAll(f2));
    assertTrue(merged.containsAll(fShared));
  }

  private static List<VirtualFile> createFiles(VirtualFile root, String... names) {
    List<VirtualFile> files = new ArrayList<>();
    for (String name : names) {
      files.add(new FakeVirtualFile(root, name));
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