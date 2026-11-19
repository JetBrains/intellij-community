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
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.move.moveInner.MoveInnerDialog;
import com.intellij.refactoring.move.moveInner.MoveInnerImpl;
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class MoveInnerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveInner/");

  @Test
  public void testScr13730() {
    multiFile.get().doTest(createAction("pack1.TopLevel.StaticInner", "StaticInner", false, null, false, false, null));
  }

  @Test
  public void testScr15142() {
    multiFile.get().doTest(createAction("xxx.Outer.Inner", "Inner", false, null, false, false, null));
  }

  @Test
  public void testNonJavaFiles() {
    multiFile.get().doTest(createAction("pack1.Outer.Inner", "Inner", false, null, true, true, null));
  }

  @Test
  public void testXmlReferences() {
    multiFile.get().doTest(createAction("pack1.Outer.Inner", "Inner", false, null, true, true, null));
  }

  @Test
  public void testMostInnerClassImport() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    JavaCodeStyleSettings javaCodeStyleSettings = CodeStyle.getSettings(myFixture.getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    javaCodeStyleSettings.INSERT_INNER_CLASS_IMPORTS = true;
    multiFile.get().doTest(createAction("pack1.Outer.Inner", "Inner", false, null, true, true, null));
  }

  @Test
  public void testScr22592() {
    multiFile.get().doTest(createAction("xxx.Outer.Inner", "Inner", true, "outer", false, false, null));
  }

  @Test
  public void testInnerClassInheritance() {
    try {
      multiFile.get().doTest(createAction("p.A.B", "B", false, null, false, false, null));
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("class <b><code>p.A.C</code></b> will become inaccessible from class <b><code>p.A.B</code></b>", e.getMessage());
    }
  }

  @Test
  public void testInnerClassSelfRef() {
    multiFile.get().doTest(createAction("p.A.B", "B", false, null, false, false, null));
  }
  
  @Test
  public void testInnerClassQualifiedNewExpression() {
    multiFile.get().doTest(createAction("p.A.B", "B", false, null, false, false, null));
  }

  @Test
  public void testScr30106() {
    multiFile.get().doTest(createAction("p.A.B", "B", true, "outer", false, false, null));
  }

  @Test
  public void testConstructorVisibility() {  // IDEADEV-19561
    multiFile.get().doTest(createAction("p.A.B", "B", false, null, false, false, null));
  }

  @Test
  public void testConstructorProtectedVisibility() {  // IDEADEV-19561
    multiFile.get().doTest(createAction("p.b.A.B", "B", false, null, false, false, "p"));
  }

  @Test
  public void testConstructorUtilClassVisibility() {
    multiFile.get().doTest(createAction("p.A.B", "B", false, null, false, false, null));
  }

  @Test
  public void testFieldAccessInSuper() {
    multiFile.get().doTest(createAction("p.A.B", "B", true, "a", false, false, null));
  }

  @Test
  public void testToOtherPackage() {
    multiFile.get().doTest(createAction("package1.OuterClass.InnerClass", "InnerClass", false, null, false, false, "package2"));
  }

  @Test
  public void testImportStaticOfEnum() { // IDEADEV-28619
    multiFile.get().doTest(createAction("p.A.E", "E", false, null, false, false, null));
  }

  @Test
  public void testInnerInnerClassUsedInTypeParams() {
    multiFile.get().doTest(createAction("p.Main.A", "A", false, null, false, false, null));
  }

  @Test
  public void testEnumConstructorVisibility() { // IDEADEV-28619
    multiFile.get().doTest(createAction("p.A.E", "E", false, null, false, false, "p2"));
  }

  @Test
  public void testEnumStaticImport() {
    multiFile.get().doTest(createAction("pack1.Outer.Inner", "Inner", false, null, false, false, "pack1"));
  }

  @Test
  public void testQualifyThisHierarchy() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    final String innerClassName = "pack1.DImpl.MyRunnable";
    multiFile.get().doTest(new MyPerformAction(innerClassName, "MyRunnable", false, "d",
                               false, false, null) {
      @Override
      protected boolean isPassOuterClass() {
        final PsiClass outerClass = myFixture.getJavaFacade().findClass("pack1.DImpl", GlobalSearchScope.moduleScope(myFixture.getModule()));
        assertNotNull(outerClass);
        final PsiClass innerClass = myFixture.getJavaFacade().findClass(innerClassName, GlobalSearchScope.moduleScope(myFixture.getModule()));
        assertNotNull(innerClass);
        return MoveInnerDialog.isThisNeeded(innerClass, outerClass);
      }
    });
  }

  private ThrowableRunnable<Exception> createAction(@NonNls final String innerClassName,
                                         @NonNls final String newClassName,
                                         final boolean passOuterClass,
                                         @NonNls final String parameterName,
                                         final boolean searchInComments,
                                         final boolean searchInNonJava,
                                         @NonNls @Nullable final String packageName) {
    return new MyPerformAction(innerClassName, newClassName, passOuterClass, parameterName, searchInComments, searchInNonJava, packageName);
  }

  private class MyPerformAction implements ThrowableRunnable<Exception> {
    private final String myInnerClassName;
    private final String myPackageName;
    private final String myNewClassName;
    private final boolean myPassOuterClass;
    private final String myParameterName;
    private final boolean mySearchInComments;
    private final boolean mySearchInNonJava;

    MyPerformAction(String innerClassName, String newClassName, boolean passOuterClass, String parameterName, boolean searchInComments,
                           boolean searchInNonJava,
                           String packageName) {
      myInnerClassName = innerClassName;
      myPackageName = packageName;
      myNewClassName = newClassName;
      myPassOuterClass = passOuterClass;
      myParameterName = parameterName;
      mySearchInComments = searchInComments;
      mySearchInNonJava = searchInNonJava;
    }

    @Override
    public void run() {
      final JavaPsiFacade manager = multiFile.get().getFixture().getJavaFacade();
      final PsiClass aClass = manager.findClass(myInnerClassName, GlobalSearchScope.moduleScope(multiFile.get().getFixture().getModule()));
      final MoveInnerProcessor moveInnerProcessor = new MoveInnerProcessor(multiFile.get().getFixture().getProject(), null);
      final PsiElement targetContainer = myPackageName != null ? findDirectory(myPackageName) : MoveInnerImpl.getTargetContainer(aClass, false);
      assertNotNull(targetContainer);
      moveInnerProcessor.setup(aClass, myNewClassName, isPassOuterClass(), myParameterName, mySearchInComments, mySearchInNonJava, targetContainer);
      moveInnerProcessor.run();
    }

    protected boolean isPassOuterClass() {
      return myPassOuterClass;
    }

    private PsiElement findDirectory(final String packageName) {
      final PsiPackage aPackage = multiFile.get().getFixture().getJavaFacade().findPackage(packageName);
      assert aPackage != null;
      final PsiDirectory[] directories = aPackage.getDirectories();
      return directories [0];
    }
  }
}
