/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.util.CommonProcessors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    final VirtualFile libSrc2Root = libRoot.findChild("src2");
    assertNotNull(libRoot);

    PsiTestUtil.removeAllRoots(myModule, null);
    PsiTestUtil.addSourceRoot(myModule, projectRoot);
    PsiTestUtil.addSourceRoot(myModule, innerSourceRoot);
    List<String> sourceRoots = Arrays.asList(libSrcRoot.getUrl(), libSrc2Root.getUrl());
    List<String> classesRoots = Collections.singletonList(libClassesRoot.getUrl());
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", classesRoots, sourceRoots);
  }

  public void testFindUsagesInProject() {
    doTest("ProjectClass", new String[]{"ProjectClass.java"}, GlobalSearchScope.projectScope(myProject));
  }
  public void testFindUsagesInProject1() {
    doTest("LibraryClass1", new String[]{"ProjectClass.java"}, GlobalSearchScope.projectScope(myProject));
  }
  public void testFindUsagesInProject2() {
    doTest("LibraryClass2", new String[]{}, GlobalSearchScope.projectScope(myProject));
  }

  public void testFindUsagesInLibs() {
    doTest("ProjectClass", new String[]{"ProjectClass.java"}, GlobalSearchScope.allScope(myProject));
  }
  public void testFindUsagesInLibs1() {
    doTest("LibraryClass1", new String[]{"LibraryClass2.java", "ProjectClass.java"}, GlobalSearchScope.allScope(myProject));
  }
  public void testFindUsagesInLibs2() {
    doTest("LibraryClass2", new String[]{"LibraryClass1.java"}, GlobalSearchScope.allScope(myProject));
  }

  public void testFindInPathInLibraryDirActuallySearchesInTheirSourcesToo() {
    FindModel model = new FindModel();
    final PsiClass aClass = myJavaFacade.findClass("LibraryClass1");
    assertNotNull(aClass);
    model.setDirectoryName(aClass.getContainingFile().getContainingDirectory().getVirtualFile().getPath());
    model.setCaseSensitive(true);
    model.setCustomScope(false);
    model.setStringToFind("LibraryClass1");
    model.setProjectScope(false);

    List<UsageInfo> usages = Collections.synchronizedList(new ArrayList<>());
    CommonProcessors.CollectProcessor<UsageInfo> consumer = new CommonProcessors.CollectProcessor<>(usages);
    FindUsagesProcessPresentation presentation = FindInProjectUtil.setupProcessPresentation(getProject(), false, FindInProjectUtil.setupViewPresentation(false, model));
    FindInProjectUtil.findUsages(model, getProject(), consumer, presentation);

    assertSize(2, usages);
  }

  public void testFindInPathInLibrariesIsNotBrokenAgain() {
    FindModel model = new FindModel();
    final PsiClass aClass = myJavaFacade.findClass("LibraryClass1");
    assertNotNull(aClass);
    model.setDirectoryName(aClass.getContainingFile().getContainingDirectory().getVirtualFile().getPath());
    model.setCaseSensitive(true);
    model.setCustomScope(false);
    model.setStringToFind(/*LibraryClas*/"s1"); // to defeat trigram index
    model.setProjectScope(false);

    List<UsageInfo> usages = Collections.synchronizedList(new ArrayList<>());
    CommonProcessors.CollectProcessor<UsageInfo> consumer = new CommonProcessors.CollectProcessor<>(usages);
    FindUsagesProcessPresentation presentation = FindInProjectUtil.setupProcessPresentation(getProject(), false, FindInProjectUtil.setupViewPresentation(false, model));
    FindInProjectUtil.findUsages(model, getProject(), consumer, presentation);

    assertEquals(3, usages.size());
  }

  public void testFindInPathInLibrarySourceDirShouldSearchJustInThisDirectoryOnly() {
    FindModel model = new FindModel();
    final PsiClass aClass = myJavaFacade.findClass("x.X");
    assertNotNull(aClass);
    String classDirPath = aClass.getContainingFile().getContainingDirectory().getVirtualFile().getPath();
    String sourceDirPath = ((PsiFile)aClass.getContainingFile().getNavigationElement()).getContainingDirectory().getVirtualFile().getPath();
    assertFalse(classDirPath.equals(sourceDirPath));
    model.setDirectoryName(sourceDirPath);
    model.setCaseSensitive(true);
    model.setCustomScope(false);
    model.setStringToFind("xxx");
    model.setProjectScope(false);

    List<UsageInfo> usages = Collections.synchronizedList(new ArrayList<>());
    CommonProcessors.CollectProcessor<UsageInfo> consumer = new CommonProcessors.CollectProcessor<>(usages);
    FindUsagesProcessPresentation presentation = FindInProjectUtil.setupProcessPresentation(getProject(), false, FindInProjectUtil.setupViewPresentation(false, model));
    FindInProjectUtil.findUsages(model, getProject(), consumer, presentation);

    UsageInfo info = assertOneElement(usages);
    assertEquals("X.java", info.getFile().getName());
  }

  public void testInnerSourceRoot() {
    doTest("ProjectClass2", new String[]{"ProjectClass2.java"}, GlobalSearchScope.projectScope(myProject));
  }

  private void doTest(String classNameToSearch, String[] expectedFileNames, SearchScope scope) {
    final PsiClass aClass = myJavaFacade.findClass(classNameToSearch);
    assertNotNull(aClass);

    PsiReference[] refs = ReferencesSearch.search(aClass, scope, false).toArray(PsiReference.EMPTY_ARRAY);

    ArrayList<PsiFile> files = new ArrayList<>();
    for (PsiReference ref : refs) {
      PsiFile file = ref.getElement().getContainingFile();
      if (!files.contains(file)) {
        files.add(file);
      }
    }

    assertEquals("files count", expectedFileNames.length, files.size());

    Collections.sort(files, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    Arrays.sort(expectedFileNames);

    for (int i = 0; i < expectedFileNames.length; i++) {
      String name = expectedFileNames[i];
      PsiFile file = files.get(i);
      assertEquals(name, file.getName());
    }
  }
}
