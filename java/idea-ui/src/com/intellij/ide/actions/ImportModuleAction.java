/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.TransactionGuard;
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ArrayUtil.toObjectArray;

/**
 * @author Dmitry Avdeev
 */
public class ImportModuleAction extends AnAction {

  private static final String LAST_IMPORTED_LOCATION = "last.imported.location";

  @Override
  public void actionPerformed(AnActionEvent e) {
    doImport(getEventProject(e));
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(getEventProject(e) != null);
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
    Ref<List<Module>> result = Ref.create();
    TransactionGuard.getInstance().submitTransactionAndWait(() -> result.set(doCreateFromWizard(project, wizard)));
    return result.get();
  }

  private static List<Module> doCreateFromWizard(@Nullable Project project, AbstractProjectWizard wizard) {
    if (project == null) {
      Project newProject = NewProjectUtil.createFromWizard(wizard, null);
      return newProject == null ? Collections.emptyList() : Arrays.asList(ModuleManager.getInstance(newProject).getModules());
    }

    final ProjectBuilder projectBuilder = wizard.getProjectBuilder();
    try {
      if (wizard.getStepCount() > 0) {
        Module module = new NewModuleAction().createModuleFromWizard(project, null, wizard);
        return Collections.singletonList(module);
      }
      else {
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
  public static AddModuleWizard selectFileAndCreateWizard(@Nullable Project project, @Nullable Component dialogParent) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    descriptor.setHideIgnored(false);
    descriptor.setTitle("Select File or Directory to Import");
    List<ProjectImportProvider> providers = getProviders(project);
    String description = getFileChooserDescription(providers);
    descriptor.setDescription(description);
    return selectFileAndCreateWizard(project, dialogParent, descriptor, toObjectArray(providers, ProjectImportProvider.class));
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
        if (ProjectUtil.isSameProject(file.getPath(), p)) {
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
      Messages.showInfoMessage(project, "Cannot import anything from " + file.getPath(), "Cannot Import");
      return null;
    }

    String path;
    if (available.size() == 1) {
      path = available.get(0).getPathToBeImported(file);
    }
    else {
      path = ProjectImportProvider.getDefaultPath(file);
    }

    ProjectImportProvider[] availableProviders = available.toArray(new ProjectImportProvider[available.size()]);

    return dialogParent == null
           ? new AddModuleWizard(project, path, availableProviders)
           : new AddModuleWizard(project, dialogParent, path, availableProviders);
  }
}
