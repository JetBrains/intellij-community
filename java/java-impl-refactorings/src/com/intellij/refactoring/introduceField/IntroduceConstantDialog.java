// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.ui.NameSuggestionsManager;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.JavaNameSuggestionUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.ResourceBundle;
import java.util.Set;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;

class IntroduceConstantDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(IntroduceConstantDialog.class);
  private static final @NonNls String RECENTS_KEY = "IntroduceConstantDialog.RECENTS_KEY";
  protected static final @NonNls String NONNLS_SELECTED_PROPERTY = "INTRODUCE_CONSTANT_NONNLS";

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
  private NameSuggestionsManager myNameSuggestionsManager;

  private NameSuggestionsField myNameField;
  private final JCheckBox myCbReplaceAll;

  private TypeSelector myTypeSelector;
  private final StateRestoringCheckBox myCbDeleteVariable;
  private final JavaCodeStyleManager myCodeStyleManager;
  private ReferenceEditorComboWithBrowseButton myTfTargetClassName;
  private BaseExpressionToFieldHandler.TargetDestination myDestinationClass;
  private final JPanel myTypePanel;
  private final JPanel myTargetClassNamePanel;
  private final JPanel myPanel;
  private final JLabel myTypeLabel;
  private final JPanel myNameSuggestionPanel;
  private final JLabel myNameSuggestionLabel;
  private final JLabel myTargetClassNameLabel;
  private final JCheckBox myCbNonNls;
  private final JPanel myVisibilityPanel;
  private final JavaVisibilityPanel myVPanel;
  private final JCheckBox myIntroduceEnumConstantCb = new JCheckBox(JavaRefactoringBundle.message("introduce.constant.enum.cb"), true);

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
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(8, 1, new Insets(0, 0, 0, 0), -1, -1));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                              0, false));
      myTypeLabel = new JLabel();
      this.$$$loadLabelText$$$(myTypeLabel,
                               this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "introduce.constant.field.of.type"));
      panel1.add(myTypeLabel,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myTypePanel = new JPanel();
      panel1.add(myTypePanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myNameSuggestionLabel = new JLabel();
      this.$$$loadLabelText$$$(myNameSuggestionLabel, this.$$$getMessageFromBundle$$$("messages/RefactoringBundle", "name.prompt"));
      panel2.add(myNameSuggestionLabel,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myNameSuggestionPanel = new JPanel();
      panel2.add(myNameSuggestionPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myTargetClassNameLabel = new JLabel();
      this.$$$loadLabelText$$$(myTargetClassNameLabel,
                               this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "introduce.constant.introduce.to.class"));
      myPanel.add(myTargetClassNameLabel,
                  new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myTargetClassNamePanel = new JPanel();
      myPanel.add(myTargetClassNamePanel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myCbReplaceAll = new JCheckBox();
      myCbReplaceAll.setFocusable(false);
      myCbReplaceAll.setSelected(true);
      this.$$$loadButtonText$$$(myCbReplaceAll,
                                this.$$$getMessageFromBundle$$$("messages/RefactoringBundle", "replace.all.occurences.checkbox"));
      myPanel.add(myCbReplaceAll, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myCbDeleteVariable = new StateRestoringCheckBox();
      this.$$$loadButtonText$$$(myCbDeleteVariable,
                                this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "delete.variable.declaration"));
      myPanel.add(myCbDeleteVariable, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myCbNonNls = new JCheckBox();
      myCbNonNls.setSelected(false);
      this.$$$loadButtonText$$$(myCbNonNls,
                                this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "annotate.field.as.nonnls.checkbox"));
      myPanel.add(myCbNonNls, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myVisibilityPanel = new JPanel();
      myVisibilityPanel.setLayout(new BorderLayout(0, 0));
      myPanel.add(myVisibilityPanel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                         null, null, 0, false));
    }
    myOccurrencesCount = occurrences.length;
    myTargetClass = targetClass;
    myTypeSelectorManager = typeSelectorManager;
    myDestinationClass = null;

    setTitle(IntroduceConstantHandler.getRefactoringNameText());
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

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return myPanel; }

  public String getEnteredName() {
    return myNameField.getEnteredName();
  }

  private String getTargetClassName() {
    return myTfTargetClassName.getText().trim();
  }

  public BaseExpressionToFieldHandler.TargetDestination getDestinationClass() {
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
  protected String getHelpId() {
    return HelpID.INTRODUCE_CONSTANT;
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
      new ReferenceEditorComboWithBrowseButton(new ChooseClassAction(), "", myProject, true,
                                               JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE, RECENTS_KEY);
    myTargetClassNamePanel.setLayout(new BorderLayout());
    myTargetClassNamePanel.add(myTfTargetClassName, BorderLayout.CENTER);
    myTargetClassNameLabel.setLabelFor(myTfTargetClassName);
    for (String possibleClassName : possibleClassNames) {
      myTfTargetClassName.prependItem(possibleClassName);
    }
    myTfTargetClassName.getChildComponent().setSelectedItem(myParentClass.getQualifiedName());
    myTfTargetClassName.getChildComponent().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
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
    myNameSuggestionsManager =
      new NameSuggestionsManager(myTypeSelector, myNameField, createNameSuggestionGenerator(propertyName, myInitializerExpression,
                                                                                            myCodeStyleManager, myEnteredName,
                                                                                            myParentClass));

    myNameSuggestionsManager.setLabelsFor(myTypeLabel, myNameSuggestionLabel);
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
      myCbReplaceAll.setSelected(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_REPLACE_ALL);
    }
    else {
      myCbReplaceAll.setVisible(false);
    }

    if (myLocalVariable != null) {
      if (myInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      }
      else {
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
        JavaFeature.ANNOTATIONS.isSufficient(LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel()) &&
        JavaPsiFacade.getInstance(myProject).findClass(AnnotationUtil.NON_NLS, myParentClass.getResolveScope()) != null) {
      final PropertiesComponent component = PropertiesComponent.getInstance(myProject);
      myCbNonNls.setSelected(component.getBoolean(NONNLS_SELECTED_PROPERTY));
      myCbNonNls.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          component.setValue(NONNLS_SELECTED_PROPERTY, myCbNonNls.isSelected());
        }
      });
    }
    else {
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
          PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(psiExpression.getProject()).getResolveHelper();
          for (int i = 0, namesLength = names.length; i < namesLength; i++) {
            String name = names[i];
            if (resolveHelper.resolveAccessibleReferencedVariable(name, parentClass) != null) {
              names[i] = codeStyleManager.suggestUniqueVariableName(name, psiExpression, true);
            }
          }
        }
        final String[] strings = JavaNameSuggestionUtil.appendUnresolvedExprName(JavaCompletionUtil
                                                                                   .completeVariableNameForRefactoring(codeStyleManager,
                                                                                                                       type,
                                                                                                                       VariableKind.LOCAL_VARIABLE,
                                                                                                                       nameInfo),
                                                                                 psiExpression);
        return new SuggestedNameInfo.Delegate(enteredName != null ? ArrayUtil.mergeArrays(new String[]{enteredName}, strings) : strings,
                                              nameInfo);
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
                                                                                                               PsiEnumConstant.class) ==
                                                                                            null;
  }

  private void enableEnumDependant(boolean enable) {
    if (enable) {
      myVPanel.disableAllButPublic();
    }
    else {
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
      for (Iterator<String> iterator = visible.iterator(); iterator.hasNext(); ) {
        String modifier = iterator.next();

        try {
          final String modifierText = PsiModifier.PACKAGE_LOCAL.equals(modifier) ? "" : modifier + " ";
          final PsiField field =
            JavaPsiFacade.getElementFactory(psiManager.getProject()).createFieldFromText(modifierText + "int xxx;", targetClass);
          if (!JavaResolveUtil.isAccessible(field, targetClass, field.getModifierList(), occurrence, targetClass, null)) {
            iterator.remove();
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    if (!visible.isEmpty() && !visible.contains(initialVisibility)) {
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
        if (Messages.showOkCancelDialog(myProject, JavaRefactoringBundle.message("class.does.not.exist.in.the.project"),
                                        IntroduceConstantHandler.getRefactoringNameText(), Messages.getErrorIcon()) != Messages.OK) {
          return;
        }
        myDestinationClass = new BaseExpressionToFieldHandler.TargetDestination(targetClassName, myParentClass);
      }
      else {
        myDestinationClass = new BaseExpressionToFieldHandler.TargetDestination(newClass);
      }
    }

    String fieldName = getEnteredName();
    String errorString = null;
    if (fieldName != null && fieldName.isEmpty()) {
      errorString = RefactoringBundle.message("no.field.name.specified");
    }
    else if (!PsiNameHelper.getInstance(myProject).isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }
    else if (newClass != null && !myParentClass.getLanguage().equals(newClass.getLanguage())) {
      errorString = RefactoringBundle.message("move.to.different.language", UsageViewUtil.getType(myParentClass),
                                              myParentClass.getQualifiedName(), newClass.getQualifiedName());
    }
    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(
        IntroduceFieldHandler.getRefactoringNameText(),
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
          IntroduceFieldHandler.getRefactoringNameText(),
          Messages.getWarningIcon()
        );
        if (answer != Messages.YES) {
          return;
        }
      }
    }

    JavaRefactoringSettings javaRefactoringSettings = JavaRefactoringSettings.getInstance();
    javaRefactoringSettings.INTRODUCE_CONSTANT_VISIBILITY = getFieldVisibility();
    if (myOccurrencesCount > 1) {
      javaRefactoringSettings.INTRODUCE_CONSTANT_REPLACE_ALL = isReplaceAllOccurrences();
    }

    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, targetClassName);
    myNameSuggestionsManager.nameSelected();
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  private class ChooseClassAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(
        RefactoringBundle.message("choose.destination.class"), GlobalSearchScope.projectScope(myProject),
        aClass -> aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC), null);
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
