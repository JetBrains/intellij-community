/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.FileAppearanceService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

class TableItem {
  private final String myUrl;
  private final CellAppearanceEx myCellAppearance;

  public TableItem(@NotNull final VirtualFile file) {
    myUrl = file.getUrl();
    myCellAppearance = FileAppearanceService.getInstance().forVirtualFile(file);
  }

  public TableItem(@NotNull final String url) {
    myUrl = url;

    final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file != null) {
      myCellAppearance = FileAppearanceService.getInstance().forVirtualFile(file);
    }
    else {
      myCellAppearance = FileAppearanceService.getInstance().forInvalidUrl(url);
    }
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public CellAppearanceEx getCellAppearance() {
    return myCellAppearance;
  }
}
