/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class CreateLocalFromUsageFix extends CreateVarFromUsageFix {

  public CreateLocalFromUsageFix(PsiReferenceExpression referenceExpression) {
    super(referenceExpression);
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalFromUsageFix");

  @Override
  public String getText(String varName) {
    return QuickFixBundle.message("create.local.from.usage.text", varName);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    if(myReferenceExpression.isQualified()) return false;
    if (PsiTreeUtil.getParentOfType(myReferenceExpression, PsiCodeBlock.class) != null) {
      PsiStatement anchor = getAnchor(myReferenceExpression);
      if (anchor instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement)anchor).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
          if (method != null && method.isConstructor()) { //this or super call
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, false)) {
      return;
    }

    final Project project = myReferenceExpression.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    final PsiFile targetFile = targetClass.getContainingFile();

    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
    final SmartTypePointer defaultType = SmartTypePointerManager.getInstance(project).createSmartTypePointer(expectedTypes[0]);
    final PsiType preferredType = TypeSelectorManagerImpl.getPreferredType(expectedTypes, expectedTypes[0]);
    PsiType type = preferredType != null ? preferredType : expectedTypes[0];
    if (LambdaUtil.notInferredType(type)) {
      type = PsiType.getJavaLangObject(myReferenceExpression.getManager(), targetClass.getResolveScope());
    }

    String varName = myReferenceExpression.getReferenceName();
    PsiExpression initializer = null;
    boolean isInline = false;
    PsiExpression[] expressions = CreateFromUsageUtils.collectExpressions(myReferenceExpression, PsiMember.class, PsiFile.class);
    PsiStatement anchor = getAnchor(expressions);
    if (anchor instanceof PsiExpressionStatement &&
        ((PsiExpressionStatement)anchor).getExpression() instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)((PsiExpressionStatement)anchor).getExpression();
      if (assignment.getLExpression().textMatches(myReferenceExpression)) {
        initializer = assignment.getRExpression();
        isInline = true;
      }
    }

    PsiDeclarationStatement decl = factory.createVariableDeclarationStatement(varName, type, initializer);

    TypeExpression expression = new TypeExpression(project, expectedTypes);

    if (isInline) {
      final PsiExpression expr = ((PsiExpressionStatement)anchor).getExpression();
      final PsiElement semicolon = expr.getNextSibling();
      if (semicolon != null) {
        final PsiElement nextSibling = semicolon.getNextSibling();
        if (nextSibling != null) {
          decl.addRange(nextSibling, anchor.getLastChild());
        }
      }
      decl = (PsiDeclarationStatement)anchor.replace(decl);
    }
    else {
      decl = (PsiDeclarationStatement)anchor.getParent().addBefore(decl, anchor);
    }

    PsiVariable var = (PsiVariable)decl.getDeclaredElements()[0];
    boolean isFinal =
      CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS &&
      !CreateFromUsageUtils.isAccessedForWriting(expressions);
    PsiUtil.setModifierProperty(var, PsiModifier.FINAL, isFinal);

    var = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(var);
    if (var == null) return;
    TemplateBuilderImpl builder = new TemplateBuilderImpl(var);
    final PsiTypeElement typeElement = var.getTypeElement();
    LOG.assertTrue(typeElement != null);
    builder.replaceElement(typeElement,
                           AbstractJavaInplaceIntroducer.createExpression(expression, typeElement.getText()));
    builder.setEndVariableAfter(var.getNameIdentifier());
    Template template = builder.buildTemplate();

    final Editor newEditor = positionCursor(project, targetFile, var);
    if (newEditor == null) return;
    TextRange range = var.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
        final int offset = newEditor.getCaretModel().getOffset();
        final PsiLocalVariable localVariable = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset, PsiLocalVariable.class, false);
        if (localVariable != null) {
          TypeSelectorManagerImpl.typeSelected(localVariable.getType(), defaultType.getType());

          ApplicationManager.getApplication().runWriteAction(() -> {
            CodeStyleManager.getInstance(project).reformat(localVariable);
          });
        }
      }
    });
  }

  @Override
  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  private static PsiStatement getAnchor(PsiExpression... expressionOccurences) {
    PsiElement parent = expressionOccurences[0];
    int minOffset = expressionOccurences[0].getTextRange().getStartOffset();
    for (int i = 1; i < expressionOccurences.length; i++) {
      parent = PsiTreeUtil.findCommonParent(parent, expressionOccurences[i]);
      LOG.assertTrue(parent != null);
      minOffset = Math.min(minOffset, expressionOccurences[i].getTextRange().getStartOffset());
    }

    final PsiCodeBlock block = PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class, false);
    LOG.assertTrue(block != null && block.getStatements().length > 0, "block: " + block +"; parent: " + parent);
    PsiStatement[] statements = block.getStatements();
    for (int i = 1; i < statements.length; i++) {
      if (statements[i].getTextRange().getStartOffset() > minOffset) return statements[i-1];
    }
    return statements[statements.length - 1];
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.local.from.usage.family");
  }

}
