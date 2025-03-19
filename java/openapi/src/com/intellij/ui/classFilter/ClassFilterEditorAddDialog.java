// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.ui.classFilter;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class ClassFilterEditorAddDialog extends DialogWrapper {
  private final Project myProject;
  private TextFieldWithBrowseButton myClassName;
  private final @Nullable String myHelpId;

  ClassFilterEditorAddDialog(Project project, @Nullable String helpId) {
    super(project, true);
    myProject = project;
    myHelpId = helpId;
    setTitle(JavaBundle.message("class.filter.editor.add.dialog.title"));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JLabel header = new JLabel(JavaBundle.message("label.class.filter.editor.add.dialog.filter.pattern"));
    myClassName = new TextFieldWithBrowseButton(new JTextField(35));
    final JLabel iconLabel = new JLabel(Messages.getQuestionIcon());

    panel.add(header, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 10, 0, 0), 0, 0));
    panel.add(myClassName, new GridBagConstraints(1, 1, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 10, 0, 0), 0, 0));
    panel.add(iconLabel, new GridBagConstraints(0, 0, 1, 2, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(15, 0, 0, 0), 0, 0));

    myClassName.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PsiClass currentClass = getSelectedClass();
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createNoInnerClassesScopeChooser(
          JavaBundle.message("class.filter.editor.choose.class.title"), GlobalSearchScope.allScope(myProject), null, null);
        if (currentClass != null) {
          PsiFile containingFile = currentClass.getContainingFile();
          if (containingFile != null) {
            PsiDirectory containingDirectory = containingFile.getContainingDirectory();
            if (containingDirectory != null) {
              chooser.selectDirectory(containingDirectory);
            }
          }
        }
        chooser.showDialog();
        PsiClass selectedClass = chooser.getSelected();
        if (selectedClass != null) {
          myClassName.setText(selectedClass.getQualifiedName());
        }
      }
    });

    myClassName.setEnabled(myProject != null);

    return panel;
  }

  private PsiClass getSelectedClass() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    String classQName = myClassName.getText();
    if (classQName.isEmpty()) {
      return null;
    }
    return JavaPsiFacade.getInstance(psiManager.getProject()).findClass(classQName, GlobalSearchScope.allScope(myProject));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myClassName.getTextField();
  }

  public String getPattern() {
    return myClassName.getText();
  }

  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.breakpoints.BreakpointsConfigurationDialogFactory.BreakpointsConfigurationDialog.AddFieldBreakpointDialog";
  }

  @Override
  protected @Nullable String getHelpId() {
    return myHelpId;
  }
}
