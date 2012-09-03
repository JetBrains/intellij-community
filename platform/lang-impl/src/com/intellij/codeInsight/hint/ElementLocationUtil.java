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

package com.intellij.codeInsight.hint;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.List;

public class ElementLocationUtil {

  private ElementLocationUtil() {
  }

  public static void customizeElementLabel(final PsiElement element, final JLabel label) {
    if (element != null) {
      PsiFile file = element.getContainingFile();
      VirtualFile vfile = file == null ? null : file.getVirtualFile();

      if (vfile == null) {
        label.setText("");
        label.setIcon(null);

        return;
      }

      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
      final Module module = fileIndex.getModuleForFile(vfile);

      if (module != null) {
        label.setText(module.getName());
        label.setIcon(ModuleType.get(module).getIcon());
      }
      else {
        final List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(vfile);

        OrderEntry entry = null;

        for (OrderEntry order : entries) {
          if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
            entry = order;
            break;
          }
        }

        if (entry != null) {
          label.setText(entry.getPresentableName());
          label.setIcon(AllIcons.Nodes.PpLibFolder);
        }
      }
    }
  }
}
