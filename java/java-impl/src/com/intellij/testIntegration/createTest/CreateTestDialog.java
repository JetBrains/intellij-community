// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntegration.createTest;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
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
import com.intellij.ui.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
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
import java.io.IOException;
import java.util.List;
import java.util.*;

public class CreateTestDialog extends DialogWrapper {
  private static final String RECENTS_KEY = "CreateTestDialog.RecentsKey";
  private static final String RECENT_SUPERS_KEY = "CreateTestDialog.Recents.Supers";
  private static final String DEFAULT_LIBRARY_NAME_PROPERTY = CreateTestDialog.class.getName() + ".defaultLibrary";
  private static final String DEFAULT_LIBRARY_SUPERCLASS_NAME_PROPERTY = CreateTestDialog.class.getName() + ".defaultLibrarySuperClass";
  private static final String SHOW_INHERITED_MEMBERS_PROPERTY = CreateTestDialog.class.getName() + ".includeInheritedMembers";

  private final Project myProject;
  private final PsiClass myTargetClass;
  private final PsiPackage myTargetPackage;
  private final Module myTargetModule;

  protected PsiDirectory myTargetDirectory;
  private TestFramework mySelectedFramework;

  private final ComboBox<TestFramework> myLibrariesCombo = new ComboBox<>(new DefaultComboBoxModel<>());
  private EditorTextField myTargetClassNameField;
  private ReferenceEditorComboWithBrowseButton mySuperClassField;
  private ReferenceEditorComboWithBrowseButton myTargetPackageField;
  private final JCheckBox myGenerateBeforeBox = new JCheckBox(JavaBundle.message("intention.create.test.dialog.setUp"));
  private final JCheckBox myGenerateAfterBox = new JCheckBox(JavaBundle.message("intention.create.test.dialog.tearDown"));
  private final JCheckBox myShowInheritedMethodsBox = new JCheckBox(JavaBundle.message("intention.create.test.dialog.show.inherited"));
  private final MemberSelectionTable myMethodsTable = new MemberSelectionTable(Collections.emptyList(), null);
  private final JButton myFixLibraryButton = new JButton(JavaBundle.message("intention.create.test.dialog.fix.library"));
  private JPanel myFixLibraryPanel;
  private JLabel myFixLibraryLabel;

  public CreateTestDialog(@NotNull Project project,
                          @NotNull String title,
                          PsiClass targetClass,
                          PsiPackage targetPackage,
                          Module targetModule) {
    super(project, true);
    myProject = project;

    myTargetClass = targetClass;
    myTargetPackage = targetPackage;
    myTargetModule = targetModule;

    setTitle(title);
    init();
  }

  protected String suggestTestClassName(PsiClass targetClass) {
    JavaCodeStyleSettings customSettings = JavaCodeStyleSettings.getInstance(targetClass.getContainingFile());
    String prefix = customSettings.TEST_NAME_PREFIX;
    String suffix = customSettings.TEST_NAME_SUFFIX;
    return prefix + targetClass.getName() + suffix;
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
      if (superClass.equals(getLastSelectedSuperClassName(framework))) {
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
      String text = JavaBundle.message("intention.create.test.dialog.library.not.found", descriptor.getName());
      myFixLibraryLabel.setText(text);
      myFixLibraryButton.setVisible(descriptor instanceof JavaTestFramework && ((JavaTestFramework)descriptor).getFrameworkLibraryDescriptor() != null
                                    || descriptor.getLibraryPath() != null);
    }

    String libraryDefaultSuperClass = descriptor.getDefaultSuperClass();
    String lastSelectedSuperClass = getLastSelectedSuperClassName(descriptor);
    String superClass = lastSelectedSuperClass != null ? lastSelectedSuperClass : libraryDefaultSuperClass;

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

    Set<PsiMember> selectedMethods = new HashSet<>();
    for (MemberInfo each : myMethodsTable.getSelectedMemberInfos()) {
      selectedMethods.add(each.getMember());
    }
    for (MemberInfo each : methods) {
      each.setChecked(selectedMethods.contains(each.getMember()));
    }

    myMethodsTable.setMemberInfos(methods);
  }

  private String getDefaultLibraryName() {
    return getProperties().getValue(DEFAULT_LIBRARY_NAME_PROPERTY, "JUnit5");
  }

  private String getLastSelectedSuperClassName(TestFramework framework) {
    return getProperties().getValue(getDefaultSuperClassPropertyName(framework));
  }

  private void saveDefaultLibraryNameAndSuperClass() {
    getProperties().setValue(DEFAULT_LIBRARY_NAME_PROPERTY, mySelectedFramework.getName());
    getProperties().setValue(getDefaultSuperClassPropertyName(mySelectedFramework), mySuperClassField.getText());
  }

  private static String getDefaultSuperClassPropertyName(TestFramework framework) {
    return DEFAULT_LIBRARY_SUPERCLASS_NAME_PROPERTY + "." + framework.getName();
  }

