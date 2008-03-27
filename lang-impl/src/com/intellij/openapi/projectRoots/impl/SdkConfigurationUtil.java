package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class SdkConfigurationUtil {
  private SdkConfigurationUtil() {
  }

  @Nullable
  public static Sdk addSdk(final Project project, final SdkType sdkType) {
    final FileChooserDescriptor descriptor = sdkType.getHomeChooserDescriptor();
    final FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
    String suggestedPath = sdkType.suggestHomePath();
    VirtualFile suggestedDir = suggestedPath == null
                               ? null
                               :  LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(suggestedPath));
    final VirtualFile[] selection = dialog.choose(suggestedDir, project);
    if (selection.length > 0) {
      return setupSdk(selection [0], sdkType);
    }
    return null;
  }

  public static Sdk setupSdk(final VirtualFile homeDir, final SdkType sdkType) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Sdk>() {
        public Sdk compute(){
          final ProjectJdkImpl projectJdk = new ProjectJdkImpl(sdkType.suggestSdkName(null, homeDir.getPath()), sdkType);
          projectJdk.setHomePath(homeDir.getPath());
          sdkType.setupSdkPaths(projectJdk);
          ProjectJdkTable.getInstance().addJdk(projectJdk);
          return projectJdk;
        }
    });
  }

  public static void setDirectoryProjectSdk(final Project project, final Sdk pythonSdk) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectRootManager.getInstance(project).setProjectJdk(pythonSdk);
        final Module[] modules = ModuleManager.getInstance(project).getModules();
        final ModifiableRootModel model = ModuleRootManager.getInstance(modules[0]).getModifiableModel();
        model.inheritSdk();
        model.commit();
      }
    });
  }

  public static void configureDirectoryProjectSdk(final Project project, final SdkType sdkType) {
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(sdkType);

    if (sdks.size() > 0) {
      setDirectoryProjectSdk(project, sdks.get(0));
    }
  }
}
