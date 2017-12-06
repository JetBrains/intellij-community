/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.jshell;

import com.intellij.ProjectTopics;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class SnippetEditorDecorator extends EditorNotifications.Provider<SnippetEditorDecorator.ConfigurationPane>{
  public static final Key<ConfigurationPane> CONTEXT_KEY = Key.create("jshell.editor.toolbar");
  private final Project myProject;

  public SnippetEditorDecorator(Project project) {
    myProject = project;
  }

  public static class ConfigurationPane extends EditorHeaderComponent {
    private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private final JrePathEditor myJreEditor;
    private final ConfigurationModuleSelector myModuleSelector;
    private MessageBusConnection myBusConnection;

    ConfigurationPane(Project project) {
      final DefaultActionGroup actions = new DefaultActionGroup(ExecuteJShellAction.getSharedInstance(), DropJShellStateAction.getSharedInstance());
      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("JShellSnippetEditor", actions, true);

      myJreEditor = new JrePathEditor(DefaultJreSelector.projectSdk(project));
      myJreEditor.setToolTipText("Alternative JRE to run JShell");
      myJreEditor.setPathOrName(null, true);

      LabeledComponent<ModulesComboBox> modulePane = new LabeledComponent<>();
      ModulesComboBox modulesCombo = new ModulesComboBox();
      modulePane.setComponent(modulesCombo);
      modulePane.setLabelLocation(BorderLayout.WEST);
      modulePane.setText("Use classpath of:");
      myModuleSelector = new ConfigurationModuleSelector(project, modulesCombo, "<whole project>");

      JPanel mainPane = new JPanel(new GridBagLayout());
      mainPane.add(toolbar.getComponent(), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(2, 3, 0, 0), 0, 0));
      mainPane.add(modulePane, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(2, 3, 0, 0), 0, 0));
      mainPane.add(myJreEditor, new GridBagConstraints(2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(2, 15, 0, 0), 0, 0));
      add(mainPane, BorderLayout.CENTER);
    }

    @Override
    public void addNotify() {
      super.addNotify();
      myBusConnection = myModuleSelector.getProject().getMessageBus().connect();
      myBusConnection.subscribe(ProjectTopics.MODULES, new ModuleListener() {
        @Override
        public void moduleAdded(@NotNull Project project, @NotNull Module module) {
          reloadModules();
        }

        @Override
        public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
          reloadModules();
        }

        @Override
        public void modulesRenamed(@NotNull Project project, @NotNull List<Module> modules, @NotNull Function<Module, String> oldNameProvider) {
          reloadModules();
        }
      });
      reloadModules();
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      final MessageBusConnection conn = myBusConnection;
      if (conn != null) {
        myBusConnection = null;
        conn.disconnect();
        myUpdateAlarm.cancelAllRequests();
      }
    }

    private void reloadModules() {
      myUpdateAlarm.cancelAllRequests();
      myUpdateAlarm.addRequest(()->myModuleSelector.reset(), 300L);
    }

    @Nullable
    public Module getContextModule() {
      return myModuleSelector.getModule();
    }

    @Nullable
    public Sdk getRuntimeSdk() {
      final String pathOrName = myJreEditor.getJrePathOrName();
      if (pathOrName != null) {
        final JavaSdk javaSdkType = JavaSdk.getInstance();
        final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
        final Sdk sdkByName = jdkTable.findJdk(pathOrName, javaSdkType.getName());
        if (sdkByName != null) {
          return sdkByName;
        }
        // assuming we have sdk home path
        for (Sdk sdk : jdkTable.getSdksOfType(javaSdkType)) {
          if (FileUtil.pathsEqual(pathOrName, sdk.getHomePath())) {
            return sdk;
          }
        }
      }
      return null;
    }
  }
  
  @NotNull
  @Override
  public Key<ConfigurationPane> getKey() {
    return CONTEXT_KEY;
  }

  @Nullable
  @Override
  public ConfigurationPane createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    final RootType root = ScratchFileService.getInstance().getRootType(file);
    if (!(root instanceof JShellRootType)) {
      return null;
    }
    return new ConfigurationPane(myProject);
  }

  @Nullable
  public static ConfigurationPane getJShellConfiguration(DataContext context) {
    return getJShellConfiguration(PlatformDataKeys.FILE_EDITOR.getData(context));
  }

  @Nullable
  public static ConfigurationPane getJShellConfiguration(FileEditor fileEditor) {
    return CONTEXT_KEY.get(fileEditor);
  }
}
