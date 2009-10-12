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
package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.util.Icons;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class ChooseLibrariesDialog extends ChooseElementsDialog<Library> {

  public ChooseLibrariesDialog(Project project, List<? extends Library> items, String title, String description) {
    super(project, items, title, description, true);
  }

  protected String getItemText(final Library item) {
    return item != null ? PackagingEditorUtil.getLibraryItemText(item, true) : "";
  }

  protected Icon getItemIcon(final Library item) {
    if (item.getName() != null) {
      return Icons.LIBRARY_ICON;
    }
    VirtualFile[] files = item.getFiles(OrderRootType.CLASSES);
    if (files.length == 1) {
      return files[0].getFileType().getIcon();
    }
    return Icons.LIBRARY_ICON;
  }
}
