/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.newProjectWizard.modes.CreateFromTemplateMode;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

public class ProjectNameWithTypeStep extends ProjectNameStep {
  private JEditorPane myModuleDescriptionPane;
  private JList myTypesList;
  protected JCheckBox myCreateModuleCb;
  private JPanel myModulePanel;
  private JPanel myInternalPanel;
  private JTextField myModuleName;
  private TextFieldWithBrowseButton myModuleContentRoot;
  private TextFieldWithBrowseButton myModuleFileLocation;
  private JPanel myHeader;

  private JLabel myModuleTypeLabel;
  private JLabel myDescriptionLabel;
  private JBScrollPane myModuleTypeScrollPane;
  private JBScrollPane myDescriptionScrollPane;

  private boolean myModuleNameChangedByUser = false;
  private boolean myModuleNameDocListenerEnabled = true;

  private boolean myContentRootChangedByUser = false;
  private boolean myContentRootDocListenerEnabled = true;

  private boolean myImlLocationChangedByUser = false;
  private boolean myImlLocationDocListenerEnabled = true;
  private final StepSequence mySequence;


  public ProjectNameWithTypeStep(final WizardContext wizardContext, StepSequence sequence, final WizardMode mode) {
    super(wizardContext, mode);
    mySequence = sequence;
    myAdditionalContentPanel.add(myModulePanel,
                                 new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.NORTHWEST,
                                                        GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    myHeader.setVisible(myWizardContext.isCreatingNewProject() && !isCreateFromTemplateMode());
    myCreateModuleCb.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        UIUtil.setEnabled(myInternalPanel, myCreateModuleCb.isSelected(), true);
        fireStateChanged();
      }
    });
    myCreateModuleCb.setSelected(true);
    if (!myWizardContext.isCreatingNewProject()){
      myInternalPanel.setBorder(null);
    }
    myModuleDescriptionPane.setContentType(UIUtil.HTML_MIME);
    myModuleDescriptionPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        try {
          BrowserUtil.browse(e.getURL());
        }
        catch (IllegalThreadStateException ex) {
          // it's not a problem
        }
      }
    });
    myModuleDescriptionPane.setEditable(false);

    final DefaultListModel defaultListModel = new DefaultListModel();
    for (ModuleBuilder builder : ModuleBuilder.getAllBuilders()) {
      defaultListModel.addElement(builder);
    }
    myTypesList.setModel(defaultListModel);
    myTypesList.setSelectionModel(new PermanentSingleSelectionModel());
    myTypesList.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final ModuleBuilder builder = (ModuleBuilder)value;
        setIcon(builder.getBigIcon());
        setDisabledIcon(builder.getBigIcon());
        setText(builder.getPresentableName());
        return rendererComponent;
      }
    });
    myTypesList.addListSelectionListener(new ListSelectionListener() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }

        final ModuleBuilder typeSelected = (ModuleBuilder)myTypesList.getSelectedValue();

        final StringBuilder sb = new StringBuilder("<html><body><font face=\"Verdana\" ");
        sb.append(SystemInfo.isMac ? "" : "size=\"-1\"").append('>');
        sb.append(typeSelected.getDescription()).append("</font></body></html>");

        myModuleDescriptionPane.setText(sb.toString());

        boolean focusOwner = myTypesList.isFocusOwner();
        fireStateChanged();
        if (focusOwner) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myTypesList.requestFocusInWindow();
            }
          });
        }
      }
    });
    myTypesList.setSelectedIndex(0);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        myWizardContext.requestNextStep();
        return true;
      }
    }.installOn(myTypesList);

    final Dimension preferredSize = calcTypeListPreferredSize(ModuleBuilder.getAllBuilders());
    final JBScrollPane pane = IJSwingUtilities.findParentOfType(myTypesList, JBScrollPane.class);
    pane.setPreferredSize(preferredSize);
    pane.setMinimumSize(preferredSize);

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
        String path = getDefaultBaseDir(wizardContext);
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
          setImlFileLocation(myModuleContentRoot.getText());
        }
        if (!myModuleNameChangedByUser) {
          final String path = FileUtil.toSystemIndependentName(myModuleContentRoot.getText());
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
    if (wizardContext.isCreatingNewProject()) {
      setModuleName(myNamePathComponent.getNameValue());
      setModuleContentRoot(myNamePathComponent.getPath());
      setImlFileLocation(myNamePathComponent.getPath());
    } else {
      final Project project = wizardContext.getProject();
      assert project != null;
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) { //e.g. was deleted
        final String baseDirPath = baseDir.getPath();
        String moduleName = ProjectWizardUtil.findNonExistingFileName(baseDirPath, "untitled", "");
        String contentRoot = baseDirPath + "/" + moduleName;
        if (!Comparing.strEqual(project.getName(), wizardContext.getProjectName()) && !wizardContext.isCreatingNewProject() && wizardContext.getProjectName() != null) {
          moduleName = ProjectWizardUtil.findNonExistingFileName(wizardContext.getProjectFileDirectory(), wizardContext.getProjectName(), "");
          contentRoot = wizardContext.getProjectFileDirectory();
        }
        setModuleName(moduleName);
        setModuleContentRoot(contentRoot);
        setImlFileLocation(contentRoot);
        myModuleName.select(0, moduleName.length());
      }
    }


    if (isCreateFromTemplateMode()) {
      replaceModuleTypeOptions(new JPanel());
    }
    else {
      final AnAction arrow = new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (e.getInputEvent() instanceof KeyEvent) {
            final int code = ((KeyEvent)e.getInputEvent()).getKeyCode();
            if (!myCreateModuleCb.isSelected()) return;
            int i = myTypesList.getSelectedIndex();
            if (code == KeyEvent.VK_DOWN) {
              if (++i == myTypesList.getModel().getSize()) return;
            }
            else if (code == KeyEvent.VK_UP) {
              if (--i == -1) return;
            }
            myTypesList.setSelectedIndex(i);
          }
        }
      };
      CustomShortcutSet shortcutSet = new CustomShortcutSet(KeyEvent.VK_UP, KeyEvent.VK_DOWN);
      arrow.registerCustomShortcutSet(shortcutSet, myNamePathComponent.getNameComponent());
      arrow.registerCustomShortcutSet(shortcutSet, myModuleName);
    }
  }

  private boolean isCreateFromTemplateMode() {
    return myMode instanceof CreateFromTemplateMode;
  }

  private Dimension calcTypeListPreferredSize(final List<ModuleBuilder> allModuleTypes) {
    int width = 0;
    int height = 0;
    final FontMetrics fontMetrics = myTypesList.getFontMetrics(myTypesList.getFont());
    final int fontHeight = fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent();
    for (final ModuleBuilder type : allModuleTypes) {
      final Icon icon = type.getBigIcon();
      final int iconHeight = icon != null ? icon.getIconHeight() : 0;
      final int iconWidth = icon != null ? icon.getIconWidth() : 0;
      height += Math.max(iconHeight, fontHeight) + 6;
      width = Math.max(width, iconWidth + fontMetrics.stringWidth(type.getPresentableName()) + 10);
    }
    return new Dimension(width, height);
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

  @Override
  public boolean isStepVisible() {
    return true;
  }

  public JComponent getPreferredFocusedComponent() {
    return myWizardContext.isCreatingNewProject() ? super.getPreferredFocusedComponent() : myModuleName;
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

  private void setModuleName(String moduleName) {
    myModuleNameDocListenerEnabled = false;
    myModuleName.setText(moduleName);
    myModuleNameDocListenerEnabled = true;
  }

  public void updateStep() {
    super.updateStep();
    if (!isCreateFromTemplateMode()) {
      if (myCreateModuleCb.isSelected()) {
        mySequence.setType(getSelectedBuilderId());
      } else {
        mySequence.setType(null);
      }
    }
  }

  public void updateDataModel() {

    if (!isCreateFromTemplateMode()) {
      mySequence.setType(myCreateModuleCb.isSelected() ? getSelectedBuilderId() : null);
    }
    super.updateDataModel();

    if (!isCreateFromTemplateMode() && myCreateModuleCb.isSelected()) {
      final ModuleBuilder builder = (ModuleBuilder)myMode.getModuleBuilder();
      assert builder != null;
      final String moduleName = getModuleName();
      builder.setName(moduleName);
      builder.setModuleFilePath(
        FileUtil.toSystemIndependentName(myModuleFileLocation.getText()) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      builder.setContentEntryPath(FileUtil.toSystemIndependentName(myModuleContentRoot.getText()));
    }
  }

  protected String getSelectedBuilderId() {
    return ((ModuleBuilder)myTypesList.getSelectedValue()).getBuilderId();
  }

  public boolean validate() throws ConfigurationException {
    if (myCreateModuleCb.isSelected() || !myWizardContext.isCreatingNewProject()) {
      if (validateModulePaths()) return false;
    }
    if (!myWizardContext.isCreatingNewProject()) {
      validateExistingModuleName();
    }
    return !myWizardContext.isCreatingNewProject() || super.validate();
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
      return true;
    }
    if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.content.root"), myModuleContentRoot.getText(),
                                                      myContentRootChangedByUser)) {
      return true;
    }

    File moduleFile = new File(moduleFileDirectory, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    if (moduleFile.exists()) {
      int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", moduleFile.getAbsolutePath(),
                                                              IdeBundle.message("project.new.wizard.module.identification")),
                                            IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
      if (answer != 0) {
        return true;
      }
    }
    return false;
  }

  private static class PermanentSingleSelectionModel extends DefaultListSelectionModel {
    public PermanentSingleSelectionModel() {
      super.setSelectionMode(SINGLE_SELECTION);
    }

    public final void setSelectionMode(int selectionMode) {
    }

    public final void removeSelectionInterval(int index0, int index1) {
    }
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch";
  }

  protected String getModuleName() {
    return myModuleName.getText().trim();
  }
  
  protected void addModuleNameListener(final DocumentListener listener) {
    myModuleName.getDocument().addDocumentListener(listener);
  }

  protected void replaceModuleTypeOptions(final Component component) {
    myModuleTypeLabel.setVisible(false);
    myDescriptionLabel.setVisible(false);
    myModuleTypeScrollPane.setVisible(false);
    myDescriptionScrollPane.setVisible(false);
    myInternalPanel.add(component, new GridBagConstraints(0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                          new Insets(10, 0, 0, 0), 0, 0));
  }

  @Override
  public String getName() {
    return "Name and Type";
  }
}
