// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CreateLocalFromUsageFix extends PsiUpdateModCommandAction<PsiReferenceExpression> {
  public CreateLocalFromUsageFix(@NotNull PsiReferenceExpression ref) {
    super(ref);
  }

  public static @NotNull @IntentionName String getMessage(String varName) {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.LOCAL_VARIABLE.object(), varName);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression ref) {
    if (ref.isQualified()) return null;
    PsiStatement anchor = getAnchor(ref);
    if (anchor == null) return null;
    if (anchor instanceof PsiExpressionStatement statement) {
      PsiExpression expression = statement.getExpression();
      if (expression instanceof PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method != null && method.isConstructor()) { //this or super call
          return null;
        }
      }
    }
    VariableKind kind = getKind(ref);
    return Presentation.of(getMessage(ref.getReferenceName()))
      .withPriority(kind == VariableKind.LOCAL_VARIABLE ? PriorityAction.Priority.HIGH : PriorityAction.Priority.NORMAL);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReferenceExpression element, @NotNull ModPsiUpdater updater) {
    String varName = element.getReferenceName();
    if (CreateFromUsageUtils.isValidReference(element, false) || varName == null) return;
    Project project = context.project();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(element, false);
    PsiType preferredType = TypeSelectorManagerImpl.getPreferredType(expectedTypes, expectedTypes[0]);
    PsiType type = preferredType != null ? preferredType : expectedTypes[0];
    type = PsiTypesUtil.removeExternalAnnotations(type);
    PsiFile psiFile = context.file();
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
    var = (PsiVariable)JavaCodeStyleManager.getInstance(project).shortenClassReferences(var);
    boolean isFinal =
      JavaCodeStyleSettings.getInstance(psiFile).GENERATE_FINAL_LOCALS &&
      !CreateFromUsageUtils.isAccessedForWriting(expressions);
    PsiUtil.setModifierProperty(var, PsiModifier.FINAL, isFinal);

    var = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(var);
    if (var == null) return;
    ModTemplateBuilder builder = updater.templateBuilder();
    PsiTypeElement typeElement = Objects.requireNonNull(var.getTypeElement());
    builder.field(typeElement, IntroduceVariableUtil.createExpression(expression, typeElement.getText()));
    PsiIdentifier identifier = var.getNameIdentifier();
    if (identifier != null) {
      builder.finishAt(identifier.getTextRange().getEndOffset());
    }
  }

  private static @Nullable PsiStatement getAnchor(PsiExpression... expressionOccurrences) {
    PsiElement parent = expressionOccurrences[0];
    int minOffset = expressionOccurrences[0].getTextRange().getStartOffset();
    for (int i = 1; i < expressionOccurrences.length; i++) {
      parent = PsiTreeUtil.findCommonParent(parent, expressionOccurrences[i]);
      Objects.requireNonNull(parent);
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

  public static @Nullable VariableKind getKind(@NotNull PsiReferenceExpression refExpr) {
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(refExpr.getProject());
    String reference = refExpr.getText();

    if (StringUtil.isUpperCase(reference)) {
      return VariableKind.STATIC_FINAL_FIELD;
    }

    for (VariableKind kind : VariableKind.values()) {
      String prefix = styleManager.getPrefixByVariableKind(kind);
      String suffix = styleManager.getSuffixByVariableKind(kind);

      if (prefix.isEmpty() && suffix.isEmpty()) {
        continue;
      }

      if (reference.startsWith(prefix) && reference.endsWith(suffix)) {
        return kind;
      }
    }

    if (StringUtil.isCapitalized(reference)) {
      return null;
    }

    return VariableKind.LOCAL_VARIABLE;
  }
}
