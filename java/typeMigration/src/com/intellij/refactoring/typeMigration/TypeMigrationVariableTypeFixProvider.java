// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeMigrationVariableTypeFixProvider implements ChangeVariableTypeQuickFixProvider {
  private static final Logger LOG1 = Logger.getInstance(TypeMigrationVariableTypeFixProvider.class);

  @Override
  public IntentionAction @NotNull [] getFixes(@NotNull PsiVariable variable, @NotNull PsiType toReturn) {
    return new IntentionAction[]{createTypeMigrationFix(variable, toReturn)};
  }

  @NotNull
  public static VariableTypeFix createTypeMigrationFix(@NotNull final PsiVariable variable,
                                                       @NotNull final PsiType toReturn) {
    return createTypeMigrationFix(variable, toReturn, false);
  }

  @NotNull
  public static VariableTypeFix createTypeMigrationFix(@NotNull final PsiVariable variable,
                                                       @NotNull final PsiType toReturn,
                                                       final boolean optimizeImports) {
    return new VariableTypeFix(variable, toReturn) {
      @NotNull
      @Override
      public String getText() {
        return TypeMigrationBundle.message("migrate.fix.text", myName, StringUtil.escapeXmlEntities(getReturnType().getPresentableText()));
      }

      @Override
      public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        // Avoid displaying the same diff as for VariableTypeFix, to avoid confusion.
        // TODO: add HTML description
        return IntentionPreviewInfo.EMPTY;
      }

      @Override
      public void invoke(@NotNull Project project,
                         @NotNull PsiFile file,
                         @Nullable Editor editor,
                         @NotNull PsiElement startElement,
                         @NotNull PsiElement endElement) {
        runTypeMigrationOnVariable((PsiVariable)startElement, getReturnType(), editor, optimizeImports, true);
      }
    };
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
      rules.setBoundScope(GlobalSearchScope.projectScope(project));
      TypeMigrationProcessor.runHighlightingTypeMigration(project, editor, rules, variable, targetType, optimizeImports, allowDependentRoots);
      WriteAction.run(() -> JavaCodeStyleManager.getInstance(project).shortenClassReferences(variable));
      UndoUtil.markPsiFileForUndo(variable.getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG1.error(e);
    }
  }
}
