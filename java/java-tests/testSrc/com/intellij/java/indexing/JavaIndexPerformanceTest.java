// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SkipSlowTestLocally
@PerformanceUnitTest
@StressTestApplication
public class JavaIndexPerformanceTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void test_Vfs_Event_Processing_Performance() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String filename = "A.java";
    myFixture.addFileToProject("foo/bar/" + filename, "class A {}");

    Benchmark.newBenchmark("Vfs Event Processing By Index", () -> ReadAction.runBlocking(() -> {
      PsiFile[] files = FilenameIndex.getFilesByName(myFixture.getProject(), filename, GlobalSearchScope.moduleScope(myFixture.getModule()));
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

      files = FilenameIndex.getFilesByName(myFixture.getProject(), filename, GlobalSearchScope.moduleScope(myFixture.getModule()));
      assertEquals(1, files.length);
    })).start();
  }
}
