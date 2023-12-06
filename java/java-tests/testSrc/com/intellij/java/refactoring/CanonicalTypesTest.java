// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class CanonicalTypesTest extends LightJavaCodeInsightFixtureTestCase {
  public void testAnnotationImport() {
    myFixture.configureByText("X.java", """
      import java.lang.annotation.*;
      
                                      
      class X {
        @Target(ElementType.TYPE_USE)
        @interface Anno {}

        void test() {
          @Anno <caret>String s;
        }
      }
      """);
    PsiElement element = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
    assertNotNull(typeElement);
    CanonicalTypes.Type wrapper = CanonicalTypes.createTypeWrapper(typeElement.getType());
    String text = wrapper.getTypeText();
    assertEquals("@Anno String", text);
    PsiTypeCodeFragment fragment = JavaCodeFragmentFactory.getInstance(getProject()).createTypeCodeFragment(text, typeElement, false);
    wrapper.addImportsTo(fragment);
    assertEquals("java.lang.String,X.Anno", fragment.importsToString());
  }
  
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }
}
