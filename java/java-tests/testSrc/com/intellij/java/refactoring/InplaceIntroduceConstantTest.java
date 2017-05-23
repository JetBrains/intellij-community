/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 * @since 16.03.2011
 */
public class InplaceIntroduceConstantTest extends AbstractJavaInplaceIntroduceTest {
  private static final String BASE_PATH = "/refactoring/inplaceIntroduceConstant/";

  public void testReplaceAll() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  @Nullable
  @Override
  protected PsiExpression getExpressionFromEditor() {
    final PsiExpression expression = super.getExpressionFromEditor();
    if (expression != null) {
      return expression;
    }
    final PsiExpression expr = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    return expr instanceof PsiLiteralExpression ? expr : null;
  }

  public void testReplaceAllInsideParenthesized() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testReplaceAllWithClassRefType() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
        type("ONE");
      }
    });
  }

  public void testReplaceAllWithBrokenIdentifier() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type("A B");
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testReplaceAllFromSecondOccurrence() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type("O");
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testReplaceAllFromLiteral() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type("NINE");
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testConflictingConstantName() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) { }
    });
  }

  public void testNoConflictingConstantName() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) { }
    });
  }

  public void testEnsureVisibilityForAnno() {
    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = PsiModifier.PRIVATE;
    try {
      doTest(new Pass<AbstractInplaceIntroducer>() {
        @Override
        public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) { }
      });
    }
    finally {
      JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = null;
    }
  }

  public void testCorrectFinalPosition() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type("SEC");
      }
    });
  }

  public void testCorrectConstantPosition() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type("R");
      }
    });
  }

  public void testEscapePosition() {
    doTestEscape();
  }

  public void testEscapePositionOnLocal() {
    doTestEscape();
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new MyIntroduceConstantHandler();
  }

  public static class MyIntroduceConstantHandler extends IntroduceConstantHandler implements MyIntroduceHandler {
    @Override
    public boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
      return super.invokeImpl(project, selectedExpr, editor);
    }

    @Override
    public boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
      return super.invokeImpl(project, localVariable, editor);
    }
  }
}