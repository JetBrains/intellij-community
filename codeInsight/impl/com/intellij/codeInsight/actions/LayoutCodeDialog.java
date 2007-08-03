package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class LayoutCodeDialog extends DialogWrapper {
  private final PsiFile myFile;
  @Nullable private final PsiDirectory myDirectory;
  private final Boolean myTextSelected;

  private JRadioButton myRbFile;
  private JRadioButton myRbSelectedText;
  private JRadioButton myRbDirectory;
  private JCheckBox myCbIncludeSubdirs;
  private JCheckBox myCbOptimizeImports;
  public static final @NonNls String OPTIMIZE_IMPORTS_KEY = "LayoutCode.optimizeImports";
  private final String myHelpId;

  public LayoutCodeDialog(Project project,
                          String title,
                          PsiFile file,
                          @Nullable PsiDirectory directory,
                          Boolean isTextSelected,
                          final String helpId) {
    super(project, true);
    myFile = file;
    myDirectory = directory;
    myTextSelected = isTextSelected;

    setOKButtonText(CodeInsightBundle.message("reformat.code.accept.button.text"));
    setTitle(title);
    init();
    myHelpId = helpId;
  }

  protected void init() {
    super.init();

    if (myTextSelected == Boolean.TRUE) {
      myRbSelectedText.setSelected(true);
    }
    else {
      if (myFile != null) {
        myRbFile.setSelected(true);
      }
      else {
        myRbDirectory.setSelected(true);
      }
    }

    myCbIncludeSubdirs.setSelected(true);
    myCbOptimizeImports.setSelected(isOptmizeImportsOptionOn());

    ItemListener listener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateState();
      }
    };
    myRbFile.addItemListener(listener);
    myRbSelectedText.addItemListener(listener);
    myRbDirectory.addItemListener(listener);
    myCbIncludeSubdirs.addItemListener(listener);

    updateState();
  }

  private static boolean isOptmizeImportsOptionOn() {
    return Boolean.toString(true).equals(PropertiesComponent.getInstance().getValue(OPTIMIZE_IMPORTS_KEY));
  }

  private static void setOptimizeImportsOption(boolean state) {
    PropertiesComponent.getInstance().setValue(OPTIMIZE_IMPORTS_KEY, Boolean.toString(state));
  }

  private void updateState() {
    myCbIncludeSubdirs.setEnabled(myRbDirectory.isSelected());
    myCbOptimizeImports.setEnabled(
      !myRbSelectedText.isSelected() && !(myFile != null && !(myFile instanceof PsiJavaFile) && myRbFile.isSelected()));
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 0));
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 3;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.insets = new Insets(0, 0, 0, 0);

    myRbFile = new JRadioButton(CodeInsightBundle.message("process.scope.file",
                                                          (myFile != null ? "'" + myFile.getVirtualFile().getPresentableUrl() + "'" : "")));
    panel.add(myRbFile, gbConstraints);

    myRbSelectedText = new JRadioButton(CodeInsightBundle.message("reformat.option.selected.text"));
    if (myTextSelected != null) {
      gbConstraints.gridy++;
      gbConstraints.insets = new Insets(0, 0, 0, 0);
      panel.add(myRbSelectedText, gbConstraints);
    }

    if (myDirectory != null) {
      myRbDirectory = new JRadioButton(CodeInsightBundle.message("reformat.option.all.files.in.directory",
                                                                 myDirectory.getVirtualFile().getPresentableUrl()));
      gbConstraints.gridy++;
      gbConstraints.insets = new Insets(0, 0, 0, 0);
      panel.add(myRbDirectory, gbConstraints);

      myCbIncludeSubdirs = new JCheckBox(CodeInsightBundle.message("reformat.option.include.subdirectories"));
      if (myDirectory.getSubdirectories().length > 0) {
        gbConstraints.gridy++;
        gbConstraints.insets = new Insets(0, 20, 0, 0);
        panel.add(myCbIncludeSubdirs, gbConstraints);
      }
    }

    myCbOptimizeImports = new JCheckBox(CodeInsightBundle.message("reformat.option.optimize.imports"));
    if (myTextSelected != null) {
      gbConstraints.gridy++;
      gbConstraints.insets = new Insets(0, 0, 0, 0);
      panel.add(myCbOptimizeImports, gbConstraints);
    }

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbFile);
    buttonGroup.add(myRbSelectedText);
    buttonGroup.add(myRbDirectory);

    myRbFile.setEnabled(myFile != null);
    myRbSelectedText.setEnabled(myTextSelected == Boolean.TRUE);

    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;
    gbConstraints.insets = new Insets(0, 4, 0, 0);
    gbConstraints.fill = GridBagConstraints.BOTH;

    return panel;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpId);
  }

  public boolean isProcessSelectedText() {
    return myRbSelectedText.isSelected();
  }

  public boolean isProcessDirectory() {
    return myRbDirectory.isSelected();
  }

  public boolean isIncludeSubdirectories() {
    return myCbIncludeSubdirs.isSelected();
  }

  public boolean isOptimizeImports() {
    return myCbOptimizeImports.isSelected();
  }

  protected void doOKAction() {
    super.doOKAction();
    setOptimizeImportsOption(isOptimizeImports());
  }
}