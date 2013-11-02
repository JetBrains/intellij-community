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
package com.intellij.testIntegration.createTest;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class CreateTestDialog extends DialogWrapper {
  private static final String RECENTS_KEY = "CreateTestDialog.RecentsKey";
  private static final String RECENT_SUPERS_KEY = "CreateTestDialog.Recents.Supers";
  private static final String DEFAULT_LIBRARY_NAME_PROPERTY = CreateTestDialog.class.getName() + ".defaultLibrary";
  private static final String SHOW_INHERITED_MEMBERS_PROPERTY = CreateTestDialog.class.getName() + ".includeInheritedMembers";

  private final Project myProject;
  private final PsiClass myTargetClass;
  private final Module myTargetModule;

  private PsiDirectory myTargetDirectory;
  private TestFramework mySelectedFramework;

  private final List<JRadioButton> myLibraryButtons = new ArrayList<JRadioButton>();
  private EditorTextField myTargetClassNameField;
  private ReferenceEditorComboWithBrowseButton mySuperClassField;
  private ReferenceEditorComboWithBrowseButton myTargetPackageField;
  private JCheckBox myGenerateBeforeBox;
  private JCheckBox myGenerateAfterBox;
  private JCheckBox myShowInheritedMethodsBox;
  private MemberSelectionTable myMethodsTable;
  private JButton myFixLibraryButton;
  private JPanel myFixLibraryPanel;
  private JLabel myFixLibraryLabel;

  private JRadioButton myDefaultLibraryButton;

  public CreateTestDialog(@NotNull Project project,
                          @NotNull String title,
                          PsiClass targetClass,
                          PsiPackage targetPackage,
                          Module targetModule) {
    super(project, true);
    myProject = project;

    myTargetClass = targetClass;
    myTargetModule = targetModule;

    initControls(targetClass, targetPackage);
    setTitle(title);
    init();

    myDefaultLibraryButton.doClick();
  }

  private void initControls(PsiClass targetClass, PsiPackage targetPackage) {
    ButtonGroup group = new ButtonGroup();

    Map<String, JRadioButton> nameToButtonMap = new HashMap<String, JRadioButton>();
    List<Pair<String, JRadioButton>> attachedLibraries = new ArrayList<Pair<String, JRadioButton>>();

    for (final TestFramework descriptor : Extensions.getExtensions(TestFramework.EXTENSION_NAME)) {
      final JRadioButton b = new JRadioButton(descriptor.getName());
      if (descriptor instanceof JavaTestFramework) {
        final char mnemonic = ((JavaTestFramework)descriptor).getMnemonic();
        if (mnemonic > -1) {
          b.setMnemonic(mnemonic);
        }
      }
      myLibraryButtons.add(b);
      group.add(b);

      nameToButtonMap.put(descriptor.getName(), b);
      if (descriptor.isLibraryAttached(myTargetModule)) {
        attachedLibraries.add(Pair.create(descriptor.getName(), b));
      }

      b.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (b.isSelected()) onLibrarySelected(descriptor);
        }
      });
    }

    String defaultLibrary = getDefaultLibraryName();
    if (attachedLibraries.isEmpty()) {
      if (defaultLibrary != null) {
        myDefaultLibraryButton = nameToButtonMap.get(defaultLibrary);
      }
    }
    else {
      if (defaultLibrary != null) {
        for (Pair<String, JRadioButton> each : attachedLibraries) {
          if (each.first.equals(defaultLibrary)) {
            myDefaultLibraryButton = each.second;
          }
        }
      }
      if (myDefaultLibraryButton == null) {
        myDefaultLibraryButton = attachedLibraries.get(0).second;
      }
    }
    if (myDefaultLibraryButton == null) {
      myDefaultLibraryButton = myLibraryButtons.get(0);
    }

    myFixLibraryButton = new JButton(CodeInsightBundle.message("intention.create.test.dialog.fix.library"));
    myFixLibraryButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            OrderEntryFix.addJarToRoots(mySelectedFramework.getLibraryPath(), myTargetModule, null);
          }
        });
        myFixLibraryPanel.setVisible(false);
      }
    });

    myTargetClassNameField = new EditorTextField(targetClass.getName() + "Test");
    myTargetClassNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        getOKAction().setEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(getClassName()));
      }
    });

    mySuperClassField = new ReferenceEditorComboWithBrowseButton(new MyChooseSuperClassAction(), null, myProject, true,
                                                                 JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE, RECENT_SUPERS_KEY);
    mySuperClassField.setMinimumSize(mySuperClassField.getPreferredSize());

    String targetPackageName = targetPackage != null ? targetPackage.getQualifiedName() : "";
    myTargetPackageField = new PackageNameReferenceEditorCombo(targetPackageName, myProject, RECENTS_KEY, CodeInsightBundle.message("dialog.create.class.package.chooser.title"));

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        myTargetPackageField.getButton().doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
                                myTargetPackageField.getChildComponent());

    myGenerateBeforeBox = new JCheckBox("setUp/@Before");
    myGenerateAfterBox = new JCheckBox("tearDown/@After");

    myShowInheritedMethodsBox = new JCheckBox(CodeInsightBundle.message("intention.create.test.dialog.show.inherited"));
    myShowInheritedMethodsBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateMethodsTable();
      }
    });
    restoreShowInheritedMembersStatus();
    myMethodsTable = new MemberSelectionTable(Collections.<MemberInfo>emptyList(), null);
    updateMethodsTable();
  }

  private boolean isSuperclassSelectedManually() {
    String superClass = mySuperClassField.getText();
    if (StringUtil.isEmptyOrSpaces(superClass)) {
      return false;
    }

    for (TestFramework framework : TestFramework.EXTENSION_NAME.getExtensions()) {
      if (superClass.equals(framework.getDefaultSuperClass())) {
        return false;
      }
    }

    return true;
  }

  private void onLibrarySelected(TestFramework descriptor) {
    if (descriptor.isLibraryAttached(myTargetModule)) {
      myFixLibraryPanel.setVisible(false);
    }
    else {
      myFixLibraryPanel.setVisible(true);
      String text = CodeInsightBundle.message("intention.create.test.dialog.library.not.found", descriptor.getName());
      myFixLibraryLabel.setText(text);

      myFixLibraryButton.setVisible(descriptor.getLibraryPath() != null);
    }

    String superClass = descriptor.getDefaultSuperClass();

    if (isSuperclassSelectedManually()) {
      if (superClass != null) {
        String currentSuperClass = mySuperClassField.getText();
        mySuperClassField.appendItem(superClass);
        mySuperClassField.setText(currentSuperClass);
      }
    }
    else {
      mySuperClassField.appendItem(StringUtil.notNullize(superClass));
      mySuperClassField.getChildComponent().setSelectedItem(StringUtil.notNullize(superClass));
    }

    mySelectedFramework = descriptor;
  }

  private void updateMethodsTable() {
    List<MemberInfo> methods = TestIntegrationUtils.extractClassMethods(
      myTargetClass, myShowInheritedMethodsBox.isSelected());

    Set<PsiMember> selectedMethods = new HashSet<PsiMember>();
    for (MemberInfo each : myMethodsTable.getSelectedMemberInfos()) {
      selectedMethods.add(each.getMember());
    }
    for (MemberInfo each : methods) {
      each.setChecked(selectedMethods.contains(each.getMember()));
    }

    myMethodsTable.setMemberInfos(methods);
  }

  private String getDefaultLibraryName() {
    return getProperties().getValue(DEFAULT_LIBRARY_NAME_PROPERTY);
  }

  private void saveDefaultLibraryName() {
    getProperties().setValue(DEFAULT_LIBRARY_NAME_PROPERTY, mySelectedFramework.getName());
  }

  private void restoreShowInheritedMembersStatus() {
    String v = getProperties().getValue(SHOW_INHERITED_MEMBERS_PROPERTY);
    myShowInheritedMethodsBox.setSelected(v != null && v.equals("true"));
  }

  private void saveShowInheritedMembersStatus() {
    boolean v = myShowInheritedMethodsBox.isSelected();
    getProperties().setValue(SHOW_INHERITED_MEMBERS_PROPERTY, Boolean.toString(v));
  }

  private PropertiesComponent getProperties() {
    return PropertiesComponent.getInstance(myProject);
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myTargetClassNameField;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints constr = new GridBagConstraints();

    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;

    JPanel librariesPanel = new JPanel();
    BoxLayout l = new BoxLayout(librariesPanel, BoxLayout.X_AXIS);
    librariesPanel.setLayout(l);

    for (JRadioButton b : myLibraryButtons) {
      librariesPanel.add(b);
    }

    
    int gridy = 1;
    
    constr.insets = insets(4);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightBundle.message("intention.create.test.dialog.testing.library")), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    constr.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(librariesPanel, constr);

    myFixLibraryPanel = new JPanel(new BorderLayout());
    myFixLibraryLabel = new JLabel();
    myFixLibraryLabel.setIcon(AllIcons.Actions.IntentionBulb);
    myFixLibraryPanel.add(myFixLibraryLabel, BorderLayout.CENTER);
    myFixLibraryPanel.add(myFixLibraryButton, BorderLayout.EAST);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    constr.gridx = 0;
    panel.add(myFixLibraryPanel, constr);

    constr.gridheight = 1;

    constr.insets = insets(6);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    constr.gridwidth = 1;
    panel.add(new JLabel(CodeInsightBundle.message("intention.create.test.dialog.class.name")), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(myTargetClassNameField, constr);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightBundle.message("intention.create.test.dialog.super.class")), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(mySuperClassField, constr);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightBundle.message("dialog.create.class.destination.package.label")), constr);

    constr.gridx = 1;
    constr.weightx = 1;

    JPanel targetPackagePanel = new JPanel(new BorderLayout());
    targetPackagePanel.add(myTargetPackageField, BorderLayout.CENTER);
    panel.add(targetPackagePanel, constr);

    constr.insets = insets(6);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightBundle.message("intention.create.test.dialog.generate")), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(myGenerateBeforeBox, constr);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    panel.add(myGenerateAfterBox, constr);

    constr.insets = insets(6);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightBundle.message("intention.create.test.dialog.select.methods")), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(myShowInheritedMethodsBox, constr);

    constr.insets = insets(1, 8);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.gridwidth = GridBagConstraints.REMAINDER;
    constr.fill = GridBagConstraints.BOTH;
    constr.weighty = 1;
    panel.add(ScrollPaneFactory.createScrollPane(myMethodsTable), constr);

    return panel;
  }

  private static Insets insets(int top) {
    return insets(top, 0);
  }

  private static Insets insets(int top, int bottom) {
    return new Insets(top, 8, bottom, 8);
  }

  public String getClassName() {
    return myTargetClassNameField.getText();
  }

  @Nullable
  public String getSuperClassName() {
    String result = mySuperClassField.getText().trim();
    if (result.length() == 0) return null;
    return result;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  public Collection<MemberInfo> getSelectedMethods() {
    return myMethodsTable.getSelectedMemberInfos();
  }

  public boolean shouldGeneratedAfter() {
    return myGenerateAfterBox.isSelected();
  }

  public boolean shouldGeneratedBefore() {
    return myGenerateBeforeBox.isSelected();
  }

  public TestFramework getSelectedTestFrameworkDescriptor() {
    return mySelectedFramework;
  }

  protected void doOKAction() {
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, myTargetPackageField.getText());
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENT_SUPERS_KEY, mySuperClassField.getText());

    String errorMessage;
    try {
      myTargetDirectory = selectTargetDirectory();
      if (myTargetDirectory == null) return;
      errorMessage = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, getClassName());
    }
    catch (IncorrectOperationException e) {
      errorMessage = e.getMessage();
    }

    if (errorMessage != null) {
      Messages.showMessageDialog(myProject, errorMessage, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    }

    saveDefaultLibraryName();
    saveShowInheritedMembersStatus();
    super.doOKAction();
  }

  @Nullable
  private PsiDirectory selectTargetDirectory() throws IncorrectOperationException {
    final String packageName = getPackageName();
    final PackageWrapper targetPackage = new PackageWrapper(PsiManager.getInstance(myProject), packageName);

    final VirtualFile selectedRoot = new ReadAction<VirtualFile>() {
      protected void run(Result<VirtualFile> result) throws Throwable {
        final HashSet<VirtualFile> testFolders = new HashSet<VirtualFile>();
        CreateTestAction.checkForTestRoots(myTargetModule, testFolders);
        List<VirtualFile> roots;
        if (testFolders.isEmpty()) {
          roots = ModuleRootManager.getInstance(myTargetModule).getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
          if (roots.isEmpty()) return;
        } else {
          roots = new ArrayList<VirtualFile>(testFolders);
        }

        if (roots.size() == 1) {
          result.setResult(roots.get(0));
        }
        else {
          PsiDirectory defaultDir = chooseDefaultDirectory(packageName);
          result.setResult(MoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, roots, defaultDir));
        }
      }
    }.execute().getResultObject();

    if (selectedRoot == null) return null;

    return new WriteCommandAction<PsiDirectory>(myProject, CodeInsightBundle.message("create.directory.command")) {
      protected void run(Result<PsiDirectory> result) throws Throwable {
        result.setResult(RefactoringUtil.createPackageDirectoryInSourceRoot(targetPackage, selectedRoot));
      }
    }.execute().getResultObject();
  }

  @Nullable
  private PsiDirectory chooseDefaultDirectory(String packageName) {
    List<PsiDirectory> dirs = new ArrayList<PsiDirectory>();
    for (VirtualFile file : ModuleRootManager.getInstance(myTargetModule).getSourceRoots(JavaSourceRootType.TEST_SOURCE)) {
      final PsiDirectory dir = PsiManager.getInstance(myProject).findDirectory(file);
      if (dir != null) {
        dirs.add(dir);
      }
    }
    if (!dirs.isEmpty()) {
      for (PsiDirectory dir : dirs) {
        final String dirName = dir.getVirtualFile().getPath();
        if (dirName.contains("generated")) continue;
        return dir;
      }
      return dirs.get(0);
    }
    return PackageUtil.findPossiblePackageDirectoryInModule(myTargetModule, packageName);
  }

  private String getPackageName() {
    String name = myTargetPackageField.getText();
    return name != null ? name.trim() : "";
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.dialogs.createTest");
  }

  private class MyChooseSuperClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooserFactory f = TreeClassChooserFactory.getInstance(myProject);
      TreeClassChooser dialog =
        f.createAllProjectScopeChooser(CodeInsightBundle.message("intention.create.test.dialog.choose.super.class"));
      dialog.showDialog();
      PsiClass aClass = dialog.getSelected();
      if (aClass != null) {
        String superClass = aClass.getQualifiedName();

        mySuperClassField.appendItem(superClass);
        mySuperClassField.getChildComponent().setSelectedItem(superClass);
      }
    }
  }
}
