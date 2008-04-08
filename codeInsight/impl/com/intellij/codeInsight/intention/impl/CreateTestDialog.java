package com.intellij.codeInsight.intention.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class CreateTestDialog extends DialogWrapper {
  @NonNls private static final String RECENTS_KEY = "CreateTestDialog.RecentsKey";

  private Project myProject;
  private PsiClass myTargetClass;
  private Module myTargetModule;

  private PsiDirectory myTargetDirectory;

  private JTextField myTargetClassNameField;
  private EditorTextField mySuperClassField;
  private PsiTypeCodeFragment mySuperClassFieldCodeFragment;
  private ReferenceEditorComboWithBrowseButton myTargetPackageField;
  private MemberSelectionTable myMembersTable;

  public CreateTestDialog(@NotNull Project project,
                          @NotNull String title,
                          PsiClass targetClass,
                          @NotNull String superClassName,
                          PsiPackage targetPackage,
                          Module targetModule) {
    super(project, true);
    myProject = project;

    myTargetClass = targetClass;
    myTargetModule = targetModule;

    initControls(targetClass, superClassName, targetPackage);
    setTitle(title);
    init();
  }

  private void initControls(PsiClass targetClass, String superClassName, PsiPackage targetPackage) {
    myTargetClassNameField = new JTextField(targetClass.getName() + "Test");
    setPreferredSize(myTargetClassNameField);

    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    mySuperClassFieldCodeFragment = factory.createTypeCodeFragment(superClassName, targetPackage, true);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(mySuperClassFieldCodeFragment);
    mySuperClassField = new EditorTextField(document, myProject, StdFileTypes.JAVA);

    String targetPackageName = targetPackage != null ? targetPackage.getQualifiedName() : "";
    myTargetPackageField = new ReferenceEditorComboWithBrowseButton(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        doSelectPackage();
      }
    }, targetPackageName, PsiManager.getInstance(myProject), false, RECENTS_KEY);

    myMembersTable = new MemberSelectionTable(collectMethods(), null);
  }

  private void setPreferredSize(JTextField field) {
    Dimension size = field.getPreferredSize();
    FontMetrics fontMetrics = field.getFontMetrics(field.getFont());
    size.width = fontMetrics.charWidth('a') * 40;
    field.setPreferredSize(size);
  }

  private MemberInfo[] collectMethods() {
    return MemberInfo.extractClassMembers(myTargetClass, new MemberInfo.Filter() {
      public boolean includeMember(PsiMember member) {
        if (!(member instanceof PsiMethod)) return false;
        PsiModifierList list = member.getModifierList();
        return list.hasModifierProperty(PsiModifier.PUBLIC);
      }
    }, false);
  }

  private void doSelectPackage() {
    PackageChooserDialog d = new PackageChooserDialog(CodeInsightBundle.message("dialog.create.class.package.chooser.title"), myProject);
    d.selectPackage(myTargetPackageField.getText());
    d.show();
    PsiPackage p = d.getSelectedPackage();
    if (p != null) myTargetPackageField.setText(p.getQualifiedName());
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myTargetClassNameField;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createBorder());

    GridBagConstraints constr = new GridBagConstraints();
    constr.insets = new Insets(4, 8, 4, 8);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.WEST;

    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightBundle.message("intention.create.test.dialog.class.name")), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(myTargetClassNameField, constr);

    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightBundle.message("intention.create.test.dialog.super.class")), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    panel.add(mySuperClassField, constr);

    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightBundle.message("dialog.create.class.destination.package.label")), constr);

    constr.gridx = 1;
    constr.weightx = 1;

    JPanel targetPackagePanel = new JPanel(new BorderLayout());
    targetPackagePanel.add(myTargetPackageField, BorderLayout.CENTER);
    panel.add(targetPackagePanel, constr);

    constr.gridx = 0;
    constr.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(new JLabel("Select methods to create tests for"), constr);

    constr.gridx = 0;
    constr.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(new JScrollPane(myMembersTable), constr);

    myTargetClassNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        getOKAction().setEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(getClassName()));
      }
    });

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        myTargetPackageField.getButton().doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
                                myTargetPackageField.getChildComponent());

    return panel;
  }

  public String getClassName() {
    return myTargetClassNameField.getText();
  }

  public String getSuperClassName() {
    if (mySuperClassField.getText().trim().length() == 0) return null;
    try {
      return mySuperClassFieldCodeFragment.getType().getCanonicalText();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
    }
    return mySuperClassField.getText();
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  public MemberInfo[] getSelectedMethods() {
    return myMembersTable.getSelectedMemberInfos();
  }

  protected void doOKAction() {
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, myTargetPackageField.getText());

    String errorString = new WriteCommandAction<String>(myProject, CodeInsightBundle.message("create.directory.command")) {
      protected void run(Result<String> result) throws Throwable {
        try {
          myTargetDirectory = selectTargetDirectory();
          if (myTargetDirectory == null) {
            result.setResult("");
            return;
          }
          result.setResult(RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, getClassName()));
        }
        catch (IncorrectOperationException e) {
          result.setResult(e.getMessage());
        }
      }
    }.execute().getResultObject();

    if (errorString != null) {
      if (errorString.length() != 0) {
        Messages.showMessageDialog(myProject, errorString, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      }
      return;
    }
    
    super.doOKAction();
  }

  private PsiDirectory selectTargetDirectory() throws IncorrectOperationException {
    VirtualFile[] roots = ProjectRootManager.getInstance(myProject).getContentSourceRoots();
    if (roots.length == 0) return null;

    String packageName = getPackageName();
    PackageWrapper targetPackage = new PackageWrapper(PsiManager.getInstance(myProject), packageName);

    VirtualFile selectedRoot;
    if (roots.length == 1) {
      selectedRoot = roots[0];
    } else {
      PsiDirectory defaultDir = chooseDefaultDirectory(packageName);
      selectedRoot = MoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, roots, defaultDir);
      if (selectedRoot == null) return null;
    }

    return RefactoringUtil.createPackageDirectoryInSourceRoot(targetPackage, selectedRoot);
  }

  private PsiDirectory chooseDefaultDirectory(String packageName) {
    for (ContentEntry e : ModuleRootManager.getInstance(myTargetModule).getContentEntries()) {
      for (SourceFolder f : e.getSourceFolders()) {
        if (f.getFile() != null && f.isTestSource()) {
          return PsiManager.getInstance(myProject).findDirectory(f.getFile());
        }
      }
    }
    return PackageUtil.findPossiblePackageDirectoryInModule(myTargetModule, packageName);
  }

  private String getPackageName() {
    String name = myTargetPackageField.getText();
    return name != null ? name.trim() : "";
  }
}