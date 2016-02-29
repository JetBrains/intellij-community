/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.module.impl;

import com.intellij.ProjectTopics;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.UnknownModuleType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author yole
 */
@State(name = ModuleManagerImpl.COMPONENT_NAME, storages = @Storage("modules.xml"))
public class ModuleManagerComponent extends ModuleManagerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleManagerComponent");
  private final ProgressManager myProgressManager;
  private final MessageBusConnection myConnection;

  public ModuleManagerComponent(Project project, ProgressManager progressManager, MessageBus bus) {
    super(project, bus);
    myConnection = bus.connect(project);
    myProgressManager = progressManager;
    myConnection.setDefaultHandler(new MessageHandler() {
      @Override
      public void handle(Method event, Object... params) {
        cleanCachedStuff();
      }
    });

    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS);
    myConnection.subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener.Adapter() {
      @Override
      public void projectComponentsInitialized(@NotNull final Project project) {
        if (project != myProject) return;

        long t = System.currentTimeMillis();
        loadModules(myModuleModel);
        t = System.currentTimeMillis() - t;
        LOG.info(myModuleModel.getModules().length + " module(s) loaded in " + t + " ms");
      }
    });

  }

  @Override
  protected void showUnknownModuleTypeNotification(@NotNull List<Module> modulesWithUnknownTypes) {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment() && !modulesWithUnknownTypes.isEmpty()) {
      String message;
      if (modulesWithUnknownTypes.size() == 1) {
        message = ProjectBundle.message("module.unknown.type.single.error", modulesWithUnknownTypes.get(0).getName(),
                                        ModuleType.get(modulesWithUnknownTypes.get(0)).getId());
      }
      else {
        StringBuilder modulesBuilder = new StringBuilder();
        for (final Module module : modulesWithUnknownTypes) {
          modulesBuilder.append("<br>\"");
          modulesBuilder.append(module.getName()).append("\" (type '").append(ModuleType.get(module).getId()).append("')");
        }
        modulesBuilder.append("<br>");
        message = ProjectBundle.message("module.unknown.type.multiple.error", modulesBuilder.toString());
      }
      // it is not modal warning at all
      //Messages.showWarningDialog(myProject, message, ProjectBundle.message("module.unknown.type.title"));
      Notifications.Bus.notify(new Notification(
        "Module Manager",
        ProjectBundle.message("module.unknown.type.title"),
        message,
        NotificationType.WARNING
      ), myProject);
    }
  }

  @NotNull
  @Override
  protected ModuleEx createModule(@NotNull String filePath) {
    return new ModuleImpl(filePath, myProject);
  }

  @NotNull
  @Override
  protected ModuleEx createAndLoadModule(@NotNull String filePath) {
    return new ModuleImpl(filePath, myProject);
  }

  @Override
  protected boolean isUnknownModuleType(@NotNull Module module) {
    return ModuleType.get(module) instanceof UnknownModuleType;
  }

  @Override
  protected void fireModulesAdded() {
    if (myModuleModel.myModules.isEmpty()) {
      return;
    }

    Runnable runnableWithProgress = new Runnable() {
      @Override
      public void run() {
        for (final Module module : myModuleModel.myModules.values()) {
          final Application app = ApplicationManager.getApplication();
          final Runnable swingRunnable = new Runnable() {
            @Override
            public void run() {
              fireModuleAddedInWriteAction(module);
            }
          };
          ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
          app.invokeAndWait(swingRunnable, pi.getModalityState());
        }
      }
    };

    ProgressIndicator progressIndicator = myProgressManager.getProgressIndicator();
    if (progressIndicator == null) {
      myProgressManager.runProcessWithProgressSynchronously(runnableWithProgress, "Initializing modules...", false, myProject);
    }
    else {
      runnableWithProgress.run();
    }
  }

  @Override
  protected void deliverPendingEvents() {
    myConnection.deliverImmediately();
  }
}
