/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.generation.surroundWith.JavaExpressionSurrounder;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.JavaBundle;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.Conditions.and;

public class SwitchStatementPostfixTemplate extends SurroundPostfixTemplateBase {

  private static final Condition<PsiElement> SWITCH_TYPE = expression -> {
    if (!(expression instanceof PsiExpression)) return false;

    PsiType type = ((PsiExpression)expression).getType();

    if (type == null) return false;
    if (PsiType.INT.isAssignableFrom(type)) return true;

    if (type instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null && psiClass.isEnum()) return true;
    }

    if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      PsiFile containingFile = expression.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        LanguageLevel level = ((PsiJavaFile)containingFile).getLanguageLevel();
        if (level.isAtLeast(LanguageLevel.JDK_1_7)) return true;
      }
    }

    return false;
  };

  public SwitchStatementPostfixTemplate() {
    super("switch", "switch(expr)", JavaPostfixTemplatesUtils.JAVA_PSI_INFO, selectorTopmost(SWITCH_TYPE));
  }

  @NotNull
  @Override
  protected Surrounder getSurrounder() {
    return new JavaExpressionSurrounder() {
      @Override
      public boolean isApplicable(PsiExpression expr) {
        return expr.isPhysical() && SWITCH_TYPE.value(expr);
      }

      @Override
      public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

        PsiElement parent = expr.getParent();
        if (parent instanceof PsiExpressionStatement) {
          PsiSwitchStatement switchStatement = (PsiSwitchStatement)factory.createStatementFromText("switch(1){case 1:}", null);
          return postprocessSwitch(editor, expr, codeStyleManager, parent, switchStatement);
        }
        else if (HighlightingFeature.ENHANCED_SWITCH.isAvailable(expr)) {
          PsiSwitchExpression switchExpression = (PsiSwitchExpression)factory.createExpressionFromText("switch(1){case 1->1;}", null);
          return postprocessSwitch(editor, expr, codeStyleManager, expr, switchExpression);
        }

        return TextRange.from(editor.getCaretModel().getOffset(), 0);
      }

      @NotNull
      private TextRange postprocessSwitch(Editor editor,
                                          PsiExpression expr,
                                          CodeStyleManager codeStyleManager,
                                          PsiElement toReplace,
                                          PsiSwitchBlock switchBlock) {

        switchBlock = (PsiSwitchBlock)codeStyleManager.reformat(switchBlock);
        PsiExpression selectorExpression = switchBlock.getExpression();
        if (selectorExpression != null) {
          selectorExpression.replace(expr);
        }

        switchBlock = (PsiSwitchBlock)toReplace.replace(switchBlock);

        PsiCodeBlock body = switchBlock.getBody();
        if (body != null) {
          body = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(body);
          TextRange range = body.getStatements()[0].getTextRange();
          editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
          return TextRange.from(range.getStartOffset(), 0);
        }
        return TextRange.from(editor.getCaretModel().getOffset(), 0);
      }

      @Override
      public String getTemplateDescription() {
        return JavaBundle.message("switch.stmt.template.description");
      }
    };
  }

  public static PostfixTemplateExpressionSelector selectorTopmost(Condition<? super PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        boolean isEnhancedSwitchAvailable = HighlightingFeature.ENHANCED_SWITCH.isAvailable(context);
        List<PsiElement> result = new ArrayList<>();

        for (PsiElement element = PsiTreeUtil.getNonStrictParentOfType(context, PsiExpression.class, PsiStatement.class);
             element instanceof PsiExpression; element = element.getParent()) {
          PsiElement parent = element.getParent();
          if (parent instanceof PsiExpressionStatement) {
            result.add(element);
          }
          else if (isEnhancedSwitchAvailable && (isVariableInitializer(element, parent) ||
                                                 isRightSideOfAssignment(element, parent) ||
                                                 isReturnValue(element, parent) ||
                                                 isArgumentList(parent))) {
            result.add(element);
          }
        }
        return result;
      }

      @Override
      protected Condition<PsiElement> getFilters(int offset) {
        return and(super.getFilters(offset), getPsiErrorFilter());
      }

      @NotNull
      @Override
      public Function<PsiElement, String> getRenderer() {
        return JavaPostfixTemplatesUtils.getRenderer();
      }

      private boolean isVariableInitializer(PsiElement element, PsiElement parent) {
        return parent instanceof PsiVariable && ((PsiVariable)parent).getInitializer() == element;
      }

      private boolean isRightSideOfAssignment(PsiElement element, PsiElement parent) {
        return parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getRExpression() == element;
      }

      private boolean isReturnValue(PsiElement element, PsiElement parent) {
        return parent instanceof PsiReturnStatement && ((PsiReturnStatement)parent).getReturnValue() == element;
      }

      private boolean isArgumentList(PsiElement parent) {
        return parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCall;
      }
    };
  }
}
