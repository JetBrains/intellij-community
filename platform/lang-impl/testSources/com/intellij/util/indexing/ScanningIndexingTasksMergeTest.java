// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.indexing.diagnostic.ScanningType;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
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

    Map<IndexableFilesIterator, List<VirtualFile>> map1 = new HashMap<>();
    map1.put(iter1, f1);
    map1.put(iterShared, fShared.subList(0, 2));

    Map<IndexableFilesIterator, List<VirtualFile>> map2 = new HashMap<>();
    map2.put(iter2, f2);
    map2.put(iterShared, fShared.subList(1, 3));

    task1 = new UnindexedFilesIndexer(getProject(), map1);
    task2 = new UnindexedFilesIndexer(getProject(), map2);
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

    return ((UnindexedFilesScannerAsDumbModeTaskWrapper)mergedDumb).getTask();
  }

  @NotNull
  private UnindexedFilesScanner createScanningTask(IndexableFilesIterator iter, String reason, ScanningType type) {
    List<IndexableFilesIterator> iterators = iter == null ? null : Collections.singletonList(iter);
    return new UnindexedFilesScanner(getProject(), false, false, iterators, null, reason, type);
  }

  private void assertMergedStateInvariants(UnindexedFilesIndexer mergedTask) {
    Map<IndexableFilesIterator, List<VirtualFile>> merged = mergedTask.getProviderToFiles();

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