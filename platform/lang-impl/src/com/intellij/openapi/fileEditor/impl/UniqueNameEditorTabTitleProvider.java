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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class UniqueNameEditorTabTitleProvider implements EditorTabTitleProvider {
  @Override
  public String getEditorTabTitle(Project project, VirtualFile file) {
    if (!UISettings.getInstance().SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES || DumbService.isDumb(project)) {
      return null;
    }
    // Even though this is a 'tab title provider' it is used also when tabs are not shown, namely for building IDE frame title.
    final String uniqueName = UISettings.getInstance().EDITOR_TAB_PLACEMENT == UISettings.TABS_NONE ? 
                              UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file) : 
                              UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePathWithinOpenedFileEditors(project, file);
    return uniqueName.equals(file.getName()) ? null : uniqueName;
  }
}
