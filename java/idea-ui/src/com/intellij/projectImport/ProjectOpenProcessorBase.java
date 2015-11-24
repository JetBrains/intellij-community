/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.projectImport;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author anna
 * @since 12-Jul-2007
 */
public abstract class ProjectOpenProcessorBase<T extends ProjectImportBuilder> extends ProjectOpenProcessor {
  private final T myBuilder;

  protected ProjectOpenProcessorBase(@NotNull final T builder) {
    myBuilder = builder;
  }

  public String getName() {
    return getBuilder().getName();
  }

  @Nullable
  public Icon getIcon() {
    return getBuilder().getIcon();
  }

  public boolean canOpenProject(final VirtualFile file) {
    final String[] supported = getSupportedExtensions();
    if (supported != null) {
      if (file.isDirectory()) {
        for (VirtualFile child : getFileChildren(file)) {
          if (canOpenFile(child, supported)) return true;
        }
        return false;
      }
      if (canOpenFile(file, supported)) return true;
    }
    return false;
  }

  private static Collection<VirtualFile> getFileChildren(VirtualFile file) {
    if (file instanceof NewVirtualFile) {
      return ((NewVirtualFile)file).getCachedChildren();
    }

    return Arrays.asList(file.getChildren());
  }

  protected static boolean canOpenFile(VirtualFile file, String[] supported) {
    final String fileName = file.getName();
    for (String name : supported) {
      if (fileName.equals(name)) {
        return true;
      }
    }
    return false;
  }

  protected boolean doQuickImport(VirtualFile file, final WizardContext wizardContext) {
    return false;
  }

  @NotNull
  public T getBuilder() {
    return myBuilder;
  }

  @Nullable
  public abstract String[] getSupportedExtensions();

  @Nullable
  public Project doOpenProject(@NotNull VirtualFile virtualFile, Project projectToClose, boolean forceOpenInNewFrame) {
    try {
      getBuilder().setUpdate(false);
      final WizardContext wizardContext = new WizardContext(null);
      if (virtualFile.isDirectory()) {
        final String[] supported = getSupportedExtensions();
        for (VirtualFile file : getFileChildren(virtualFile)) {
          if (canOpenFile(file, supported)) {
            virtualFile = file;
            break;
          }
        }
      }

      wizardContext.setProjectFileDirectory(virtualFile.getParent().getPath());

      if (!doQuickImport(virtualFile, wizardContext)) return null;

      if (wizardContext.getProjectName() == null) {
        if (wizardContext.getProjectStorageFormat() == StorageScheme.DEFAULT) {
          wizardContext.setProjectName(IdeBundle.message("project.import.default.name", getName()) + ProjectFileType.DOT_DEFAULT_EXTENSION);
        }
        else {
          wizardContext.setProjectName(IdeBundle.message("project.import.default.name.dotIdea", getName()));
        }
      }

      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      Sdk jdk = ProjectRootManager.getInstance(defaultProject).getProjectSdk();
      if (jdk == null) {
        jdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance());
      }
      wizardContext.setProjectJdk(jdk);

      final String dotIdeaFilePath = wizardContext.getProjectFileDirectory() + File.separator + Project.DIRECTORY_STORE_FOLDER;
      final String projectFilePath = wizardContext.getProjectFileDirectory() + File.separator + wizardContext.getProjectName() +
                                     ProjectFileType.DOT_DEFAULT_EXTENSION;

      File dotIdeaFile = new File(dotIdeaFilePath);
      File projectFile = new File(projectFilePath);

      String pathToOpen;
      if (wizardContext.getProjectStorageFormat() == StorageScheme.DEFAULT) {
        pathToOpen = projectFilePath;
      } else {
        pathToOpen = dotIdeaFile.getParent();
      }

      boolean shouldOpenExisting = false;
      boolean importToProject = true;
      if (projectFile.exists() || dotIdeaFile.exists()) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
          shouldOpenExisting = true;
          importToProject = true;
        }
        else {
          String existingName;
          if (dotIdeaFile.exists()) {
            existingName = "an existing project";
            pathToOpen = dotIdeaFile.getParent();
          }
          else {
            existingName = "'" + projectFile.getName() + "'";
            pathToOpen = projectFilePath;
          }
          int result = Messages.showYesNoCancelDialog(
            projectToClose,
            IdeBundle.message("project.import.open.existing", existingName, projectFile.getParent(), virtualFile.getName()),
            IdeBundle.message("title.open.project"),
            IdeBundle.message("project.import.open.existing.openExisting"),
            IdeBundle.message("project.import.open.existing.reimport"),
            CommonBundle.message("button.cancel"),
            Messages.getQuestionIcon());
          if (result == Messages.CANCEL) return null;
          shouldOpenExisting = result == Messages.YES;
          importToProject = !shouldOpenExisting;
        }
      }

      final Project projectToOpen;
      if (shouldOpenExisting) {
        try {
          projectToOpen = ProjectManagerEx.getInstanceEx().loadProject(pathToOpen);
        }
        catch (Exception e) {
          return null;
        }
      }
      else {
        projectToOpen = ProjectManagerEx.getInstanceEx().newProject(wizardContext.getProjectName(), pathToOpen, true, false);
      }
      if (projectToOpen == null) return null;

      if (importToProject) {
        if (!getBuilder().validate(projectToClose, projectToOpen)) {
          return null;
        }

        projectToOpen.save();

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            Sdk jdk = wizardContext.getProjectJdk();
            if (jdk != null) {
              NewProjectUtil.applyJdkToProject(projectToOpen, jdk);
            }

            String projectDirPath = wizardContext.getProjectFileDirectory();
            String path = StringUtil.endsWithChar(projectDirPath, '/') ? projectDirPath + "classes" : projectDirPath + "/classes";
            CompilerProjectExtension extension = CompilerProjectExtension.getInstance(projectToOpen);
            if (extension != null) {
              extension.setCompilerOutputUrl(getUrl(path));
            }
          }
        });

        getBuilder().commit(projectToOpen, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
      }

      if (!forceOpenInNewFrame) {
        NewProjectUtil.closePreviousProject(projectToClose);
      }
      ProjectUtil.updateLastProjectLocation(pathToOpen);
      ProjectManagerEx.getInstanceEx().openProject(projectToOpen);

      return projectToOpen;
    }
    finally {
      getBuilder().cleanup();
    }
  }

  public static String getUrl(@NonNls String path) {
    try {
      path = FileUtil.resolveShortWindowsName(path);
    }
    catch (IOException ignored) { }
    return VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(path));
  }
}
