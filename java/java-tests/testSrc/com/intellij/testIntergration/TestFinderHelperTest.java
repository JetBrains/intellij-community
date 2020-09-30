// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntergration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.psi.*;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testIntegration.TestFinderHelper;
import com.intellij.util.IncorrectOperationException;

public class TestFinderHelperTest extends JavaPsiTestCase {
  private VirtualFile myContentRootDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myContentRootDir = getTempDir().createVirtualDir();
    PsiTestUtil.addSourceRoot(myModule, myContentRootDir);
    IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getProjectLibrary("JUnit4");
    ModuleRootModificationUtil.addModuleLibrary(myModule, "JUnit4", junit4Library.getClassesUrls(), junit4Library.getSourcesUrls());
  }

  public void testNoTestsForClass() {
    PsiClass c = createClass("Foo");
    assertSameElements(TestFinderHelper.findTestsForClass(c));
  }

  public void testNoClassesForTest() {
    PsiClass t = createTest("FooTest");
    assertEmpty(TestFinderHelper.findClassesForTest(t));
  }

  public void testIsTest() {
    assertFalse(TestFinderHelper.isTest(createClass("Foo")));
    assertTrue(TestFinderHelper.isTest(createTest("FooTest")));
    assertTrue(TestFinderHelper.isTest(createTest("XXX")));
  }

  public void testSimpleCase() {
    PsiClass c = createClass("Foo");
    PsiClass t = createTest("FooTest");

    assertSameElements(TestFinderHelper.findTestsForClass(c), t);
    assertSameElements(TestFinderHelper.findClassesForTest(t), c);
  }

  public void testIgnoreCase() {
    PsiClass c = createClass("FOo");
    PsiClass t = createTest("FooTest");

    assertSameElements(TestFinderHelper.findTestsForClass(c), t);
  }

  public void testFromClassInners() {
    PsiClass c = createClass("Foo");
    PsiClass t = createTest("FooTest");

    PsiMethod cm = addMethod(c);
    PsiMethod tm = addMethod(t);

    assertSameElements(TestFinderHelper.findTestsForClass(cm), t);
    assertSameElements(TestFinderHelper.findClassesForTest(tm), c);

    assertFalse(TestFinderHelper.isTest(cm));
    assertTrue(TestFinderHelper.isTest(tm));
  }

  public void testVariousTestNaming() {
    PsiClass c = createClass("Foo");

    PsiClass[] tests = new PsiClass[] {
        createTest("FooTestCase"),
        createTest("FooFixture"),
        createTest("FooAbc"),
        createTest("TestFoo"),
        createTest("TestCaseFoo"),
        createTest("FixtureFoo"),
        createTest("AbcFoo"),
        createTest("AbcFooAbc")};

    assertSameElements(TestFinderHelper.findTestsForClass(c), tests);
    for (PsiClass each : tests) {
      assertSameElements(TestFinderHelper.findClassesForTest(each), c);
    }
  }

  public void testVariousClassNaming() {
    PsiClass t = createTest("FooBarBaz");

    PsiClass[] classes = new PsiClass[] {
        createClass("Foo"),
        createClass("Bar"),
        createClass("Baz"),
        createClass("FooBar"),
        createClass("BarBaz")};

    // not to find classes:
    createClass("FooBaz"); // adjacent-words only
    createClass("ooBar"); // start with word
    createClass("az"); // whole word

    assertSameElements(TestFinderHelper.findClassesForTest(t), classes);
    for (PsiClass each : classes) {
      assertSameElements(TestFinderHelper.findTestsForClass(each), t);
    }
  }

  public void testSortingFoundTestsByRelevance() {
    PsiClass c = createClass("FooBar");

    PsiClass[] tests = new PsiClass[] {
        createTest("FooBarBaz"),
        createTest("BazFooBar"),
        createTest("FooBarBazBaz"),
        createTest("BazFooBarBaz"),
        createTest("BazBazFooBar")};

    assertSameElements(TestFinderHelper.findTestsForClass(c), tests);
  }

  public void testSortingFoundClassesByRelevance() {
    PsiClass t = createTest("FooBarBaz");

    PsiClass[] classes = new PsiClass[] {
        createClass("BarBaz"),
        createClass("FooBar"),
        createClass("Bar"),
        createClass("Baz"),
        createClass("Foo")};

    assertOrderedEquals(TestFinderHelper.findClassesForTest(t), classes);
  }

  private PsiClass createClass(String name) throws IncorrectOperationException {
    PsiDirectory directory = myPsiManager.findDirectory(myContentRootDir);
    assertNotNull(directory);
    return JavaDirectoryService.getInstance().createClass(directory, name);
  }

  private PsiClass createTest(String name) throws IncorrectOperationException {
    final PsiClass result = createClass(name);

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        PsiAnnotation annotation = getPsiFactory().createAnnotationFromText("@org.junit.runner.RunWith", result);
        PsiModifierList modifiers = result.getModifierList();
        assertNotNull(modifiers);
        PsiElement first = modifiers.getFirstChild();
        if (first == null) {
          modifiers.add(annotation);
        }
        else {
          modifiers.addBefore(annotation, first);
        }
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
    }), null, null);

    return result;
  }

  private PsiMethod addMethod(final PsiClass c) throws IncorrectOperationException {
    final PsiMethod[] method = new PsiMethod[1];
    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        method[0] = (PsiMethod)c.add(getPsiFactory().createMethod("foo", PsiType.VOID));
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
    }), null, null);
    return method[0];
  }

  private PsiElementFactory getPsiFactory() {
    return JavaPsiFacade.getInstance(myProject).getElementFactory();
  }
}
