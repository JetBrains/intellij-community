package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.components.StorageScheme;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 17, 2007
 */
public class ProjectNameStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");

  private final JPanel myPanel;
  protected final JPanel myAdditionalContentPanel;
  protected NamePathComponent myNamePathComponent;
  protected final WizardContext myWizardContext;
  protected final StepSequence mySequence;
  protected final WizardMode myMode;
  private static final String DIR_BASED = ".idea (directory based)";
  private static final String FILE_BASED = ".ipr (file based)";
  private final JComboBox myStorageFormatCombo = new JComboBox();


  public ProjectNameStep(WizardContext wizardContext, StepSequence sequence, final WizardMode mode) {
    myWizardContext = wizardContext;
    mySequence = sequence;
    myMode = mode;
    myNamePathComponent = new NamePathComponent(
      IdeBundle.message("label.project.name"),
      IdeBundle.message("label.project.files.location"),
      IdeBundle.message("title.select.project.file.directory", IdeBundle.message("project.new.wizard.project.identification")),
      IdeBundle.message("description.select.project.file.directory", StringUtil.capitalize(IdeBundle.message("project.new.wizard.project.identification"))),
      true, false
    );
    final String baseDir = myWizardContext.getProjectFileDirectory();
    final String projectName = myWizardContext.getProjectName();
    final String initialProjectName = projectName != null ? projectName : ProjectWizardUtil.findNonExistingFileName(baseDir, "untitled", "");
    myNamePathComponent.setPath(projectName == null ? (baseDir + File.separator + initialProjectName) : baseDir);
    myNamePathComponent.setNameValue(initialProjectName);
    myNamePathComponent.getNameComponent().setSelectionStart(0);
    myNamePathComponent.getNameComponent().setSelectionEnd(initialProjectName.length());
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 0), 0, 0));
    JPanel projectFileFormatPanel = new JPanel(new BorderLayout());
    myPanel.add(projectFileFormatPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 6, 0, 0), 0, 0));

    myNamePathComponent.setVisible(myWizardContext.getProject() == null);
    projectFileFormatPanel.setVisible(myWizardContext.getProject() == null);

    final JLabel label = new JLabel("Project storage format:");
    label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
    projectFileFormatPanel.add(label, BorderLayout.WEST);
    projectFileFormatPanel.add(myStorageFormatCombo, BorderLayout.CENTER);
    myStorageFormatCombo.insertItemAt(DIR_BASED, 0);
    myStorageFormatCombo.insertItemAt(FILE_BASED, 1);

    myStorageFormatCombo.setSelectedItem(FILE_BASED);

    myAdditionalContentPanel = new JPanel(new GridBagLayout());
    myPanel.add(myAdditionalContentPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
  }
  
  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myWizardContext.setProjectName(getProjectName());
    final String projectFileDirectory = getProjectFileDirectory();
    myWizardContext.setProjectFileDirectory(projectFileDirectory);
    final ProjectBuilder moduleBuilder = myMode.getModuleBuilder();
    myWizardContext.setProjectBuilder(moduleBuilder);
    myWizardContext.setProjectStorageFormat(getSelectedProjectStorageFormat());
    if (moduleBuilder instanceof SourcePathsBuilder) {
      ((SourcePathsBuilder)moduleBuilder).setContentEntryPath(projectFileDirectory);
    }
  }

  private StorageScheme getSelectedProjectStorageFormat() {
    return FILE_BASED.equals(myStorageFormatCombo.getSelectedItem()) ? StorageScheme.DEFAULT : StorageScheme.DIRECTORY_BASED;
  }

  public Icon getIcon() {
    return myWizardContext.getProject() == null ? NEW_PROJECT_ICON : ICON;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.fromCode.name";
  }

  public String getProjectFilePath() {
    if (myWizardContext.getProject() == null) {
      if (getSelectedProjectStorageFormat() == StorageScheme.DEFAULT) {
        return getProjectFileDirectory() + "/" + myNamePathComponent.getNameValue() + ProjectFileType.DOT_DEFAULT_EXTENSION;
      }
      else {
        return getProjectFileDirectory() + "/" + ".idea";
      }
    }
    else {
      return getProjectFileDirectory() + "/" + myNamePathComponent.getNameValue() + ModuleFileType.DOT_DEFAULT_EXTENSION;
    }
  }

  public String getProjectFileDirectory() {
    return myNamePathComponent.getPath();
  }

  public String getProjectName() {
    return myNamePathComponent.getNameValue();
  }

  public boolean validate() throws ConfigurationException {
     final String name = myNamePathComponent.getNameValue();
     if (name.length() == 0) {
       final ApplicationInfo info = ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
       throw new ConfigurationException(IdeBundle.message("prompt.new.project.file.name", info.getVersionName(), myWizardContext.getPresentationName()));
     }

     final String projectFileDirectory = getProjectFileDirectory();
     if (projectFileDirectory.length() == 0) {
       throw new ConfigurationException(IdeBundle.message("prompt.enter.project.file.location", myWizardContext.getPresentationName()));
     }

     final boolean shouldPromptCreation = myNamePathComponent.isPathChangedByUser();
     if (!ProjectWizardUtil
       .createDirectoryIfNotExists(IdeBundle.message("directory.project.file.directory", myWizardContext.getPresentationName()), projectFileDirectory, shouldPromptCreation)) {
       return false;
     }

     boolean shouldContinue = true;
     final File projectFile = new File(getProjectFilePath());
     if (projectFile.exists()) {
       int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", projectFile.getAbsolutePath(), myWizardContext.getPresentationName()),
                                             IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
       shouldContinue = (answer == 0);
     }

     return shouldContinue;
   }
}
