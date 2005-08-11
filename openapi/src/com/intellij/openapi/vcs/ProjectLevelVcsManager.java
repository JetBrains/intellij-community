/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class ProjectLevelVcsManager {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final String FILE_VIEW_TOOL_WINDOW_ID = "File View";

  public static ProjectLevelVcsManager getInstance(Project project) {
    return project.getComponent(ProjectLevelVcsManager.class);
  }

  public abstract AbstractVcs[] getAllVcss();


  public abstract AbstractVcs findVcsByName(String name);

  public abstract boolean checkAllFilesAreUnder(AbstractVcs abstractVcs, VirtualFile[] files);

  public abstract AbstractVcs getVcsFor(VirtualFile file);

  public abstract boolean checkVcsIsActive(AbstractVcs vcs);

  public abstract String getPresentableRelativePathFor(VirtualFile file);

  public abstract DataProvider createVirtualAndPsiFileDataProvider(VirtualFile[] virtualFileArray, VirtualFile selectedFile);

  public abstract Module[] getAllModulesUnder(AbstractVcs vcs);

  public abstract AbstractVcs[] getAllActiveVcss();

  public abstract void addMessageToConsoleWindow(String message, TextAttributes attributes);

  @NotNull
  public abstract VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option,
                                                         @NotNull AbstractVcs vcs);

  @NotNull
  public abstract VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option,
                                                             @NotNull AbstractVcs vcs);

  @NotNull
  public abstract VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName,
                                                               @NotNull AbstractVcs vcs);
}
