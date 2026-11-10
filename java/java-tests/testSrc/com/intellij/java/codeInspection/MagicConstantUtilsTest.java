// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.magicConstant.MagicConstantUtils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public final class MagicConstantUtilsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testTextualRepresentationFlags() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.MagicConstant;

      class Test {
        @MagicConstant(flagsFromClass = Test.class)
        int myFlag = 0;
  
        static final int OPEN = 1;
        static final int CLOSED = 2;
        static final int PENDING = 4;
        static final int ERROR = 8;
      }
    """);
    ReadAction.runBlocking(() -> {
      PsiField field = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getFields()[0];
      assertNull(MagicConstantUtils.getPresentableText(0, field));
      assertEquals("OPEN", MagicConstantUtils.getPresentableText(1, field));
      assertEquals("CLOSED | PENDING", MagicConstantUtils.getPresentableText(6, field));
    });
  }

  @Test
  public void testTextualRepresentationValues() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.MagicConstant;

      class Test {
        @StateValue
        long myFlag = 0;
      }

      @MagicConstant(valuesFromClass = State.class)
      @interface StateValue {}
  
      interface State {
        long GOOD = 1;
        long DECENT = 2;
        long BAD = 4;
      }
    """);
    ReadAction.runBlocking(() -> {
      PsiField field = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getFields()[0];
      assertEquals("DECENT", MagicConstantUtils.getPresentableText(2L, field));
      assertNull(MagicConstantUtils.getPresentableText(3L, field));
      assertEquals("BAD", MagicConstantUtils.getPresentableText(4L, field));
    });
  }

  @Test
  public void testTextualRepresentationValuesWithIntermediateVar() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      import org.intellij.lang.annotations.MagicConstant;

      class Test {
        @StateValue
        native long getFlags();
  
        void use() {
          long <caret>flags = getFlags();
        }
      }

      @MagicConstant(valuesFromClass = State.class)
      @interface StateValue {}
  
      interface State {
        long GOOD = 1;
        long DECENT = 2;
        long BAD = 4;
      }
    """);
    ReadAction.runBlocking(() -> {
      PsiLocalVariable variable =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);
      assertEquals("DECENT", MagicConstantUtils.getPresentableText(2L, variable));
      assertNull(MagicConstantUtils.getPresentableText(3L, variable));
      assertEquals("BAD", MagicConstantUtils.getPresentableText(4L, variable));
    });
  }
}
