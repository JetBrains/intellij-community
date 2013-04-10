package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testEmptyConstructor() throws Exception { runTest("01", null); }

  public void testSubclass() throws Exception { runTest("02", null); }

  public void testDefaultConstructor() throws Exception { runTest("03", null); }
  public void testDefaultConstructorWithTypeParams() throws Exception { runTest("TypeParams", null); }

  public void testInnerClass() throws Exception { runTest("04", "OuterClass"); }

  public void testSubclassVisibility() throws Exception { runTest("05", null); }

  public void testImplicitConstructorUsages() throws Exception { runTest("06", null); }

  public void testImplicitConstructorCreation() throws Exception { runTest("07", null); }

  public void testConstructorTypeParameters() throws Exception { runTest("08", null); }

  private void runTest(final String testIndex, @NonNls String targetClassName) throws Exception {
    configureByFile("/refactoring/replaceConstructorWithFactory/before" + testIndex + ".java");
    perform(targetClassName);
    checkResultByFile("/refactoring/replaceConstructorWithFactory/after" + testIndex + ".java");
  }


  private void perform(String targetClassName) {
    int offset = myEditor.getCaretModel().getOffset();
    PsiElement element = myFile.findElementAt(offset);
    PsiMethod constructor = null;
    PsiClass aClass = null;
    while (true) {
      if (element == null || element instanceof PsiFile) {
        assertTrue(false);
        return;
      }

      if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
        constructor = (PsiMethod)element;
        break;
      }

      if (element instanceof PsiClass && ((PsiClass)element).getConstructors().length == 0) {
        aClass = (PsiClass)element;
        break;
      }
      element = element.getParent();
    }
    PsiClass targetClass = null;
    if (targetClassName != null) {
      targetClass = JavaPsiFacade.getInstance(getProject()).findClass(targetClassName, GlobalSearchScope.allScope(getProject()));
      assertTrue(targetClass != null);
    }

    final ReplaceConstructorWithFactoryProcessor replaceConstructorWithFactoryProcessor;
    if (constructor != null) {
      if (targetClass == null) {
        targetClass = constructor.getContainingClass();
      }
      replaceConstructorWithFactoryProcessor = new ReplaceConstructorWithFactoryProcessor(
        getProject(), constructor, constructor.getContainingClass(), targetClass, "new" + constructor.getName());
    }
    else {
      if (targetClass == null) {
        targetClass = aClass;
      }
      replaceConstructorWithFactoryProcessor = new ReplaceConstructorWithFactoryProcessor(
        getProject(), null, aClass, targetClass, "new" + aClass.getName());
    }
    replaceConstructorWithFactoryProcessor.run();
  }
}
