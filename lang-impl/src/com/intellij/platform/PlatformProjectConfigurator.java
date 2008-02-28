package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformProjectConfigurator implements ProjectComponent {
  private Project myProject;

  public PlatformProjectConfigurator(final Project project, MessageBus bus) {
    myProject = project;
    if (!project.isDefault()) {
      bus.connect().subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener.Adapter() {
        public void projectComponentsInitialized(final Project project) {
          initDefaultModule();
        }
      });
    }
  }

  private void initDefaultModule() {
    if (PlatformProjectOpenProcessor.BASE_DIR != null) {
      final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      final Module[] modules = moduleManager.getModules();
      if (modules.length == 0) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            String imlName = PlatformProjectOpenProcessor.getIprBaseName() + ".iml";
            final Module module = moduleManager.newModule(imlName, ModuleType.EMPTY);
            ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
            rootModel.addContentEntry(PlatformProjectOpenProcessor.BASE_DIR);
            rootModel.commit();
          }
        });
      }
    }
  }

  public void projectOpened() {
  }

  public void projectClosed() {
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
