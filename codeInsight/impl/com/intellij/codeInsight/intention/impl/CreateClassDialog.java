package com.intellij.codeInsight.intention.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class CreateClassDialog extends DialogWrapper {
  private final JLabel myInformationLabel = new JLabel("#");
  private final JLabel myPackageLabel = new JLabel(CodeInsightBundle.message("dialog.create.class.destination.package.label"));
  private final JTextField myTfPackage = new MyTextField();
  private final FixedSizeButton myPackageChooseButton = new FixedSizeButton(myTfPackage);
  private final JTextField myTfClassName = new MyTextField();
  private final Project myProject;
  private PsiDirectory myTargetDirectory;
  private String myClassName;
  private final boolean myClassNameEditable;

  public CreateClassDialog(Project project,
                           String title,
                           String targetClassName,
                           String targetPackageName,
                           String toCreateKindString,
                           boolean classNameEditable) {
    super(project, true);
    myClassNameEditable = classNameEditable;
    init();
    myClassName = targetClassName;
    myProject = project;

    setTitle(title);

    if (!myClassNameEditable) {
      myInformationLabel.setText(CodeInsightBundle.message("dialog.create.class.name", toCreateKindString, targetClassName));
    }
    else {
      myInformationLabel.setText(CodeInsightBundle.message("dialog.create.class.label", toCreateKindString));
    }

    myTfClassName.setText(myClassName);
    myTfPackage.setText(targetPackageName != null ? targetPackageName : "");
  }


  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myClassNameEditable ? myTfClassName : myTfPackage;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    return panel;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    panel.setBorder(IdeBorderFactory.createBorder());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weightx = myClassNameEditable ? 0 : 1;
    gbConstraints.gridwidth = myClassNameEditable ? 1 : 2;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(myInformationLabel, gbConstraints);

    if (myClassNameEditable) {
      gbConstraints.insets = new Insets(4, 8, 4, 8);
      gbConstraints.gridx = 1;
      gbConstraints.weightx = 1;
      gbConstraints.gridwidth = 1;
      gbConstraints.fill = GridBagConstraints.HORIZONTAL;
      gbConstraints.anchor = GridBagConstraints.WEST;
      panel.add(myTfClassName, gbConstraints);
    }

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 2;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    panel.add(myPackageLabel, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        myPackageChooseButton.doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK)), myTfPackage);

    myPackageChooseButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PackageChooserDialog chooser = new PackageChooserDialog(CodeInsightBundle.message("dialog.create.class.package.chooser.title"), myProject);
        chooser.selectPackage(myTfPackage.getText());
        chooser.show();
        PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          myTfPackage.setText(aPackage.getQualifiedName());
        }
      }
    });
    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(myTfPackage, BorderLayout.CENTER);
    _panel.add(myPackageChooseButton, BorderLayout.EAST);
    panel.add(_panel, gbConstraints);

    return panel;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  private String getPackageName() {
    String name = myTfPackage.getText();
    return name != null ? name.trim() : "";
  }

  private class MyTextField extends JTextField {
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      FontMetrics fontMetrics = getFontMetrics(getFont());
      size.width = fontMetrics.charWidth('a') * 40;
      return size;
    }
  }

  protected void doOKAction() {
    final String packageName = getPackageName();

    final String[] errorString = new String[1];
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          myTargetDirectory = PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, null, true);
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

  public String getClassName() {
    if (myClassNameEditable) {
      return myTfClassName.getText();
    }
    else {
      return myClassName;
    }
  }
}
