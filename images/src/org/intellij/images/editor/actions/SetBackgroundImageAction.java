/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.intellij.images.editor.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;

/**
 * @author gregsh
 */
public class SetBackgroundImageAction extends DumbAwareAction {

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean image = file != null && ImageFileTypeManager.getInstance().isImage(file);
    boolean visible = !ActionPlaces.isPopupPlace(e.getPlace()) || image;
    e.getPresentation().setEnabled(project != null);
    e.getPresentation().setVisible(visible);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean image = file != null && ImageFileTypeManager.getInstance().isImage(file);
    SetBackgroundImageDialog dialog = new SetBackgroundImageDialog(project, image ? file.getPath() : null);
    dialog.showAndGet();
  }
}
