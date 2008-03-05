package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformProjectConfigurator implements ProjectComponent {
  private Project myProject;
  private ProjectBaseDirectory.Listener myListener;
  private ProjectBaseDirectory myProjectBaseDir;

  public PlatformProjectConfigurator(final Project project) {
    myProject = project;
    if (!project.isDefault()) {
      myListener = new ProjectBaseDirectory.Listener() {
        public void baseDirChanged() {
          initDefaultModule();
        }
      };
      myProjectBaseDir = ProjectBaseDirectory.getInstance(project);
      myProjectBaseDir.addListener(myListener);
    }
  }

  private void initDefaultModule() {
    final VirtualFile baseDir = ProjectBaseDirectory.getInstance(myProject).getBaseDir();
    if (baseDir != null) {
      final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      final Module[] modules = moduleManager.getModules();
      if (modules.length == 0) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            String imlName = PlatformProjectOpenProcessor.getIprBaseName(baseDir) + ".iml";
            final Module module = moduleManager.newModule(imlName, ModuleType.EMPTY);
            ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
            rootModel.addContentEntry(baseDir);
            rootModel.commit();
          }
        });
      }
    }
  }

  public void projectOpened() {
  }

  public void projectClosed() {
    if (myProjectBaseDir != null) {
      myProjectBaseDir.removeListener(myListener);
    }
  }

  @NotNull
  public String getComponentName() {
    return "PlatformProjectConfigurator";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
