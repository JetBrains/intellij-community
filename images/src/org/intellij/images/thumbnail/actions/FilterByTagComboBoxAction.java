/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package org.intellij.images.thumbnail.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.search.ImageTagManager;
import org.intellij.images.search.TagFilter;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class FilterByTagComboBoxAction extends ComboBoxAction {
    
    public void update(final AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
        ImageTagManager tagManager = ImageTagManager.getInstance(project);
        e.getPresentation().setVisible(view != null && !tagManager.getAllTags().isEmpty());
        TagFilter filter = view != null ? view.getTagFilter() : null;
        e.getPresentation().setText(filter == null ? "All" : filter.getDisplayName());
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(button));
        ImageTagManager tagManager = ImageTagManager.getInstance(project);
        group.add(new FilterImagesAction(new TagFilter("All", tagManager) {
            @Override
            public String getDisplayName() {
                return "All";
            }

            @Override
            public boolean accepts(VirtualFile file) {
                return true;
            }

            @Override
            public boolean isApplicableToProject(Project project) {
                return true;
            }
        }));

        for (String tag : tagManager.getAllTags()) {
            group.add(new FilterImagesAction(new TagFilter(tag, tagManager)));
        }

        return group;
    }
}
