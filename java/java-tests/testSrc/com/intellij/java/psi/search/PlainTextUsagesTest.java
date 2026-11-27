// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class PlainTextUsagesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private Project myProject;
  private Module myModule;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    myProject = fixture.getProject();
    myModule = fixture.getModule();
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/plainTextUsages/" + fixture.getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    fixture.createTestProjectStructure(root);
  }

  @Test
  public void testSimple() {
    doTest("com.Foo", null, new String[]{"Test.txt"}, new int[]{4}, new int[]{11});
  }

  @Test
  public void testXmlOutOfScope() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final VirtualFile resourcesDir = ModuleRootManager.getInstance(myModule).getSourceRoots()[0].findChild("resources");
    assertNotNull(resourcesDir);
    WriteAction.runAndWait(() -> {
      final Module module = fixture.createModule("res");
      PsiTestUtil.addContentRoot(module, resourcesDir);
      final VirtualFile child = resourcesDir.findChild("Test.xml");
      assert child != null;
      assertSame(module, ModuleUtilCore.findModuleForFile(child, myProject));
    });

    PsiClass aClass = ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("com.Foo", GlobalSearchScope.allScope(myProject)));
    assertNotNull(aClass);
    doTest("com.Foo", aClass, new String[]{"Test.xml"}, new int[]{28}, new int[]{35});
  }

  private void doTest(String qNameToSearch,
                      final PsiElement originalElement,
                      String[] fileNames,
                      int[] starts,
                      int[] ends) {
    PsiSearchHelper helper = PsiSearchHelper.getInstance(myProject);
    final List<PsiFile> filesList = Collections.synchronizedList(new ArrayList<>());
    final IntList startsList = IntLists.synchronize(new IntArrayList());
    final IntList endsList = IntLists.synchronize(new IntArrayList());
    helper.processUsagesInNonJavaFiles(originalElement,
                                       qNameToSearch,
                                       (file, startOffset, endOffset) -> {
                                         filesList.add(file);
                                         startsList.add(startOffset);
                                         endsList.add(endOffset);
                                         return true;
                                       },
                                       GlobalSearchScope.projectScope(myProject)
    );

    assertEquals(fileNames.length, filesList.size(), "usages count");

    for (int i = 0; i < fileNames.length; i++) {
      PsiFile file = filesList.get(i);
      assertEquals(fileNames[i], ReadAction.computeBlocking(() -> file.getName()), "files[" + i + "]");
    }

    for (int i = 0; i < starts.length; i++) {
      assertEquals(starts[i], startsList.getInt(i), "starts[" + i + "]");
    }

    for (int i = 0; i < ends.length; i++) {
      assertEquals(ends[i], endsList.getInt(i), "ends[" + i + "]");
    }
  }
  @Test
  public void testPsiSearchHelperPerformSearchInNonJavaFilesInParallel() {
    if (JobSchedulerImpl.getJobPoolParallelism() < 5) {
      return; // not enough cores
    }
    PsiDirectory psiDir = ReadAction.computeBlocking(
      () -> PsiManager.getInstance(myProject).findDirectory(ModuleRootManager.getInstance(myModule).getContentRoots()[0]));
    assertNotNull(psiDir);
    String s = "zzzzzz333";
    WriteAction.runAndWait(() -> {
      for (int i=0; i<5; i++) {
        PsiFile child = psiDir.createFile("Test" + i + ".txt");
        PsiDocumentManager.getInstance(myProject).getDocument(child).setText(s);
      }
      FileDocumentManager.getInstance().saveAllDocuments();
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    });

    Set<PsiFile> files = ConcurrentCollectionFactory.createConcurrentSet();
    CountDownLatch l = new CountDownLatch(5);
    PsiSearchHelper.getInstance(myProject).processUsagesInNonJavaFiles(s, (file, startOffset, endOffset) -> {
      files.add(file);
      l.countDown();
      try {
        l.await();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      return true;
    }, GlobalSearchScope.allScope(myProject));
    assertEquals(5, files.size());
  }
}
