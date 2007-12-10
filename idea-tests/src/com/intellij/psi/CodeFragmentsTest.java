package com.intellij.psi;

import com.intellij.testFramework.PsiTestCase;

public class CodeFragmentsTest extends PsiTestCase{
  public CodeFragmentsTest() {
    myRunCommandForTest = true;
  }

  public void testAddImport() throws Exception {
    PsiCodeFragment fragment = myJavaFacade.getElementFactory().createExpressionCodeFragment("AAA.foo()", null, null, false);
    PsiClass arrayListClass = myJavaFacade.findClass("java.util.ArrayList");
    PsiReference ref = fragment.findReferenceAt(0);
    ref.bindToElement(arrayListClass);
    assertEquals("ArrayList.foo()", fragment.getText());
  }
}
