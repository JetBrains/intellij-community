package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
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
public class PlatformProjectConfigurator implements DirectoryProjectConfigurator {
  public void configureProject(final Project project, @NotNull final VirtualFile baseDir) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    final Module[] modules = moduleManager.getModules();
    if (modules.length == 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          String imlName = baseDir.getPath() + "/.idea/" + baseDir.getName() + ".iml";
          final Module module = moduleManager.newModule(imlName, ModuleType.EMPTY);
          ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
          rootModel.addContentEntry(baseDir);
          rootModel.commit();
        }
      });
    }
  }
}
