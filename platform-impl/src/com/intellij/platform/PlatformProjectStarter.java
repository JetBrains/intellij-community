/*
 * @author max
 */
package com.intellij.platform;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Ref;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PlatformProjectStarter implements ApplicationComponent {
  public PlatformProjectStarter(MessageBus bus) {
    bus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      public void appFrameCreated(@NotNull final Ref<Boolean> willOpenProject) {
        willOpenProject.set(true);
      }

      public void appStarting(final Project projectFromCommandLine) {
        if (projectFromCommandLine == null) {
          final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
          Project project = projectManager.newProject(PathManager.getConfigPath() + "/dummy.ipr", true, false);
          if (project == null) return;
          projectManager.openProject(project);
        }
      }
    });
  }

  public void disposeComponent() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "platform.ProjectStarter";
  }

  public void initComponent() {
  }
}