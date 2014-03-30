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
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.FileAppearanceService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileState;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public class JumpFromRemoteFileToLocalAction extends AnAction {
  private final HttpVirtualFile myFile;
  private final Project myProject;

  public JumpFromRemoteFileToLocalAction(HttpVirtualFile file, Project project) {
    super("Find Local File", "", AllIcons.General.AutoscrollToSource);

    myFile = file;
    myProject = project;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(myFile.getFileInfo().getState() == RemoteFileState.DOWNLOADED);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Collection<VirtualFile> files = findLocalFiles(myProject, Urls.newFromVirtualFile(myFile), myFile.getName());
    if (files.isEmpty()) {
      Messages.showErrorDialog(myProject, "Cannot find local file for '" + myFile.getUrl() + "'", CommonBundle.getErrorTitle());
      return;
    }

    if (files.size() == 1) {
      navigateToFile(myProject, ContainerUtil.getFirstItem(files, null));
    }
    else {
      final JList list = new JBList(files);
      //noinspection unchecked
      list.setCellRenderer(new ColoredListCellRenderer() {
        @Override
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          FileAppearanceService.getInstance().forVirtualFile((VirtualFile)value).customize(this);
        }
      });
      new PopupChooserBuilder(list)
       .setTitle("Select Target File")
       .setMovable(true)
       .setItemChoosenCallback(new Runnable() {
         @Override
         public void run() {
           //noinspection deprecation
           for (Object value : list.getSelectedValues()) {
             navigateToFile(myProject, (VirtualFile)value);
           }
         }
       }).createPopup().showUnderneathOf(e.getInputEvent().getComponent());
    }
  }

  private static Collection<VirtualFile> findLocalFiles(Project project, Url url, String fileName) {
    for (LocalFileFinder finder : LocalFileFinder.EP_NAME.getExtensions()) {
      final VirtualFile file = finder.findLocalFile(url, project);
      if (file != null) {
        return Collections.singletonList(file);
      }
    }

    return FilenameIndex.getVirtualFilesByName(project, fileName, GlobalSearchScope.allScope(project));
  }

  private static void navigateToFile(Project project, @NotNull VirtualFile file) {
    new OpenFileDescriptor(project, file).navigate(true);
  }
}
