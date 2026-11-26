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

package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaProjectBuilderCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MultipleJdksHighlightingTest {
  private final JavaModuleFixtureBuilder<?>[] myBuilders = new JavaModuleFixtureBuilder[3];
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaProjectBuilderCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/multipleJdks", (projectBuilder, ignored) -> {
      myBuilders[0] = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
      myBuilders[0].setLanguageLevel(LanguageLevel.JDK_1_3);
      myBuilders[0].addJdkVersion(LanguageLevel.JDK_1_4);

      myBuilders[1] = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
      myBuilders[1].addJdkVersion(LanguageLevel.JDK_1_7);

      myBuilders[2] = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
      myBuilders[2].addJdkVersion(LanguageLevel.JDK_1_8);
    });
  private Module myJava3Module;
  private Module myJava7Module;
  private Module myJava8Module;
  private String myTestName;

  @BeforeEach
  void setUp(TestInfo testInfo) throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTestName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);

    VirtualFile java3Root = myFixture.getTempDirFixture().findOrCreateDir("java3");
    VirtualFile java7Root = myFixture.getTempDirFixture().findOrCreateDir("java7");
    VirtualFile java8Root = myFixture.getTempDirFixture().findOrCreateDir("java8");

    myJava3Module = myBuilders[0].getFixture().getModule();
    myJava7Module = myBuilders[1].getFixture().getModule();
    myJava8Module = myBuilders[2].getFixture().getModule();
    ModuleRootModificationUtil.updateModel(myJava3Module, model -> model.addContentEntry(java3Root).addSourceFolder(java3Root, false));
    ModuleRootModificationUtil.updateModel(myJava7Module, model -> model.addContentEntry(java7Root).addSourceFolder(java7Root, false));
    ModuleRootModificationUtil.updateModel(myJava8Module, model -> model.addContentEntry(java8Root).addSourceFolder(java8Root, false));
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());
  }

  private void addDependencies_37_78() {
    ModuleRootModificationUtil.addDependency(myJava7Module, myJava8Module);
    ModuleRootModificationUtil.addDependency(myJava3Module, myJava7Module);
  }

  @Test
  public void testGetClass() {
    addDependencies_37_78();
    doTest();
  }

  @Test
  public void testAutoCloseable() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Sdk mockJdk14 = IdeaTestUtil.getMockJdk14();
    ModuleRootModificationUtil.updateModel(myJava8Module, model -> {
      WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(mockJdk14, model.getProject()));
      model.setSdk(mockJdk14);
    });
    addDependencies_37_78();
    final String name = myTestName;
    for (Module module : new Module[] {myJava7Module, myJava8Module}) {
      ModuleRootModificationUtil.updateModel(module, model -> ClsGenericsHighlightingTestUtil.commitLibraryModel(model, myFixture.getTestDataPath(), name + ".jar"));
    }
    myFixture.configureByFile("java8/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testWrongSuperInLibrary() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addDependencies_37_78();
    final String name = myTestName;
    for (Module module : new Module[] {myJava7Module, myJava8Module}) {
      ModuleRootModificationUtil.updateModel(module, model -> ClsGenericsHighlightingTestUtil.commitLibraryModel(model, myFixture.getTestDataPath(), name + ".jar"));
    }

    myFixture.configureByFile("java8/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testWrongComparator() {
    addDependencies_37_78();
   doTestWithoutLibrary();
  }

  @Test
  public void testWrongComparatorInUpperBound() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  @Test
  public void testGenericComparator() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  @Test
  public void testGenericCallableWithDifferentTypeArgs() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  @Test
  public void testSuperclassImplementsUnknownType() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  @Test
  public void testDeclaredTypeOfVariableImplementsUnknownType() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }
  
  @Test
  public void testSuperclassImplementsGenericsOfUnknownType() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  @Test
  public void testSuperMethodNotExist() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  @Test
  public void testNoOverriding() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  @Test
  public void testStaticCallOnChildWithNotAccessibleParent() {
    addDependencies_37_78();
    ModuleRootModificationUtil.updateModel(myJava3Module, m -> m.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_5));
    doTest3Modules();
  }

  @Test
  public void testBoxedTypesWhenPreGenericJDKPresentInProject() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("java8/p/" + myTestName + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testRawAssignmentToGenerics() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ModuleRootModificationUtil.addDependency(myJava7Module, myJava3Module);
    final String name = myTestName;
    myFixture.copyFileToProject("java3/p/" + name + ".java");
    myFixture.configureByFiles("java7/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testCloseableAutoCloseable() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myJava7Module, LanguageLevel.JDK_1_7);
    ModuleRootModificationUtil.addDependency(myJava7Module, myJava3Module);
    final String name = myTestName;
    myFixture.copyFileToProject("java3/p/" + name + ".java");
    myFixture.configureByFiles("java7/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testDependsOnNewerJdk() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myJava7Module, LanguageLevel.JDK_1_7);
    ModuleRootModificationUtil.addDependency(myJava3Module, myJava7Module);
    final String name = myTestName;
    myFixture.copyFileToProject("java7/p/" + name + ".java");
    myFixture.configureByFiles("java3/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testMissedAutoCloseable() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myJava7Module, LanguageLevel.JDK_1_7);
    ModuleRootModificationUtil.addDependency(myJava3Module, myJava7Module);
    final String name = myTestName;
    myFixture.copyFileToProject("java7/p/" + name + ".java");
    myFixture.configureByFiles("java3/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testLanguageLevelInReturnTypeCheck() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addDependencies_37_78();
    final String name = myTestName;
    myFixture.configureByFiles("java3/p/" + name + ".java", "java7/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testUnrelatedDefaultsFromDifferentJdkVersions() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava7Module);
    myFixture.copyFileToProject("java7/p/I.java");
    myFixture.copyFileToProject("java8/p/I.java");

    final String testName = myTestName;
    myFixture.configureByFiles("java8/p/" + testName + ".java", "java7/p/" + testName + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testMethodReferencePointingToDifferentJdk() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava3Module);
    final String testName = myTestName;
    myFixture.copyFileToProject("java3/p/" + testName + ".java");
    myFixture.copyFileToProject("java8/p/" + testName + ".java");

    myFixture.configureByFiles("java8/p/" + testName + ".java", "java3/p/" + testName + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testInheritorsOfJdkClassOnlyInModulesWithThatJdk() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava7Module);

    PsiJavaFile file7 = (PsiJavaFile) myFixture.addFileToProject("java7/a.java", "class A extends java.util.ArrayList {}");
    PsiJavaFile file8 = (PsiJavaFile) myFixture.addFileToProject("java8/a.java", "class A extends java.util.ArrayList {}");
    PsiClass usage7 = ReadAction.computeBlocking(() -> file7.getClasses()[0]);
    PsiClass usage8 = ReadAction.computeBlocking(() -> file8.getClasses()[0]);
    ReadAction.runBlocking(() -> PsiUtilCore.ensureValid(usage7));

    PsiClass abstractList7 = ReadAction.computeBlocking(() -> myFixture.getJavaFacade().findClass(AbstractList.class.getName(), usage7.getResolveScope()));
    PsiClass abstractList8 = ReadAction.computeBlocking(() -> myFixture.getJavaFacade().findClass(AbstractList.class.getName(), usage8.getResolveScope()));
    assertNotSame(abstractList7, abstractList8);

    checkScopes(
      ReadAction.computeBlocking(()->ClassInheritorsSearch.search(abstractList7).findAll()), IdeaTestUtil.getMockJdk17Path(), usage7);
    checkScopes(ReadAction.computeBlocking(()->ClassInheritorsSearch.search(abstractList8).findAll()), IdeaTestUtil.getMockJdk18Path(), usage8);
  }

  private static void checkScopes(Collection<PsiClass> classes, File jdkHome, PsiClass usageInProject) {
    assertTrue(classes.contains(usageInProject));

    for (PsiClass cls : classes) {
      if (cls == usageInProject) continue;

      VirtualFile file = PsiUtilCore.getVirtualFile(cls);
      assertNotNull(file);
      assertTrue(FileUtil.startsWith(file.getPath(), FileUtil.toSystemIndependentName(jdkHome.getPath()), true), file.getPath());
    }
  }

  @Test
  public void testFindUsagesInLibrarySource() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiTestUtil.addLibrary(myJava7Module, "lib", myFixture.getTempDirFixture().findOrCreateDir("lib").getPath(), new String[]{"/libClasses"}, new String[]{"/libSrc"});
    PsiFile libSrc = myFixture.addFileToProject("lib/libSrc/Foo.java", "class C{{ new javax.swing.JScrollPane().getHorizontalScrollBar(); }}");
    assertTrue(ReadAction.computeBlocking(() -> FileIndexFacade.getInstance(myFixture.getProject()).isInLibrarySource(libSrc.getVirtualFile())));

    PsiReference ref = ReadAction.computeBlocking(() -> libSrc.findReferenceAt(libSrc.getText().indexOf("Horizontal")));
    PsiMethod method = assertInstanceOf(PsiMethod.class, ReadAction.computeBlocking(() -> ref.resolve()));
    assertThat(ReadAction.computeBlocking(()->MethodReferencesSearch.search(method).findAll())).contains(ref);
  }

  @Test
  public void testConditionalAssignedToJava3Object() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava3Module);
    final String name = myTestName;
    myFixture.copyFileToProject("java3/p/" + name + ".java");
    myFixture.configureByFiles("java8/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  @Test
  public void testInFileReferencesHighlighting() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava7Module);
    myFixture.copyFileToProject("java7/p/Object7.java");
    myFixture.configureByFiles("java8/p/" + myTestName + ".java");
    RangeHighlighter[] highlighters = EdtTestUtil.runInEdtAndGet(() -> {
      HighlightUsagesHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      return myFixture.getEditor().getMarkupModel().getAllHighlighters();
    });
    assertEquals(1, highlighters.length, Arrays.toString(highlighters));
    final RangeHighlighter highlighter = highlighters[0];
    assertEquals(64, highlighter.getStartOffset());
    assertEquals(72, highlighter.getEndOffset());
  }

  @Test
  public void testInFileReferencesHighlighting2() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava7Module);
    myFixture.copyFileToProject("java7/p/List7.java");
    myFixture.configureByFiles("java8/p/" + myTestName + ".java");
    RangeHighlighter[] highlighters = EdtTestUtil.runInEdtAndGet(() -> {
      HighlightUsagesHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      return myFixture.getEditor().getMarkupModel().getAllHighlighters();
    });
    assertEquals(1, highlighters.length, Arrays.toString(highlighters));
    final RangeHighlighter highlighter = highlighters[0];
    assertEquals(60, highlighter.getStartOffset());
    assertEquals(66, highlighter.getEndOffset());
  }

  @Test
  public void testOverrideClassLoaderMethodSince7() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava3Module);
    myFixture.addFileToProject("java3/MyLoader.java",
                               "public class MyLoader extends ClassLoader { protected Object getClassLoadingLock(String className) {} }");
    PsiFile file = myFixture.addFileToProject("java8/Usage.java",
                                              "class My extends MyLoader {{ " +
                                              "  <caret>getClassLoadingLock(\"\"); " +
                                              "}}\n" +
                                              "class Standard extends ClassLoader {{ " +
                                              "  getClassLoadingLock(\"\"); " +
                                              "}}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    RangeHighlighter[] highlighters = EdtTestUtil.runInEdtAndGet(() -> {
      myFixture.checkHighlighting();

      HighlightUsagesHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      return myFixture.getEditor().getMarkupModel().getAllHighlighters();
    });
    assertEquals(2, highlighters.length, Arrays.toString(highlighters));
  }

  private void doTestWithoutLibrary() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = myTestName;
    myFixture.configureByFiles("java7/p/" + name + ".java", "java8/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }

  private void doTest3Modules() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = myTestName;
    myFixture.configureByFiles("java3/p/" + name + ".java", "java7/p/" + name + ".java", "java8/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }


  private void doTest() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = myTestName;
    for (Module module : new Module[] {myJava7Module, myJava8Module}) {
      ModuleRootModificationUtil.updateModel(module, model -> ClsGenericsHighlightingTestUtil.commitLibraryModel(model, myFixture.getTestDataPath(), name + ".jar"));
    }

    myFixture.configureByFiles("java7/p/" + name + ".java", "java8/p/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting());
  }
}
