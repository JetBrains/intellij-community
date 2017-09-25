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

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;

import java.io.File;
import java.io.IOException;
import java.util.AbstractList;
import java.util.Collection;

public class MultipleJdksHighlightingTest extends UsefulTestCase {
  private JavaCodeInsightTestFixture myFixture;
  private Module myJava3Module;
  private Module myJava7Module;
  private Module myJava8Module;

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    finally {
      myFixture = null;
      myJava3Module = null;
      myJava7Module = null;
      myJava8Module = null;

      super.tearDown();
    }
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setTestDataPath(PathManagerEx.getTestDataPath() + "/codeInsight/multipleJdks");
    final JavaModuleFixtureBuilder[] builders = new JavaModuleFixtureBuilder[3];

    builders[0] = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builders[0].setLanguageLevel(LanguageLevel.JDK_1_3);
    builders[0].addJdk(IdeaTestUtil.getMockJdk14Path().getPath());

    builders[1] = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builders[1].addJdk(IdeaTestUtil.getMockJdk17Path().getPath());

    builders[2] = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builders[2].addJdk(IdeaTestUtil.getMockJdk18Path().getPath());

    myFixture.setUp();
    myJava3Module = builders[0].getFixture().getModule();
    myJava7Module = builders[1].getFixture().getModule();
    myJava8Module = builders[2].getFixture().getModule();
    ModuleRootModificationUtil.updateModel(myJava3Module, model -> {
      String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath()) + "/java3";
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl, false);
    });

    ModuleRootModificationUtil.updateModel(myJava7Module, model -> {
      String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath()) + "/java7";
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl, false);
    });

    ModuleRootModificationUtil.updateModel(myJava8Module, model -> {
      String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath()) + "/java8";
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl, false);
    });
  }

  private void addDependencies_37_78() {
    ModuleRootModificationUtil.addDependency(myJava7Module, myJava8Module);
    ModuleRootModificationUtil.addDependency(myJava3Module, myJava7Module);
  }

  public void testGetClass() {
    addDependencies_37_78();
    doTest();
  }

  public void testWrongSuperInLibrary() {
    addDependencies_37_78();
    final String name = getTestName(false);
    for (Module module : new Module[] {myJava7Module, myJava8Module}) {
      ModuleRootModificationUtil.updateModel(module, model -> ClsGenericsHighlightingTest.commitLibraryModel(model, myFixture.getTestDataPath(), name + ".jar"));
    }

    myFixture.configureByFile("java8/p/" + name + ".java");
    myFixture.checkHighlighting();
  }

  public void testWrongComparator() {
    addDependencies_37_78();
   doTestWithoutLibrary();
  }

  public void testWrongComparatorInUpperBound() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  public void testGenericComparator() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  public void testGenericCallableWithDifferentTypeArgs() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  public void testSuperclassImplementsUnknownType() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  public void testDeclaredTypeOfVariableImplementsUnknownType() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }
  
  public void testSuperclassImplementsGenericsOfUnknownType() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  public void testSuperMethodNotExist() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  public void testNoOverriding() {
    addDependencies_37_78();
    doTestWithoutLibrary();
  }

  public void testStaticCallOnChildWithNotAccessibleParent() {
    addDependencies_37_78();
    doTest3Modules();
  }

  public void testBoxedTypesWhenPreGenericJDKPresentInProject() {
    myFixture.configureByFiles("java8/p/" + getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  public void testRawAssignmentToGenerics() {
    ModuleRootModificationUtil.addDependency(myJava7Module, myJava3Module);
    final String name = getTestName(false);
    myFixture.copyFileToProject("java3/p/" + name + ".java");
    myFixture.configureByFiles("java7/p/" + name + ".java");
    myFixture.checkHighlighting();
  }

  public void testCloseableAutoCloseable() {
    IdeaTestUtil.setModuleLanguageLevel(myJava7Module, LanguageLevel.JDK_1_7);
    ModuleRootModificationUtil.addDependency(myJava7Module, myJava3Module);
    final String name = getTestName(false);
    myFixture.copyFileToProject("java3/p/" + name + ".java");
    myFixture.configureByFiles("java7/p/" + name + ".java");
    myFixture.checkHighlighting();
  }

  public void testMissedAutoCloseable() {
    IdeaTestUtil.setModuleLanguageLevel(myJava7Module, LanguageLevel.JDK_1_7);
    ModuleRootModificationUtil.addDependency(myJava3Module, myJava7Module);
    final String name = getTestName(false);
    myFixture.copyFileToProject("java7/p/" + name + ".java");
    myFixture.configureByFiles("java3/p/" + name + ".java");
    myFixture.checkHighlighting();
  }

  public void testLanguageLevelInReturnTypeCheck() {
    addDependencies_37_78();
    final String name = getTestName(false);
    myFixture.configureByFiles("java3/p/" + name + ".java", "java7/p/" + name + ".java");
    myFixture.checkHighlighting();
  }

  public void testUnrelatedDefaultsFromDifferentJdkVersions() {
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava7Module);
    myFixture.copyFileToProject("java7/p/I.java");
    myFixture.copyFileToProject("java8/p/I.java");

    final String testName = getTestName(false);
    myFixture.configureByFiles("java8/p/" + testName + ".java", "java7/p/" + testName + ".java");
    myFixture.checkHighlighting();
  }

  public void testMethodReferencePointingToDifferentJdk() {
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava3Module);
    final String testName = getTestName(false);
    myFixture.copyFileToProject("java3/p/" + testName + ".java");
    myFixture.copyFileToProject("java8/p/" + testName + ".java");

    myFixture.configureByFiles("java8/p/" + testName + ".java", "java3/p/" + testName + ".java");
    myFixture.checkHighlighting();
  }

  public void testInheritorsOfJdkClassOnlyInModulesWithThatJdk() {
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava7Module);

    PsiClass usage7 = ((PsiJavaFile) myFixture.addFileToProject("java7/a.java", "class A extends java.util.ArrayList {}")).getClasses()[0];
    PsiClass usage8 = ((PsiJavaFile) myFixture.addFileToProject("java8/a.java", "class A extends java.util.ArrayList {}")).getClasses()[0];

    PsiClass abstractList7 = myFixture.getJavaFacade().findClass(AbstractList.class.getName(), usage7.getResolveScope());
    PsiClass abstractList8 = myFixture.getJavaFacade().findClass(AbstractList.class.getName(), usage8.getResolveScope());
    assertNotSame(abstractList7, abstractList8);

    checkScopes(ClassInheritorsSearch.search(abstractList7).findAll(), IdeaTestUtil.getMockJdk17Path(), usage7);
    checkScopes(ClassInheritorsSearch.search(abstractList8).findAll(), IdeaTestUtil.getMockJdk18Path(), usage8);
  }

  private static void checkScopes(Collection<PsiClass> classes, File jdkHome, PsiClass usageInProject) {
    assertTrue(classes.contains(usageInProject));

    for (PsiClass cls : classes) {
      if (cls == usageInProject) continue;

      VirtualFile file = PsiUtilCore.getVirtualFile(cls);
      assertNotNull(file);
      assertTrue(file.getPath(), FileUtil.startsWith(file.getPath(), FileUtil.toSystemIndependentName(jdkHome.getPath()), true));
    }
  }

  public void testFindUsagesInLibrarySource() throws IOException {
    PsiTestUtil.addLibrary(myJava7Module, "lib", myFixture.getTempDirFixture().findOrCreateDir("lib").getPath(), new String[]{"/libClasses"}, new String[]{"/libSrc"});
    PsiFile libSrc = myFixture.addFileToProject("lib/libSrc/Foo.java", "class C{{ new javax.swing.JScrollPane().getHorizontalScrollBar(); }}");
    assertTrue(FileIndexFacade.getInstance(myFixture.getProject()).isInLibrarySource(libSrc.getVirtualFile()));

    PsiReference ref = libSrc.findReferenceAt(libSrc.getText().indexOf("Horizontal"));
    PsiMethod method = assertInstanceOf(ref.resolve(), PsiMethod.class);
    assertContainsElements(MethodReferencesSearch.search(method).findAll(), ref);
  }

  public void testConditionalAssignedToJava3Object() {
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava3Module);
    final String name = getTestName(false);
    myFixture.copyFileToProject("java3/p/" + name + ".java");
    myFixture.configureByFiles("java8/p/" + name + ".java");
    myFixture.checkHighlighting();
  }

  public void testInFileReferencesHighlighting() {
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava7Module);
    myFixture.copyFileToProject("java7/p/Object7.java");
    myFixture.configureByFiles("java8/p/" + getTestName(false) + ".java");
    HighlightUsagesHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    final RangeHighlighter highlighter = assertOneElement(myFixture.getEditor().getMarkupModel().getAllHighlighters());
    assertEquals(64, highlighter.getStartOffset());
    assertEquals(72, highlighter.getEndOffset());
  }

  public void testInFileReferencesHighlighting2() {
    ModuleRootModificationUtil.addDependency(myJava8Module, myJava7Module);
    myFixture.copyFileToProject("java7/p/List7.java");
    myFixture.configureByFiles("java8/p/" + getTestName(false) + ".java");
    HighlightUsagesHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    final RangeHighlighter highlighter = assertOneElement(myFixture.getEditor().getMarkupModel().getAllHighlighters());
    assertEquals(60, highlighter.getStartOffset());
    assertEquals(66, highlighter.getEndOffset());
  }

  private void doTestWithoutLibrary() {
    final String name = getTestName(false);
    myFixture.configureByFiles("java7/p/" + name + ".java", "java8/p/" + name + ".java");
    myFixture.checkHighlighting();
  }

  private void doTest3Modules() {
    final String name = getTestName(false);
    myFixture.configureByFiles("java3/p/" + name + ".java", "java7/p/" + name + ".java", "java8/p/" + name + ".java");
    myFixture.checkHighlighting();
  }


  private void doTest() {
    final String name = getTestName(false);
    for (Module module : new Module[] {myJava7Module, myJava8Module}) {
      ModuleRootModificationUtil.updateModel(module, model -> ClsGenericsHighlightingTest.commitLibraryModel(model, myFixture.getTestDataPath(), name + ".jar"));
    }

    myFixture.configureByFiles("java7/p/" + name + ".java", "java8/p/" + name + ".java");
    myFixture.checkHighlighting();
  }
}
