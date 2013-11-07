/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.templates.TemplateModuleBuilder;
import com.intellij.projectImport.ProjectFormatPanel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;

/**
 * @author Dmitry Avdeev
 *         Date: 9/26/12
 */
public class SelectTemplateStep extends ModuleWizardStep implements SettingsStep {

  private JBList myTemplatesList;
  private JPanel mySettingsPanel;
  private JTextPane myDescriptionPane;
  private JPanel myDescriptionPanel;

  private JPanel myExpertPlaceholder;
  private JPanel myExpertPanel;
  private final HideableDecorator myExpertDecorator;

  private final NamePathComponent myNamePathComponent;
  private final ProjectFormatPanel myFormatPanel;

  private JTextField myModuleName;
  private TextFieldWithBrowseButton myModuleContentRoot;
  private TextFieldWithBrowseButton myModuleFileLocation;
  private JPanel myModulePanel;

  private JPanel myPanel;

  private boolean myModuleNameChangedByUser = false;
  private boolean myModuleNameDocListenerEnabled = true;

  private boolean myContentRootChangedByUser = false;
  private boolean myContentRootDocListenerEnabled = true;

  private boolean myImlLocationChangedByUser = false;
  private boolean myImlLocationDocListenerEnabled = true;

  private final WizardContext myWizardContext;
  private final StepSequence mySequence;
  @Nullable
  private ModuleWizardStep mySettingsStep;

  private final ProjectTypesList myList;

  @Nullable
  private AbstractModuleBuilder myModuleBuilder;

