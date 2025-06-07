// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing;

import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;

import java.util.ArrayList;
import java.util.List;

@SkipSlowTestLocally
public class JavaIndexPerformanceTest extends JavaCodeInsightFixtureTestCase {
  public void test_Vfs_Event_Processing_Performance() {
    final String filename = "A.java";
    myFixture.addFileToProject("foo/bar/" + filename, "class A {}");

    Benchmark.newBenchmark("Vfs Event Processing By Index", () -> {
      PsiFile[] files = FilenameIndex.getFilesByName(getProject(), filename, GlobalSearchScope.moduleScope(getModule()));
      assertEquals(1, files.length);

      VirtualFile file = files[0].getVirtualFile();

      String filename2 = "B.java";
      int max = 100000;
      List<VFileEvent> eventList = new ArrayList<>(max);
      int len = max / 2;

      for (int i = 0; i < len; ++i) {
        eventList.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, filename, filename2));
        eventList.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, filename2, filename));
        eventList.add(new VFileDeleteEvent(null, file));
        eventList.add(new VFileCreateEvent(null, file.getParent(), filename, false, null, null, null));
      }


      AsyncFileListener.ChangeApplier applier =
        ((FileBasedIndexImpl)FileBasedIndex.getInstance()).getChangedFilesCollector().prepareChange(eventList);
      applier.beforeVfsChange();
      applier.afterVfsChange();

      files = FilenameIndex.getFilesByName(getProject(), filename, GlobalSearchScope.moduleScope(getModule()));
      assertEquals(1, files.length);
    }).start();
  }
}
