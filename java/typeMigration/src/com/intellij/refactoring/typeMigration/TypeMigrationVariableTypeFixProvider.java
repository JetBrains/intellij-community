// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TypeMigrationVariableTypeFixProvider implements ChangeVariableTypeQuickFixProvider {
  private static final Logger LOG = Logger.getInstance(TypeMigrationVariableTypeFixProvider.class);

  @Override
  public IntentionAction @NotNull [] getFixes(@NotNull PsiVariable variable, @NotNull PsiType toReturn) {
    if (!typeMigrationMightBeUseful(variable, toReturn)) return IntentionAction.EMPTY_ARRAY;
    return new IntentionAction[]{createTypeMigrationFix(variable, toReturn)};
  }

  private static @NotNull VariableTypeFix createTypeMigrationFix(final @NotNull PsiVariable variable, final @NotNull PsiType toReturn) {
    return new VariableTypeFix(variable, toReturn) {
      @Override
      public @NotNull String getText() {
        return TypeMigrationBundle.message("migrate.fix.text", myName, StringUtil.escapeXmlEntities(getReturnType().getPresentableText()));
      }

      @Override
      public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
        // Avoid displaying the same diff as for VariableTypeFix, to avoid confusion.
        // TODO: add HTML description
        return IntentionPreviewInfo.EMPTY;
      }

      @Override
      public void invoke(@NotNull Project project,
                         @NotNull PsiFile psiFile,
                         @Nullable Editor editor,
                         @NotNull PsiElement startElement,
                         @NotNull PsiElement endElement) {
        runTypeMigrationOnVariable((PsiVariable)startElement, getReturnType(), editor, false, true);
      }
    };
  }

  private static boolean typeMigrationMightBeUseful(@NotNull PsiVariable variable, @NotNull PsiType targetType) {
    if (!PsiUtil.isJvmLocalVariable(variable)) return true;
    List<PsiReferenceExpression> refs = VariableAccessUtils.getVariableReferences(variable);
    if (refs.isEmpty()) return false;
    Project project = variable.getProject();
    TypeMigrationRules rules = new TypeMigrationRules(project);
    rules.setBoundScope(variable.getUseScope());
    TypeMigrationLabeler labeler = new TypeMigrationLabeler(rules, targetType, project);
    for (PsiReferenceExpression ref : refs) {
      labeler.getTypeEvaluator().setType(new TypeMigrationUsageInfo(ref), targetType);
      if (rules.findConversion(variable.getType(), targetType, null, ref, labeler) != null) {
        return true;
      }
    }
    return false;
  }

  public static void runTypeMigrationOnVariable(@NotNull PsiVariable variable,
                                                @NotNull PsiType targetType,
                                                @Nullable Editor editor,
                                                boolean optimizeImports,
                                                boolean allowDependentRoots) {
    Project project = variable.getProject();
    if (!FileModificationService.getInstance().prepareFileForWrite(variable.getContainingFile())) return;
    try {
      WriteAction.run(() -> variable.normalizeDeclaration());
      final TypeMigrationRules rules = new TypeMigrationRules(project);
      rules.setBoundScope(variable.getUseScope());
      TypeMigrationProcessor.runHighlightingTypeMigration(project, editor, rules, variable, targetType, optimizeImports, allowDependentRoots);
      WriteAction.run(() -> JavaCodeStyleManager.getInstance(project).shortenClassReferences(variable));
      UndoUtil.markPsiFileForUndo(variable.getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
