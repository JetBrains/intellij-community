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
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;

import java.util.*;

public class SearchInLibsTest extends PsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/searchInLibs";
    VirtualFile rootFile = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete, false);

    final VirtualFile projectRoot = rootFile.findChild("project");
    assertNotNull(projectRoot);

    final VirtualFile innerSourceRoot = projectRoot.findChild("src2");
    assertNotNull(innerSourceRoot);

    VirtualFile libRoot = rootFile.findChild("lib");
    final VirtualFile libClassesRoot = libRoot.findChild("classes");
    final VirtualFile libSrcRoot = libRoot.findChild("src");
    assertNotNull(libRoot);

    PsiTestUtil.removeAllRoots(myModule, null);
    PsiTestUtil.addSourceRoot(myModule, projectRoot);
    PsiTestUtil.addSourceRoot(myModule, innerSourceRoot);
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.singletonList(libClassesRoot.getUrl()), Collections.singletonList(libSrcRoot.getUrl()));
  }

  public void testFindUsagesInProject() throws Exception {
    doTest("ProjectClass", new String[]{"ProjectClass.java"}, GlobalSearchScope.projectScope(myProject));
  }
  public void testFindUsagesInProject1() throws Exception {
    doTest("LibraryClass1", new String[]{"ProjectClass.java"}, GlobalSearchScope.projectScope(myProject));
  }
  public void testFindUsagesInProject2() throws Exception {
    doTest("LibraryClass2", new String[]{}, GlobalSearchScope.projectScope(myProject));
  }

  public void testFindUsagesInLibs() throws Exception {
    doTest("ProjectClass", new String[]{"ProjectClass.java"}, GlobalSearchScope.allScope(myProject));
  }
  public void testFindUsagesInLibs1() throws Exception {
    doTest("LibraryClass1", new String[]{"LibraryClass2.java", "ProjectClass.java"}, GlobalSearchScope.allScope(myProject));
  }
  public void testFindUsagesInLibs2() throws Exception {
    doTest("LibraryClass2", new String[]{"LibraryClass1.java"}, GlobalSearchScope.allScope(myProject));
  }

  public void testFindInPathInLibraryDirActuallySearchesInTheirSourcesToo() throws Exception {
    FindModel model = new FindModel();
    final PsiClass aClass = myJavaFacade.findClass("LibraryClass1");
    assertNotNull(aClass);
    model.setDirectoryName(aClass.getContainingFile().getContainingDirectory().getVirtualFile().getPath());
    model.setCaseSensitive(true);
    model.setCustomScope(false);
    model.setStringToFind("LibraryClass1");
    model.setProjectScope(false);

    List<UsageInfo> usages = new ArrayList<UsageInfo>();
    FindInProjectUtil.findUsages(model, aClass.getContainingFile().getContainingDirectory(), getProject(),
                                 new CommonProcessors.CollectProcessor<UsageInfo>(
                                   usages), FindInProjectUtil
                                   .setupProcessPresentation(getProject(), false, FindInProjectUtil.setupViewPresentation(false, model)));

    assertEquals(2, usages.size());
  }

  public void testInnerSourceRoot() throws Exception {
    doTest("ProjectClass2", new String[]{"ProjectClass2.java"}, GlobalSearchScope.projectScope(myProject));
  }

  private void doTest(String classNameToSearch, String[] expectedFileNames, SearchScope scope) throws Exception {
    final PsiClass aClass = myJavaFacade.findClass(classNameToSearch);
    assertNotNull(aClass);

    PsiReference[] refs = ReferencesSearch.search(aClass, scope, false).toArray(new PsiReference[0]);

    ArrayList<PsiFile> files = new ArrayList<PsiFile>();
    for (PsiReference ref : refs) {
      PsiFile file = ref.getElement().getContainingFile();
      if (!files.contains(file)) {
        files.add(file);
      }
    }

    assertEquals("files count", expectedFileNames.length, files.size());

    Collections.sort(files, new Comparator<PsiFile>() {
      @Override
      public int compare(PsiFile o1, PsiFile o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    Arrays.sort(expectedFileNames);

    for (int i = 0; i < expectedFileNames.length; i++) {
      String name = expectedFileNames[i];
      PsiFile file = files.get(i);
      assertEquals(name, file.getName());
    }
  }
}
