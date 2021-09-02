// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectStructureElementConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.project.ProjectKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public class ProjectConfigurable extends ProjectStructureElementConfigurable<Project> implements DetailsComponent.Facade {

  private final Project myProject;

  private ProjectConfigurableUi myUi;

  private final StructureConfigurableContext myContext;
  private final ModulesConfigurator myModulesConfigurator;

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
    myUi = new ProjectConfigurableUi(this, project);
    myUi.initComponents(myModulesConfigurator, model);
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
    myDetailsComponent.setContent(myUi.getPanel());
    myDetailsComponent.setText(getBannerSlogan());

    myUi.reloadJdk();

    return myDetailsComponent.getComponent();
  }

  protected boolean isFrozen() {
    return myFreeze;
  }

  @Override
  public void disposeUIResources() {
    myUi.disposeUIResources();
  }

  @Override
  public void reset() {
    myFreeze = true;
    try {
      final String compilerOutput = getOriginalCompilerOutputUrl();
      myUi.reset(compilerOutput);
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

    final String myProjectName = myUi.getProjectName();
    if (StringUtil.isEmptyOrSpaces(myProjectName)) {
      throw new ConfigurationException(JavaUiBundle.message("project.configurable.dialog.message"));
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      // set the output path first so that handlers of RootsChanged event sent after JDK is set
      // would see the updated path
      String canonicalPath = myUi.getProjectCompilerOutput();
      if (canonicalPath.length() > 0) {
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
      LanguageLevel level = myUi.getLanguageLevel();
      if (level != null) {
        extension.setLanguageLevel(level);
      }
      extension.setDefault(myUi.isDefaultLanguageLevel());
      myUi.applyProjectJdkConfigurable();

      if (myProject instanceof ProjectEx) {
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
    return JavaUiBundle.message("project.roots.project.banner.text", myProject.getName());
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
  public boolean isModified() {
    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(myProject);
    if (extension.isDefault() != myUi.isDefaultLanguageLevel() ||
        !extension.isDefault() && !extension.getLanguageLevel().equals(myUi.getLanguageLevel())) {
      return true;
    }
    final String compilerOutput = getOriginalCompilerOutputUrl();
    if (!Comparing.strEqual(FileUtil.toSystemIndependentName(VfsUtilCore.urlToPath(compilerOutput)),
                            FileUtil.toSystemIndependentName(myUi.getProjectCompilerOutput()))) return true;
    if (myUi.isProjectJdkConfigurableModified()) return true;
    if (!getProjectName().equals(myProject.getName())) return true;

    return false;
  }

  @NotNull
  public @NlsSafe String getProjectName() {
    if (ProjectKt.isDirectoryBased(myProject)) {
      @NlsSafe final String text = myUi.getProjectName();
      return text.trim();
    }
    return myProject.getName();
  }

  @Nullable
  private String getOriginalCompilerOutputUrl() {
    final CompilerProjectExtension extension = CompilerProjectExtension.getInstance(myProject);
    return extension != null ? extension.getCompilerOutputUrl() : null;
  }

  public String getCompilerOutputUrl() {
    return VfsUtilCore.pathToUrl(myUi.getProjectCompilerOutput().trim());
  }
}
