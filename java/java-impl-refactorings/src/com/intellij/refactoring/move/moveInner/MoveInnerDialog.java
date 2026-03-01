// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveInner;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveDialogBase;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.CommonMoveClassesOrPackagesUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.RecentsManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

public class MoveInnerDialog extends MoveDialogBase {
  private final PsiClass myInnerClass;
  private final PsiElement myTargetContainer;
  private final MoveInnerProcessor myProcessor;

  private final EditorTextField myClassNameField;
  private final NameSuggestionsField myParameterField;
  private final JCheckBox myCbPassOuterClass;
  private final JPanel myPanel;
  private final JCheckBox myCbSearchInComments;
  private final JCheckBox myCbSearchForTextOccurences;
  private final PackageNameReferenceEditorCombo myPackageNameField;
  private final JLabel myPackageNameLabel;
  private final JLabel myClassNameLabel;
  private final JLabel myParameterNameLabel;
  private SuggestedNameInfo mySuggestedNameInfo;
  private final PsiClass myOuterClass;

  private static final @NonNls String RECENTS_KEY = "MoveInnerDialog.RECENTS_KEY";

  @Override
  protected @NotNull String getRefactoringId() {
    return "MoveInner";
  }

  public MoveInnerDialog(Project project, PsiClass innerClass, MoveInnerProcessor processor, final PsiElement targetContainer) {
    super(project, true, true);
    myInnerClass = innerClass;
    myTargetContainer = targetContainer;
    {
      if (!myInnerClass.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiManager manager = myInnerClass.getManager();
        PsiType outerType = JavaPsiFacade.getElementFactory(manager.getProject()).createType(myInnerClass.getContainingClass());
        mySuggestedNameInfo =
          JavaCodeStyleManager.getInstance(myProject).suggestVariableName(VariableKind.PARAMETER, null, null, outerType);
        String[] variants = mySuggestedNameInfo.names;
        myParameterField = new NameSuggestionsField(variants, myProject, JavaFileType.INSTANCE);
      }
      else {
        myParameterField = new NameSuggestionsField(new String[]{""}, myProject, JavaFileType.INSTANCE);
        myParameterField.getComponent().setEnabled(false);
      }

      PsiPackage psiPackage = getTargetPackage();
      myPackageNameField =
        new PackageNameReferenceEditorCombo(psiPackage != null ? psiPackage.getQualifiedName() : "", myProject, RECENTS_KEY,
                                            RefactoringBundle.message("choose.destination.package"));
    }
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(9, 2, new Insets(0, 0, 0, 0), -1, -1));
      myClassNameLabel = new JLabel();
      this.$$$loadLabelText$$$(myClassNameLabel, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "class.name.prompt"));
      myPanel.add(myClassNameLabel,
                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer1 = new Spacer();
      myPanel.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      final Spacer spacer2 = new Spacer();
      myPanel.add(spacer2, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      myClassNameField = new EditorTextField();
      myPanel.add(myClassNameField, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
      myCbPassOuterClass = new NonFocusableCheckBox();
      this.$$$loadButtonText$$$(myCbPassOuterClass, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                                    "pass.outer.class.instance.as.parameter"));
      myPanel.add(myCbPassOuterClass, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
      myParameterNameLabel = new JLabel();
      this.$$$loadLabelText$$$(myParameterNameLabel,
                               this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "parameter.name.prompt"));
      myPanel.add(myParameterNameLabel,
                  new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
      myPanel.add(myParameterField, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 1, false));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
      myPanel.add(panel1, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                              0, false));
      myCbSearchInComments = new NonFocusableCheckBox();
      this.$$$loadButtonText$$$(myCbSearchInComments,
                                this.$$$getMessageFromBundle$$$("messages/RefactoringBundle", "search.in.comments.and.strings"));
      panel1.add(myCbSearchInComments);
      myCbSearchForTextOccurences = new NonFocusableCheckBox();
      this.$$$loadButtonText$$$(myCbSearchForTextOccurences,
                                this.$$$getMessageFromBundle$$$("messages/RefactoringBundle", "search.for.text.occurrences"));
      panel1.add(myCbSearchForTextOccurences);
      myPackageNameLabel = new JLabel();
      this.$$$loadLabelText$$$(myPackageNameLabel,
                               this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "package.name.prompt"));
      myPanel.add(myPackageNameLabel,
                  new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myPanel.add(myPackageNameField, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
    }
    myOuterClass = myInnerClass.getContainingClass();
    myProcessor = processor;
    setTitle(MoveInnerImpl.getRefactoringName());
    init();
    myPackageNameLabel.setLabelFor(myPackageNameField.getChildComponent());
    myClassNameLabel.setLabelFor(myClassNameField);
    myParameterNameLabel.setLabelFor(myParameterField);
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

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  public boolean isSearchInNonJavaFiles() {
    return myCbSearchForTextOccurences.isSelected();
  }

  public @NotNull String getClassName() {
    return myClassNameField.getText().trim();
  }

  public @Nullable String getParameterName() {
    if (myParameterField != null) {
      return myParameterField.getEnteredName();
    }
    else {
      return null;
    }
  }

  public boolean isPassOuterClass() {
    return myCbPassOuterClass.isSelected();
  }

  public @NotNull PsiClass getInnerClass() {
    return myInnerClass;
  }

  @Override
  protected void init() {
    myClassNameField.setText(myInnerClass.getName());
    myClassNameField.selectAll();

    if (!myInnerClass.hasModifierProperty(PsiModifier.STATIC)) {
      myCbPassOuterClass.setSelected(true);
      myCbPassOuterClass.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          myParameterField.setEnabled(myCbPassOuterClass.isSelected());
        }
      });
    }
    else {
      myCbPassOuterClass.setSelected(false);
      myCbPassOuterClass.setEnabled(false);
      myParameterField.setEnabled(false);
    }

    if (myCbPassOuterClass.isEnabled()) {
      boolean thisNeeded = isThisNeeded(myInnerClass, myOuterClass);
      myCbPassOuterClass.setSelected(thisNeeded);
      myParameterField.setEnabled(thisNeeded);
    }

    myCbPassOuterClass.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        boolean selected = myCbPassOuterClass.isSelected();
        myParameterField.getComponent().setEnabled(selected);
      }
    });

    if (!(myTargetContainer instanceof PsiDirectory)) {
      myPackageNameField.setVisible(false);
      myPackageNameLabel.setVisible(false);
    }

    super.init();
  }

  public static boolean isThisNeeded(final PsiClass innerClass, final PsiClass outerClass) {
    final Map<PsiClass, Set<PsiMember>> classesToMembers = MoveInstanceMembersUtil.getThisClassesToMembers(innerClass);
    for (PsiClass psiClass : classesToMembers.keySet()) {
      if (InheritanceUtil.isInheritorOrSelf(outerClass, psiClass, true)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myClassNameField;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.move.moveInner.MoveInnerDialog";
  }

  @Override
  protected JComponent createNorthPanel() {
    return myPanel;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  private @Nullable PsiElement getTargetContainer() {
    if (myTargetContainer instanceof PsiDirectory psiDirectory) {
      PsiPackage oldPackage = getTargetPackage();
      String name = oldPackage == null ? "" : oldPackage.getQualifiedName();
      final String targetName = getPackageName();
      if (!Objects.equals(name, targetName)) {
        final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
        final List<VirtualFile> contentSourceRoots = JavaProjectRootsUtil.getSuitableDestinationSourceRoots(myProject);
        final PackageWrapper newPackage = new PackageWrapper(PsiManager.getInstance(myProject), targetName);
        final VirtualFile targetSourceRoot;
        if (contentSourceRoots.size() > 1) {
          PsiPackage targetPackage = JavaPsiFacade.getInstance(myProject).findPackage(targetName);
          PsiDirectory initialDir = null;
          if (targetPackage != null) {
            final PsiDirectory[] directories = targetPackage.getDirectories();
            final VirtualFile root = projectRootManager.getFileIndex().getSourceRootForFile(psiDirectory.getVirtualFile());
            for (PsiDirectory dir : directories) {
              if (Comparing.equal(projectRootManager.getFileIndex().getSourceRootForFile(dir.getVirtualFile()), root)) {
                initialDir = dir;
                break;
              }
            }
          }
          final VirtualFile sourceRoot = CommonMoveClassesOrPackagesUtil.chooseSourceRoot(newPackage, contentSourceRoots, initialDir);
          if (sourceRoot == null) return null;
          targetSourceRoot = sourceRoot;
        }
        else {
          targetSourceRoot = contentSourceRoots.get(0);
        }
        PsiDirectory dir = CommonJavaRefactoringUtil.findPackageDirectoryInSourceRoot(newPackage, targetSourceRoot);
        if (dir == null) {
          dir = ApplicationManager.getApplication().runWriteAction((NullableComputable<PsiDirectory>)() -> {
            try {
              return CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(newPackage, targetSourceRoot);
            }
            catch (IncorrectOperationException e) {
              return null;
            }
          });
        }
        return dir;
      }
    }
    return myTargetContainer;
  }

  @Override
  protected void doAction() {
    String message = null;
    final String className = getClassName();
    final String parameterName = getParameterName();
    PsiManager manager = PsiManager.getInstance(myProject);
    if (className.isEmpty()) {
      message = JavaRefactoringBundle.message("no.class.name.specified");
    }
    else {
      if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(className)) {
        message = RefactoringMessageUtil.getIncorrectIdentifierMessage(className);
      }
      else {
        if (myCbPassOuterClass.isSelected()) {
          if (parameterName != null && parameterName.isEmpty()) {
            message = JavaRefactoringBundle.message("no.parameter.name.specified");
          }
          else {
            if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(parameterName)) {
              message = RefactoringMessageUtil.getIncorrectIdentifierMessage(parameterName);
            }
          }
        }
        if (message == null && myTargetContainer instanceof PsiClass targetClass) {
          PsiClass[] classes = targetClass.getInnerClasses();
          for (PsiClass aClass : classes) {
            if (className.equals(aClass.getName())) {
              message = JavaRefactoringBundle.message("inner.class.exists", className, targetClass.getName());
              break;
            }
          }
        }
      }
    }

    PsiElement target = null;

    if (message == null) {
      if (myCbPassOuterClass.isSelected() && mySuggestedNameInfo != null) {
        mySuggestedNameInfo.nameChosen(getParameterName());
      }

      target = getTargetContainer();
      if (target == null) return;

      if (target instanceof PsiDirectory) {
        message = RefactoringMessageUtil.checkCanCreateClass((PsiDirectory)target, className);

        if (message == null) {
          final String packageName = getPackageName();
          if (!packageName.isEmpty() && !PsiNameHelper.getInstance(myProject).isQualifiedName(packageName)) {
            message = RefactoringMessageUtil.getIncorrectIdentifierMessage(packageName);
          }
        }
      }
    }

    if (message != null) {
      CommonRefactoringUtil.showErrorMessage(
        MoveInnerImpl.getRefactoringName(),
        message,
        HelpID.MOVE_INNER_UPPER,
        myProject);
      return;
    }

    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, getPackageName());
    myProcessor.setup(getInnerClass(), className, isPassOuterClass(), parameterName,
                      isSearchInComments(), isSearchInNonJavaFiles(), target);

    final boolean openInEditor = isOpenInEditor();
    myProcessor.setOpenInEditor(openInEditor);
    invokeRefactoring(myProcessor);
  }

  private String getPackageName() {
    return myPackageNameField.getText().trim();
  }

  @Override
  protected String getHelpId() {
    return HelpID.MOVE_INNER_UPPER;
  }

  private @Nullable PsiPackage getTargetPackage() {
    if (myTargetContainer instanceof PsiDirectory directory) {
      return JavaDirectoryService.getInstance().getPackage(directory);
    }
    return null;
  }
}