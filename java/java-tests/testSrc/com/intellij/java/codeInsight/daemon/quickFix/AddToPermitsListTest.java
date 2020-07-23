// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.impl.AddToPermitsListFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class AddToPermitsListTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }

  public void testNoPermitsList() {
    PsiClass aClass = myFixture.addClass("sealed class A {}");
    PsiClass bClass = myFixture.addClass("final class B extends A {}");
    invokeFix(bClass);
    assertEquals("sealed class A permits B {}", aClass.getText());
  }

  public void testUnorderedPermitsList() {
    PsiClass aClass = myFixture.addClass("sealed class A permits C {}");
    PsiClass bClass = myFixture.addClass("final class B extends A {}");
    myFixture.addClass("non-sealed class C extends A {}");
    invokeFix(bClass);
    assertEquals("sealed class A permits B, C {}", aClass.getText());
  }

  public void testMultipleInheritors() {
    PsiClass aClass = myFixture.addClass("sealed class A {}");
    PsiClass bClass = myFixture.addClass("final class B extends A {}");
    PsiClass cClass = myFixture.addClass("non-sealed class C extends A {}");
    invokeFix(cClass);
    invokeFix(bClass);
    assertEquals("sealed class A permits B, C {}", aClass.getText());
  }

  public void testMultipleParents() {
    PsiClass aClass = myFixture.addClass("sealed interface A {}");
    myFixture.addClass("interface B {}");
    PsiClass cClass = myFixture.addClass("final class C extends A, B {}");
    invokeFix(cClass);
    assertEquals("sealed interface A permits C {}", aClass.getText());
  }

  private void invokeFix(PsiClass psiClass) {
    PsiJavaCodeReferenceElement parentReference = psiClass.getExtendsList().getReferenceElements()[0];
    AddToPermitsListFix fix = new AddToPermitsListFix(parentReference, psiClass.getNameIdentifier());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      fix.invoke(getProject(), psiClass.getContainingFile(), getEditor(), parentReference, parentReference);
    });
  }
}
