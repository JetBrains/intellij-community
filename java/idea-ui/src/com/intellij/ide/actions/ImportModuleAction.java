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
package com.intellij.ide.actions;

import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 10/31/12
 */
public class ImportModuleAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null);
    VirtualFile[] files = chooser.choose(null, project);
    if (files.length > 0) {
      final VirtualFile file = files[0];
      doImport(project, file, e.getPresentation().getText());
    }
  }

  public List<Module> doImport(final Project project, @NotNull final VirtualFile file, String wizardTitle) {
    ProjectImportProvider[] providers = ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensions();
    List<ProjectImportProvider> available = ContainerUtil.filter(providers, new Condition<ProjectImportProvider>() {
      @Override
      public boolean value(ProjectImportProvider provider) {
        return provider.canImport(file, project);
      }
    });
    if (available.isEmpty()) {
      Messages.showInfoMessage(project, "Cannot import anything from " + file.getPath(), "Cannot Import");
      return Collections.emptyList();
    }

    String path = file.isDirectory() ? file.getPath() : file.getParent().getPath();
    AddModuleWizard wizard = new AddModuleWizard(wizardTitle, project, path, available.toArray(new ProjectImportProvider[available.size()]));
    if (wizard.getStepCount() > 0) {
      if (wizard.showAndGet()) {
        return createFromWizard(project, wizard);
      }
      return Collections.emptyList();
    }
    else {
      ProjectImportBuilder builder = available.get(0).getBuilder();
      builder.setFileToImport(file.getPath());
      return builder.commit(project);
    }
  }

  protected List<Module> createFromWizard(Project project, AddModuleWizard wizard) {
    Module module = new NewModuleAction().createModuleFromWizard(project, null, wizard);
    return Collections.singletonList(module);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setVisible(ApplicationManager.getApplication().isInternal());
  }
}
