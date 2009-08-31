package com.intellij.javadoc;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.help.HelpManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

final class GenerateJavadocDialog extends DialogWrapper {
  private JRadioButton myForProjectRadio;
  private JRadioButton myForPackageRadio;
  private JCheckBox myIncludeSubpackagesCheckBox;
  private final String myPackageName;
  private final JavadocConfigurable myConfigurable;
  private final Project myProject;

  GenerateJavadocDialog(String packageName, Project project, JavadocConfiguration configuration) {
    super(project, true);
    myProject = project;

    myConfigurable = configuration.createConfigurable();

    setOKButtonText(JavadocBundle.message("javadoc.generate.start.button"));
    myPackageName = "".equals(packageName) ? JavadocBundle.message("javadoc.generate.package.default") : packageName;
    setTitle(JavadocBundle.message("javadoc.generate.title"));
    init();
    myConfigurable.reset();
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(4,8,8,0));
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 3;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.insets = new Insets(0,0,0,0);

    myForProjectRadio = new JRadioButton(JavadocBundle.message("javadoc.generate.scope.whole.project"));
    panel.add(myForProjectRadio, gbConstraints);

    myForPackageRadio = new JRadioButton(
      JavadocBundle.message("javadoc.generate.scope.package", (myPackageName != null ? " \"" + myPackageName + "\"" : "")));
    gbConstraints.gridy++;
    gbConstraints.insets = new Insets(0,0,0,0);
    panel.add(myForPackageRadio, gbConstraints);

    myIncludeSubpackagesCheckBox = new JCheckBox(JavadocBundle.message("javadoc.generate.scope.include.subpackages.checkbox"));
    gbConstraints.gridy++;
    gbConstraints.insets = new Insets(0,20,0,0);
    panel.add(myIncludeSubpackagesCheckBox, gbConstraints);


    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myForProjectRadio);
    buttonGroup.add(myForPackageRadio);

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myIncludeSubpackagesCheckBox.setEnabled(myForPackageRadio.isSelected());
      }
    };

    myForProjectRadio.addActionListener(actionListener);
    myForPackageRadio.addActionListener(actionListener);

    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel pane = new JPanel(new BorderLayout());
    pane.setBorder(IdeBorderFactory.createTitledBorder(JavadocBundle.message("javadoc.generate.settings.group")));
    pane.add(myConfigurable.createComponent(), BorderLayout.CENTER);
    return pane;
  }

  void reset() {
    myForPackageRadio.setEnabled(myPackageName != null);
    myForPackageRadio.setSelected(myPackageName != null);
    myForProjectRadio.setEnabled(true);
    myForProjectRadio.setSelected(myPackageName == null);
    myIncludeSubpackagesCheckBox.setSelected(myPackageName != null);
    myIncludeSubpackagesCheckBox.setEnabled(myPackageName != null && myForPackageRadio.isSelected());
  }

  boolean isGenerationForProject() {
    return myForProjectRadio.isSelected();
  }

  boolean isGenerationForPackage() {
    return myForPackageRadio.isSelected();
  }

  boolean isGenerationWithSubpackages() {
    return isGenerationForPackage() && myIncludeSubpackagesCheckBox.isSelected();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction() };
  }

  protected void doOKAction() {
    if (checkOutputDirectory(myConfigurable.getOutputDir())) {
      myConfigurable.apply();
      close(OK_EXIT_CODE);
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.javadocGeneration");
  }

  private boolean checkOutputDirectory(String outputDirectory) {
    if (outputDirectory == null || outputDirectory.trim().length() == 0) {
      Messages.showMessageDialog(myProject, JavadocBundle.message("javadoc.generate.output.directory.not.specified"),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return false;
    }

    File outputDir = new File(outputDirectory);
    if (!outputDir.exists()){
      int choice = Messages.showOkCancelDialog(
        myProject,
        JavadocBundle.message("javadoc.generate.output.directory.not.exists", outputDirectory),
        JavadocBundle.message("javadoc.generate.message.title"),
        Messages.getWarningIcon()
      );
      if (choice != 0) return false;
      if (!outputDir.mkdirs()){
        Messages.showMessageDialog(
          myProject,
          JavadocBundle.message("javadoc.generate.output.directory.creation.failed", outputDirectory),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return false;
      }
    }
    else if (!outputDir.isDirectory()){
      Messages.showMessageDialog(
        myProject,
        JavadocBundle.message("javadoc.generate.output.not.a.directory", outputDirectory),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
      return false;
    }
    return true;
  }

}