// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class UnindexedFilesIndexerTest extends LightPlatformTestCase {
  public void testTryMergeWith() {
    VirtualFile root = getSourceRoot();

    List<VirtualFile> f1 = createFiles(root, "a1", "a2");
    List<VirtualFile> fShared = createFiles(root, "s1", "s2", "s3");
    List<VirtualFile> f2 = createFiles(root, "b1");

    IndexableFilesIterator iter1 = new FakeIndexableFilesIterator();
    IndexableFilesIterator iterShared = new FakeIndexableFilesIterator();
    IndexableFilesIterator iter2 = new FakeIndexableFilesIterator();

    Map<IndexableFilesIterator, List<VirtualFile>> map1 = new HashMap<>();
    map1.put(iter1, f1);
    map1.put(iterShared, fShared.subList(0, 2));

    Map<IndexableFilesIterator, List<VirtualFile>> map2 = new HashMap<>();
    map2.put(iter2, f2);
    map2.put(iterShared, fShared.subList(1, 3));

    UnindexedFilesIndexer task1 = new UnindexedFilesIndexer(getProject(), map1);
    UnindexedFilesIndexer task2 = new UnindexedFilesIndexer(getProject(), map2);

    UnindexedFilesIndexer merged1 = task1.tryMergeWith(task2);
    UnindexedFilesIndexer merged2 = task2.tryMergeWith(task1);

    for (UnindexedFilesIndexer mergedTask : new UnindexedFilesIndexer[]{merged1, merged2}) {
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