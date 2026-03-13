// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.ide.util.TreeJavaClassChooserDialog;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.introduceParameterObject.AbstractIntroduceParameterObjectDialog;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class IntroduceParameterObjectDialog
  extends AbstractIntroduceParameterObjectDialog<PsiMethod, ParameterInfoImpl, JavaIntroduceParameterObjectClassDescriptor, VariableData> {


  private final JRadioButton useExistingClassButton;
  private final JPanel myUseExistingPanel;

  private final JRadioButton createNewClassButton;
  private final JTextField classNameField;

  private final JPanel myCreateNewClassPanel;

  private final JRadioButton myCreateInnerClassRadioButton;
  private final JTextField myInnerClassNameTextField;
  private final JPanel myInnerClassPanel;

  private final JPanel myWholePanel;
  private final ReferenceEditorComboWithBrowseButton packageTextField;
  private final ReferenceEditorComboWithBrowseButton existingClassField;
  private final JCheckBox myGenerateAccessorsCheckBox;
  private final JCheckBox myEscalateVisibilityCheckBox;
  private final ComboboxWithBrowseButton myDestinationCb;
  private static final String RECENTS_KEY = "IntroduceParameterObject.RECENTS_KEY";
  private static final String EXISTING_KEY = "IntroduceParameterObject.EXISTING_KEY";

  public IntroduceParameterObjectDialog(PsiMethod sourceMethod) {
    super(sourceMethod);
    {
      final PsiFile file = mySourceMethod.getContainingFile();
      packageTextField =
        new PackageNameReferenceEditorCombo(file instanceof PsiJavaFile ? ((PsiJavaFile)file).getPackageName() : "", myProject, RECENTS_KEY,
                                            RefactoringBundle.message("choose.destination.package"));
      final Document document = packageTextField.getChildComponent().getDocument();
      final com.intellij.openapi.editor.event.DocumentListener adapter = new com.intellij.openapi.editor.event.DocumentListener() {
        @Override
        public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent e) {
          validateButtons();
        }
      };
      document.addDocumentListener(adapter);

      existingClassField = new ReferenceEditorComboWithBrowseButton(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final Project project = mySourceMethod.getProject();
          final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
          final TreeJavaClassChooserDialog chooser =
            new TreeJavaClassChooserDialog(RefactorJBundle.message("select.wrapper.class"), project, scope, null, null);
          final String classText = existingClassField.getText();
          final PsiClass currentClass = JavaPsiFacade.getInstance(project).findClass(classText, GlobalSearchScope.allScope(project));
          if (currentClass != null) {
            chooser.select(currentClass);
          }
          chooser.show();
          final PsiClass selectedClass = chooser.getSelected();
          if (selectedClass != null) {
            final String className = selectedClass.getQualifiedName();
            existingClassField.setText(className);
            RecentsManager.getInstance(myProject).registerRecentEntry(EXISTING_KEY, className);
          }
        }
      }, "", myProject, true, EXISTING_KEY);

      existingClassField.getChildComponent().getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
        @Override
        public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent e) {
          validateButtons();
          enableGenerateAccessors();
        }
      });
      myDestinationCb = new DestinationFolderComboBox() {
        @Override
        public String getTargetPackage() {
          return getPackageName();
        }
      };
      ((DestinationFolderComboBox)myDestinationCb).setData(myProject, mySourceMethod.getContainingFile().getContainingDirectory(),
                                                           packageTextField.getChildComponent());
    }
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myWholePanel = new JPanel();
      myWholePanel.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1));
      myWholePanel.putClientProperty("BorderFactoryClass", "");
      createNewClassButton = new JRadioButton();
      this.$$$loadButtonText$$$(createNewClassButton, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                                      "introduce.parameter.object.create.new.class"));
      myWholePanel.add(createNewClassButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
      myCreateNewClassPanel = new JPanel();
      myCreateNewClassPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 20, 0, 0), -1, -1));
      myWholePanel.add(myCreateNewClassPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                       "introduce.parameter.object.new.class.name"));
      myCreateNewClassPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                            null, 0, false));
      classNameField = new JTextField();
      myCreateNewClassPanel.add(classNameField,
                                new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                    new Dimension(150, -1), null, 0, false));
      final JLabel label2 = new JLabel();
      this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                       "introduce.parameter.object.new.class.package.name"));
      myCreateNewClassPanel.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                            null, 0, false));
      myCreateNewClassPanel.add(packageTextField,
                                new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
      final JLabel label3 = new JLabel();
      this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/RefactoringBundle", "target.destination.folder"));
      myCreateNewClassPanel.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                            null, 0, false));
      myCreateNewClassPanel.add(myDestinationCb,
                                new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
      myUseExistingPanel = new JPanel();
      myUseExistingPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 20, 0, 0), -1, -1));
      myWholePanel.add(myUseExistingPanel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, false));
      final JLabel label4 = new JLabel();
      this.$$$loadLabelText$$$(label4, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                       "introduce.parameter.object.existing.class.name"));
      myUseExistingPanel.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
      myUseExistingPanel.add(existingClassField,
                             new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 0, false));
      myGenerateAccessorsCheckBox = new JCheckBox();
      this.$$$loadButtonText$$$(myGenerateAccessorsCheckBox, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                                             "introduce.parameter.object.generate.accessors.option"));
      myUseExistingPanel.add(myGenerateAccessorsCheckBox,
                             new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myEscalateVisibilityCheckBox = new JCheckBox();
      this.$$$loadButtonText$$$(myEscalateVisibilityCheckBox, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                                              "introduce.parameter.object.escalate.visibility.option"));
      myUseExistingPanel.add(myEscalateVisibilityCheckBox,
                             new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myCreateInnerClassRadioButton = new JRadioButton();
      this.$$$loadButtonText$$$(myCreateInnerClassRadioButton, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                                               "introduce.parameter.object.create.inner.class"));
      myWholePanel.add(myCreateInnerClassRadioButton,
                       new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myInnerClassPanel = new JPanel();
      myInnerClassPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 20, 0, 0), -1, -1));
      myWholePanel.add(myInnerClassPanel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              null, null, null, 0, false));
      final JLabel label5 = new JLabel();
      this.$$$loadLabelText$$$(label5, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                       "introduce.parameter.object.inner.class.name"));
      myInnerClassPanel.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                        null, 0, false));
      myInnerClassNameTextField = new JTextField();
      myInnerClassPanel.add(myInnerClassNameTextField,
                            new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                new Dimension(150, -1), null, 0, false));
      useExistingClassButton = new JRadioButton();
      this.$$$loadButtonText$$$(useExistingClassButton, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                                        "introduce.parameter.object.use.existing.class"));
      myWholePanel.add(useExistingClassButton, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                   null, null, null, 0, false));
      label1.setLabelFor(classNameField);
      label3.setLabelFor(myDestinationCb);
      label5.setLabelFor(myInnerClassNameTextField);
    }
    final DocumentListener docListener = new DocumentAdapter() {
      @Override
      protected void textChanged(final @NotNull DocumentEvent e) {
        validateButtons();
      }
    };
    final PsiClass containingClass = sourceMethod.getContainingClass();
    classNameField.getDocument().addDocumentListener(docListener);
    myInnerClassNameTextField.getDocument().addDocumentListener(docListener);

    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(useExistingClassButton);
    buttonGroup.add(createNewClassButton);
    buttonGroup.add(myCreateInnerClassRadioButton);
    createNewClassButton.setSelected(true);
    if (containingClass == null ||
        containingClass.getQualifiedName() == null ||
        containingClass.getContainingClass() != null ||
        containingClass.isInterface()) {
      myCreateInnerClassRadioButton.setEnabled(false);
    }
    init();

    final ActionListener listener = actionEvent -> {
      toggleRadioEnablement();
      final IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
      if (useExistingClass()) {
        focusManager.requestFocus(existingClassField, true);
      }
      else if (myCreateInnerClassRadioButton.isSelected()) {
        focusManager.requestFocus(myInnerClassNameTextField, true);
      }
      else {
        focusManager.requestFocus(classNameField, true);
      }
    };
    useExistingClassButton.addActionListener(listener);
    createNewClassButton.addActionListener(listener);
    myCreateInnerClassRadioButton.addActionListener(listener);
    myGenerateAccessorsCheckBox.setSelected(true);
    myEscalateVisibilityCheckBox.setSelected(true);
    toggleRadioEnablement();
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
  public JComponent $$$getRootComponent$$$() { return myWholePanel; }

  @Override
  protected boolean isDelegateCheckboxVisible() {
    final PsiClass containingClass = mySourceMethod.getContainingClass();
    return containingClass != null && !containingClass.isInterface();
  }

  private void toggleRadioEnablement() {
    UIUtil.setEnabled(myUseExistingPanel, useExistingClassButton.isSelected(), true);
    UIUtil.setEnabled(myCreateNewClassPanel, createNewClassButton.isSelected(), true);
    UIUtil.setEnabled(myInnerClassPanel, myCreateInnerClassRadioButton.isSelected(), true);
    validateButtons();
    enableGenerateAccessors();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "RefactorJ.IntroduceParameterObject";
  }

  @Override
  protected String getSourceMethodPresentation() {
    return PsiFormatUtil.formatMethod(mySourceMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                            PsiFormatUtilBase.SHOW_NAME, 0);
  }

  @Override
  protected ParameterTablePanel createParametersPanel() {
    final PsiParameterList parameterList = mySourceMethod.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    VariableData[] parameterInfo = new VariableData[parameters.length];
    for (int i = 0; i < parameterInfo.length; i++) {
      parameterInfo[i] = new VariableData(parameters[i]);
      parameterInfo[i].name = parameters[i].getName();
      parameterInfo[i].passAsParameter = true;
    }
    return new ParameterTablePanel(myProject, parameterInfo) {
      @Override
      protected void updateSignature() { }

      @Override
      protected void doEnterAction() { }

      @Override
      protected void doCancelAction() {
        IntroduceParameterObjectDialog.this.doCancelAction();
      }
    };
  }

  @Override
  protected JPanel createParameterClassPanel() {
    return myWholePanel;
  }

  @Override
  protected JavaIntroduceParameterObjectClassDescriptor createClassDescriptor() {
    final boolean useExistingClass = useExistingClass();
    final String className;
    final String packageName;
    final boolean createInnerClass = myCreateInnerClassRadioButton.isSelected();
    if (createInnerClass) {
      className = getInnerClassName();
      packageName = "";
    }
    else if (useExistingClass) {
      final String existingClassName = getExistingClassName();
      className = StringUtil.getShortName(existingClassName);
      packageName = StringUtil.getPackageName(existingClassName);
    }
    else {
      packageName = getPackageName();
      className = getClassName();
    }

    final String newVisibility =
      myEscalateVisibilityCheckBox.isEnabled() && myEscalateVisibilityCheckBox.isSelected() ? VisibilityUtil.ESCALATE_VISIBILITY : null;
    final MoveDestination moveDestination = ((DestinationFolderComboBox)myDestinationCb)
      .selectDirectory(new PackageWrapper(PsiManager.getInstance(myProject), packageName), false);
    final PsiParameterList parameterList = mySourceMethod.getParameterList();
    final List<ParameterInfoImpl> parameters = new ArrayList<>();
    for (VariableData data : myParameterTablePanel.getVariableData()) {
      if (data.passAsParameter) {
        int oldParameterIndex = parameterList.getParameterIndex((PsiParameter)data.variable);
        parameters.add(ParameterInfoImpl.create(oldParameterIndex).withName(data.name).withType(data.type));
      }
    }
    final ParameterInfoImpl[] infos = parameters.toArray(new ParameterInfoImpl[0]);
    return new JavaIntroduceParameterObjectClassDescriptor(className, packageName, moveDestination, useExistingClass, createInnerClass,
                                                           newVisibility, infos, mySourceMethod,
                                                           myGenerateAccessorsCheckBox.isSelected());
  }

  @Override
  protected void canRun() throws ConfigurationException {
    super.canRun();
    final Project project = mySourceMethod.getProject();
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
    if (myCreateInnerClassRadioButton.isSelected()) {
      final String innerClassName = getInnerClassName();
      if (!nameHelper.isIdentifier(innerClassName)) {
        throw new ConfigurationException(
          JavaRefactoringBundle.message("introduce.parameter.object.error.invalid.inner.class.name", innerClassName));
      }
      if (mySourceMethod.getContainingClass().findInnerClassByName(innerClassName, false) != null) {
        throw new ConfigurationException(
          JavaRefactoringBundle.message("introduce.parameter.object.error.inner.class.already.exist", innerClassName));
      }
    }
    else if (!useExistingClass()) {
      final String className = getClassName();
      if (className.isEmpty() || !nameHelper.isIdentifier(className)) {
        throw new ConfigurationException(
          JavaRefactoringBundle.message("introduce.parameter.object.error.invalid.parameter.class.name", className));
      }
      final String packageName = getPackageName();

      if (packageName.isEmpty() || !nameHelper.isQualifiedName(packageName)) {
        throw new ConfigurationException(
          JavaRefactoringBundle.message("introduce.parameter.object.error.invalid.parameter.class.package.name", packageName));
      }
    }
    else {
      final String className = getExistingClassName();
      if (className.isEmpty() || !nameHelper.isQualifiedName(className)) {
        throw new ConfigurationException(
          JavaRefactoringBundle.message("introduce.parameter.object.error.invalid.qualified.parameter.class.name", className));
      }
      if (JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject())) == null) {
        throw new ConfigurationException(JavaRefactoringBundle.message("introduce.parameter.object.error.class.does.not.exist", className));
      }
    }
  }

  private @NotNull String getInnerClassName() {
    return myInnerClassNameTextField.getText().trim();
  }

  public @NotNull String getPackageName() {
    return packageTextField.getText().trim();
  }

  public @NotNull String getExistingClassName() {
    return existingClassField.getText().trim();
  }

  public @NotNull String getClassName() {
    return classNameField.getText().trim();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return classNameField;
  }

  @Override
  protected String getHelpId() {
    return HelpID.IntroduceParameterObject;
  }

  public boolean useExistingClass() {
    return useExistingClassButton.isSelected();
  }

  private void enableGenerateAccessors() {
    boolean existingNotALibraryClass = false;
    if (useExistingClassButton.isSelected()) {
      final PsiClass selectedClass =
        JavaPsiFacade.getInstance(myProject).findClass(existingClassField.getText(), GlobalSearchScope.projectScope(myProject));
      if (selectedClass != null) {
        final PsiFile containingFile = selectedClass.getContainingFile();
        if (containingFile != null) {
          final VirtualFile virtualFile = containingFile.getVirtualFile();
          if (virtualFile != null) {
            existingNotALibraryClass = ProjectRootManager.getInstance(myProject).getFileIndex()
              .isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.SOURCES);
          }
        }
      }
    }
    myGenerateAccessorsCheckBox.setEnabled(existingNotALibraryClass);
    myEscalateVisibilityCheckBox.setEnabled(existingNotALibraryClass);
  }
}
