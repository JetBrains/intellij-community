/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;

public class FileDirPathFromParentMacro extends Macro {
  @Override
  public String getName() {
    return "FileDirPathFromParent";
  }

  @Override
  public String getDescription() {
    return IdeBundle.message("macro.file.directory.from.parent");
  }

  @Override
  public String expand(DataContext dataContext) {
    return null;
  }

  @Override
  public String expand(DataContext dataContext, String... args) throws ExecutionCancelledException {
    if(args.length == 0) {
      return super.expand(dataContext, args);
    }
    String param = args[0];
    VirtualFile vFile = getVirtualDirOrParent(dataContext);
    StringBuilder result = new StringBuilder();
    while (vFile != null && !param.equalsIgnoreCase(vFile.getName())) {
      result.insert(0, vFile.getName() + "/");
      vFile = vFile.getParent();
    }
    return result.toString();
  }
}
