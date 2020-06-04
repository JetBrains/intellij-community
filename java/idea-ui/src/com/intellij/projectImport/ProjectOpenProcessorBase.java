// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class ProjectOpenProcessorBase<T extends ProjectImportBuilder<?>> extends ProjectOpenProcessor {
  @Nullable
  private final T myBuilder;

  /**
   * @deprecated Override {@link #doGetBuilder()} and use {@code ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(yourClass.class)}.
   */
  @Deprecated
  protected ProjectOpenProcessorBase(@NotNull final T builder) {
    myBuilder = builder;
  }

  protected ProjectOpenProcessorBase() {
    myBuilder = null;
  }

  @NotNull
  protected T doGetBuilder() {
    assert myBuilder != null;
    return myBuilder;
  }

  @Override
  @NotNull
  public String getName() {
    return getBuilder().getName();
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return getBuilder().getIcon();
  }

  @Override
  public boolean canOpenProject(@NotNull  VirtualFile file) {
    String[] supported = getSupportedExtensions();
    if (file.isDirectory()) {
      for (VirtualFile child : getFileChildren(file)) {
        if (canOpenFile(child, supported)) {
          return true;
        }
      }
      return false;
    }
    return canOpenFile(file, supported);
  }

  private static VirtualFile @NotNull [] getFileChildren(@NotNull VirtualFile file) {
    return ObjectUtils.chooseNotNull(file.getChildren(), VirtualFile.EMPTY_ARRAY);
  }

  protected static boolean canOpenFile(@NotNull VirtualFile file, String @NotNull [] supported) {
    final String fileName = file.getName();
    for (String name : supported) {
      if (fileName.equals(name)) {
        return true;
      }
    }
    return false;
  }

  protected boolean doQuickImport(@NotNull VirtualFile file, @NotNull WizardContext wizardContext) {
    return false;
  }

  @NotNull
  public T getBuilder() {
    return doGetBuilder();
  }

  public abstract String @NotNull [] getSupportedExtensions();

  @Override
  @Nullable
  public Project doOpenProject(@NotNull VirtualFile virtualFile, Project projectToClose, boolean forceOpenInNewFrame) {
    try {
      getBuilder().setUpdate(false);
      WizardContext wizardContext = new WizardContext(null, null);
      if (virtualFile.isDirectory()) {
        String[] supported = getSupportedExtensions();
        for (VirtualFile file : getFileChildren(virtualFile)) {
          if (canOpenFile(file, supported)) {
            virtualFile = file;
            break;
          }
        }
      }

      wizardContext.setProjectFileDirectory(virtualFile.getParent().getPath());

      if (!doQuickImport(virtualFile, wizardContext)) {
        return null;
      }

      if (wizardContext.getProjectName() == null) {
        if (wizardContext.getProjectStorageFormat() == StorageScheme.DEFAULT) {
          wizardContext.setProjectName(JavaUiBundle.message("project.import.default.name", getName()) + ProjectFileType.DOT_DEFAULT_EXTENSION);
        }
        else {
          wizardContext.setProjectName(JavaUiBundle.message("project.import.default.name.dotIdea", getName()));
        }
      }

      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      Sdk jdk = ProjectRootManager.getInstance(defaultProject).getProjectSdk();
      if (jdk == null) {
        jdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance());
      }
      wizardContext.setProjectJdk(jdk);

      Path dotIdeaFile = wizardContext.getProjectDirectory().resolve(Project.DIRECTORY_STORE_FOLDER);
      Path projectFile = wizardContext.getProjectDirectory().resolve(wizardContext.getProjectName() + ProjectFileType.DOT_DEFAULT_EXTENSION).normalize();

      Path pathToOpen = wizardContext.getProjectStorageFormat() == StorageScheme.DEFAULT ? projectFile.toAbsolutePath() : dotIdeaFile.getParent();

      boolean shouldOpenExisting = false;
      boolean importToProject = true;
      if (Files.exists(projectFile) || Files.exists(dotIdeaFile)) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
          shouldOpenExisting = true;
          importToProject = true;
        }
        else {
          String existingName;
          if (Files.exists(dotIdeaFile)) {
            existingName = "an existing project";
            pathToOpen = dotIdeaFile.getParent();
          }
          else {
            existingName = "'" + projectFile.getFileName() + "'";
            pathToOpen = projectFile;
          }
          int result = Messages.showYesNoCancelDialog(
            projectToClose,
            JavaUiBundle.message("project.import.open.existing", existingName, projectFile.getParent(), virtualFile.getName()),
            IdeBundle.message("title.open.project"),
            JavaUiBundle.message("project.import.open.existing.openExisting"),
            JavaUiBundle.message("project.import.open.existing.reimport"),
            CommonBundle.getCancelButtonText(),
            Messages.getQuestionIcon());
          if (result == Messages.CANCEL) {
            return null;
          }
          shouldOpenExisting = result == Messages.YES;
          importToProject = !shouldOpenExisting;
        }
      }

      OpenProjectTask options = shouldOpenExisting ? OpenProjectTask.withProjectToClose(projectToClose, forceOpenInNewFrame) : OpenProjectTask.newProject();
      if (importToProject) {
        options.withBeforeProjectCallback(project -> importToProject(projectToClose, wizardContext, project));
      }
      options.withProjectName(wizardContext.getProjectName());

      try {
        Project project = ProjectManagerEx.getInstanceEx().openProject(pathToOpen, options);
        ProjectUtil.updateLastProjectLocation(pathToOpen);
        return project;
      }
      catch (Exception e) {
        Logger.getInstance(ProjectOpenProcessorBase.class).warn(e);
        return null;
      }
    }
    finally {
      getBuilder().cleanup();
    }
  }

  private boolean importToProject(Project projectToClose, WizardContext wizardContext, Project projectToOpen) {
    if (!getBuilder().validate(projectToClose, projectToOpen)) {
      return false;
    }

    projectToOpen.save();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        Sdk jdk1 = wizardContext.getProjectJdk();
        if (jdk1 != null) {
          NewProjectUtil.applyJdkToProject(projectToOpen, jdk1);
        }

        String projectDirPath = wizardContext.getProjectFileDirectory();
        String path = projectDirPath + (StringUtil.endsWithChar(projectDirPath, '/') ? "classes" : "/classes");
        CompilerProjectExtension extension = CompilerProjectExtension.getInstance(projectToOpen);
        if (extension != null) {
          extension.setCompilerOutputUrl(getUrl(path));
        }
      });

      getBuilder().commit(projectToOpen, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
    });
    return true;
  }

  @NotNull
  public static String getUrl(@NonNls @NotNull String path) {
    try {
      path = FileUtil.resolveShortWindowsName(path);
    }
    catch (IOException ignored) { }
    return VfsUtilCore.pathToUrl(path);
  }
}