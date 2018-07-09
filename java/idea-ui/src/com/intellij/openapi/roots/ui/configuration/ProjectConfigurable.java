// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.core.JavaCoreBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectStructureElementConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.project.ProjectKt;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public class ProjectConfigurable extends ProjectStructureElementConfigurable<Project> implements DetailsComponent.Facade {

  private final Project myProject;

  private LanguageLevelCombo myLanguageLevelCombo;
  private ProjectJdkConfigurable myProjectJdkConfigurable;

  private FieldPanel myProjectCompilerOutput;

  private JTextField myProjectName;

  private JPanel myPanel;

  private final StructureConfigurableContext myContext;
  private final ModulesConfigurator myModulesConfigurator;
  private JPanel myWholePanel;

  private boolean myFreeze = false;
  private DetailsComponent myDetailsComponent;
  private final GeneralProjectSettingsElement mySettingsElement;

  public ProjectConfigurable(Project project,
                             final StructureConfigurableContext context,
                             ModulesConfigurator configurator,
                             ProjectSdksModel model) {
    myProject = project;
    myContext = context;
    myModulesConfigurator = configurator;
    mySettingsElement = new GeneralProjectSettingsElement(context);
    final ProjectStructureDaemonAnalyzer daemonAnalyzer = context.getDaemonAnalyzer();
    myModulesConfigurator.addAllModuleChangeListener(new ModuleEditor.ChangeListener() {
      @Override
      public void moduleStateChanged(ModifiableRootModel moduleRootModel) {
        daemonAnalyzer.queueUpdate(mySettingsElement);
      }
    });
    init(model);
  }

  @Override
  public ProjectStructureElement getProjectStructureElement() {
    return mySettingsElement;
  }

  @Override
  public DetailsComponent getDetailsComponent() {
    return myDetailsComponent;
  }

  @Override
  public JComponent createOptionsPanel() {
    myDetailsComponent = new DetailsComponent(false, false);
    myDetailsComponent.setContent(myPanel);
    myDetailsComponent.setText(getBannerSlogan());

    myProjectJdkConfigurable.createComponent(); //reload changed jdks

    return myDetailsComponent.getComponent();
  }

  private void init(final ProjectSdksModel model) {
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setPreferredSize(JBUI.size(700, 500));

    if (ProjectKt.isDirectoryBased(myProject)) {
      final JPanel namePanel = new JPanel(new BorderLayout());
      final JLabel label =
        new JLabel("<html><body><b>Project name:</b></body></html>", SwingConstants.LEFT);
      namePanel.add(label, BorderLayout.NORTH);

      myProjectName = new JTextField();
      myProjectName.setColumns(40);

      final JPanel nameFieldPanel = new JPanel();
      nameFieldPanel.setLayout(new BoxLayout(nameFieldPanel, BoxLayout.X_AXIS));
      nameFieldPanel.add(Box.createHorizontalStrut(4));
      nameFieldPanel.add(myProjectName);

      namePanel.add(nameFieldPanel, BorderLayout.CENTER);
      final JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      wrapper.add(namePanel);
      wrapper.setAlignmentX(0);
      myPanel.add(wrapper, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0,
                                                  GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                  JBUI.insets(4, 0, 10, 0), 0, 0));
    }

    myProjectJdkConfigurable = new ProjectJdkConfigurable(myProject, model);
    myPanel.add(myProjectJdkConfigurable.createComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0,
                                                                                   GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                                                   JBUI.insetsTop(4), 0, 0));

    myPanel.add(myWholePanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                                     GridBagConstraints.NONE, JBUI.insetsTop(4), 0, 0));

    myPanel.setBorder(new EmptyBorder(0, 10, 0, 10));
    myProjectCompilerOutput.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myFreeze) return;
        myModulesConfigurator.processModuleCompilerOutputChanged(getCompilerOutputUrl());
      }
    });
    myProjectJdkConfigurable.addChangeListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myLanguageLevelCombo.sdkUpdated(myProjectJdkConfigurable.getSelectedProjectJdk(), myProject.isDefault());
        LanguageLevelProjectExtensionImpl.getInstanceImpl(myProject).setCurrentLevel(myLanguageLevelCombo.getSelectedLevel());
      }
    });
    myLanguageLevelCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        LanguageLevelProjectExtensionImpl.getInstanceImpl(myProject).setCurrentLevel(myLanguageLevelCombo.getSelectedLevel());
      }
    });
  }

  @Override
  public void disposeUIResources() {
    if (myProjectJdkConfigurable != null) {
      myProjectJdkConfigurable.disposeUIResources();
    }
  }

  @Override
  public void reset() {
    myFreeze = true;
    try {
      myProjectJdkConfigurable.reset();
      final String compilerOutput = getOriginalCompilerOutputUrl();
      if (compilerOutput != null) {
        myProjectCompilerOutput.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(compilerOutput)));
      }
      myLanguageLevelCombo.reset(myProject);

      if (myProjectName != null) {
        myProjectName.setText(myProject.getName());
      }
    }
    finally {
      myFreeze = false;
    }

    myContext.getDaemonAnalyzer().queueUpdate(mySettingsElement);
  }


  @Override
  public void apply() throws ConfigurationException {
    final CompilerProjectExtension compilerProjectExtension = CompilerProjectExtension.getInstance(myProject);
    assert compilerProjectExtension != null : myProject;

    if (myProjectName != null && StringUtil.isEmptyOrSpaces(myProjectName.getText())) {
      throw new ConfigurationException("Please, specify project name!");
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      // set the output path first so that handlers of RootsChanged event sent after JDK is set
      // would see the updated path
      String canonicalPath = myProjectCompilerOutput.getText();
      if (canonicalPath != null && canonicalPath.length() > 0) {
        try {
          canonicalPath = FileUtil.resolveShortWindowsName(canonicalPath);
        }
        catch (IOException e) {
          //file doesn't exist yet
        }
        canonicalPath = FileUtil.toSystemIndependentName(canonicalPath);
        compilerProjectExtension.setCompilerOutputUrl(VfsUtilCore.pathToUrl(canonicalPath));
      }
      else {
        compilerProjectExtension.setCompilerOutputPointer(null);
      }

      LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(myProject);
      LanguageLevel level = myLanguageLevelCombo.getSelectedLevel();
      if (level != null) {
        extension.setLanguageLevel(level);
      }
      extension.setDefault(myLanguageLevelCombo.isDefault());
      myProjectJdkConfigurable.apply();

      if (myProjectName != null) {
        ((ProjectEx)myProject).setProjectName(getProjectName());
        if (myDetailsComponent != null) myDetailsComponent.setText(getBannerSlogan());
      }
    });
  }


  @Override
  public void setDisplayName(final String name) {
    //do nothing
  }

  @Override
  public Project getEditableObject() {
    return myProject;
  }

  @Override
  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.project.banner.text", myProject.getName());
  }

  @Override
  public String getDisplayName() {
    return ProjectBundle.message("project.roots.project.display.name");
  }

  @Override
  public Icon getIcon(boolean open) {
    return AllIcons.Nodes.Project;
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.general";
  }


  @Override
  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isModified() {
    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(myProject);
    if (extension.isDefault() != myLanguageLevelCombo.isDefault() ||
        !extension.isDefault() && !extension.getLanguageLevel().equals(myLanguageLevelCombo.getSelectedLevel())) {
      return true;
    }
    final String compilerOutput = getOriginalCompilerOutputUrl();
    if (!Comparing.strEqual(FileUtil.toSystemIndependentName(VfsUtilCore.urlToPath(compilerOutput)),
                            FileUtil.toSystemIndependentName(myProjectCompilerOutput.getText()))) return true;
    if (myProjectJdkConfigurable.isModified()) return true;
    if (!getProjectName().equals(myProject.getName())) return true;

    return false;
  }

  @NotNull
  public String getProjectName() {
    return myProjectName != null ? myProjectName.getText().trim() : myProject.getName();
  }

  @Nullable
  private String getOriginalCompilerOutputUrl() {
    final CompilerProjectExtension extension = CompilerProjectExtension.getInstance(myProject);
    return extension != null ? extension.getCompilerOutputUrl() : null;
  }

  private void createUIComponents() {
    myLanguageLevelCombo = new LanguageLevelCombo(JavaCoreBundle.message("default.language.level.description")) {
      @Override
      protected LanguageLevel getDefaultLevel() {
        Sdk sdk = myProjectJdkConfigurable.getSelectedProjectJdk();
        if (sdk == null) return null;
        JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
        return version == null ? null : version.getMaxLanguageLevel();
      }
    };
    final JTextField textField = new ExtendableTextField();
    final FileChooserDescriptor outputPathsChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);
    outputPathsChooserDescriptor.setHideIgnored(false);
    BrowseFilesListener listener = new BrowseFilesListener(textField, "", ProjectBundle.message("project.compiler.output"), outputPathsChooserDescriptor);
    myProjectCompilerOutput = new FieldPanel(textField, null, null, listener, EmptyRunnable.getInstance());
    FileChooserFactory.getInstance().installFileCompletion(myProjectCompilerOutput.getTextField(), outputPathsChooserDescriptor, true, null);
  }

  public String getCompilerOutputUrl() {
    return VfsUtilCore.pathToUrl(myProjectCompilerOutput.getText().trim());
  }
}
