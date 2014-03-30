/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class AssociateFileType extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    FileTypeChooser.associateFileType(file.getName());
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    boolean haveSmthToDo;
    if (project == null || file == null || file.isDirectory()) {
      haveSmthToDo = false;
    }
    else {
      // the action should also be available for files which have been auto-detected as text or as a particular language (IDEA-79574)
      haveSmthToDo = FileTypeManager.getInstance().getFileTypeByFileName(file.getName()) == FileTypes.UNKNOWN;
    }
    presentation.setVisible(haveSmthToDo || ActionPlaces.MAIN_MENU.equals(e.getPlace()));
    presentation.setEnabled(haveSmthToDo);
  }

}
