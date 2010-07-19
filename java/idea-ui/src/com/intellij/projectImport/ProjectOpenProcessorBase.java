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

/*
 * User: anna
 * Date: 12-Jul-2007
 */
package com.intellij.projectImport;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public abstract class ProjectOpenProcessorBase extends ProjectOpenProcessor {

  private final ProjectImportBuilder myBuilder;

  protected ProjectOpenProcessorBase(final ProjectImportBuilder builder) {
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
        for (VirtualFile child : file.getChildren()) {
          if (canOpenFile(child, supported)) return true;
        }
        return false;
      }
      if (canOpenFile(file, supported)) return true;
    }
    return false;
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

  public ProjectImportBuilder getBuilder() {
    return myBuilder;
  }

  @Nullable
  public abstract String[] getSupportedExtensions();

  @Nullable
  public Project doOpenProject(@NotNull VirtualFile virtualFile, Project projectToClose, boolean forceOpenInNewFrame) {
    try {
      final WizardContext wizardContext = new WizardContext(null);
      if (virtualFile.isDirectory()) {
        final String[] supported = getSupportedExtensions();
        for (VirtualFile file : virtualFile.getChildren()) {
          if (canOpenFile(file, supported)) {
            virtualFile = file;
            break;
          }
        }
      }
      if (!doQuickImport(virtualFile, wizardContext)) return null;

      if (wizardContext.getProjectName() == null) {
        wizardContext.setProjectName(IdeBundle.message("project.import.default.name", getName()) + ProjectFileType.DOT_DEFAULT_EXTENSION);
      }
      wizardContext.setProjectFileDirectory(virtualFile.getParent().getPath());

      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      Sdk jdk = ProjectRootManager.getInstance(defaultProject).getProjectJdk();
      if (jdk == null) {
        jdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance());
      }
      wizardContext.setProjectJdk(jdk);

      final String projectPath = wizardContext.getProjectFileDirectory() + File.separator + wizardContext.getProjectName() +
                                 ProjectFileType.DOT_DEFAULT_EXTENSION;
      boolean shouldOpenExisting = false;

      File projectFile = new File(projectPath);
      if (!ApplicationManager.getApplication().isHeadlessEnvironment()
          && projectFile.exists()) {
        int result = Messages.showDialog(projectToClose,
                                         IdeBundle.message("project.import.open.existing",
                                                           projectFile.getName(),
                                                           projectFile.getParent(),
                                                           virtualFile.getName()),
                                         IdeBundle.message("title.open.project"),
                                         new String[]{
                                           IdeBundle.message("project.import.open.existing.reimport"),
                                           IdeBundle.message("project.import.open.existing.openExisting"),
                                           CommonBundle.message("button.cancel")},
                                         0,
                                         Messages.getQuestionIcon());
        if (result == 2) return null;
        shouldOpenExisting = result == 1;
      }

      final Project projectToOpen;
      if (shouldOpenExisting) {
        try {
          projectToOpen = ProjectManagerEx.getInstanceEx().loadProject(projectPath);
        }
        catch (IOException e) {
          return null;
        }
        catch (JDOMException e) {
          return null;
        }
        catch (InvalidDataException e) {
          return null;
        }
      }
      else {
        projectToOpen = ProjectManagerEx.getInstanceEx()
          .newProject(FileUtil.getNameWithoutExtension(projectFile), projectPath, true, false);

        if (projectToOpen == null || !getBuilder().validate(projectToClose, projectToOpen)) {
          return null;
        }

        projectToOpen.save();


        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Sdk jdk = wizardContext.getProjectJdk();
            if (jdk != null) NewProjectUtil.applyJdkToProject(projectToOpen, jdk);

            final String projectFilePath = wizardContext.getProjectFileDirectory();
            CompilerProjectExtension.getInstance(projectToOpen).setCompilerOutputUrl(getUrl(
              StringUtil.endsWithChar(projectFilePath, '/') ? projectFilePath + "classes" : projectFilePath + "/classes"));
          }
        });

        getBuilder().commit(projectToOpen, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
      }

      if (!forceOpenInNewFrame) {
        NewProjectUtil.closePreviousProject(projectToClose);
      }
      ProjectUtil.updateLastProjectLocation(projectPath);
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
    catch (IOException e) {
      //file doesn't exist
    }
    return VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(path));
  }
}