  public SelectTemplateStep(WizardContext context, StepSequence sequence, final MultiMap<TemplatesGroup, ProjectTemplate> map) {

    myWizardContext = context;
    mySequence = sequence;
    Messages.installHyperlinkSupport(myDescriptionPane);

    myFormatPanel = new ProjectFormatPanel();
    myNamePathComponent = NamePathComponent.initNamePathComponent(context);
    if (context.isCreatingNewProject()) {
      mySettingsPanel.add(myNamePathComponent, BorderLayout.NORTH);
      addExpertPanel(myModulePanel);
    }
    else {
      mySettingsPanel.add(myModulePanel, BorderLayout.NORTH);
    }
    bindModuleSettings();

    myExpertDecorator = new HideableDecorator(myExpertPlaceholder, "Mor&e Settings", false);
    myExpertPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, IdeBorderFactory.TITLED_BORDER_INDENT, 5, 0));
    myExpertDecorator.setContentComponent(myExpertPanel);

    myList = new ProjectTypesList(myTemplatesList, map, context);
    myList.installKeyAction(getNameComponent());

    myTemplatesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

      @Override
      public void valueChanged(ListSelectionEvent e) {
        ProjectTemplate template = getSelectedTemplate();
        boolean loading = template instanceof LoadingProjectTemplate;
        myModuleBuilder = template == null || loading ? null : template.createModuleBuilder();
        setupPanels(template);
        mySequence.setType(myModuleBuilder == null ? null : myModuleBuilder.getBuilderId());
        myWizardContext.requestWizardButtonsUpdate();
      }
    });

    if (myWizardContext.isCreatingNewProject()) {
      addProjectFormat(myModulePanel);
    }
  }

  private JTextField getNameComponent() {
    return myWizardContext.isCreatingNewProject() ? myNamePathComponent.getNameComponent() : myModuleName;
  }

  private void addProjectFormat(JPanel panel) {
    addField("Project \u001bformat:", myFormatPanel.getStorageFormatComboBox(), panel);
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myList);
  }

  @Override
  public String getHelpId() {
    String helpId = myWizardContext.isCreatingNewProject() ? "New_Project_Main_Settings" : "Add_Module_Main_Settings";
    ProjectTemplate projectTemplate = getSelectedTemplate();
    if (projectTemplate instanceof WebProjectTemplate) {
      WebProjectTemplate webProjectTemplate = (WebProjectTemplate) projectTemplate;
      String subHelpId = webProjectTemplate.getHelpId();
      if (subHelpId != null) {
        helpId = helpId + ":" + subHelpId;
      }
    }
    return helpId;
  }

  private void setupPanels(@Nullable ProjectTemplate template) {

    restorePanel(myNamePathComponent, 4);
    restorePanel(myModulePanel, myWizardContext.isCreatingNewProject() ? 8 : 6);
    restorePanel(myExpertPanel, myWizardContext.isCreatingNewProject() ? 1 : 0);

    if (mySettingsStep != null) mySettingsStep.disposeUIResources();
    mySettingsStep = myModuleBuilder == null ? null : myModuleBuilder.modifySettingsStep(this);

    String description = null;
    if (template != null) {
      description = template.getDescription();
      if (StringUtil.isNotEmpty(description)) {
        StringBuilder sb = new StringBuilder("<html><body><font ");
        sb.append(SystemInfo.isMac ? "" : "face=\"Verdana\" size=\"-1\"").append('>');
        sb.append(description).append("</font></body></html>");
        description = sb.toString();
        myDescriptionPane.setText(description);
      }
    }

    myExpertPlaceholder.setVisible(!(myModuleBuilder instanceof TemplateModuleBuilder) && myExpertPanel.getComponentCount() > 0);
    for (int i = 0; i < 6; i++) {
      myModulePanel.getComponent(i).setVisible(!(myModuleBuilder instanceof EmptyModuleBuilder));
    }
    myDescriptionPanel.setVisible(StringUtil.isNotEmpty(description));

    mySettingsPanel.revalidate();
    mySettingsPanel.repaint();
  }

  private static int restorePanel(JPanel component, int i) {
    int removed = 0;
    while (component.getComponentCount() > i) {
      component.remove(component.getComponentCount() - 1);
      removed++;
    }
    return removed;
  }

  @Override
  public void updateStep() {
    myList.resetSelection();
    myExpertDecorator.setOn(SelectTemplateSettings.getInstance().EXPERT_MODE);
  }

  @Override
  public void onStepLeaving() {
    SelectTemplateSettings settings = SelectTemplateSettings.getInstance();
    settings.EXPERT_MODE = myExpertDecorator.isExpanded();
    myList.saveSelection();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    ProjectTemplate template = getSelectedTemplate();
    if (template == null) {
      throw new ConfigurationException(StringUtil.capitalize(ProjectBundle.message("project.new.wizard.from.template.error", myWizardContext.getPresentationName())), "Error");
    }

    if (myWizardContext.isCreatingNewProject()) {
      if (!myNamePathComponent.validateNameAndPath(myWizardContext, myFormatPanel.isDefault())) return false;
    }

    if (!validateModulePaths()) return false;
    if (!myWizardContext.isCreatingNewProject()) {
      validateExistingModuleName();
    }

    ValidationInfo info = template.validateSettings();
    if (info != null) {
      throw new ConfigurationException(info.message, "Error");
    }
    if (mySettingsStep != null) {
      return mySettingsStep.validate();
    }
    return true;
  }

  @Nullable
  public ProjectTemplate getSelectedTemplate() {
    return myList.getSelectedTemplate();
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return getNameComponent();
  }

  @Override
  public void updateDataModel() {

    myWizardContext.setProjectBuilder(myModuleBuilder);
    myWizardContext.setProjectName(myNamePathComponent.getNameValue());
    myWizardContext.setProjectFileDirectory(myNamePathComponent.getPath());
    myFormatPanel.updateData(myWizardContext);

    if (myModuleBuilder != null) {
      final String moduleName = getModuleName();
      myModuleBuilder.setName(moduleName);
      myModuleBuilder.setModuleFilePath(
        FileUtil.toSystemIndependentName(myModuleFileLocation.getText()) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      myModuleBuilder.setContentEntryPath(FileUtil.toSystemIndependentName(getModuleContentRoot()));
      if (myModuleBuilder instanceof TemplateModuleBuilder) {
        myWizardContext.setProjectStorageFormat(StorageScheme.DIRECTORY_BASED);
      }
    }

    if (mySettingsStep != null) {
      mySettingsStep.updateDataModel();
    }
  }

  @Override
  public String getName() {
    return "Template Type";
  }

  @Override
  public WizardContext getContext() {
    return myWizardContext;
  }

  @Override
  public void addSettingsField(@NotNull String label, @NotNull JComponent field) {
    JPanel panel = myWizardContext.isCreatingNewProject() ? myNamePathComponent : myModulePanel;
    addField(label, field, panel);
  }

  private static void addField(String label, JComponent field, JPanel panel) {
    JLabel jLabel = new JBLabel(label);
    jLabel.setLabelFor(field);
    panel.add(jLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.WEST,
                                                 GridBagConstraints.NONE, new Insets(0, 0, 5, 0), 0, 0));
    panel.add(field, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                GridBagConstraints.HORIZONTAL, new Insets(0, 0, 5, 0), 0, 0));
  }

  @Override
  public void addSettingsComponent(@NotNull JComponent component) {
    JPanel panel = myWizardContext.isCreatingNewProject() ? myNamePathComponent : myModulePanel;
    panel.add(component, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
  }

  @Override
  public void addExpertPanel(@NotNull JComponent panel) {
    myExpertPanel.add(panel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                    GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
  }

  @Override
  public void addExpertField(@NotNull String label, @NotNull JComponent field) {
    JPanel panel = myWizardContext.isCreatingNewProject() ? myModulePanel : myExpertPanel;
    addField(label, field, panel);
  }

  public void bindModuleSettings() {

    myNamePathComponent.getNameComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myModuleNameChangedByUser) {
          setModuleName(myNamePathComponent.getNameValue());
        }
      }
    });

    myModuleContentRoot.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.content.root.chooser.title"), ProjectBundle.message("project.new.wizard.module.content.root.chooser.description"),
                                                myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);

    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myContentRootChangedByUser) {
          setModuleContentRoot(myNamePathComponent.getPath());
        }
      }
    });
    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myModuleNameDocListenerEnabled) {
          myModuleNameChangedByUser = true;
        }
        String path = getDefaultBaseDir(myWizardContext);
        final String moduleName = getModuleName();
        if (path.length() > 0 && !Comparing.strEqual(moduleName, myNamePathComponent.getNameValue())) {
          path += "/" + moduleName;
        }
        if (!myContentRootChangedByUser) {
          final boolean f = myModuleNameChangedByUser;
          myModuleNameChangedByUser = true;
          setModuleContentRoot(path);
          myModuleNameChangedByUser = f;
        }
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(path);
        }
      }
    });
    myModuleContentRoot.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myContentRootDocListenerEnabled) {
          myContentRootChangedByUser = true;
        }
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(getModuleContentRoot());
        }
        if (!myModuleNameChangedByUser) {
          final String path = FileUtil.toSystemIndependentName(getModuleContentRoot());
          final int idx = path.lastIndexOf("/");

          boolean f = myContentRootChangedByUser;
          myContentRootChangedByUser = true;

          boolean i = myImlLocationChangedByUser;
          myImlLocationChangedByUser = true;

          setModuleName(idx >= 0 ? path.substring(idx + 1) : "");

          myContentRootChangedByUser = f;
          myImlLocationChangedByUser = i;
        }
      }
    });

    myModuleFileLocation.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.file.chooser.title"), ProjectBundle.message("project.new.wizard.module.file.description"),
                                                 myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    myModuleFileLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myImlLocationDocListenerEnabled) {
          myImlLocationChangedByUser = true;
        }
      }
    });
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(myNamePathComponent.getPath());
        }
      }
    });
    if (myWizardContext.isCreatingNewProject()) {
      setModuleName(myNamePathComponent.getNameValue());
      setModuleContentRoot(myNamePathComponent.getPath());
      setImlFileLocation(myNamePathComponent.getPath());
    } else {
      final Project project = myWizardContext.getProject();
      assert project != null;
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) { //e.g. was deleted
        final String baseDirPath = baseDir.getPath();
        String moduleName = ProjectWizardUtil.findNonExistingFileName(baseDirPath, "untitled", "");
        String contentRoot = baseDirPath + "/" + moduleName;
        if (!Comparing.strEqual(project.getName(), myWizardContext.getProjectName()) && !myWizardContext.isCreatingNewProject() && myWizardContext.getProjectName() != null) {
          moduleName = ProjectWizardUtil.findNonExistingFileName(myWizardContext.getProjectFileDirectory(), myWizardContext.getProjectName(), "");
          contentRoot = myWizardContext.getProjectFileDirectory();
        }
        setModuleName(moduleName);
        setModuleContentRoot(contentRoot);
        setImlFileLocation(contentRoot);
        myModuleName.select(0, moduleName.length());
      }
    }
  }

  private void validateExistingModuleName() throws ConfigurationException {
    final String moduleName = getModuleName();
    final Module module;
    final ProjectStructureConfigurable fromConfigurable = ProjectStructureConfigurable.getInstance(myWizardContext.getProject());
    if (fromConfigurable != null) {
      module = fromConfigurable.getModulesConfig().getModule(moduleName);
    }
    else {
      module = ModuleManager.getInstance(myWizardContext.getProject()).findModuleByName(moduleName);
    }
    if (module != null) {
      throw new ConfigurationException("Module \'" + moduleName + "\' already exist in project. Please, specify another name.");
    }
  }

  private boolean validateModulePaths() throws ConfigurationException {
    final String moduleName = getModuleName();
    final String moduleFileDirectory = myModuleFileLocation.getText();
    if (moduleFileDirectory.length() == 0) {
      throw new ConfigurationException("Enter module file location");
    }
    if (moduleName.length() == 0) {
      throw new ConfigurationException("Enter a module name");
    }

    if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.file"), moduleFileDirectory,
                                                      myImlLocationChangedByUser)) {
      return false;
    }
    if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.content.root"), myModuleContentRoot.getText(),
                                                      myContentRootChangedByUser)) {
      return false;
    }

    File moduleFile = new File(moduleFileDirectory, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    if (moduleFile.exists()) {
      int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", moduleFile.getAbsolutePath(),
                                                              IdeBundle.message("project.new.wizard.module.identification")),
                                            IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
      if (answer != 0) {
        return false;
      }
    }
    return true;
  }

  protected String getModuleContentRoot() {
    return myModuleContentRoot.getText();
  }

  private String getDefaultBaseDir(WizardContext wizardContext) {
    if (wizardContext.isCreatingNewProject()) {
      return myNamePathComponent.getPath();
    } else {
      final Project project = wizardContext.getProject();
      assert project != null;
      final VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) {
        return baseDir.getPath();
      }
      return "";
    }
  }

  private void setImlFileLocation(final String path) {
    myImlLocationDocListenerEnabled = false;
    myModuleFileLocation.setText(FileUtil.toSystemDependentName(path));
    myImlLocationDocListenerEnabled = true;
  }

  private void setModuleContentRoot(final String path) {
    myContentRootDocListenerEnabled = false;
    myModuleContentRoot.setText(FileUtil.toSystemDependentName(path));
    myContentRootDocListenerEnabled = true;
  }

  public void setModuleName(String moduleName) {
    myModuleNameDocListenerEnabled = false;
    myModuleName.setText(moduleName);
    myModuleNameDocListenerEnabled = true;
  }

  @Override
  @NotNull
  public JTextField getModuleNameField() {
    return myModuleName;
  }

  protected String getModuleName() {
    return myModuleName.getText().trim();
  }

  @TestOnly
  public boolean setSelectedTemplate(String group, String name) {
    return myList.setSelectedTemplate(group, name);
  }

  @TestOnly
  @Nullable
  public ModuleWizardStep getSettingsStep() {
    return mySettingsStep;
  }

  @Override
  public Icon getIcon() {
    return myWizardContext.getStepIcon();
  }
}
