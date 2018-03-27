/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
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
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JavaVariableInplaceIntroducer extends AbstractJavaInplaceIntroducer {

  private SmartPsiElementPointer<? extends PsiElement> myPointer;

  private JCheckBox myCanBeFinalCb;
  private final IntroduceVariableSettings mySettings;
  private final SmartPsiElementPointer<PsiElement> myChosenAnchor;
  private final boolean myCantChangeFinalModifier;
  private final boolean myHasTypeSuggestion;
  private ResolveSnapshotProvider.ResolveSnapshot myConflictResolver;
  private final TypeExpression myExpression;
  private final boolean myReplaceSelf;
  private boolean myDeleteSelf = true;
  private final boolean mySkipTypeExpressionOnStart;

  public JavaVariableInplaceIntroducer(final Project project,
                                       IntroduceVariableSettings settings, PsiElement chosenAnchor, final Editor editor,
                                       final PsiExpression expr,
                                       final boolean cantChangeFinalModifier,
                                       final PsiExpression[] occurrences,
                                       final TypeSelectorManagerImpl selectorManager,
                                       final String title) {
    super(project, editor, RefactoringUtil.outermostParenthesizedExpression(expr), null, occurrences, selectorManager, title);
    mySettings = settings;
    myChosenAnchor = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(chosenAnchor);
    myCantChangeFinalModifier = cantChangeFinalModifier;
    myHasTypeSuggestion = selectorManager.getTypesForAll().length > 1;
    myTitle = title;
    myExpression = new TypeExpression(myProject, isReplaceAllOccurrences()
                                                 ? myTypeSelectorManager.getTypesForAll()
                                                 : myTypeSelectorManager.getTypesForOne());

    final List<RangeMarker> rangeMarkers = getOccurrenceMarkers();
    editor.putUserData(ReassignVariableUtil.OCCURRENCES_KEY,
                       rangeMarkers.toArray(new RangeMarker[rangeMarkers.size()]));
    myReplaceSelf = myExpr.getParent() instanceof PsiExpressionStatement;
    mySkipTypeExpressionOnStart = !(myExpr instanceof PsiFunctionalExpression && myReplaceSelf);
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

  @Nullable
  protected PsiVariable getVariable() {
    final PsiElement declarationStatement = myPointer != null ? myPointer.getElement() : null;
    if (declarationStatement instanceof PsiDeclarationStatement) {
      PsiElement[] declaredElements = ((PsiDeclarationStatement)declarationStatement).getDeclaredElements();
      return declaredElements.length == 0 ? null : (PsiVariable)declaredElements[0];
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
    if (myCanBeFinalCb != null) {
      JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = psiVariable.hasModifierProperty(PsiModifier.FINAL);
    }

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

  @Nullable
  protected JComponent getComponent() {
    if (!myCantChangeFinalModifier) {
      myCanBeFinalCb = new NonFocusableCheckBox("Declare final");
      myCanBeFinalCb.setSelected(createFinals());
      myCanBeFinalCb.setMnemonic('f');
      final FinalListener finalListener = new FinalListener(myEditor);
      myCanBeFinalCb.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          new WriteCommandAction(myProject, getCommandName(), getCommandName()) {
            @Override
            protected void run(@NotNull Result result) {
              PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
              final PsiVariable variable = getVariable();
              if (variable != null) {
                finalListener.perform(myCanBeFinalCb.isSelected(), variable);
              }
            }
          }.execute();
        }
      });
    } else {
      return null;
    }
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(null);

    if (myCanBeFinalCb != null) {
      panel.add(myCanBeFinalCb, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                       JBUI.insets(5), 0, 0));
    }

    panel.add(Box.createVerticalBox(), new GridBagConstraints(0, 2, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                              JBUI.emptyInsets(), 0, 0));

    return panel;
  }

  protected void addAdditionalVariables(TemplateBuilderImpl builder) {
    final PsiVariable variable = getVariable();
    if (variable != null) {
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement != null) {
        builder.replaceElement(typeElement, "Variable_Type", AbstractJavaInplaceIntroducer.createExpression(myExpression, typeElement.getText()), true, mySkipTypeExpressionOnStart);
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
            if (((PsiReferenceExpression)parent).resolve() == null && type != null && !type.equals(psiVariable.getType())) {
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
  private static String getAdvertisementText(final PsiDeclarationStatement declaration,
                                             final PsiType type,
                                             final boolean hasTypeSuggestion) {
    final VariablesProcessor processor = ReassignVariableUtil.findVariablesOfType(declaration, type);
    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    if (processor.size() > 0) {
      final Shortcut[] shortcuts = keymap.getShortcuts("IntroduceVariable");
      if (shortcuts.length > 0) {
        return "Press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to reassign existing variable";
      }
    }
    if (hasTypeSuggestion) {
      final Shortcut[] shortcuts = keymap.getShortcuts("PreviousTemplateVariable");
      if  (shortcuts.length > 0) {
        return "Press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to change type";
      }
    }
    return null;
  }


  protected boolean createFinals() {
    return IntroduceVariableBase.createFinals(myProject);
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
    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
    initOccurrencesMarkers();
    return variable;
  }

  protected PsiVariable introduceVariable() {
    PsiVariable variable = IntroduceVariableBase
      .introduce(myProject, myExpr, myEditor, myChosenAnchor.getElement(), getOccurrences(), mySettings);
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    if (variable instanceof PsiField) {
      myPointer = smartPointerManager.createSmartPsiElementPointer(variable);
    }
    else {
      final PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
      if (declarationStatement != null) {
        SmartPsiElementPointer<PsiDeclarationStatement> pointer = smartPointerManager.createSmartPsiElementPointer(declarationStatement);
        myPointer = pointer;
        myEditor.putUserData(ReassignVariableUtil.DECLARATION_KEY, pointer);
        setAdvertisementText(getAdvertisementText(declarationStatement, variable.getType(), myHasTypeSuggestion));
      }
    }

    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
    return variable;
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
    return IntroduceVariableBase.getSuggestedName(defaultType, myExpr).names;
  }

  @Override
  protected VariableKind getVariableKind() {
    return VariableKind.LOCAL_VARIABLE;
  }
}
