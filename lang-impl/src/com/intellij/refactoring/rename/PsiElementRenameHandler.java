package com.intellij.refactoring.rename;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * created at Nov 13, 2001
 *
 * @author Jeka, dsl
 */
public class PsiElementRenameHandler implements RenameHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.PsiElementRenameHandler");

  public static final ExtensionPointName<Condition<PsiElement>> VETO_RENAME_CONDITION_EP = ExtensionPointName.create("com.intellij.vetoRenameCondition");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = getElement(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
    invoke(element, project, nameSuggestionContext, editor);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = getElement(dataContext);
    LOG.assertTrue(element != null);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    invoke(element, project, element, editor);
  }

  public static void invoke(PsiElement element, Project project, PsiElement nameSuggestionContext, Editor editor) {
    if (element != null && !canRename(element, project)) {
      return;
    }
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.rename");

    rename(element, project, nameSuggestionContext, editor);
  }

  static boolean canRename(PsiElement element, Project project) {
    final String REFACTORING_NAME = RefactoringBundle.message("rename.title");

    boolean hasRenameProcessor = (RenamePsiElementProcessor.forElement(element) != RenamePsiElementProcessor.DEFAULT);
    boolean hasWritableMetaData = element instanceof PsiMetaOwner && ((PsiMetaOwner) element).getMetaData() instanceof PsiWritableMetaData;

    if (!hasRenameProcessor && !hasWritableMetaData && !(element instanceof PsiNamedElement)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.symbol"));
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
      }
      return false;
    }

    if (!PsiManager.getInstance(project).isInProject(element) && element.isPhysical()) {
      String message = RefactoringBundle
        .getCannotRefactorMessage(RefactoringBundle.message("error.out.of.project.element", UsageViewUtil.getType(element)));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
      return false;
    }

    if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(element)) {
      String message = RefactoringBundle
        .getCannotRefactorMessage(RefactoringBundle.message("error.in.injected.lang.prefix.suffix", UsageViewUtil.getType(element)));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
      return false;
    }

    return true;//CommonRefactoringUtil.checkReadOnlyStatus(project, element);
  }


  public static void rename(PsiElement element, final Project project, PsiElement nameSuggestionContext, Editor editor) {
    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(element);
    element = processor.substituteElementToRename(element);
    if (element == null) return;

    final RenameDialog dialog = new RenameDialog(project, element, nameSuggestionContext, editor);
    dialog.show();
  }

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final PsiElement element = getElement(dataContext);

    if (element == null || element instanceof SyntheticElement) return false;
    for(Condition<PsiElement> condition: Extensions.getExtensions(VETO_RENAME_CONDITION_EP)) {
      if (condition.value(element)) return false;
    }

    return true;
  }

  @Nullable
  public static PsiElement getElement(final DataContext dataContext) {
    PsiElement[] elementArray = BaseRefactoringAction.getPsiElementArray(dataContext);

    if (elementArray.length != 1) {
      return null;
    }
    return elementArray[0];
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }
}
