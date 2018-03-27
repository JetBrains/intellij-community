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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.move.moveInner.MoveInnerDialog;
import com.intellij.refactoring.move.moveInner.MoveInnerImpl;
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  @author dsl
 */
public class MoveInnerTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
  
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/moveInner/";
  }

  public void testScr13730() {
    doTest(createAction("pack1.TopLevel.StaticInner", "StaticInner", false, null, false, false, null));
  }

  public void testScr15142() {
    doTest(createAction("xxx.Outer.Inner", "Inner", false, null, false, false, null));
  }

  public void testNonJavaFiles() {
    doTest(createAction("pack1.Outer.Inner", "Inner", false, null, true, true, null));
  }

  public void testXmlReferences() {
    doTest(createAction("pack1.Outer.Inner", "Inner", false, null, true, true, null));
  }

  public void testScr22592() {
    doTest(createAction("xxx.Outer.Inner", "Inner", true, "outer", false, false, null));
  }

  public void testInnerClassInheritance() {
    try {
      doTest(createAction("p.A.B", "B", false, null, false, false, null));
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("class <b><code>p.A.C</code></b> will become inaccessible from class <b><code>p.A.B</code></b>", e.getMessage());
    }
  }

  public void testInnerClassSelfRef() {
    doTest(createAction("p.A.B", "B", false, null, false, false, null));
  }
  
  public void testInnerClassQualifiedNewExpression() {
    doTest(createAction("p.A.B", "B", false, null, false, false, null));
  }

  public void testScr30106() {
    doTest(createAction("p.A.B", "B", true, "outer", false, false, null));
  }

  public void testConstructorVisibility() {  // IDEADEV-19561
    doTest(createAction("p.A.B", "B", false, null, false, false, null));
  }

  public void testConstructorProtectedVisibility() {  // IDEADEV-19561
    doTest(createAction("p.b.A.B", "B", false, null, false, false, "p"));
  }

  public void testConstructorUtilClassVisibility() {
    doTest(createAction("p.A.B", "B", false, null, false, false, null));
  }

  public void testFieldAccessInSuper() {
    doTest(createAction("p.A.B", "B", true, "a", false, false, null));
  }

  public void testToOtherPackage() {
    doTest(createAction("package1.OuterClass.InnerClass", "InnerClass", false, null, false, false, "package2"));
  }

  public void testImportStaticOfEnum() { // IDEADEV-28619
    doTest(createAction("p.A.E", "E", false, null, false, false, null));
  }

  public void testInnerInnerClassUsedInTypeParams() {
    doTest(createAction("p.Main.A", "A", false, null, false, false, null));
  }

  public void testEnumConstructorVisibility() { // IDEADEV-28619
    doTest(createAction("p.A.E", "E", false, null, false, false, "p2"));
  }

  public void testQualifyThisHierarchy() {
    final String innerClassName = "pack1.DImpl.MyRunnable";
    doTest(new MyPerformAction(innerClassName, "MyRunnable", false, "d",
                               false, false, null) {
      @Override
      protected boolean isPassOuterClass() {
        final PsiClass outerClass = getJavaFacade().findClass("pack1.DImpl", GlobalSearchScope.moduleScope(myModule));
        assertNotNull(outerClass);
        final PsiClass innerClass = getJavaFacade().findClass(innerClassName, GlobalSearchScope.moduleScope(myModule));
        assertNotNull(innerClass);
        return MoveInnerDialog.isThisNeeded(innerClass, outerClass);
      }
    });
  }

  private PerformAction createAction(@NonNls final String innerClassName,
                                     @NonNls final String newClassName,
                                     final boolean passOuterClass,
                                     @NonNls final String parameterName,
                                     final boolean searchInComments,
                                     final boolean searchInNonJava,
                                     @NonNls @Nullable final String packageName) {
    return new MyPerformAction(innerClassName, newClassName, passOuterClass, parameterName, searchInComments, searchInNonJava, packageName);
  }

  private class MyPerformAction implements PerformAction {
    private final String myInnerClassName;
    private final String myPackageName;
    private final String myNewClassName;
    private final boolean myPassOuterClass;
    private final String myParameterName;
    private final boolean mySearchInComments;
    private final boolean mySearchInNonJava;

    public MyPerformAction(String innerClassName, String newClassName, boolean passOuterClass, String parameterName, boolean searchInComments,
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
    public void performAction(VirtualFile rootDir, VirtualFile rootAfter) {
      final JavaPsiFacade manager = getJavaFacade();
      final PsiClass aClass = manager.findClass(myInnerClassName, GlobalSearchScope.moduleScope(myModule));
      final MoveInnerProcessor moveInnerProcessor = new MoveInnerProcessor(myProject, null);
      final PsiElement targetContainer = myPackageName != null ? findDirectory(myPackageName) : MoveInnerImpl.getTargetContainer(aClass, false);
      assertNotNull(targetContainer);
      moveInnerProcessor.setup(aClass, myNewClassName, isPassOuterClass(), myParameterName, mySearchInComments, mySearchInNonJava, targetContainer);
      moveInnerProcessor.run();
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      FileDocumentManager.getInstance().saveAllDocuments();
    }

    protected boolean isPassOuterClass() {
      return myPassOuterClass;
    }

    private PsiElement findDirectory(final String packageName) {
      final PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(packageName);
      assert aPackage != null;
      final PsiDirectory[] directories = aPackage.getDirectories();
      return directories [0];
    }
  }
}
