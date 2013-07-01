/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.intellij.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 12/8/10
 */
public class JavaVariableInplaceIntroducer extends InplaceVariableIntroducer<PsiExpression> {
  protected final Project myProject;
  private final SmartPsiElementPointer<PsiDeclarationStatement> myPointer;

  private JCheckBox myCanBeFinalCb;

  private final boolean myCantChangeFinalModifier;
  private final String myTitle;
  private String myExpressionText;
  protected final SmartTypePointer myDefaultType;
  protected final TypeExpression myExpression;

  private ResolveSnapshotProvider.ResolveSnapshot myConflictResolver;

  public JavaVariableInplaceIntroducer(final Project project,
                                       final TypeExpression expression,
                                       final Editor editor,
                                       @NotNull final PsiVariable elementToRename,
                                       final boolean cantChangeFinalModifier,
                                       final boolean hasTypeSuggestion,
                                       final RangeMarker exprMarker,
                                       final List<RangeMarker> occurrenceMarkers,
                                       final String title) {
    super(elementToRename, editor, project, title, new PsiExpression[0], null);
    myProject = project;
    myCantChangeFinalModifier = cantChangeFinalModifier;
    myTitle = title;
    setExprMarker(exprMarker);
    setOccurrenceMarkers(occurrenceMarkers);
    final PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(elementToRename, PsiDeclarationStatement.class);
    myPointer = declarationStatement != null ? SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declarationStatement) : null;
    editor.putUserData(ReassignVariableUtil.DECLARATION_KEY, myPointer);
    if (occurrenceMarkers != null) {
      final ArrayList<RangeMarker> rangeMarkers = new ArrayList<RangeMarker>(occurrenceMarkers);
      rangeMarkers.add(exprMarker);
      editor.putUserData(ReassignVariableUtil.OCCURRENCES_KEY,
                         rangeMarkers.toArray(new RangeMarker[rangeMarkers.size()]));
    }
    myExpression = expression;
    final PsiType defaultType = elementToRename.getType();
    myDefaultType = SmartTypePointerManager.getInstance(project).createSmartTypePointer(defaultType);
    setAdvertisementText(getAdvertisementText(declarationStatement, defaultType, hasTypeSuggestion));
  }

  public void initInitialText(String text) {
    myExpressionText = text;
  }

  @Override
  protected StartMarkAction startRename() throws StartMarkAction.AlreadyStartedException {
    return StartMarkAction.start(myEditor, myProject, getCommandName());
  }

  @Override
  protected void beforeTemplateStart() {
    super.beforeTemplateStart();
    final ResolveSnapshotProvider resolveSnapshotProvider = VariableInplaceRenamer.INSTANCE.forLanguage(myScope.getLanguage());
    myConflictResolver = resolveSnapshotProvider != null ? resolveSnapshotProvider.createSnapshot(myScope) : null;
  }

  @Nullable
  protected PsiVariable getVariable() {
    final PsiDeclarationStatement declarationStatement = myPointer.getElement();
    if (declarationStatement != null) {
      PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      return declaredElements.length == 0 ? null : (PsiVariable)declaredElements[0];
    }
    return null;
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    try {
      if (success) {
        final Document document = myEditor.getDocument();
        @Nullable final PsiVariable psiVariable = getVariable();
        if (psiVariable == null) {
          return;
        }
        LOG.assertTrue(psiVariable.isValid());
        TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myDefaultType.getType());
        if (myCanBeFinalCb != null) {
          JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = psiVariable.hasModifierProperty(PsiModifier.FINAL);
        }
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
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            if (psiVariable.getInitializer() != null) {
              appendTypeCasts(getOccurrenceMarkers(), file, myProject, psiVariable);
            }
            if (myConflictResolver != null && myInsertedName != null && isIdentifier(myInsertedName, psiVariable.getLanguage())) {
              myConflictResolver.apply(psiVariable.getName());
            }
          }
        });
      }
      else {
        RangeMarker exprMarker = getExprMarker();
        if (exprMarker != null && exprMarker.isValid()) {
          myEditor.getCaretModel().moveToOffset(exprMarker.getStartOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        }
        if (myExpressionText != null) {
          if (!ReadonlyStatusHandler.ensureDocumentWritable(myProject, InjectedLanguageUtil.getTopLevelEditor(myEditor).getDocument())) return;
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              final PsiDeclarationStatement element = myPointer.getElement();
              if (element != null) {
                final PsiElement[] vars = element.getDeclaredElements();
                if (vars.length > 0 && vars[0] instanceof PsiVariable) {
                  final PsiFile containingFile = element.getContainingFile();
                  //todo pull up method restore state
                  final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
                  final RangeMarker exprMarker = getExprMarker();
                  if (exprMarker != null) {
                    myExpr = AbstractJavaInplaceIntroducer.restoreExpression(containingFile, (PsiVariable)vars[0], elementFactory, exprMarker, myExpressionText);
                    if (myExpr != null && myExpr.isPhysical()) {
                      myExprMarker = createMarker(myExpr);
                    }
                  }
                  List<RangeMarker> markers = getOccurrenceMarkers();
                  for (RangeMarker occurrenceMarker : markers) {
                    if (getExprMarker() != null && occurrenceMarker.getStartOffset() == getExprMarker().getStartOffset() && myExpr != null) {
                      continue;
                    }
                    AbstractJavaInplaceIntroducer
                          .restoreExpression(containingFile, (PsiVariable)vars[0], elementFactory, occurrenceMarker, myExpressionText);
                  }
                  final PsiExpression initializer = ((PsiVariable)vars[0]).getInitializer();
                  if (initializer != null && Comparing.strEqual(initializer.getText(), myExpressionText) && myExpr == null) {
                    element.replace(JavaPsiFacade.getInstance(myProject).getElementFactory().createStatementFromText(myExpressionText, element));
                  } else {
                    element.delete();
                  }
                }
              }
            }
          });
        }
      }
    }
    finally {
      myEditor.putUserData(ReassignVariableUtil.DECLARATION_KEY, null);
      for (RangeMarker occurrenceMarker : getOccurrenceMarkers()) {
        occurrenceMarker.dispose();
      }
      myEditor.putUserData(ReassignVariableUtil.OCCURRENCES_KEY, null);
      if (getExprMarker() != null) getExprMarker().dispose();
    }
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
            protected void run(com.intellij.openapi.application.Result result) throws Throwable {
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
      panel.add(myCanBeFinalCb, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
    }

    panel.add(Box.createVerticalBox(), new GridBagConstraints(0, 2, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));

    return panel;
  }

  protected void addAdditionalVariables(TemplateBuilderImpl builder) {
    final PsiTypeElement typeElement = getVariable().getTypeElement();
    builder.replaceElement(typeElement, "Variable_Type", AbstractJavaInplaceIntroducer.createExpression(myExpression, typeElement.getText()), true, true);
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
            if (((PsiReferenceExpression)parent).resolve() == null && type != null) {
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
    if (initializerType != null && !TypeConversionUtil.isAssignable(type, initializerType)) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression castExpr =
        elementFactory.createExpressionFromText("(" + psiVariable.getType().getCanonicalText() + ")" + initializer.getText(), psiVariable);
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

    ApplicationManager.getApplication().runWriteAction(new Runnable() { //adjust line indent if final was inserted and then deleted

      public void run() {
        PsiDocumentManager.getInstance(psiVariable.getProject()).doPostponedOperationsAndUnblockDocument(document);
        CodeStyleManager.getInstance(psiVariable.getProject()).adjustLineIndent(document, document.getLineStartOffset(varLineNumber));
      }
    });
  }


  protected String getTitle() {
    return myTitle;
  }


  @Nullable
  private static String getAdvertisementText(final boolean hasTypeSuggestion) {
    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    if (hasTypeSuggestion) {
      final Shortcut[] shortcuts = keymap.getShortcuts("PreviousTemplateVariable");
      if (shortcuts.length > 0) {
        return "Press " + shortcuts[0] + " to change type";
      }
    }
    return null;
  }
}
