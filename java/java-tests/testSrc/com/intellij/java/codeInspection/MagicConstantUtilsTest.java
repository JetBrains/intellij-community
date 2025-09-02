// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.magicConstant.MagicConstantUtils;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public final class MagicConstantUtilsTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  public void testTextualRepresentationFlags() {
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
    PsiField field = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getFields()[0];
    assertNull(MagicConstantUtils.getPresentableText(0, field));
    assertEquals("OPEN", MagicConstantUtils.getPresentableText(1, field));
    assertEquals("CLOSED | PENDING", MagicConstantUtils.getPresentableText(6, field));
  }

  public void testTextualRepresentationValues() {
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
    PsiField field = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getFields()[0];
    assertEquals("DECENT", MagicConstantUtils.getPresentableText(2L, field));
    assertNull(MagicConstantUtils.getPresentableText(3L, field));
    assertEquals("BAD", MagicConstantUtils.getPresentableText(4L, field));
  }

  public void testTextualRepresentationValuesWithIntermediateVar() {
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
    PsiLocalVariable variable =
      PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);
    assertEquals("DECENT", MagicConstantUtils.getPresentableText(2L, variable));
    assertNull(MagicConstantUtils.getPresentableText(3L, variable));
    assertEquals("BAD", MagicConstantUtils.getPresentableText(4L, variable));
  }
}
