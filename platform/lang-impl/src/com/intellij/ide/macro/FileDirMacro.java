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

package com.intellij.ide.macro;

import com.intellij.ide.DataAccessors;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;

public final class FileDirMacro extends Macro {
  public String getName() {
    return "FileDir";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.directory");
  }

  public String expand(DataContext dataContext) {
    //Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    //if (project == null) return null;
    //VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    //if (file == null) return null;
    //if (!file.isDirectory()) {
    //  file = file.getParent();
    //  if (file == null) return null;
    //}
    VirtualFile dir = DataAccessors.VIRTUAL_DIR_OR_PARENT.from(dataContext);
    if (dir == null) return null;
    return getPath(dir);
  }
}
