// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * @author anna
 */
public abstract class ProjectOpenProcessorBase<T extends ProjectImportBuilder> extends ProjectOpenProcessor {
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
  public boolean canOpenProject(@NotNull final VirtualFile file) {
    final String[] supported = getSupportedExtensions();
    if (file.isDirectory()) {
      for (VirtualFile child : getFileChildren(file)) {
        if (canOpenFile(child, supported)) return true;
      }
      return false;
    }
    return canOpenFile(file, supported);
  }

  @NotNull
  private static VirtualFile[] getFileChildren(@NotNull VirtualFile file) {
    return ObjectUtils.chooseNotNull(file.getChildren(), VirtualFile.EMPTY_ARRAY);
  }

  protected static boolean canOpenFile(@NotNull VirtualFile file, @NotNull String[] supported) {
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

  @NotNull
  public abstract String[] getSupportedExtensions();

  @Override
  @Nullable
  public Project doOpenProject(@NotNull VirtualFile virtualFile, Project projectToClose, boolean forceOpenInNewFrame) {
    try {
      getBuilder().setUpdate(false);
      final WizardContext wizardContext = new WizardContext(null, null);
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
          projectToOpen = ProjectManagerEx.getInstanceEx().loadProject(Paths.get(pathToOpen).toAbsolutePath());
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
      }

      if (!forceOpenInNewFrame) {
        ProjectUtil.closePreviousProject(projectToClose);
      }
      ProjectUtil.updateLastProjectLocation(pathToOpen);
      ProjectManagerEx.getInstanceEx().openProject(projectToOpen);

      return projectToOpen;
    }
    finally {
      getBuilder().cleanup();
    }
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