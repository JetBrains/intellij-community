// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.DeprecatedProjectBuilderForImport;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ImportModuleAction extends AnAction implements NewProjectOrModuleAction {

  private static final String LAST_IMPORTED_LOCATION = "last.imported.location";
  private static final Logger LOG = Logger.getInstance(ImportModuleAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    doImport(getEventProject(e));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(getEventProject(e) != null);
    NewProjectAction.updateActionText(this, e);
  }

  @NotNull
  @Override
  public String getActionText(boolean isInNewSubmenu, boolean isInJavaIde) {
    return JavaUiBundle.message("import.module.action.text", isInNewSubmenu ? 1 : 0, isInJavaIde ? 1 : 0);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  public static List<Module> doImport(@Nullable Project project) {
    AddModuleWizard wizard = selectFileAndCreateWizard(project, null);

    if (wizard == null || wizard.getStepCount() > 0 && !wizard.showAndGet()) {
      return Collections.emptyList();
    }
    return createFromWizard(project, wizard);
  }

  public static List<Module> createFromWizard(@Nullable Project project, AbstractProjectWizard wizard) {
    try {
      return doCreateFromWizard(project, wizard);
    }
    finally {
      wizard.disposeIfNeeded();
    }
  }

  private static List<Module> doCreateFromWizard(@Nullable Project project, AbstractProjectWizard wizard) {
    final ProjectBuilder projectBuilder = wizard.getProjectBuilder();
    if (project == null) {
      Project newProject;
      if (projectBuilder instanceof DeprecatedProjectBuilderForImport) {
        // The path to remove import action
        newProject = openProject((DeprecatedProjectBuilderForImport)projectBuilder, wizard.getNewProjectFilePath());
      }
      else {
        newProject = NewProjectUtil.createFromWizard(wizard);
      }
      return newProject == null ? Collections.emptyList() : Arrays.asList(ModuleManager.getInstance(newProject).getModules());
    }

    try {
      if (wizard.getStepCount() > 0) {
        Module module = new NewModuleAction().createModuleFromWizard(project, null, wizard);
        return Collections.singletonList(module);
      }
      else {
        if (!projectBuilder.validate(project, project)) {
          return Collections.emptyList();
        }
        return projectBuilder.commit(project);
      }
    }
    finally {
      if (projectBuilder != null) {
        projectBuilder.cleanup();
      }
    }
  }

  @Nullable
  private static Project openProject(@NotNull DeprecatedProjectBuilderForImport projectBuilder, @NotNull String projectPath) {
    VirtualFile file = ProjectUtil.getFileAndRefresh(Paths.get(projectPath));
    if (file == null) {
      LOG.warn(String.format("Cannot find project file in vfs `%s`", projectPath));
      return null;
    }
    return projectBuilder.getProjectOpenProcessor().doOpenProject(file, null, false);
  }

  @Nullable
  public static AddModuleWizard selectFileAndCreateWizard(@Nullable Project project, @Nullable Component dialogParent) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    descriptor.setHideIgnored(false);
    descriptor.setTitle(JavaUiBundle.message("chooser.title.select.file.or.directory.to.import"));
    List<ProjectImportProvider> providers = getProviders(project);
    String description = getFileChooserDescription(providers);
    descriptor.setDescription(description);
    return selectFileAndCreateWizard(project, dialogParent, descriptor, providers.toArray(new ProjectImportProvider[0]));
  }

  @Nullable
  public static AddModuleWizard selectFileAndCreateWizard(@Nullable Project project,
                                                          @Nullable Component dialogParent,
                                                          @NotNull FileChooserDescriptor descriptor,
                                                          ProjectImportProvider[] providers) {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, dialogParent);
    VirtualFile toSelect = null;
    String lastLocation = PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION);
    if (lastLocation != null) {
      toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation);
    }
    VirtualFile[] files = chooser.choose(project, toSelect);
    if (files.length == 0) {
      return null;
    }

    final VirtualFile file = files[0];
    if (project == null) { // wizard will create a new project
      for (Project p : ProjectManager.getInstance().getOpenProjects()) {
        if (ProjectUtil.isSameProject(file.toNioPath(), p)) {
          ProjectUtil.focusProjectWindow(p, false);
          return null;
        }
      }
    }
    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, file.getPath());
    return createImportWizard(project, dialogParent, file, providers);
  }

  private static String getFileChooserDescription(List<ProjectImportProvider> providers) {
    StringBuilder builder = new StringBuilder("<html>Select ");
    boolean first = true;
    if (providers.size() > 0) {
      for (ProjectImportProvider provider : providers) {
        String sample = provider.getFileSample();
        if (sample != null) {
          if (!first) {
            builder.append(", <br>");
          }
          else {
            first = false;
          }
          builder.append(sample);
        }
      }
    }
    builder.append(".</html>");
    return builder.toString();
  }

  @NotNull
  public static List<ProjectImportProvider> getProviders(@Nullable final Project project) {
    ProjectImportProvider[] providers = ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensions();
    return ContainerUtil.filter(providers, provider -> project == null ? provider.canCreateNewProject() : provider.canImportModule());
  }

  @Nullable
  public static AddModuleWizard createImportWizard(@Nullable final Project project,
                                                   @Nullable Component dialogParent,
                                                   @NotNull final VirtualFile file,
                                                   ProjectImportProvider... providers) {
    List<ProjectImportProvider> available = ContainerUtil.filter(providers, provider -> provider.canImport(file, project));
    if (available.isEmpty()) {
      Messages.showInfoMessage(project, JavaUiBundle.message("message.cannot.import.anything.from.0", file.getPath()),
                               JavaUiBundle.message("dialog.title.cannot.import"));
      return null;
    }

    String path;
    if (available.size() == 1) {
      path = available.get(0).getPathToBeImported(file);
    }
    else {
      path = ProjectImportProvider.getDefaultPath(file);
    }

    ProjectImportProvider[] availableProviders = available.toArray(new ProjectImportProvider[0]);

    return dialogParent == null
           ? new AddModuleWizard(project, path, availableProviders)
           : new AddModuleWizard(project, dialogParent, path, availableProviders);
  }
}
