// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateLocalFromUsageFix extends CreateVarFromUsageFix {

  public CreateLocalFromUsageFix(PsiReferenceExpression referenceExpression) {
    super(referenceExpression);
  }

  private static final Logger LOG = Logger.getInstance(CreateLocalFromUsageFix.class);

  @Override
  public String getText(String varName) {
    return getMessage(varName);
  }

  public static @NotNull @IntentionName String getMessage(String varName) {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.LOCAL_VARIABLE.object(), varName);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if (element == null) return false;
    if (element.isQualified()) return false;
    PsiStatement anchor = getAnchor(element);
    if (anchor == null) return false;
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

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if (element == null) return;
    String varName = element.getReferenceName();
    if (CreateFromUsageUtils.isValidReference(element, false) || varName == null) return;

    if (psiFile.isPhysical()) {
      IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    }

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(element, false);
    final SmartTypePointer defaultType = SmartTypePointerManager.getInstance(project).createSmartTypePointer(expectedTypes[0]);
    final PsiType preferredType = TypeSelectorManagerImpl.getPreferredType(expectedTypes, expectedTypes[0]);
    PsiType type = preferredType != null ? preferredType : expectedTypes[0];
    if (LambdaUtil.notInferredType(type)) {
      type = PsiType.getJavaLangObject(element.getManager(), psiFile.getResolveScope());
    }

    PsiExpression initializer = null;
    boolean isInline = false;
    PsiExpression[] expressions = CreateFromUsageUtils.collectExpressions(element, PsiMember.class, PsiFile.class);
    PsiStatement anchor = getAnchor(expressions);
    if (anchor == null) {
      expressions = new PsiExpression[]{element};
      anchor = getAnchor(expressions);
      if (anchor == null) return;
    }
    if (anchor instanceof PsiExpressionStatement expressionStatement &&
        expressionStatement.getExpression() instanceof PsiAssignmentExpression assignment &&
        assignment.getLExpression().textMatches(element)) {
      initializer = assignment.getRExpression();
      isInline = true;
    }

    PsiDeclarationStatement decl = factory.createVariableDeclarationStatement(varName, type, initializer);

    TypeExpression expression = new TypeExpression(project, expectedTypes);

    if (isInline) {
      CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(initializer);
      decl = (PsiDeclarationStatement)tracker.replaceAndRestoreComments(anchor, decl);
    }
    else {
      decl = (PsiDeclarationStatement)anchor.getParent().addBefore(decl, anchor);
    }

    PsiVariable var = (PsiVariable)decl.getDeclaredElements()[0];
    boolean isFinal =
      JavaCodeStyleSettings.getInstance(psiFile).GENERATE_FINAL_LOCALS &&
      !CreateFromUsageUtils.isAccessedForWriting(expressions);
    PsiUtil.setModifierProperty(var, PsiModifier.FINAL, isFinal);

    var = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(var);
    if (var == null || !psiFile.isPhysical()) return;
    TemplateBuilderImpl builder = new TemplateBuilderImpl(var);
    final PsiTypeElement typeElement = var.getTypeElement();
    LOG.assertTrue(typeElement != null);
    builder.replaceElement(typeElement,
                           IntroduceVariableUtil.createExpression(expression, typeElement.getText()));
    builder.setEndVariableAfter(var.getNameIdentifier());
    Template template = builder.buildTemplate();

    final Editor newEditor = CodeInsightUtil.positionCursor(project, psiFile, var);
    if (newEditor == null) return;
    TextRange range = var.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(@NotNull Template template, boolean brokenOff) {
        PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
        final int offset = newEditor.getCaretModel().getOffset();
        final PsiLocalVariable localVariable = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, PsiLocalVariable.class, false);
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

  private static @Nullable PsiStatement getAnchor(PsiExpression... expressionOccurrences) {
    PsiElement parent = expressionOccurrences[0];
    int minOffset = expressionOccurrences[0].getTextRange().getStartOffset();
    for (int i = 1; i < expressionOccurrences.length; i++) {
      parent = PsiTreeUtil.findCommonParent(parent, expressionOccurrences[i]);
      LOG.assertTrue(parent != null);
      minOffset = Math.min(minOffset, expressionOccurrences[i].getTextRange().getStartOffset());
    }

    PsiCodeBlock block = null;
    while (parent != null) {
      if (parent instanceof PsiCodeBlock) {
        block = (PsiCodeBlock)parent;
        break;
      }
      else if (parent instanceof PsiSwitchLabeledRuleStatement) {
        parent = ((PsiSwitchLabeledRuleStatement)parent).getEnclosingSwitchBlock();
      }
      else {
        parent = parent.getParent();
      }
    }
    if (block == null) return null;
    PsiStatement[] statements = block.getStatements();
    for (int i = 1; i < statements.length; i++) {
      if (statements[i].getTextRange().getStartOffset() > minOffset) return statements[i - 1];
    }
    return statements[statements.length - 1];
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.local.from.usage.family");
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiReferenceExpression referenceExpression = myReferenceExpression.getElement();
    if(referenceExpression==null) return null;
    return new CreateLocalFromUsageFix(PsiTreeUtil.findSameElementInCopy(referenceExpression, target));
  }
}
