// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testIntegration;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testIntegration.TestFinderHelper;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class TestFinderHelperTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile myContentRootDir;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module myModule = fixture.getModule();
    myContentRootDir = fixture.createTempVirtualDir();
    PsiTestUtil.addSourceRoot(myModule, myContentRootDir);
    IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.junit4", "JUnit4");
    ModuleRootModificationUtil.addModuleLibrary(myModule, "JUnit4", junit4Library.getClassesUrls(), junit4Library.getSourcesUrls());
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
  }

  @Test
  public void testNoTestsForClass() {
    PsiClass c = createClass("Foo");
    assertThat(findTestsForClass(c)).isEmpty();
  }

  @Test
  public void testNoClassesForTest() {
    PsiClass t = createTest("FooTest");
    assertThat(findClassesForTest(t)).isEmpty();
  }

  @Test
  public void testIsTest() {
    assertFalse(isTest(createClass("Foo")));
    assertTrue(isTest(createTest("FooTest")));
    assertTrue(isTest(createTest("XXX")));
  }

  @Test
  public void testSimpleCase() {
    PsiClass c = createClass("Foo");
    PsiClass t = createTest("FooTest");

    assertThat(findTestsForClass(c)).containsExactlyInAnyOrder(t);
    assertThat(findClassesForTest(t)).containsExactlyInAnyOrder(c);
  }

  @Test
  public void testImplicitClass() {
    PsiFile file = heavyFixture.get().createFile("Main.java", "void main() { }");
    PsiClass c = ReadAction.computeBlocking(() -> PsiTreeUtil.getChildOfType(file, PsiImplicitClass.class));
    PsiClass t = createTest("MainTest");

    assertThat(findTestsForClass(c)).containsExactlyInAnyOrder(t);
    assertThat(findClassesForTest(t)).containsExactlyInAnyOrder(c);
  }

  @Test
  public void testIgnoreCase() {
    PsiClass c = createClass("FOo");
    PsiClass t = createTest("FooTest");

    assertThat(findTestsForClass(c)).containsExactlyInAnyOrder(t);
  }

  @Test
  public void testFromClassInners() {
    PsiClass c = createClass("Foo");
    PsiClass t = createTest("FooTest");

    PsiMethod cm = addMethod(c);
    PsiMethod tm = addMethod(t);

    assertThat(findTestsForClass(cm)).containsExactlyInAnyOrder(t);
    assertThat(findClassesForTest(tm)).containsExactlyInAnyOrder(c);

    assertFalse(isTest(cm));
    assertTrue(isTest(tm));
  }

  @Test
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

    assertThat(findTestsForClass(c)).containsExactlyInAnyOrder(tests);
    for (PsiClass each : tests) {
      assertThat(findClassesForTest(each)).containsExactlyInAnyOrder(c);
    }
  }

  @Test
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

    assertThat(findClassesForTest(t)).containsExactlyInAnyOrder(classes);
    for (PsiClass each : classes) {
      assertThat(findTestsForClass(each)).containsExactlyInAnyOrder(t);
    }
  }

  @Test
  public void testSortingFoundTestsByRelevance() {
    PsiClass c = createClass("FooBar");

    PsiClass[] tests = new PsiClass[] {
        createTest("FooBarBaz"),
        createTest("BazFooBar"),
        createTest("FooBarBazBaz"),
        createTest("BazFooBarBaz"),
        createTest("BazBazFooBar")};

    assertThat(findTestsForClass(c)).containsExactlyInAnyOrder(tests);
  }

  @Test
  public void testSortingFoundClassesByRelevance() {
    PsiClass t = createTest("FooBarBaz");

    PsiClass[] classes = new PsiClass[] {
        createClass("BarBaz"),
        createClass("FooBar"),
        createClass("Bar"),
        createClass("Baz"),
        createClass("Foo")};

    assertThat(findClassesForTest(t)).containsExactly(classes);
  }

  private PsiClass createClass(String name) throws IncorrectOperationException {
    PsiDirectory directory = ReadAction.computeBlocking(() -> heavyFixture.get().getPsiManager().findDirectory(myContentRootDir));
    assertNotNull(directory);
    return EdtTestUtil.runInEdtAndGet(() -> JavaDirectoryService.getInstance().createClass(directory, name));
  }

  private PsiClass createTest(String name) throws IncorrectOperationException {
    final PsiClass result = createClass(name);

    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(heavyFixture.get().getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
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
    }), null, null));

    return result;
  }

  private PsiMethod addMethod(final PsiClass c) throws IncorrectOperationException {
    final PsiMethod[] method = new PsiMethod[1];
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(heavyFixture.get().getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        method[0] = (PsiMethod)c.add(getPsiFactory().createMethod("foo", PsiTypes.voidType()));
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
    }), null, null));
    return method[0];
  }

  private PsiElementFactory getPsiFactory() {
    return JavaPsiFacade.getInstance(heavyFixture.get().getProject()).getElementFactory();
  }

  private static Collection<PsiElement> findTestsForClass(PsiElement element) {
    return ReadAction.computeBlocking(() -> TestFinderHelper.findTestsForClass(element));
  }

  private static Collection<PsiElement> findClassesForTest(PsiElement element) {
    return ReadAction.computeBlocking(() -> TestFinderHelper.findClassesForTest(element));
  }

  private static boolean isTest(PsiElement element) {
    return ReadAction.computeBlocking(() -> TestFinderHelper.isTest(element));
  }
}
