/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PlainTextUsagesTest extends PsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/plainTextUsages/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  public void testSimple() throws Exception {
    doTest("com.Foo", null, new String[]{"Test.txt"}, new int[]{4}, new int[]{11});
  }

  public void testXmlOutOfScope() throws Exception {
    final VirtualFile resourcesDir = ModuleRootManager.getInstance(myModule).getSourceRoots()[0].findChild("resources");
    assertNotNull(resourcesDir);
    new WriteAction() {
      @Override
      protected void run(@NotNull final Result result) {
        final Module module = createModule("res");
        PsiTestUtil.addContentRoot(module, resourcesDir);
        final VirtualFile child = resourcesDir.findChild("Test.xml");
        assert child != null;
        assertSame(module, ModuleUtil.findModuleForFile(child, getProject()));
      }
    }.execute();

    PsiClass aClass = myJavaFacade.findClass("com.Foo", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);
    doTest("com.Foo", aClass, new String[]{"Test.xml"}, new int[]{28}, new int[]{35});
  }

  private void doTest(String qNameToSearch,
                      final PsiElement originalElement,
                      String[] fileNames,
                      int[] starts,
                      int[] ends) throws Exception {
    PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(myProject);
    final List<PsiFile> filesList = new ArrayList<>();
    final IntArrayList startsList = new IntArrayList();
    final IntArrayList endsList = new IntArrayList();
    helper.processUsagesInNonJavaFiles(originalElement,
                                       qNameToSearch,
                                       new PsiNonJavaFileReferenceProcessor() {
                                         @Override
                                         public boolean process(PsiFile file, int startOffset, int endOffset) {
                                           filesList.add(file);
                                           startsList.add(startOffset);
                                           endsList.add(endOffset);
                                           return true;
                                         }
                                       },
                                       GlobalSearchScope.projectScope(myProject)
    );

    assertEquals("usages count", fileNames.length, filesList.size());

    for (int i = 0; i < fileNames.length; i++) {
      assertEquals("files[" + i + "]", fileNames[i], filesList.get(i).getName());
    }

    for (int i = 0; i < starts.length; i++) {
      assertEquals("starts[" + i + "]", starts[i], startsList.get(i));
    }

    for (int i = 0; i < ends.length; i++) {
      assertEquals("ends[" + i + "]", ends[i], endsList.get(i));
    }
  }
}
