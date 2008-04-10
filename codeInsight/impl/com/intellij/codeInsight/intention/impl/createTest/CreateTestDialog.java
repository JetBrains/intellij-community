package com.intellij.codeInsight.intention.impl.createTest;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.Extensions;
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
import java.util.ArrayList;
import java.util.List;

public class CreateTestDialog extends DialogWrapper {
  @NonNls private static final String RECENTS_KEY = "CreateTestDialog.RecentsKey";

  private Project myProject;
  private PsiClass myTargetClass;
  private Module myTargetModule;

  private PsiDirectory myTargetDirectory;
  private CreateTestProvider mySelectedTestProvider;

  private List<JRadioButton> myLibraryButtons = new ArrayList<JRadioButton>();
  private JTextField myTargetClassNameField;
  private ReferenceEditorWithBrowseButton mySuperClassField;
  private ReferenceEditorComboWithBrowseButton myTargetPackageField;
  private JCheckBox myGenerateBeforeBox;
  private JCheckBox myGenerateAfterBox;
  private MemberSelectionTable myMembersTable;
  private JButton myFixLibraryButton;
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
    myTargetModule = targetModule;

    initControls(targetClass, targetPackage);
    setTitle(title);
    init();

    myLibraryButtons.get(0).doClick();
  }

  private void initControls(PsiClass targetClass, PsiPackage targetPackage) {
    ButtonGroup group = new ButtonGroup();
    for (final CreateTestProvider p : Extensions.getExtensions(CreateTestProvider.EXTENSION_NAME)) {
      final JRadioButton b = new JRadioButton(p.getName());
      myLibraryButtons.add(b);
      group.add(b);
      
      b.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (b.isSelected()) onLibrarySelected(p);
        }
      });
    }

    myFixLibraryButton = new JButton(CodeInsightBundle.message("intention.create.test.dialog.fix.library"));
    myFixLibraryButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            OrderEntryFix.addJarToRoots(mySelectedTestProvider.getLibraryPath(), myTargetModule);
          }
        });
        myFixLibraryPanel.setVisible(false);
      }
    });

    myTargetClassNameField = new JTextField(targetClass.getName() + "Test");
    setPreferredSize(myTargetClassNameField);
    myTargetClassNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        getOKAction().setEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(getClassName()));
      }
    });

    mySuperClassField = new ReferenceEditorWithBrowseButton(new MyChooseSuperClassAction(), "", PsiManager.getInstance(myProject), true);

    String targetPackageName = targetPackage != null ? targetPackage.getQualifiedName() : "";
    myTargetPackageField = new ReferenceEditorComboWithBrowseButton(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        doSelectPackage();
      }
    }, targetPackageName, PsiManager.getInstance(myProject), false, RECENTS_KEY);

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        myTargetPackageField.getButton().doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
                                myTargetPackageField.getChildComponent());

    myGenerateBeforeBox = new JCheckBox(CodeInsightBundle.message("intention.create.test.dialog.generate", "setUp/@Before"));
    myGenerateAfterBox = new JCheckBox(CodeInsightBundle.message("intention.create.test.dialog.generate", "tearDown/@After"));

    myMembersTable = new MemberSelectionTable(collectMethods(), null);
  }

  private void onLibrarySelected(CreateTestProvider p) {
    String text = CodeInsightBundle.message("intention.create.test.dialog.library.not.found", p.getName());
    myFixLibraryLabel.setText(text);
    myFixLibraryPanel.setVisible(!p.isLibraryAttached(myTargetModule));

    String superClass = p.getDefaultSuperClass();
    mySuperClassField.setText(superClass == null ? "" : superClass);
    mySelectedTestProvider = p;
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

    JPanel librariesPanel = new JPanel();
    BoxLayout l = new BoxLayout(librariesPanel, BoxLayout.X_AXIS);
    librariesPanel.setLayout(l);

    for (JRadioButton b : myLibraryButtons) {
      librariesPanel.add(b);
    }

    constr.gridx = 0;
    constr.weightx = 0;
    panel.add(new JLabel(CodeInsightBundle.message("intention.create.test.dialog.testing.library")), constr);

    constr.gridx = 1;
    constr.weightx = 1;
    constr.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(librariesPanel, constr);

    myFixLibraryPanel = new JPanel(new BorderLayout());
    myFixLibraryLabel = new JLabel();
    myFixLibraryLabel.setIcon(Messages.getWarningIcon());
    myFixLibraryPanel.add(myFixLibraryLabel, BorderLayout.CENTER);
    myFixLibraryPanel.add(myFixLibraryButton, BorderLayout.EAST);

    constr.gridx = 0;
    panel.add(myFixLibraryPanel, constr);

    constr.gridheight = 1;

    constr.gridx = 0;
    constr.weightx = 0;
    constr.gridwidth = 1;
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
    panel.add(myGenerateBeforeBox, constr);
    panel.add(myGenerateAfterBox, constr);

    panel.add(new JLabel(CodeInsightBundle.message("intention.create.test.dialog.select.methods")), constr);
    constr.fill = GridBagConstraints.BOTH;
    constr.weighty = 1;
    panel.add(new JScrollPane(myMembersTable), constr);

    return panel;
  }

  public String getClassName() {
    return myTargetClassNameField.getText();
  }

  public String getSuperClassName() {
    String result = mySuperClassField.getText().trim();
    if (result.length() == 0) return null;
    return result;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  public MemberInfo[] getSelectedMethods() {
    return myMembersTable.getSelectedMemberInfos();
  }

  public boolean shouldGeneratedAfter() {
    return myGenerateAfterBox.isSelected();
  }

  public boolean shouldGeneratedBefore() {
    return myGenerateBeforeBox.isSelected();
  }

  public CreateTestProvider getSelectedTestProvider() {
    return mySelectedTestProvider;
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

  private class MyChooseSuperClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooserFactory f = TreeClassChooserFactory.getInstance(myProject);
      TreeClassChooser dialog = f.createAllProjectScopeChooser(CodeInsightBundle.message("intention.create.test.dialog.choose.super.class"));
      dialog.showDialog();
      PsiClass aClass = dialog.getSelectedClass();
      if (aClass != null) {
        mySuperClassField.setText(aClass.getQualifiedName());
      }
    }
  }
}