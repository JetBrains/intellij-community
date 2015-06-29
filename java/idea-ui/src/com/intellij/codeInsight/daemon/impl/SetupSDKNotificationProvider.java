/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

/**
 * @author Danila Ponomarenko
 */
public class SetupSDKNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("Setup SDK");

  private final Project myProject;

  public SetupSDKNotificationProvider(Project project, final EditorNotifications notifications) {
    myProject = project;
    myProject.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        notifications.updateAllNotifications();
      }
    });
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (file.getFileType() == JavaClassFileType.INSTANCE) return null;

    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) {
      return null;
    }

    if (psiFile.getLanguage() != JavaLanguage.INSTANCE) {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
    if (module == null) {
      return null;
    }

    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null) {
      return null;
    }

    return createPanel(myProject, psiFile);
  }

  @NotNull
  private static EditorNotificationPanel createPanel(@NotNull final Project project, @NotNull final PsiFile file) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(ProjectBundle.message("project.sdk.not.defined"));
    panel.createActionLabel(ProjectBundle.message("project.sdk.setup"), new Runnable() {
      @Override
      public void run() {
        final Sdk projectSdk = ProjectSettingsService.getInstance(project).chooseAndSetSdk();
        if (projectSdk == null) return;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            final Module module = ModuleUtilCore.findModuleForPsiElement(file);
            if (module != null) {
              ModuleRootModificationUtil.setSdkInherited(module);
            }
          }
        });
      }
    });
    return panel;
  }
}
