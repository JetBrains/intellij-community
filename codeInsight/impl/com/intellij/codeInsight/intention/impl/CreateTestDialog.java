package com.intellij.codeInsight.intention.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringMessageUtil;
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
  private Module myTargetModule;

  private PsiDirectory myTargetDirectory;

  private JTextField myTargetClassNameField;
  private EditorTextField mySuperClassField;
  private PsiTypeCodeFragment mySuperClassFieldCodeFragment;
  private ReferenceEditorComboWithBrowseButton myTargetPackageField;

  public CreateTestDialog(@NotNull Project project,
                          @NotNull String title,
                          String targetClassName,
                          PsiPackage targetPackage,
                          Module targetModule) {
    super(project, true);
    myProject = project;

    myTargetModule = targetModule;

    myTargetClassNameField = new JTextField(targetClassName);
    setPreferredSize(myTargetClassNameField);

    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    mySuperClassFieldCodeFragment = factory.createTypeCodeFragment("TestCase", targetPackage, true);
    mySuperClassFieldCodeFragment.addImportsFromString("junit.framework.TestCase");
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(mySuperClassFieldCodeFragment);
    mySuperClassField = new EditorTextField(document, myProject, StdFileTypes.JAVA);

    String targetPackageName = targetPackage != null ? targetPackage.getQualifiedName() : "";
    myTargetPackageField = new ReferenceEditorComboWithBrowseButton(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        PackageChooserDialog chooser =
            new PackageChooserDialog(CodeInsightBundle.message("dialog.create.class.package.chooser.title"), myProject);
        chooser.selectPackage(myTargetPackageField.getText());
        chooser.show();
        PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          myTargetPackageField.setText(aPackage.getQualifiedName());
        }
      }
    }, targetPackageName, PsiManager.getInstance(myProject), false, RECENTS_KEY);

    init();
    setTitle(title);
  }

  private void setPreferredSize(JTextField field) {
    Dimension size = field.getPreferredSize();
    FontMetrics fontMetrics = field.getFontMetrics(field.getFont());
    size.width = fontMetrics.charWidth('a') * 40;
    field.setPreferredSize(size);
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myTargetClassNameField;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    panel.setBorder(IdeBorderFactory.createBorder());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridx = 0;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel("Class name"), gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(myTargetClassNameField, gbConstraints);

    myTargetClassNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        getOKAction().setEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(getClassName()));
      }
    });

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 2;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel("Super Class"), gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 2;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(mySuperClassField, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 3;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    panel.add(new JLabel(CodeInsightBundle.message("dialog.create.class.destination.package.label")), gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        myTargetPackageField.getButton().doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
                                myTargetPackageField.getChildComponent());

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(myTargetPackageField, BorderLayout.CENTER);
    panel.add(_panel, gbConstraints);

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

  protected void doOKAction() {
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, myTargetPackageField.getText());
    final String packageName = getPackageName();

    final String[] errorString = new String[1];
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          final PsiDirectory baseDir =
              myTargetModule == null ? null : PackageUtil.findPossiblePackageDirectoryInModule(myTargetModule, packageName);
          myTargetDirectory = myTargetModule == null
                              ? PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, baseDir, true)
                              : PackageUtil.findOrCreateDirectoryForPackage(myTargetModule, packageName, baseDir, true);
          if (myTargetDirectory == null) {
            errorString[0] = ""; // message already reported by PackageUtil
            return;
          }
          errorString[0] = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, getClassName());
        }
        catch (IncorrectOperationException e) {
          errorString[0] = e.getMessage();
        }
      }
    }, CodeInsightBundle.message("create.directory.command"), null);

    if (errorString[0] != null) {
      if (errorString[0].length() > 0) {
        Messages.showMessageDialog(myProject, errorString[0], CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      }
      return;
    }
    super.doOKAction();
  }

  private String getPackageName() {
    String name = myTargetPackageField.getText();
    return name != null ? name.trim() : "";
  }
}