// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.refactoring.rename.inplace.SelectableInlayPresentation;
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JavaVariableInplaceIntroducer extends AbstractJavaInplaceIntroducer {

  private SmartPsiElementPointer<? extends PsiElement> myPointer;

  private final IntroduceVariableSettings mySettings;
  private final SmartPsiElementPointer<PsiElement> myChosenAnchor;
  private final boolean myCantChangeFinalModifier;
  private final boolean myHasTypeSuggestion;
  private ResolveSnapshotProvider.ResolveSnapshot myConflictResolver;
  private final TypeExpression myExpression;
  private final boolean myReplaceSelf;
  private boolean myDeleteSelf = true;
  private final boolean mySkipTypeExpressionOnStart;
  private final PsiFile myFile;
  private final boolean myCanBeVarType;

  public JavaVariableInplaceIntroducer(final Project project,
                                       IntroduceVariableSettings settings, PsiElement chosenAnchor, final Editor editor,
                                       final PsiExpression expr,
                                       final boolean cantChangeFinalModifier,
                                       final PsiExpression[] occurrences,
                                       final TypeSelectorManagerImpl selectorManager,
                                       final @NlsContexts.Command String title) {
    super(project, editor, CommonJavaRefactoringUtil.outermostParenthesizedExpression(expr), null, occurrences, selectorManager, title);
    mySettings = settings;
    myChosenAnchor = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(chosenAnchor);
    myFile = chosenAnchor.getContainingFile();
    myCantChangeFinalModifier = cantChangeFinalModifier;
    myHasTypeSuggestion = selectorManager.getTypesForAll().length > 1;
    myTitle = title;
    myExpression = new TypeExpression(myProject, isReplaceAllOccurrences()
                                                 ? myTypeSelectorManager.getTypesForAll()
                                                 : myTypeSelectorManager.getTypesForOne());

    final List<RangeMarker> rangeMarkers = getOccurrenceMarkers();
    editor.putUserData(ReassignVariableUtil.OCCURRENCES_KEY,
                       rangeMarkers.toArray(new RangeMarker[0]));
    PsiElement parent = myExpr.getParent();
    myReplaceSelf = parent instanceof PsiExpressionStatement && !(parent.getParent() instanceof PsiSwitchLabeledRuleStatement);
    mySkipTypeExpressionOnStart = !(myExpr instanceof PsiFunctionalExpression && myReplaceSelf);
    myCanBeVarType = IntroduceVariableBase.canBeExtractedWithoutExplicitType(myExpr);
  }

  @Override
  protected void beforeTemplateStart() {
    if (!mySkipTypeExpressionOnStart) {
      final PsiVariable variable = getVariable();
      final PsiTypeElement typeElement = variable != null ? variable.getTypeElement() : null;
      if (typeElement != null) {
        myEditor.getCaretModel().moveToOffset(typeElement.getTextOffset());
      }
    }
    final ResolveSnapshotProvider resolveSnapshotProvider = VariableInplaceRenamer.INSTANCE.forLanguage(myScope.getLanguage());
    myConflictResolver = resolveSnapshotProvider != null ? resolveSnapshotProvider.createSnapshot(myScope) : null;
    super.beforeTemplateStart();
  }

  @Override
  @Nullable
  protected PsiVariable getVariable() {
    final PsiElement declarationStatement = myPointer != null ? myPointer.getElement() : null;
    if (declarationStatement instanceof PsiDeclarationStatement) {
      PsiElement[] declaredElements = ((PsiDeclarationStatement)declarationStatement).getDeclaredElements();
      return declaredElements.length == 0 ? null : (PsiVariable)declaredElements[0];
    }
    else if (declarationStatement instanceof PsiInstanceOfExpression) {
      PsiPattern pattern = ((PsiInstanceOfExpression)declarationStatement).getPattern();
      if (pattern instanceof PsiTypeTestPattern) {
        return ((PsiTypeTestPattern)pattern).getPatternVariable();
      }
    }
    return declarationStatement instanceof PsiVariable ? (PsiVariable)declarationStatement : null;
  }

  @Override
  protected String getActionName() {
    return "IntroduceVariable";
  }

  @Override
  protected String getRefactoringId() {
    return "refactoring.extractVariable";
  }

  @Override
  protected void restoreState(@NotNull PsiVariable psiField) {
    if (myDeleteSelf) return;
    super.restoreState(psiField);
  }

  @Override
  protected boolean ensureValid() {
    final PsiVariable variable = getVariable();
    return variable != null && isIdentifier(getInputName(), variable.getLanguage());
  }

  @Override
  protected void performCleanup() {
    super.performCleanup();
    PsiVariable variable = getVariable();
    if (variable != null) {
      CommandProcessor.getInstance().executeCommand(myProject, () -> super.restoreState(variable), null, null);
    }
  }

  @Override
  protected void deleteTemplateField(PsiVariable variable) {
    if (!myDeleteSelf) return;
    if (myReplaceSelf) {
      variable.replace(variable.getInitializer());
    } else {
      super.deleteTemplateField(variable);
    }
  }

  @Override
  protected PsiExpression getBeforeExpr() {
    final PsiVariable variable = getVariable();
    if (variable != null) {
      return variable.getInitializer();
    }
    return super.getBeforeExpr();
  }

  @Override
  protected void performIntroduce() {
    final PsiVariable psiVariable = getVariable();
    if (psiVariable == null) {
      return;
    }

    TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myTypeSelectorManager.getDefaultType());

    final Document document = myEditor.getDocument();
    LOG.assertTrue(psiVariable.isValid());
    adjustLine(psiVariable, document);

    int startOffset = getExprMarker() != null && getExprMarker().isValid() ? getExprMarker().getStartOffset() : psiVariable.getTextOffset();
    final PsiFile file = psiVariable.getContainingFile();
    final PsiReference referenceAt = file.findReferenceAt(startOffset);
    if (referenceAt != null && referenceAt.resolve() instanceof PsiVariable) {
      startOffset = referenceAt.getElement().getTextRange().getEndOffset();
    }
    else {
      final PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(psiVariable, PsiDeclarationStatement.class);
      if (declarationStatement != null) {
        startOffset = declarationStatement.getTextRange().getEndOffset();
      }
    }

    myEditor.getCaretModel().moveToOffset(startOffset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

    ApplicationManager.getApplication().runWriteAction(() -> {
      if (myConflictResolver != null && myInsertedName != null && isIdentifier(myInsertedName, psiVariable.getLanguage())) {
        myConflictResolver.apply(psiVariable.getName());
      }
      if (psiVariable.getInitializer() != null) {
        appendTypeCasts(getOccurrenceMarkers(), file, myProject, psiVariable);
      }
    });
  }

  @Override
  public boolean isReplaceAllOccurrences() {
    return mySettings.isReplaceAllOccurrences();
  }

  @Override
  public void setReplaceAllOccurrences(boolean allOccurrences) {}

  @Override
  protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
    return handler instanceof IntroduceVariableHandler && super.startsOnTheSameElement(handler, element);
  }

  @Override
  protected @Nullable JComponent getComponent() {
    return null;
  }

  @Override
  protected void afterTemplateStart() {
    super.afterTemplateStart();
    TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
    if (templateState == null) return;

    TextRange currentVariableRange = templateState.getCurrentVariableRange();
    if (currentVariableRange == null) return;

    final PsiVariable variable = getVariable();
    if (variable instanceof PsiPatternVariable && !PsiUtil.isLanguageLevel16OrHigher(variable)) return;
    boolean canBeVarType = myCanBeVarType && variable instanceof PsiLocalVariable;
    if (myCantChangeFinalModifier && !canBeVarType) return;

    IntroduceVariablePopupComponent
      popupComponent = new IntroduceVariablePopupComponent(myEditor, myProject, myCantChangeFinalModifier, canBeVarType, getCommandName(), () -> getVariable());
    
    SelectableInlayPresentation presentation = TemplateInlayUtil.createSettingsPresentation((EditorImpl)templateState.getEditor(), popupComponent.logStatisticsOnShowCallback());
    TemplateInlayUtil.SelectableTemplateElement templateElement = new TemplateInlayUtil.SelectableTemplateElement(presentation) {
      @Override
      public void onSelect(@NotNull TemplateState templateState) {
        super.onSelect(templateState);
        popupComponent.logStatisticsOnShow(null);
      }
    };
    TemplateInlayUtil.createNavigatableButtonWithPopup(templateState, 
                                                       currentVariableRange.getEndOffset(),
                                                       presentation, 
                                                       popupComponent.createPopupPanel(), 
                                                       templateElement,
                                                       popupComponent.logStatisticsOnHideCallback()); 
  }

  @Override
  protected void showDialogAdvertisement(@NonNls String actionId) {
    initPopupOptionsAdvertisement();
  }

  @Override
  protected void addAdditionalVariables(TemplateBuilderImpl builder) {
    final PsiVariable variable = getVariable();
    if (variable != null) {
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement != null) {
        builder.replaceElement(typeElement, "Variable_Type", IntroduceVariableUtil.createExpression(myExpression, typeElement.getText()), true, mySkipTypeExpressionOnStart);
      }
    }
  }

  @Override
  protected void collectAdditionalElementsToRename(@NotNull List<Pair<PsiElement, TextRange>> stringUsages) {
    if (isReplaceAllOccurrences()) {
      for (PsiExpression expression : getOccurrences()) {
        LOG.assertTrue(expression.isValid(), expression.getText());
        stringUsages.add(Pair.create(expression, new TextRange(0, expression.getTextLength())));
      }
    } else if (getExpr() != null && !myReplaceSelf && getExpr().getParent() != getVariable()) {
      final PsiExpression expr = getExpr();
      LOG.assertTrue(expr.isValid(), expr.getText());
      stringUsages.add(Pair.create(expr, new TextRange(0, expr.getTextLength())));
    }
  }

  @Override
  protected void addReferenceAtCaret(Collection<PsiReference> refs) {
    if (!isReplaceAllOccurrences()) {
      final PsiExpression expr = getExpr();
      if (expr == null && !myReplaceSelf || expr != null && expr.getParent() == getVariable()) {
        return;
      }
    }
    super.addReferenceAtCaret(refs);
  }

  private static void appendTypeCasts(List<RangeMarker> occurrenceMarkers,
                                      PsiFile file,
                                      Project project,
                                      @Nullable PsiVariable psiVariable) {
    if (occurrenceMarkers != null) {
      for (RangeMarker occurrenceMarker : occurrenceMarkers) {
        final PsiElement refVariableElement = file.findElementAt(occurrenceMarker.getStartOffset());
        final PsiReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(refVariableElement, PsiReferenceExpression.class);
        if (referenceExpression != null) {
          final PsiElement parent = referenceExpression.getParent();
          if (parent instanceof PsiVariable) {
            createCastInVariableDeclaration(project, (PsiVariable)parent);
          }
          else if (parent instanceof PsiReferenceExpression && psiVariable != null) {
            final PsiExpression initializer = psiVariable.getInitializer();
            LOG.assertTrue(initializer != null);
            final PsiType type = initializer.getType();
            if (((PsiReferenceExpression)parent).resolve() == null && type != null && !type.equals(psiVariable.getType()) &&
                !LambdaUtil.notInferredType(type) && !PsiType.NULL.equals(type)) {
              final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
              final PsiExpression castedExpr =
                elementFactory.createExpressionFromText("((" + type.getCanonicalText() + ")" + referenceExpression.getText() + ")", parent);
              JavaCodeStyleManager.getInstance(project).shortenClassReferences(referenceExpression.replace(castedExpr));
            }
          }
        }
      }
    }
    if (psiVariable != null && psiVariable.isValid()) {
      createCastInVariableDeclaration(project, psiVariable);
    }
  }

  private static void createCastInVariableDeclaration(Project project, PsiVariable psiVariable) {
    final PsiExpression initializer = psiVariable.getInitializer();
    LOG.assertTrue(initializer != null);
    final PsiType type = psiVariable.getType();
    final PsiType initializerType = initializer.getType();
    if (initializerType != null &&
        !TypeConversionUtil.isAssignable(type, initializerType) &&
        !PsiTypesUtil.hasUnresolvedComponents(type)) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression castExpr =
        elementFactory.createExpressionFromText("(" + type.getCanonicalText() + ")" + initializer.getText(), psiVariable);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(initializer.replace(castExpr));
    }
  }

  @Nullable
  private static @NlsContexts.PopupAdvertisement String getAdvertisementText(final PsiDeclarationStatement declaration,
                                                                             final PsiType type,
                                                                             final boolean hasTypeSuggestion) {
    final VariablesProcessor processor = ReassignVariableUtil.findVariablesOfType(declaration, type);
    if (processor.size() > 0) {
      final Shortcut shortcut = KeymapUtil.getPrimaryShortcut("IntroduceVariable");
      if (shortcut != null) {
        return JavaBundle.message("introduce.variable.reassign.adv", KeymapUtil.getShortcutText(shortcut));
      }
    }
    if (hasTypeSuggestion) {
      final Shortcut shortcut = KeymapUtil.getPrimaryShortcut("PreviousTemplateVariable");
      if (shortcut != null) {
        return JavaBundle.message("introduce.variable.change.type.adv", KeymapUtil.getShortcutText(shortcut));
      }
    }
    return null;
  }


  protected boolean createFinals() {
    return createFinals(myFile);
  }

  static boolean createFinals(PsiFile file) {
    return IntroduceVariableBase.createFinals(file);
  }

  public static void adjustLine(final PsiVariable psiVariable, final Document document) {
    final int modifierListOffset = psiVariable.getTextRange().getStartOffset();
    final int varLineNumber = document.getLineNumber(modifierListOffset);

    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiDocumentManager.getInstance(psiVariable.getProject()).doPostponedOperationsAndUnblockDocument(document);
      CodeStyleManager.getInstance(psiVariable.getProject()).adjustLineIndent(document, document.getLineStartOffset(varLineNumber));
    });
  }

  @Override
  protected PsiVariable createFieldToStartTemplateOn(String[] names, PsiType psiType) {
    PsiVariable variable = introduceVariable();

    final PsiVariable restoredVar = getVariable();
    if (restoredVar != null) {
      variable = restoredVar;
    }

    if (variable == null) return null;

    if (isReplaceAllOccurrences()) {
      List<RangeMarker> occurrences = new ArrayList<>();
      ReferencesSearch.search(variable).forEach(reference -> {
        occurrences.add(createMarker(reference.getElement()));
      });
      setOccurrenceMarkers(occurrences);
      myOccurrences = new PsiExpression[occurrences.size()];
    }

    final PsiIdentifier identifier = variable.getNameIdentifier();
    if (identifier != null) {
      myEditor.getCaretModel().moveToOffset(identifier.getTextOffset());
    }
    try {
      myDeleteSelf = false;
      restoreState(variable);
    }
    finally {
      myDeleteSelf = true;
    }
    initOccurrencesMarkers();
    return variable;
  }

  @Nullable
  protected PsiVariable introduceVariable() {
    PsiElement anchor = myChosenAnchor.getElement();
    if (anchor == null) return null;
    PsiVariable variable = VariableExtractor.introduce(myProject, myExpr, myEditor, anchor, getOccurrences(), mySettings);
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    if (variable instanceof PsiField || variable instanceof PsiResourceVariable) {
      myPointer = smartPointerManager.createSmartPsiElementPointer(variable);
    }
    else if (variable instanceof PsiPatternVariable) {
      PsiElement parent = ((PsiPatternVariable)variable).getPattern().getParent();
      LOG.assertTrue(parent instanceof PsiInstanceOfExpression);
      myPointer = smartPointerManager.createSmartPsiElementPointer(parent);
    }
    else {
      final PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
      if (declarationStatement != null) {
        SmartPsiElementPointer<PsiDeclarationStatement> pointer = smartPointerManager.createSmartPsiElementPointer(declarationStatement);
        myPointer = pointer;
        myEditor.putUserData(ReassignVariableUtil.DECLARATION_KEY, pointer);
        PsiType variableType = variable.getType();
        ReadAction.nonBlocking(() -> {
            PsiDeclarationStatement element = pointer.getElement();
            return element != null && variableType.isValid() ? getAdvertisementText(element, variableType, myHasTypeSuggestion) : null;
          })
          .finishOnUiThread(ModalityState.NON_MODAL, (@NlsContexts.PopupAdvertisement String text) -> setAdvertisementText(text))
          .submit(NonUrgentExecutor.getInstance());
      }
    }

    SmartPsiElementPointer<PsiVariable> pointer = variable == null ? null : smartPointerManager.createSmartPsiElementPointer(variable);
    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
    return pointer == null ? null : pointer.getElement();
  }

  @Override
  protected int getCaretOffset() {
    final PsiVariable variable = getVariable();
    if (variable != null) {
      final PsiIdentifier identifier = variable.getNameIdentifier();
      if (identifier != null) {
        return identifier.getTextOffset();
      }
    }
    return super.getCaretOffset();
  }

  @Override
  public void finish(boolean success) {
    super.finish(success);
    myEditor.putUserData(ReassignVariableUtil.DECLARATION_KEY, null);
  }

  @Override
  protected String[] suggestNames(PsiType defaultType, String propName) {
    return CommonJavaRefactoringUtil.getSuggestedName(defaultType, myExpr).names;
  }

  @Override
  protected VariableKind getVariableKind() {
    return VariableKind.LOCAL_VARIABLE;
  }
}
