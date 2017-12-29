// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;

class IntroduceConstantDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.IntroduceConstantDialog");
  @NonNls private static final String RECENTS_KEY = "IntroduceConstantDialog.RECENTS_KEY";
  @NonNls protected static final String NONNLS_SELECTED_PROPERTY = "INTRODUCE_CONSTANT_NONNLS";

  private final Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myInvokedOnDeclaration;
  private final PsiExpression[] myOccurrences;
  private final String myEnteredName;
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
                          TypeSelectorManager typeSelectorManager, String enteredName) {
    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myInvokedOnDeclaration = isInvokedOnDeclaration;
    myOccurrences = occurrences;
    myEnteredName = enteredName;
    myOccurrencesCount = occurrences.length;
    myTargetClass = targetClass;
    myTypeSelectorManager = typeSelectorManager;
    myDestinationClass = null;

    setTitle(IntroduceConstantHandler.REFACTORING_NAME);
    myCodeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    myVPanel = new JavaVisibilityPanel(false, true);
    myVisibilityPanel.add(myVPanel, BorderLayout.CENTER);
    init();

    String initialVisibility = JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY;
    if (initialVisibility == null) {
      initialVisibility = PsiModifier.PUBLIC;
    }
    myVPanel.setVisibility(initialVisibility);
    myIntroduceEnumConstantCb.setEnabled(isSuitableForEnumConstant());
    updateVisibilityPanel();
    updateButtons();
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

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_CONSTANT);
  }

  @Override
  protected JComponent createNorthPanel() {
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    myTypePanel.setLayout(new BorderLayout());
    myTypePanel.add(myTypeSelector.getComponent(), BorderLayout.CENTER);
    if (myTypeSelector.getFocusableComponent() != null) {
      myTypeLabel.setLabelFor(myTypeSelector.getFocusableComponent());
    }

    myNameField = new NameSuggestionsField(myProject);
    myNameSuggestionPanel.setLayout(new BorderLayout());
    myNameField.addDataChangedListener(() -> updateButtons());
    myNameSuggestionPanel.add(myNameField.getComponent(), BorderLayout.CENTER);
    myNameSuggestionLabel.setLabelFor(myNameField.getFocusableComponent());

    Set<String> possibleClassNames = new LinkedHashSet<>();
    for (final PsiExpression occurrence : myOccurrences) {
      final PsiClass parentClass = new IntroduceConstantHandler().getParentClass(occurrence);
      if (parentClass != null && parentClass.getQualifiedName() != null) {
        possibleClassNames.add(parentClass.getQualifiedName());
      }
    }
    myTfTargetClassName =
      new ReferenceEditorComboWithBrowseButton(new ChooseClassAction(), "", myProject, true, RECENTS_KEY);
    myTargetClassNamePanel.setLayout(new BorderLayout());
    myTargetClassNamePanel.add(myTfTargetClassName, BorderLayout.CENTER);
    myTargetClassNameLabel.setLabelFor(myTfTargetClassName);
    for (String possibleClassName : possibleClassNames) {
      myTfTargetClassName.prependItem(possibleClassName);
    }
    myTfTargetClassName.getChildComponent().setSelectedItem(myParentClass.getQualifiedName());
    myTfTargetClassName.getChildComponent().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        targetClassChanged();
        enableEnumDependant(introduceEnumConstant());
      }
    });
    myIntroduceEnumConstantCb.addActionListener(new ActionListener() {
      @Override
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
      new NameSuggestionsManager(myTypeSelector, myNameField, createNameSuggestionGenerator(propertyName, myInitializerExpression,
                                                                                            myCodeStyleManager, myEnteredName, myParentClass));

    nameSuggestionsManager.setLabelsFor(myTypeLabel, myNameSuggestionLabel);
    //////////
    if (myOccurrencesCount > 1) {
      myCbReplaceAll.addItemListener(new ItemListener() {
        @Override
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
          @Override
          public void itemStateChanged(ItemEvent e) {
            updateCbDeleteVariable();
          }
        });
      }
    }
    else {
      myCbDeleteVariable.setVisible(false);
    }

    if ((myTypeSelectorManager.isSuggestedType(CommonClassNames.JAVA_LANG_STRING) ||
         (myLocalVariable != null && AnnotationUtil.isAnnotated(myLocalVariable, AnnotationUtil.NON_NLS, CHECK_EXTERNAL))) &&
        LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5) &&
        JavaPsiFacade.getInstance(myProject).findClass(AnnotationUtil.NON_NLS, myParentClass.getResolveScope()) != null) {
      final PropertiesComponent component = PropertiesComponent.getInstance(myProject);
      myCbNonNls.setSelected(component.getBoolean(NONNLS_SELECTED_PROPERTY));
      myCbNonNls.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          component.setValue(NONNLS_SELECTED_PROPERTY, myCbNonNls.isSelected());
        }
      });
    } else {
      myCbNonNls.setVisible(false);
    }

    updateTypeSelector();

    enableEnumDependant(introduceEnumConstant());
    return myPanel;
  }

  public void setReplaceAllOccurrences(boolean replaceAllOccurrences) {
    if (myCbReplaceAll != null) {
      myCbReplaceAll.setSelected(replaceAllOccurrences);
    }
  }

  protected static NameSuggestionsGenerator createNameSuggestionGenerator(final String propertyName,
                                                                          final PsiExpression psiExpression,
                                                                          final JavaCodeStyleManager codeStyleManager,
                                                                          final String enteredName, final PsiClass parentClass) {
    return new NameSuggestionsGenerator() {
      @Override
      public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
        SuggestedNameInfo nameInfo =
            codeStyleManager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, propertyName, psiExpression, type);
        if (psiExpression != null) {
          String[] names = nameInfo.names;
          for (int i = 0, namesLength = names.length; i < namesLength; i++) {
            String name = names[i];
            if (parentClass.findFieldByName(name, false) != null) {
              names[i] = codeStyleManager.suggestUniqueVariableName(name, psiExpression, true);
            }
          }
        }
        final String[] strings = AbstractJavaInplaceIntroducer.appendUnresolvedExprName(JavaCompletionUtil
          .completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo), psiExpression);
        return new SuggestedNameInfo.Delegate(enteredName != null ? ArrayUtil.mergeArrays(new String[]{enteredName}, strings): strings, nameInfo);
      }

    };
  }

  private void updateButtons() {
    setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(getEnteredName()));
  }

  private void targetClassChanged() {
    final String targetClassName = getTargetClassName();
    myTargetClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
    updateVisibilityPanel();
    myIntroduceEnumConstantCb.setEnabled(isSuitableForEnumConstant());
  }

  private boolean isSuitableForEnumConstant() {
    return EnumConstantsUtil.isSuitableForEnumConstant(getSelectedType(), myTargetClass) && PsiTreeUtil
                                                                                              .getParentOfType(myInitializerExpression,
                                                                                                               PsiEnumConstant.class) == null;
  }

  private void enableEnumDependant(boolean enable) {
    if (enable) {
      myVPanel.disableAllButPublic();
    } else {
      updateVisibilityPanel();
    }
    myCbNonNls.setEnabled(!enable);
  }

  @Override
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
      myTypeSelectorManager.setAllOccurrences(myCbReplaceAll.isSelected());
    }
    else {
      myTypeSelectorManager.setAllOccurrences(false);
    }
  }

  private void updateVisibilityPanel() {
    if (myTargetClass != null && myTargetClass.isInterface()) {
      myVPanel.disableAllButPublic();
    }
    else {
      UIUtil.setEnabled(myVisibilityPanel, true, true);
      // exclude all modifiers not visible from all occurrences
      String effectiveVisibility = getEffectiveVisibility(getFieldVisibility(), myOccurrences, myTargetClass, myProject);
      if (effectiveVisibility != null) {
        myVPanel.setVisibility(effectiveVisibility);
      }
    }
  }

  public static String getEffectiveVisibility(String initialVisibility,
                                              PsiExpression[] occurrences,
                                              PsiClass targetClass,
                                              Project project) {
    final ArrayList<String> visible = new ArrayList<>();
    visible.add(PsiModifier.PRIVATE);
    visible.add(PsiModifier.PROTECTED);
    visible.add(PsiModifier.PACKAGE_LOCAL);
    visible.add(PsiModifier.PUBLIC);
    for (PsiExpression occurrence : occurrences) {
      final PsiManager psiManager = PsiManager.getInstance(project);
      for (Iterator<String> iterator = visible.iterator(); iterator.hasNext();) {
        String modifier = iterator.next();

        try {
          final String modifierText = PsiModifier.PACKAGE_LOCAL.equals(modifier) ? "" : modifier + " ";
          final PsiField field = JavaPsiFacade
            .getInstance(psiManager.getProject()).getElementFactory().createFieldFromText(modifierText + "int xxx;", targetClass);
          if (!JavaResolveUtil.isAccessible(field, targetClass, field.getModifierList(), occurrence, targetClass, null)) {
            iterator.remove();
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    if (!visible.contains(initialVisibility) && !visible.isEmpty()) {
      return visible.get(0);
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    final String targetClassName = getTargetClassName();
    PsiClass newClass = myParentClass;

    if (!targetClassName.isEmpty() && !Comparing.strEqual(targetClassName, myParentClass.getQualifiedName())) {
      newClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
      if (newClass == null) {
        if (Messages.showOkCancelDialog(myProject, RefactoringBundle.message("class.does.not.exist.in.the.project"), IntroduceConstantHandler.REFACTORING_NAME, Messages.getErrorIcon()) != Messages.OK) {
          return;
        }
        myDestinationClass = new BaseExpressionToFieldHandler.TargetDestination(targetClassName, myParentClass);
      } else {
        myDestinationClass = new BaseExpressionToFieldHandler.TargetDestination(newClass);
      }
    }

    String fieldName = getEnteredName();
    String errorString = null;
    if (fieldName != null && fieldName.isEmpty()) {
      errorString = RefactoringBundle.message("no.field.name.specified");
    } else if (!PsiNameHelper.getInstance(myProject).isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    } else if (newClass != null && !myParentClass.getLanguage().equals(newClass.getLanguage())) {
      errorString = RefactoringBundle.message("move.to.different.language", UsageViewUtil.getType(myParentClass),
                                              myParentClass.getQualifiedName(), newClass.getQualifiedName());
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
        if (answer != Messages.YES) {
          return;
        }
      }
    }

    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getFieldVisibility();

    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, targetClassName);
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  private class ChooseClassAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(RefactoringBundle.message("choose.destination.class"), GlobalSearchScope.projectScope(myProject), new ClassFilter() {
        @Override
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      if (myTargetClass != null) {
        chooser.selectDirectory(myTargetClass.getContainingFile().getContainingDirectory());
      }
      chooser.showDialog();
      PsiClass aClass = chooser.getSelected();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }
}
