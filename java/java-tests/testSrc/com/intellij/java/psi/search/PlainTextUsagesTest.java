// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.List;

public class PlainTextUsagesTest extends JavaPsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/plainTextUsages/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    createTestProjectStructure( root);
  }

  public void testSimple() {
    doTest("com.Foo", null, new String[]{"Test.txt"}, new int[]{4}, new int[]{11});
  }

  public void testXmlOutOfScope() {
    final VirtualFile resourcesDir = ModuleRootManager.getInstance(myModule).getSourceRoots()[0].findChild("resources");
    assertNotNull(resourcesDir);
    WriteAction.runAndWait(() -> {
      final Module module = createModule("res");
      PsiTestUtil.addContentRoot(module, resourcesDir);
      final VirtualFile child = resourcesDir.findChild("Test.xml");
      assert child != null;
      assertSame(module, ModuleUtilCore.findModuleForFile(child, getProject()));
    });

    PsiClass aClass = myJavaFacade.findClass("com.Foo", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);
    doTest("com.Foo", aClass, new String[]{"Test.xml"}, new int[]{28}, new int[]{35});
  }

  private void doTest(String qNameToSearch,
                      final PsiElement originalElement,
                      String[] fileNames,
                      int[] starts,
                      int[] ends) {
    PsiSearchHelper helper = PsiSearchHelper.getInstance(myProject);
    final List<PsiFile> filesList = new ArrayList<>();
    final IntList startsList = new IntArrayList();
    final IntList endsList = new IntArrayList();
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

    assertEquals("usages count", fileNames.length, filesList.size());

    for (int i = 0; i < fileNames.length; i++) {
      assertEquals("files[" + i + "]", fileNames[i], filesList.get(i).getName());
    }

    for (int i = 0; i < starts.length; i++) {
      assertEquals("starts[" + i + "]", starts[i], startsList.getInt(i));
    }

    for (int i = 0; i < ends.length; i++) {
      assertEquals("ends[" + i + "]", ends[i], endsList.getInt(i));
    }
  }
}
