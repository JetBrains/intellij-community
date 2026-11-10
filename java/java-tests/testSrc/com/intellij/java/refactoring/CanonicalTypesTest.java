// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeCodeFragment;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class CanonicalTypesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testAnnotationImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    ReadAction.runBlocking(() -> {
      PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
      assertNotNull(typeElement);
      CanonicalTypes.Type wrapper = CanonicalTypes.createTypeWrapper(typeElement.getType());
      String text = wrapper.getTypeText();
      assertEquals("@Anno String", text);
      PsiTypeCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject()).createTypeCodeFragment(text, typeElement, false);
      wrapper.addImportsTo(fragment);
      assertEquals("java.lang.String,X.Anno", fragment.importsToString());
    });
  }
}
