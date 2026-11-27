// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@TestApplication
public class SearchInLibsTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private Project myProject;
  private JavaPsiFacadeEx myJavaFacade;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    myProject = fixture.getProject();
    myJavaFacade = fixture.getJavaFacade();
    Module myModule = fixture.getModule();
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/searchInLibs";
    VirtualFile rootFile = fixture.createTestProjectStructure(root, false);

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
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
  }

  @Test
  public void testFindUsagesInProject() {
    doTest("ProjectClass", new String[]{"ProjectClass.java"}, GlobalSearchScope.projectScope(myProject));
  }
  @Test
  public void testFindUsagesInProject1() {
    doTest("LibraryClass1", new String[]{"ProjectClass.java"}, GlobalSearchScope.projectScope(myProject));
  }
  @Test
  public void testFindUsagesInProject2() {
    doTest("LibraryClass2", ArrayUtil.EMPTY_STRING_ARRAY, GlobalSearchScope.projectScope(myProject));
  }

  @Test
  public void testFindUsagesInLibs() {
    doTest("ProjectClass", new String[]{"ProjectClass.java"}, GlobalSearchScope.allScope(myProject));
  }
  @Test
  public void testFindUsagesInLibs1() {
    doTest("LibraryClass1", new String[]{"LibraryClass2.java", "ProjectClass.java"}, GlobalSearchScope.allScope(myProject));
  }
  @Test
  public void testFindUsagesInLibs2() {
    doTest("LibraryClass2", new String[]{"LibraryClass1.java"}, GlobalSearchScope.allScope(myProject));
  }

  @Test
  public void testFindInPathInLibraryDirActuallySearchesInTheirSourcesToo() {
    FindModel model = new FindModel();
    final PsiClass aClass = ReadAction.computeBlocking(() -> myJavaFacade.findClass("LibraryClass1"));
    assertNotNull(aClass);
    model.setDirectoryName(ReadAction.computeBlocking(() -> aClass.getContainingFile().getContainingDirectory().getVirtualFile().getPath()));
    model.setCaseSensitive(true);
    model.setCustomScope(false);
    model.setStringToFind("LibraryClass1");
    model.setProjectScope(false);

    List<UsageInfo> usages = Collections.synchronizedList(new ArrayList<>());
    Processor<UsageInfo> consumer = new CommonProcessors.CollectProcessor<>(usages);
    FindUsagesProcessPresentation presentation = FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model));
    FindInProjectUtil.findUsages(model, myProject, consumer, presentation);

    assertEquals(2, usages.size());
  }

  @Test
  public void testFindInPathInLibrariesIsNotBrokenAgain() {
    FindModel model = new FindModel();
    final PsiClass aClass = ReadAction.computeBlocking(() -> myJavaFacade.findClass("LibraryClass1"));
    assertNotNull(aClass);
    model.setDirectoryName(ReadAction.computeBlocking(() -> aClass.getContainingFile().getContainingDirectory().getVirtualFile().getPath()));
    model.setCaseSensitive(true);
    model.setCustomScope(false);
    model.setStringToFind(/*LibraryClas*/"s1"); // to defeat trigram index
    model.setProjectScope(false);

    List<UsageInfo> usages = Collections.synchronizedList(new ArrayList<>());
    Processor<UsageInfo> consumer = new CommonProcessors.CollectProcessor<>(usages);
    FindUsagesProcessPresentation presentation = FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model));
    FindInProjectUtil.findUsages(model, myProject, consumer, presentation);

    assertEquals(3, usages.size());
  }

  @Test
  public void testFindInPathInLibrarySourceDirShouldSearchJustInThisDirectoryOnly() {
    FindModel model = new FindModel();
    final PsiClass aClass = ReadAction.computeBlocking(() -> myJavaFacade.findClass("x.X"));
    assertNotNull(aClass);
    String classDirPath = ReadAction.computeBlocking(() -> aClass.getContainingFile().getContainingDirectory().getVirtualFile().getPath());
    String sourceDirPath = ReadAction.computeBlocking(
      () -> ((PsiFile)aClass.getContainingFile().getNavigationElement()).getContainingDirectory().getVirtualFile().getPath());
    assertNotEquals(classDirPath, sourceDirPath);
    model.setDirectoryName(sourceDirPath);
    model.setCaseSensitive(true);
    model.setCustomScope(false);
    model.setStringToFind("xxx");
    model.setProjectScope(false);

    List<UsageInfo> usages = Collections.synchronizedList(new ArrayList<>());
    CommonProcessors.CollectProcessor<UsageInfo> consumer = new CommonProcessors.CollectProcessor<>(usages);
    FindUsagesProcessPresentation presentation = FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model));
    FindInProjectUtil.findUsages(model, myProject, consumer, presentation);

    UsageInfo info = assertThat(usages).singleElement().actual();
    assertEquals("X.java", ReadAction.computeBlocking(() -> info.getFile().getName()));
  }

  @Test
  public void testInnerSourceRoot() {
    doTest("ProjectClass2", new String[]{"ProjectClass2.java"}, GlobalSearchScope.projectScope(myProject));
  }

  private void doTest(String classNameToSearch, String[] expectedFileNames, SearchScope scope) {
    ReadAction.runBlocking(() -> {
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

      assertEquals(expectedFileNames.length, files.size(), "files count");

      Collections.sort(files, Comparator.comparing(PsiFileSystemItem::getName));
      Arrays.sort(expectedFileNames);

      for (int i = 0; i < expectedFileNames.length; i++) {
        String name = expectedFileNames[i];
        PsiFile file = files.get(i);
        assertEquals(name, file.getName());
      }
    });
  }
}
