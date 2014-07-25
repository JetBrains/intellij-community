package com.intellij.psi;

import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.psi.search.GlobalSearchScope;

@PlatformTestCase.WrapInCommand
public class CodeFragmentsTest extends PsiTestCase{
  public void testAddImport() throws Exception {
    PsiCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment("AAA.foo()", null, null, false);
    PsiClass arrayListClass = myJavaFacade.findClass("java.util.ArrayList", GlobalSearchScope.allScope(getProject()));
    PsiReference ref = fragment.findReferenceAt(0);
    ref.bindToElement(arrayListClass);
    assertEquals("ArrayList.foo()", fragment.getText());
  }

  public void testDontLoseDocument() {
    PsiExpressionCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment("a", null, null, true);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(fragment);
    document.insertString(1, "b");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    assertEquals("ab", fragment.getText());
    assertEquals("ab", fragment.getExpression().getText());

    //noinspection UnusedAssignment
    document = null;

    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertEquals("ab", PsiDocumentManager.getInstance(myProject).getDocument(fragment).getText());
  }
}
