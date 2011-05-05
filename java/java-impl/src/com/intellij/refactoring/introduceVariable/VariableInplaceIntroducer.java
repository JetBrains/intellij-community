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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.TitlePanel;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: anna
 * Date: 12/8/10
 */
public class VariableInplaceIntroducer extends VariableInplaceRenamer {
  private final PsiVariable myElementToRename;
  private final Editor myEditor;
  private final TypeExpression myExpression;
  private final Project myProject;
  private final SmartPsiElementPointer<PsiDeclarationStatement> myPointer;
  private final RangeMarker myExprMarker;
  private final List<RangeMarker> myOccurrenceMarkers;
  private final SmartTypePointer myDefaultType;

  protected JCheckBox myCanBeFinal;
  private Balloon myBalloon;

  public VariableInplaceIntroducer(final Project project,
                                   final TypeExpression expression,
                                   final Editor editor,
                                   final PsiVariable elementToRename,
                                   final boolean cantChangeFinalModifier,
                                   final boolean hasTypeSuggestion,
                                   final RangeMarker exprMarker,
                                   final List<RangeMarker> occurrenceMarkers,
                                   final String commandName) {
    super(elementToRename, editor);
    myProject = project;
    myEditor = editor;
    myElementToRename = elementToRename;
    myExpression = expression;

    myExprMarker = exprMarker;
    myOccurrenceMarkers = occurrenceMarkers;

    final PsiType defaultType = elementToRename.getType();
    myDefaultType = SmartTypePointerManager.getInstance(project).createSmartTypePointer(defaultType);

    final PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(elementToRename, PsiDeclarationStatement.class);
    myPointer = declarationStatement != null ? SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declarationStatement) : null;
    editor.putUserData(ReassignVariableUtil.DECLARATION_KEY, myPointer);
    editor.putUserData(ReassignVariableUtil.OCCURRENCES_KEY,
                       occurrenceMarkers.toArray(new RangeMarker[occurrenceMarkers.size()]));
    setAdvertisementText(getAdvertisementText(declarationStatement, defaultType, hasTypeSuggestion));
    if (!cantChangeFinalModifier) {
      myCanBeFinal = new NonFocusableCheckBox("Declare final");
      myCanBeFinal.setSelected(createFinals());
      myCanBeFinal.setMnemonic('f');
      myCanBeFinal.addActionListener(new FinalListener(project, commandName));
    }
  }

  @Override
  protected void addAdditionalVariables(TemplateBuilderImpl builder) {
    final PsiTypeElement typeElement = myElementToRename.getTypeElement();
    builder.replaceElement(typeElement, "Variable_Type", createExpression(myExpression, typeElement.getText()), true, true);
  }

  @Override
  protected LookupElement[] createLookupItems(LookupElement[] lookupItems, String name) {
    TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
    final PsiVariable psiVariable = getVariable();
    if (psiVariable != null) {
      final TextResult insertedValue =
        templateState != null ? templateState.getVariableValue(PRIMARY_VARIABLE_NAME) : null;
      if (insertedValue != null) {
        final String text = insertedValue.getText();
        if (!text.isEmpty() && !Comparing.strEqual(text, name)) {
          final LinkedHashSet<String> names = new LinkedHashSet<String>();
          names.add(text);
          for (NameSuggestionProvider provider : Extensions.getExtensions(NameSuggestionProvider.EP_NAME)) {
            provider.getSuggestedNames(psiVariable, psiVariable, names);
          }
          final LookupElement[] items = new LookupElement[names.size()];
          final Iterator<String> iterator = names.iterator();
          for (int i = 0; i < items.length; i++) {
            items[i] = LookupElementBuilder.create(iterator.next());
          }
          return items;
        }
      }
    }
    return super.createLookupItems(lookupItems, name);
  }

  @Nullable
  protected PsiVariable getVariable() {
    final PsiDeclarationStatement declarationStatement = myPointer.getElement();
    return declarationStatement != null ? (PsiVariable)declarationStatement.getDeclaredElements()[0] : null;
  }

  @Override
  protected TextRange preserveSelectedRange(SelectionModel selectionModel) {
    return null;
  }

  @Override
  public boolean performInplaceRename(boolean processTextOccurrences, LinkedHashSet<String> nameSuggestions) {
    final boolean result = super.performInplaceRename(processTextOccurrences, nameSuggestions);
    showBalloon();
    return result;
  }

  public RangeMarker getExprMarker() {
    return myExprMarker;
  }

  @Override
  protected boolean performAutomaticRename() {
    return false;
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    try {
      if (success) {
        final Document document = myEditor.getDocument();
        final @Nullable PsiVariable psiVariable = getVariable();
        if (psiVariable == null) {
          return;
        }
        LOG.assertTrue(psiVariable.isValid());
        saveSettings(psiVariable);
        adjustLine(psiVariable, document);
        int startOffset = myExprMarker != null && myExprMarker.isValid() ? myExprMarker.getStartOffset() : psiVariable.getTextOffset();
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
        if (psiVariable.getInitializer() != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              appendTypeCasts(myOccurrenceMarkers, file, myProject, psiVariable);
            }
          });
        }
      } else {
        if (myExprMarker != null) {
          myEditor.getCaretModel().moveToOffset(myExprMarker.getStartOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        }
      }
    }
    finally {
      myEditor.putUserData(ReassignVariableUtil.DECLARATION_KEY, null);
      for (RangeMarker occurrenceMarker : myOccurrenceMarkers) {
        occurrenceMarker.dispose();
      }
      myEditor.putUserData(ReassignVariableUtil.OCCURRENCES_KEY, null);
      if (myExprMarker != null) myExprMarker.dispose();
    }
  }

  @Override
  public void finish() {
    super.finish();
    if (myBalloon != null) myBalloon.hide();
  }

  protected void saveSettings(PsiVariable psiVariable) {
    JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = psiVariable.hasModifierProperty(PsiModifier.FINAL);
    TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myDefaultType.getType());
  }


  @Nullable
  protected JComponent getComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(null);

    final TitlePanel titlePanel = new TitlePanel();
    titlePanel.setBorder(null);
    titlePanel.setText(IntroduceVariableBase.REFACTORING_NAME);
    panel.add(titlePanel, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

    if (myCanBeFinal != null) {
      panel.add(myCanBeFinal, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
    }

    panel.add(Box.createVerticalBox(), new GridBagConstraints(0, 2, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));

    return panel;
  }

  private static void appendTypeCasts(List<RangeMarker> occurrenceMarkers,
                                      PsiFile file,
                                      Project project,
                                      @Nullable PsiVariable psiVariable) {
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
        return "Press " + shortcuts[0] + " to reassign existing variable";
      }
    }
    if (hasTypeSuggestion) {
      final Shortcut[] shortcuts = keymap.getShortcuts("PreviousTemplateVariable");
      if  (shortcuts.length > 0) {
        return "Press " + shortcuts[0] + " to change type";
      }
    }
    return null;
  }

  private static Expression createExpression(final TypeExpression expression, final String defaultType) {
    return new Expression() {
      @Override
      public Result calculateResult(ExpressionContext context) {
        return new TextResult(defaultType);
      }

      @Override
      public Result calculateQuickResult(ExpressionContext context) {
        return new TextResult(defaultType);
      }

      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        return expression.calculateLookupItems(context);
      }

      @Override
      public String getAdvertisingText() {
        return null;
      }
    };
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


  private void showBalloon() {
    final JComponent component = getComponent();
    if (component == null) return;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    final BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(component);
    balloonBuilder.setFadeoutTime(0)
      .setFillColor(IdeTooltipManager.GRAPHITE_COLOR.brighter().brighter())
      .setAnimationCycle(0)
      .setHideOnClickOutside(false)
      .setHideOnKeyOutside(false)
      .setHideOnAction(false)
      .setCloseButtonEnabled(true);

    final RelativePoint target = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
    final Point screenPoint = target.getScreenPoint();
    myBalloon = balloonBuilder.createBalloon();
    int y = screenPoint.y;
    if (target.getPoint().getY() > myEditor.getLineHeight() + myBalloon.getPreferredSize().getHeight()) {
      y -= myEditor.getLineHeight();
    }
    myBalloon.show(new RelativePoint(new Point(screenPoint.x, y)), Balloon.Position.above);
  }

  public class FinalListener implements ActionListener {
    private final Project myProject;
    private final String myCommandName;

    public FinalListener(Project project, String commandName) {
      myProject = project;
      myCommandName = commandName;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      perform(myCanBeFinal.isSelected());
    }

    public void perform(final boolean generateFinal) {
      perform(generateFinal, PsiModifier.FINAL);
    }

    public void perform(final boolean generateFinal, final String modifier) {
      new WriteCommandAction(myProject, myCommandName, myCommandName){
        @Override
        protected void run(com.intellij.openapi.application.Result result) throws Throwable {
          final Document document = myEditor.getDocument();
          PsiDocumentManager.getInstance(getProject()).commitDocument(document);
          final PsiVariable variable = getVariable();
          LOG.assertTrue(variable != null);
          final PsiModifierList modifierList = variable.getModifierList();
          LOG.assertTrue(modifierList != null);
          final int textOffset = modifierList.getTextOffset();

          final Runnable runnable = new Runnable() {
            public void run() {
              if (generateFinal) {
                final PsiTypeElement typeElement = variable.getTypeElement();
                final int typeOffset = typeElement != null ? typeElement.getTextOffset() : textOffset;
                document.insertString(typeOffset, modifier + " ");
              }
              else {
                final int idx = modifierList.getText().indexOf(modifier);
                document.deleteString(textOffset + idx, textOffset + idx + modifier.length() + 1);
              }
            }
          };
          final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
          if (lookup != null) {
            lookup.performGuardedChange(runnable);
          } else {
            runnable.run();
          }
        }
      }.execute();
    }
  }
}
