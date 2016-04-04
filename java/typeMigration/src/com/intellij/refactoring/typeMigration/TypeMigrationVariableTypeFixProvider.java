/*
 * User: anna
 * Date: 27-Aug-2009
 */
package com.intellij.refactoring.typeMigration;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
  private static final Logger LOG1 = Logger.getInstance("#" + TypeMigrationVariableTypeFixProvider.class.getName());

  @NotNull
  public IntentionAction[] getFixes(@NotNull PsiVariable variable, @NotNull PsiType toReturn) {
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
        return "Migrate \'" + myName + "\' type to \'" + getReturnType().getCanonicalText() + "\'";
      }

      @Override
      public void invoke(@NotNull Project project,
                         @NotNull PsiFile file,
                         @Nullable("is null when called from inspection") Editor editor,
                         @NotNull PsiElement startElement,
                         @NotNull PsiElement endElement) {
        runTypeMigrationOnVariable((PsiVariable)startElement, getReturnType(), editor, optimizeImports);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }

  public static void runTypeMigrationOnVariable(@NotNull PsiVariable variable,
                                                @NotNull PsiType targetType,
                                                @Nullable("is null when called from inspection") Editor editor,
                                                boolean optimizeImports) {
    Project project = variable.getProject();
    if (!FileModificationService.getInstance().prepareFileForWrite(variable.getContainingFile())) return;
    try {
      variable.normalizeDeclaration();
      final TypeMigrationRules rules = new TypeMigrationRules();
      rules.setBoundScope(GlobalSearchScope.projectScope(project));
      TypeMigrationProcessor.runHighlightingTypeMigration(project, editor, rules, variable, targetType, optimizeImports);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(variable);
      UndoUtil.markPsiFileForUndo(variable.getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG1.error(e);
    }
  }
}