  private void restoreShowInheritedMembersStatus() {
    myShowInheritedMethodsBox.setSelected(getProperties().getBoolean(SHOW_INHERITED_MEMBERS_PROPERTY));
  }

  private void saveShowInheritedMembersStatus() {
    getProperties().setValue(SHOW_INHERITED_MEMBERS_PROPERTY, myShowInheritedMethodsBox.isSelected());
  }

  private PropertiesComponent getProperties() {
    return PropertiesComponent.getInstance(myProject);
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.createTest";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTargetClassNameField;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints constr = new GridBagConstraints();

    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;

    int gridy = 1;

    constr.insets = insets(4);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    final JLabel libLabel = new JLabel(JavaBundle.message("intention.create.test.dialog.testing.library"));
    libLabel.setLabelFor(myLibrariesCombo);
    panel.add(libLabel, constr);

    constr.gridx = 1;
    constr.weightx = 1;
    constr.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(myLibrariesCombo, constr);

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
    panel.add(new JLabel(JavaBundle.message("intention.create.test.dialog.class.name")), constr);

    myTargetClassNameField = new EditorTextField(suggestTestClassName(myTargetClass));
    myTargetClassNameField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        getOKAction().setEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(getClassName()));
      }
    });

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(myTargetClassNameField, constr);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(JavaBundle.message("intention.create.test.dialog.super.class")), constr);

    mySuperClassField = new ReferenceEditorComboWithBrowseButton(new MyChooseSuperClassAction(), null, myProject, true,
                                                                 JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE, RECENT_SUPERS_KEY);
    mySuperClassField.setMinimumSize(mySuperClassField.getPreferredSize());
    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(mySuperClassField, constr);

    constr.insets = insets(1);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(JavaBundle.message("dialog.create.class.destination.package.label")), constr);

    constr.gridx = 1;
    constr.weightx = 1;


    String targetPackageName = myTargetPackage != null ? myTargetPackage.getQualifiedName() : "";
    myTargetPackageField = new PackageNameReferenceEditorCombo(targetPackageName, myProject, RECENTS_KEY, JavaBundle.message("dialog.create.class.package.chooser.title"));

    new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myTargetPackageField.getButton().doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
                                myTargetPackageField.getChildComponent());
    JPanel targetPackagePanel = new JPanel(new BorderLayout());
    targetPackagePanel.add(myTargetPackageField, BorderLayout.CENTER);
    panel.add(targetPackagePanel, constr);

    constr.insets = insets(6);
    constr.gridy = gridy++;
    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(JavaBundle.message("intention.create.test.dialog.generate")), constr);

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
    final JLabel membersLabel = new JLabel(JavaBundle.message("intention.create.test.dialog.select.methods"));
    membersLabel.setLabelFor(myMethodsTable);
    panel.add(membersLabel, constr);

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

    myLibrariesCombo.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      if (value != null) {
        label.setText(value.getName());
        label.setIcon(value.getIcon());
      }
    }));
    final boolean hasTestRoots = !ModuleRootManager.getInstance(myTargetModule).getSourceRoots(JavaModuleSourceRootTypes.TESTS).isEmpty();
    final List<TestFramework> attachedLibraries = new ArrayList<>();
    final String defaultLibrary = getDefaultLibraryName();
    TestFramework defaultDescriptor = null;

    final DefaultComboBoxModel<TestFramework> model = (DefaultComboBoxModel<TestFramework>)myLibrariesCombo.getModel();

    final List<TestFramework> descriptors = new SmartList<>(TestFramework.EXTENSION_NAME.getExtensionList());
    descriptors.sort((d1, d2) -> Comparing.compare(d1.getName(), d2.getName()));

    for (final TestFramework descriptor : descriptors) {
      model.addElement(descriptor);
      if (hasTestRoots && descriptor.isLibraryAttached(myTargetModule)) {
        attachedLibraries.add(descriptor);
      }

      if (Objects.equals(defaultLibrary, descriptor.getName())) {
        defaultDescriptor = descriptor;
      }
    }

    myLibrariesCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Object selectedItem = myLibrariesCombo.getSelectedItem();
        if (selectedItem != null) {
          final DumbService dumbService = DumbService.getInstance(myProject);
          dumbService.runWithAlternativeResolveEnabled(() ->
            onLibrarySelected((TestFramework)selectedItem));
        }
      }
    });

    if (defaultDescriptor != null && (attachedLibraries.contains(defaultDescriptor) || attachedLibraries.isEmpty())) {
      myLibrariesCombo.setSelectedItem(defaultDescriptor);
    }
    else if (!descriptors.isEmpty()) {
      List<TestFramework> applicableFrameworks = attachedLibraries.isEmpty() ? descriptors : attachedLibraries;
      TestFramework preferredFramework =
        ObjectUtils.notNull(ContainerUtil.find(applicableFrameworks, d -> d.getLanguage().equals(myTargetClass.getLanguage())),
                            applicableFrameworks.get(0));
      myLibrariesCombo.setSelectedItem(preferredFramework);
    }

    myFixLibraryButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySelectedFramework instanceof JavaTestFramework) {
          ((JavaTestFramework)mySelectedFramework).setupLibrary(myTargetModule)
            .onSuccess(__ -> myFixLibraryPanel.setVisible(false));
        }
        else {
          OrderEntryFix.addJarToRoots(mySelectedFramework.getLibraryPath(), myTargetModule, null);
          myFixLibraryPanel.setVisible(false);
        }
      }
    });



    myShowInheritedMethodsBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateMethodsTable();
      }
    });
    restoreShowInheritedMembersStatus();
    updateMethodsTable();
    return panel;
  }

  private static Insets insets(int top) {
    return insets(top, 0);
  }

  private static Insets insets(int top, int bottom) {
    return JBUI.insets(top, 8, bottom, 8);
  }

  public String getClassName() {
    return myTargetClassNameField.getText();
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
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

  @Override
  protected void doOKAction() {
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, myTargetPackageField.getText());
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENT_SUPERS_KEY, mySuperClassField.getText());

    String errorMessage = null;
    try {
      myTargetDirectory = selectTargetDirectory();
      if (myTargetDirectory == null) return;
    }
    catch (IncorrectOperationException e) {
      errorMessage = e.getMessage();
    }

    if (errorMessage == null) {
      try {
        errorMessage = checkCanCreateClass();
      }
      catch (IncorrectOperationException e) {
        errorMessage = e.getMessage();
      }
    }

    if (errorMessage != null) {
      final int result = Messages
        .showOkCancelDialog(myProject, JavaBundle.message("dialog.message.0.update.existing.class", errorMessage), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      if (result == Messages.CANCEL) {
        return;
      }
    }

    saveDefaultLibraryNameAndSuperClass();
    saveShowInheritedMembersStatus();
    super.doOKAction();
  }

  protected String checkCanCreateClass() {
    return RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, getClassName());
  }

  @Nullable
  private PsiDirectory selectTargetDirectory() throws IncorrectOperationException {
    final String packageName = getPackageName();
    final PackageWrapper targetPackage = new PackageWrapper(PsiManager.getInstance(myProject), packageName);

    final VirtualFile selectedRoot = ReadAction.compute(() -> {
      final List<VirtualFile> testFolders = CreateTestAction.computeTestRoots(myTargetModule);
      List<VirtualFile> roots;
      if (testFolders.isEmpty()) {
        roots = new ArrayList<>();
        List<String> urls = CreateTestAction.computeSuitableTestRootUrls(myTargetModule);
        for (String url : urls) {
          try {
            ContainerUtil.addIfNotNull(roots, VfsUtil.createDirectories(VfsUtilCore.urlToPath(url)));
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
        if (roots.isEmpty()) {
          JavaProjectRootsUtil.collectSuitableDestinationSourceRoots(myTargetModule, roots);
        }
        if (roots.isEmpty()) return null;
      }
      else {
        roots = new ArrayList<>(testFolders);
      }

      if (roots.size() == 1) {
        return roots.get(0);
      }
      else {
        PsiDirectory defaultDir = chooseDefaultDirectory(targetPackage.getDirectories(), roots);
        return MoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, roots, defaultDir);
      }
    });

    if (selectedRoot == null) return null;

    return WriteCommandAction.writeCommandAction(myProject).withName(CodeInsightBundle.message("create.directory.command"))
                             .compute(() -> RefactoringUtil.createPackageDirectoryInSourceRoot(targetPackage, selectedRoot));
  }

  @Nullable
  private PsiDirectory chooseDefaultDirectory(PsiDirectory[] directories, List<VirtualFile> roots) {
    List<PsiDirectory> dirs = new ArrayList<>();
    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (VirtualFile file : ModuleRootManager.getInstance(myTargetModule).getSourceRoots(JavaSourceRootType.TEST_SOURCE)) {
      final PsiDirectory dir = psiManager.findDirectory(file);
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
    for (PsiDirectory dir : directories) {
      final VirtualFile file = dir.getVirtualFile();
      for (VirtualFile root : roots) {
        if (VfsUtilCore.isAncestor(root, file, false)) {
          final PsiDirectory rootDir = psiManager.findDirectory(root);
          if (rootDir != null) {
            return rootDir;
          }
        }
      }
    }
    return ModuleManager.getInstance(myProject)
      .getModuleDependentModules(myTargetModule)
      .stream().flatMap(module -> ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE).stream())
      .map(root -> psiManager.findDirectory(root)).findFirst().orElse(null);
  }

  private String getPackageName() {
    String name = myTargetPackageField.getText();
    return name != null ? name.trim() : "";
  }

  private class MyChooseSuperClassAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      TreeClassChooserFactory f = TreeClassChooserFactory.getInstance(myProject);
      TreeClassChooser dialog =
        f.createAllProjectScopeChooser(JavaBundle.message("intention.create.test.dialog.choose.super.class"));
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