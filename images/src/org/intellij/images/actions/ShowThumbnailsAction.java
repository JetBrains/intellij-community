/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;

/**
 * Show thumbnail for directory.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class ShowThumbnailsAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project != null && file != null && file.isDirectory()) {
            ThumbnailManager thumbnailManager = ThumbnailManager.getManager(project);
            ThumbnailView thumbnailView = thumbnailManager.getThumbnailView();
            thumbnailView.setRoot(file);
            thumbnailView.setVisible(true);
            thumbnailView.activate();
        }
    }

    public void update(AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean enabled = file != null && file.isDirectory();
        if (ActionPlaces.isPopupPlace(e.getPlace())) {
            e.getPresentation().setEnabledAndVisible(enabled);
        }
        else {
            e.getPresentation().setEnabled(enabled);
        }
    }
}
