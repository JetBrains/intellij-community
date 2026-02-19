// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LightResolveTestCase;
import org.jetbrains.annotations.NotNull;

public class ResolveMethod21Test extends LightResolveTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testResolveInsideLambdaSwitchSelector() {
    PsiFile file = myFixture.configureByFile("method/" + getTestName(false) + ".java");
    PsiReference reference = file.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    PsiElement target = reference.resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertEquals("collect", ((PsiMethod)target).getName());

    int index = myFixture.getEditor().getDocument().getText().indexOf("diag.getCode");
    PsiReference getCodeReference = file.findReferenceAt(index + 6);
    PsiElement getCodeTarget = getCodeReference.resolve();
    assertInstanceOf(getCodeTarget, PsiMethod.class);
    assertEquals("getCode", ((PsiMethod)getCodeTarget).getName());
  }
}
