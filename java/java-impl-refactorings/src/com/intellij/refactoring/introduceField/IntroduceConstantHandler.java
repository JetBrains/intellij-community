// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.PreviewableRefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class IntroduceConstantHandler extends BaseExpressionToFieldHandler implements JavaIntroduceVariableHandlerBase,
                                                                                      PreviewableRefactoringActionHandler {
  protected InplaceIntroduceConstantPopup myInplaceIntroduceConstantPopup;

  public IntroduceConstantHandler() {
    super(true);
  }

  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_CONSTANT;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiExpression expression) {
    invoke(project, new PsiExpression[]{expression});
  }

  public void invoke(@NotNull Project project, PsiExpression @NotNull [] expressions) {
    for (PsiExpression expression : expressions) {
      final PsiFile file = expression.getContainingFile();
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    }
    super.invoke(project, expressions, null);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    ElementToWorkOn.processElementToWorkOn(editor, file, getRefactoringNameText(), getHelpID(), project, getElementProcessor(project, editor));
  }

  @Override
  protected boolean invokeImpl(final Project project, final PsiLocalVariable localVariable, final Editor editor) {
    final PsiElement parent = localVariable.getParent();
    if (!(parent instanceof PsiDeclarationStatement)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
      return false;
    }
    final LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, true){
      @Override
      protected Settings showRefactoringDialog(PsiClass aClass,
                                               PsiLocalVariable local,
                                               PsiExpression[] occurences,
                                               boolean isStatic) {
        return IntroduceConstantHandler.this.showRefactoringDialog(project, editor, aClass, local.getInitializer(), local.getType(), occurences, local, null);
      }
    };
    return localToFieldHandler.convertLocalToField(localVariable, editor);
  }


  @Override
  protected Settings showRefactoringDialog(Project project,
                                           final Editor editor,
                                           PsiClass parentClass,
                                           PsiExpression expr,
                                           PsiType type,
                                           PsiExpression[] occurrences,
                                           PsiElement anchorElement,
                                           PsiElement anchorElementIfAll) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expr != null ? expr : anchorElement, PsiMethod.class);

    ElementToWorkOn elementToWorkOn = ElementToWorkOn.adjustElements(expr, anchorElement);
    PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
    expr = elementToWorkOn.getExpression();

    String enteredName = null;
    boolean replaceAllOccurrences = true;

    final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
    if (activeIntroducer != null) {
      activeIntroducer.stopIntroduce(editor);
      expr = (PsiExpression)activeIntroducer.getExpr();
      localVariable = (PsiLocalVariable)activeIntroducer.getLocalVariable();
      occurrences = (PsiExpression[])activeIntroducer.getOccurrences();
      enteredName = activeIntroducer.getInputName();
      replaceAllOccurrences = activeIntroducer.isReplaceAllOccurrences();
      type = ((InplaceIntroduceConstantPopup)activeIntroducer).getType();
    }

    for (PsiExpression occurrence : occurrences) {
      if (RefactoringUtil.isAssignmentLHS(occurrence)) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("introduce.constant.used.for.write.cannot.refactor.message"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
        highlightError(project, editor, occurrence);
        return null;
      }
    }

    if (localVariable == null) {
      final PsiElement errorElement = isStaticFinalInitializer(expr);
      if (errorElement != null) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.expression.cannot.be.a.constant.initializer"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
        highlightError(project, editor, errorElement);
        return null;
      }
    }
    else {
      final PsiExpression initializer = localVariable.getInitializer();
      if (initializer == null) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(JavaRefactoringBundle.message("variable.does.not.have.an.initializer", localVariable.getName()));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
        return null;
      }
      final PsiElement errorElement = isStaticFinalInitializer(initializer);
      if (errorElement != null) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          JavaRefactoringBundle.message("initializer.for.variable.cannot.be.a.constant.initializer", localVariable.getName()));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
        highlightError(project, editor, errorElement);
        return null;
      }
    }


    final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, type, containingMethod, expr, occurrences);
    if (editor != null && editor.getSettings().isVariableInplaceRenameEnabled() &&
        (expr == null || expr.isPhysical()) && activeIntroducer == null) {
      myInplaceIntroduceConstantPopup =
        new InplaceIntroduceConstantPopup(project, editor, parentClass, expr, localVariable, occurrences,
                                          typeSelectorManager, anchorElement, anchorElementIfAll);
      if (myInplaceIntroduceConstantPopup.startInplaceIntroduceTemplate()) {
        return null;
      }
    }

    if (IntentionPreviewUtils.isIntentionPreviewActive()) {
      boolean preselectNonNls = PropertiesComponent.getInstance(project).getBoolean(IntroduceConstantDialog.NONNLS_SELECTED_PROPERTY);
      PsiType defaultType = typeSelectorManager.getDefaultType();
      NameSuggestionsGenerator generator = IntroduceConstantDialog.createNameSuggestionGenerator(null, expr, JavaCodeStyleManager.getInstance(project), enteredName, getParentClass());
      return new Settings(generator.getSuggestedNameInfo(defaultType).names[0], expr, occurrences, replaceAllOccurrences, true, true,
                          InitializationPlace.IN_FIELD_DECLARATION,
                          ObjectUtils.notNull(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY, PsiModifier.PUBLIC),
                          localVariable, defaultType, localVariable != null, getParentClass(), preselectNonNls, false);
    }

    final IntroduceConstantDialog dialog =
      new IntroduceConstantDialog(project, parentClass, expr, localVariable, localVariable != null, occurrences, getParentClass(),
                                  typeSelectorManager, enteredName);
    dialog.setReplaceAllOccurrences(replaceAllOccurrences);
    if (!dialog.showAndGet()) {
      if (occurrences.length > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
      return null;
    }
    return new Settings(dialog.getEnteredName(), expr, occurrences, dialog.isReplaceAllOccurrences(), true, true,
                        InitializationPlace.IN_FIELD_DECLARATION, dialog.getFieldVisibility(), localVariable,
                        dialog.getSelectedType(), dialog.isDeleteVariable(), dialog.getDestinationClass(),
                        dialog.isAnnotateAsNonNls(),
                        dialog.introduceEnumConstant());
  }

  private static void highlightError(Project project, Editor editor, PsiElement errorElement) {
    if (editor != null) {
      final TextRange textRange = errorElement.getTextRange();
      HighlightManager.getInstance(project).addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(),
                                                              EditorColors.SEARCH_RESULT_ATTRIBUTES, true, new ArrayList<>());
    }
  }

  @Override
  protected String getRefactoringName() {
    return getRefactoringNameText();
  }

  @Override
  public InplaceIntroduceConstantPopup getInplaceIntroducer() {
    return myInplaceIntroduceConstantPopup;
  }

  @Override
  protected OccurrenceManager createOccurrenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    return new ExpressionOccurrenceManager(selectedExpr, parentClass, null);
  }

  @Override
  protected boolean accept(ElementToWorkOn elementToWorkOn) {
    final PsiExpression expr = elementToWorkOn.getExpression();
    if (expr != null) {
      return isStaticFinalInitializer(expr) == null;
    }
    final PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
    final PsiExpression initializer = localVariable.getInitializer();
    return initializer != null && isStaticFinalInitializer(initializer) == null;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull PsiElement element) {
    if (element instanceof PsiExpression expression) {
      invoke(project, null, expression);
      return IntentionPreviewInfo.DIFF;
    }
    return IntentionPreviewInfo.EMPTY;
  }

  @Override
  protected boolean validClass(PsiClass parentClass, PsiExpression selectedExpr, Editor editor) {
    return true;
  }

  public static @NlsActions.ActionText String getRefactoringNameText() {
    return RefactoringBundle.message("introduce.constant.title");
  }
}
