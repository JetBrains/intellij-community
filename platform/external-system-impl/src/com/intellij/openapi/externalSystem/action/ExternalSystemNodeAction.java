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
package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemConfigLocator;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 10/17/2014
 */
public abstract class ExternalSystemNodeAction<T> extends ExternalSystemAction {

  private final Class<T> myExternalDataClazz;

  public ExternalSystemNodeAction(Class<T> externalDataClazz) {
    super();
    myExternalDataClazz = externalDataClazz;
  }

  protected boolean isEnabled(AnActionEvent e) {
    return super.isEnabled(e) && getSystemId(e) != null && getExternalData(e, myExternalDataClazz) != null;
  }

  protected abstract void perform(@NotNull Project project,
                                  @NotNull ProjectSystemId projectSystemId,
                                  @NotNull T externalData,
                                  @NotNull AnActionEvent e);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getProject(e);
    if (project == null) return;

    ProjectSystemId projectSystemId = getSystemId(e);
    if (projectSystemId == null) return;

    final T data = getExternalData(e, myExternalDataClazz);
    if (data == null) return;

    perform(project, projectSystemId, data, e);
  }

  @Nullable
  protected ExternalSystemUiAware getExternalSystemUiAware(AnActionEvent e) {
    return ExternalSystemDataKeys.UI_AWARE.getData(e.getDataContext());
  }

  @SuppressWarnings("unchecked")
  @Nullable
  protected <T> T getExternalData(AnActionEvent e, Class<T> dataClass) {
    ExternalSystemNode node = ContainerUtil.getFirstItem(ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext()));
    return node != null && dataClass.isInstance(node.getData()) ? (T)node.getData() : null;
  }

  @SuppressWarnings("unchecked")
  protected boolean isIgnoredNode(AnActionEvent e) {
    ExternalSystemNode node = ContainerUtil.getFirstItem(ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext()));
    return node != null && myExternalDataClazz.isInstance(node.getData()) && node.isIgnored();
  }

  @Nullable
  protected VirtualFile getExternalConfig(@NotNull ExternalConfigPathAware data, ProjectSystemId externalSystemId) {
    String path = data.getLinkedExternalProjectPath();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile externalSystemConfigPath = fileSystem.refreshAndFindFileByPath(path);
    if (externalSystemConfigPath == null) {
      return null;
    }

    VirtualFile toOpen = externalSystemConfigPath;
    for (ExternalSystemConfigLocator locator : ExternalSystemConfigLocator.EP_NAME.getExtensions()) {
      if (externalSystemId.equals(locator.getTargetExternalSystemId())) {
        toOpen = locator.adjust(toOpen);
        if (toOpen == null) {
          return null;
        }
        break;
      }
    }
    return toOpen.isDirectory() ? null : toOpen;
  }
}