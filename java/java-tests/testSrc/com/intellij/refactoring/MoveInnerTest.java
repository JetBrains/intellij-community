package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.move.moveInner.MoveInnerDialog;
import com.intellij.refactoring.move.moveInner.MoveInnerImpl;
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 *  @author dsl
 */
public class MoveInnerTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
  
  protected String getTestRoot() {
    return "/refactoring/moveInner/";
  }

  public void testScr13730() throws Exception {
    doTest(createAction("pack1.TopLevel.StaticInner", "StaticInner", false, null, false, false, null));
  }

  public void testScr15142() throws Exception {
    doTest(createAction("xxx.Outer.Inner", "Inner", false, null, false, false, null));
  }

  public void testNonJavaFiles() throws Exception {
    doTest(createAction("pack1.Outer.Inner", "Inner", false, null, true, true, null));
  }

  public void testXmlReferences() throws Exception {
    doTest(createAction("pack1.Outer.Inner", "Inner", false, null, true, true, null));
  }

  public void testScr22592() throws Exception {
    doTest(createAction("xxx.Outer.Inner", "Inner", true, "outer", false, false, null));
  }

  public void testScr30106() throws Exception {
    doTest(createAction("p.A.B", "B", true, "outer", false, false, null));
  }

  public void testConstructorVisibility() throws Exception {  // IDEADEV-19561
    doTest(createAction("p.A.B", "B", false, null, false, false, null));
  }

  public void testFieldAccessInSuper() throws Exception {
    doTest(createAction("p.A.B", "B", true, "a", false, false, null));
  }

  public void testToOtherPackage() throws Exception {
    doTest(createAction("package1.OuterClass.InnerClass", "InnerClass", false, null, false, false, "package2"));
  }

  public void testImportStaticOfEnum() throws Exception { // IDEADEV-28619
    doTest(createAction("p.A.E", "E", false, null, false, false, null));
  }

  public void testQualifyThisHierarchy() throws Exception {
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

    public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
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
