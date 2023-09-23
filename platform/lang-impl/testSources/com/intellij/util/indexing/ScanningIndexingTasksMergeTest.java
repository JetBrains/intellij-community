// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.FilesScanningTaskAsDumbModeTaskWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.UnindexedFilesScannerExecutor;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.indexing.dependencies.IndexingRequestToken;
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService;
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
  private IndexableFilesIterator iterShared;
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
    iterShared = new FakeIndexableFilesIterator();
    iter2 = new FakeIndexableFilesIterator();

    Map<IndexableFilesIterator, Collection<VirtualFile>> map1 = new HashMap<>();
    map1.put(iter1, f1);
    map1.put(iterShared, fShared.subList(0, 2));

    Map<IndexableFilesIterator, Collection<VirtualFile>> map2 = new HashMap<>();
    map2.put(iter2, f2);
    map2.put(iterShared, fShared.subList(1, 3));

    IndexingRequestToken indexingRequest = getProject().getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
    task1 = new UnindexedFilesIndexer(getProject(), map1, "test task1", LongSet.of(), indexingRequest);
    task2 = new UnindexedFilesIndexer(getProject(), map2, "test task2", LongSet.of(), indexingRequest);
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

    assertNull(t1.tryMergeWith(full).getPredefinedIndexableFilesIterators());
    assertNull(full.tryMergeWith(t1).getPredefinedIndexableFilesIterators());
  }

  public void testTryMergeDumbScanningTasks() {
    UnindexedFilesScanner t1 = createScanningTask(iter1, "reason 1", ScanningType.PARTIAL);
    UnindexedFilesScanner t2 = createScanningTask(iter2, "reason 2", ScanningType.PARTIAL);

    assertSameElements(mergeAsDumbTasks(t1, t2).getPredefinedIndexableFilesIterators(), Arrays.asList(iter1, iter2));
    assertSameElements(mergeAsDumbTasks(t2, t1).getPredefinedIndexableFilesIterators(), Arrays.asList(iter1, iter2));
  }

  public void testDumbWrapperInvokesOriginalDispose() {
    UnindexedFilesScanner t1 = createScanningTask(iter1, "reason 1", ScanningType.PARTIAL);
    UnindexedFilesScannerExecutor executor = new UnindexedFilesScannerExecutor(getProject());
    DumbModeTask dumb1 = executor.wrapAsDumbTask(t1);

    // Disposer.isDisposed() deprecated. Use checkedDisposable instead
    CheckedDisposable checked = Disposer.newCheckedDisposable();
    Disposer.register(t1, checked);

    assertFalse(checked.isDisposed());

    Disposer.dispose(dumb1);
    assertTrue(checked.isDisposed());
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
      IndexingRequestToken indexingRequest = getProject().getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
      UnindexedFilesIndexer t1 = new UnindexedFilesIndexer(getProject(), situation[0], indexingRequest);
      UnindexedFilesIndexer t2 = new UnindexedFilesIndexer(getProject(), situation[1], indexingRequest);
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

  private UnindexedFilesScanner mergeAsDumbTasks(UnindexedFilesScanner t1, UnindexedFilesScanner t2) {
    UnindexedFilesScannerExecutor executor = new UnindexedFilesScannerExecutor(getProject());
    DumbModeTask dumb1 = executor.wrapAsDumbTask(t1);
    DumbModeTask dumb2 = executor.wrapAsDumbTask(t2);
    DumbModeTask mergedDumb = dumb1.tryMergeWith(dumb2);

    assertEquals("Should be of the same class to merge successfully", dumb1.getClass(), dumb2.getClass());
    assertEquals(
      "Should be of the same class, otherwise mergedDumb will not be able to merge with new tasks produced by executor.wrapAsDumbTask",
      mergedDumb.getClass(), dumb1.getClass()
    );

    return (UnindexedFilesScanner)((FilesScanningTaskAsDumbModeTaskWrapper)mergedDumb).getTask();
  }

  @NotNull
  private UnindexedFilesScanner createScanningTask(IndexableFilesIterator iter, String reason, ScanningType type) {
    List<IndexableFilesIterator> iterators = iter == null ? null : Collections.singletonList(iter);
    IndexingRequestToken indexingRequest = getProject().getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
    return new UnindexedFilesScanner(getProject(), false, false, iterators, null, reason, type, indexingRequest);
  }

  private void assertMergedStateInvariants(UnindexedFilesIndexer mergedTask) {
    Map<IndexableFilesIterator, Collection<VirtualFile>> merged = mergedTask.getProviderToFiles();

    assertEquals(3, merged.keySet().size());
    assertTrue(merged.containsKey(iter1));
    assertTrue(merged.containsKey(iter2));
    assertTrue(merged.containsKey(iterShared));

    assertEquals(merged.get(iter1).toString(), f1.size(), merged.get(iter1).size());
    assertTrue(merged.get(iter1).containsAll(f1));

    assertEquals(merged.get(iter2).toString(), f2.size(), merged.get(iter2).size());
    assertTrue(merged.get(iter2).containsAll(f2));

    assertEquals(merged.get(iterShared).toString(), fShared.size(), merged.get(iterShared).size());
    assertTrue(merged.get(iterShared).containsAll(fShared));
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