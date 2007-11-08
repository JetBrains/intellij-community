package com.intellij.ide;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationAdapter;
import com.apple.eawt.ApplicationEvent;
import com.intellij.ide.actions.AboutAction;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;

import java.io.File;

/**
 * @author max
 */
public class MacOSApplicationProvider implements ApplicationComponent {
  public String getComponentName() {
    return "MACOSApplicationProvider";
  }

  public MacOSApplicationProvider() {
    if (SystemInfo.isMac) {
      try {
        Worker.initMacApplication();
      }
      catch (NoClassDefFoundError e) {
      }
    }
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  private static class Worker {
    public static void initMacApplication() {
      Application application = new Application();
      application.addApplicationListener(new ApplicationAdapter() {
        public void handleAbout(ApplicationEvent applicationEvent) {
          AboutAction.showAbout();
          applicationEvent.setHandled(true);
        }

        public void handlePreferences(ApplicationEvent applicationEvent) {
          Project project = getProject();

          if (project == null) {
            project = ProjectManager.getInstance().getDefaultProject();
          }

          ConfigurableGroup[] group = new ConfigurableGroup[]{
            new ProjectConfigurablesGroup(project),
            new IdeConfigurablesGroup()
          };

          ShowSettingsUtil.getInstance().showSettingsDialog(project, group);
          applicationEvent.setHandled(true);
        }

        public void handleQuit(ApplicationEvent applicationEvent) {
          ApplicationManagerEx.getApplicationEx().exit();
        }

        public void handleOpenFile(ApplicationEvent applicationEvent) {
          Project project = getProject();
          String filename = applicationEvent.getFilename();
          if (filename == null) return;

          File file = new File(filename);
          if (ProjectUtil.openOrImport(file.getAbsolutePath(), project, false) != null) {
            return;
          }
          if (project != null && file.exists()) {
            OpenFileAction.openFile(filename, project);
            applicationEvent.setHandled(true);
          }
        }
      });

      application.addAboutMenuItem();
      application.addPreferencesMenuItem();
      application.setEnabledAboutMenu(true);
      application.setEnabledPreferencesMenu(true);
    }

    private static Project getProject() {
      return DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    }
  }
}
