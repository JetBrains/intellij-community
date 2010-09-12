/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

class IntroduceConstantDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.IntroduceConstantDialog");
  @NonNls private static final String RECENTS_KEY = "IntroduceConstantDialog.RECENTS_KEY";
  @NonNls private static final String NONNLS_SELECTED_PROPERTY = "INTRODUCE_CONSTANT_NONNLS";

  private final Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myInvokedOnDeclaration;
  private final PsiExpression[] myOccurrences;
  private final int myOccurrencesCount;
  private PsiClass myTargetClass;
  private final TypeSelectorManager myTypeSelectorManager;

  private NameSuggestionsField myNameField;
  private JCheckBox myCbReplaceAll;

  private TypeSelector myTypeSelector;
  private StateRestoringCheckBox myCbDeleteVariable;
  private final JavaCodeStyleManager myCodeStyleManager;
  private ReferenceEditorComboWithBrowseButton myTfTargetClassName;
  private BaseExpressionToFieldHandler.TargetDestination myDestinationClass;
  private JPanel myTypePanel;
  private JPanel myTargetClassNamePanel;
  private JPanel myPanel;
  private JLabel myTypeLabel;
  private JPanel myNameSuggestionPanel;
  private JLabel myNameSuggestionLabel;
  private JLabel myTargetClassNameLabel;
  private JCheckBox myCbNonNls;
  private JPanel myVisibilityPanel;
  private final JavaVisibilityPanel myVPanel;
  private final JCheckBox myIntroduceEnumConstantCb = new JCheckBox(RefactoringBundle.message("introduce.constant.enum.cb"), true);

  IntroduceConstantDialog(Project project,
                                 PsiClass parentClass,
                                 PsiExpression initializerExpression,
                                 PsiLocalVariable localVariable,
                                 boolean isInvokedOnDeclaration,
                                 PsiExpression[] occurrences,
                                 PsiClass targetClass,
                                 TypeSelectorManager typeSelectorManager) {
    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myInvokedOnDeclaration = isInvokedOnDeclaration;
    myOccurrences = occurrences;
    myOccurrencesCount = occurrences.length;
    myTargetClass = targetClass;
    myTypeSelectorManager = typeSelectorManager;
    myDestinationClass = null;

    setTitle(IntroduceConstantHandler.REFACTORING_NAME);
    myCodeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    myVPanel = new JavaVisibilityPanel(false, true);
    myVisibilityPanel.add(myVPanel, BorderLayout.CENTER);
    init();

    myVPanel.setVisibility(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY);
    myIntroduceEnumConstantCb.setEnabled(EnumConstantsUtil.isSuitableForEnumConstant(getSelectedType(), myTargetClass));
    updateVisibilityPanel();
  }

  public String getEnteredName() {
    return myNameField.getEnteredName();
  }

  private String getTargetClassName() {
    return myTfTargetClassName.getText().trim();
  }

  public BaseExpressionToFieldHandler.TargetDestination getDestinationClass () {
    return myDestinationClass;
  }

  public boolean introduceEnumConstant() {
    return myIntroduceEnumConstantCb.isEnabled() && myIntroduceEnumConstantCb.isSelected();
  }

  public String getFieldVisibility() {
    return myVPanel.getVisibility();
  }

  public boolean isReplaceAllOccurrences() {
    return myOccurrencesCount > 1 && myCbReplaceAll.isSelected();
  }

  public PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_CONSTANT);
  }

  protected JComponent createNorthPanel() {
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    myTypePanel.setLayout(new BorderLayout());
    myTypePanel.add(myTypeSelector.getComponent(), BorderLayout.CENTER);
    if (myTypeSelector.getFocusableComponent() != null) {
      myTypeLabel.setLabelFor(myTypeSelector.getFocusableComponent());
    }

    myNameField = new NameSuggestionsField(myProject);
    myNameSuggestionPanel.setLayout(new BorderLayout());

    myNameSuggestionPanel.add(myNameField.getComponent(), BorderLayout.CENTER);
    myNameSuggestionLabel.setLabelFor(myNameField.getFocusableComponent());

    Set<String> possibleClassNames = new LinkedHashSet<String>();
    for (final PsiExpression occurrence : myOccurrences) {
      final PsiClass parentClass = new IntroduceConstantHandler().getParentClass(occurrence);
      if (parentClass != null && parentClass.getQualifiedName() != null) {
        possibleClassNames.add(parentClass.getQualifiedName());
      }
    }
    myTfTargetClassName =
      new ReferenceEditorComboWithBrowseButton(new ChooseClassAction(), "", PsiManager.getInstance(myProject), true, RECENTS_KEY);
    myTargetClassNamePanel.setLayout(new BorderLayout());
    myTargetClassNamePanel.add(myTfTargetClassName, BorderLayout.CENTER);
    myTargetClassNameLabel.setLabelFor(myTfTargetClassName);
    for (String possibleClassName : possibleClassNames) {
      myTfTargetClassName.prependItem(possibleClassName);
    }
    myTfTargetClassName.getChildComponent().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        targetClassChanged();
        enableEnumDependant(introduceEnumConstant());
      }
    });
    myIntroduceEnumConstantCb.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        enableEnumDependant(introduceEnumConstant());
      }
    });
    final JPanel enumPanel = new JPanel(new BorderLayout());
    enumPanel.add(myIntroduceEnumConstantCb, BorderLayout.EAST);
    myTargetClassNamePanel.add(enumPanel, BorderLayout.SOUTH);

    final String propertyName;
    if (myLocalVariable != null) {
      propertyName = myCodeStyleManager.variableNameToPropertyName(myLocalVariable.getName(), VariableKind.LOCAL_VARIABLE);
    }
    else {
      propertyName = null;
    }
    final NameSuggestionsManager nameSuggestionsManager =
      new NameSuggestionsManager(myTypeSelector, myNameField, new NameSuggestionsGenerator() {
        public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
          final SuggestedNameInfo nameInfo =
              myCodeStyleManager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, propertyName, myInitializerExpression, type);
          final String[] strings = JavaCompletionUtil.completeVariableNameForRefactoring(myCodeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo);
          return new SuggestedNameInfo.Delegate(strings, nameInfo);
        }

      });

    nameSuggestionsManager.setLabelsFor(myTypeLabel, myNameSuggestionLabel);
    //////////
    if (myOccurrencesCount > 1) {
      myCbReplaceAll.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateTypeSelector();

          myNameField.requestFocusInWindow();
        }
      });
      myCbReplaceAll.setText(RefactoringBundle.message("replace.all.occurences", myOccurrencesCount));
    }
    else {
      myCbReplaceAll.setVisible(false);
    }

    if (myLocalVariable != null) {
      if (myInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      }
      else if (myCbReplaceAll != null) {
        updateCbDeleteVariable();
        myCbReplaceAll.addItemListener(
          new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            updateCbDeleteVariable();
          }
        });
      }
    }
    else {
      myCbDeleteVariable.setVisible(false);
    }

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if ((myTypeSelectorManager.isSuggestedType("java.lang.String") || (myLocalVariable != null && AnnotationUtil.isAnnotated(myLocalVariable, AnnotationUtil.NON_NLS, false)))&&
        LanguageLevelProjectExtension.getInstance(psiManager.getProject()).getLanguageLevel().hasEnumKeywordAndAutoboxing() &&
        JavaPsiFacade.getInstance(psiManager.getProject()).findClass(AnnotationUtil.NON_NLS, myParentClass.getResolveScope()) != null) {
      final PropertiesComponent component = PropertiesComponent.getInstance(myProject);
      myCbNonNls.setSelected(component.isTrueValue(NONNLS_SELECTED_PROPERTY));
      myCbNonNls.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          component.setValue(NONNLS_SELECTED_PROPERTY, Boolean.toString(myCbNonNls.isSelected()));
        }
      });
    } else {
      myCbNonNls.setVisible(false);
    }

    updateTypeSelector();

    enableEnumDependant(introduceEnumConstant());
    return myPanel;
  }

  private void targetClassChanged() {
    final String targetClassName = getTargetClassName();
    myTargetClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
    updateVisibilityPanel();
    myIntroduceEnumConstantCb.setEnabled(EnumConstantsUtil.isSuitableForEnumConstant(getSelectedType(), myTargetClass));
  }

  private void enableEnumDependant(boolean enable) {
    if (enable) {
      myVPanel.disableAllButPublic();
    } else {
      updateVisibilityPanel();
    }
    myCbNonNls.setEnabled(!enable);
  }

  protected JComponent createCenterPanel() {
    return new JPanel();
  }

  public boolean isDeleteVariable() {
    return myInvokedOnDeclaration || myCbDeleteVariable != null && myCbDeleteVariable.isSelected();
  }

  public boolean isAnnotateAsNonNls() {
    return myCbNonNls != null && myCbNonNls.isSelected();
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    }
    else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private void updateTypeSelector() {
    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAll.isSelected());
    }
    else {
      myTypeSelectorManager.setAllOccurences(false);
    }
  }

  private void updateVisibilityPanel() {
    if (myTargetClass != null && myTargetClass.isInterface()) {
      myVPanel.disableAllButPublic();
    }
    else {
      UIUtil.setEnabled(myVisibilityPanel, true, true);
      // exclude all modifiers not visible from all occurences
      final Set<String> visible = new THashSet<String>();
      visible.add(PsiModifier.PRIVATE);
      visible.add(PsiModifier.PROTECTED);
      visible.add(PsiModifier.PACKAGE_LOCAL);
      visible.add(PsiModifier.PUBLIC);
      for (PsiExpression occurrence : myOccurrences) {
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        for (Iterator<String> iterator = visible.iterator(); iterator.hasNext();) {
          String modifier = iterator.next();

          try {
            final String modifierText = PsiModifier.PACKAGE_LOCAL.equals(modifier) ? "" : modifier;
            final PsiField field = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createFieldFromText(modifierText + " int xxx;", myTargetClass);
            if (!JavaResolveUtil.isAccessible(field, myTargetClass, field.getModifierList(), occurrence, myTargetClass, null)) {
              iterator.remove();
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      if (!visible.contains(getFieldVisibility())) {
        if (visible.contains(PsiModifier.PUBLIC)) myVPanel.setVisibility(PsiModifier.PUBLIC);
        if (visible.contains(PsiModifier.PACKAGE_LOCAL)) myVPanel.setVisibility(PsiModifier.PACKAGE_LOCAL);
        if (visible.contains(PsiModifier.PROTECTED)) myVPanel.setVisibility(PsiModifier.PROTECTED);
        if (visible.contains(PsiModifier.PRIVATE)) myVPanel.setVisibility(PsiModifier.PRIVATE);
      }
    }
  }

  protected void doOKAction() {
    final String targetClassName = getTargetClassName();
    PsiClass newClass = myParentClass;

    if (!"".equals (targetClassName) && !Comparing.strEqual(targetClassName, myParentClass.getQualifiedName())) {
      newClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
      if (newClass == null) {
        if (Messages.showOkCancelDialog(myProject, RefactoringBundle.message("class.does.not.exist.in.the.project"), IntroduceConstantHandler.REFACTORING_NAME, Messages.getErrorIcon()) != OK_EXIT_CODE) {
          return;
        }
        myDestinationClass = new BaseExpressionToFieldHandler.TargetDestination(targetClassName, myParentClass);
      } else {
        myDestinationClass = new BaseExpressionToFieldHandler.TargetDestination(newClass);
      }
    }

    String fieldName = getEnteredName();
    String errorString = null;
    if ("".equals(fieldName)) {
      errorString = RefactoringBundle.message("no.field.name.specified");
    } else if (!JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }
    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(
              IntroduceFieldHandler.REFACTORING_NAME,
              errorString,
              HelpID.INTRODUCE_FIELD,
              myProject);
      return;
    }
    if (newClass != null) {
      PsiField oldField = newClass.findFieldByName(fieldName, true);

      if (oldField != null) {
        int answer = Messages.showYesNoDialog(
                myProject,
                RefactoringBundle.message("field.exists", fieldName, oldField.getContainingClass().getQualifiedName()),
                IntroduceFieldHandler.REFACTORING_NAME,
                Messages.getWarningIcon()
        );
        if (answer != 0) {
          return;
        }
      }
    }

    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getFieldVisibility();

    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, targetClassName);
    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(RefactoringBundle.message("choose.destination.class"), GlobalSearchScope.projectScope(myProject), new TreeClassChooser.ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      if (myTargetClass != null) {
        chooser.selectDirectory(myTargetClass.getContainingFile().getContainingDirectory());
      }
      chooser.showDialog();
      PsiClass aClass = chooser.getSelectedClass();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }
}
