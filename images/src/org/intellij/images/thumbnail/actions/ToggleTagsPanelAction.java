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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;

public final class ToggleTagsPanelAction extends ToggleAction {
    public static final String TAGS_PANEL_VISIBLE = "tags.panel.visible";
    public static final String TAGS_PANEL_PROPORTION = "tags.panel.proportion";
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        return project != null && PropertiesComponent.getInstance(project).getBoolean(TAGS_PANEL_VISIBLE, false);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance(e.getProject()).setValue(TAGS_PANEL_VISIBLE, state);
        ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
        assert view != null;
        view.refresh();
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
        ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
        e.getPresentation().setEnabledAndVisible(view != null);
        e.getPresentation().setText(isSelected(e) ? IdeBundle.message("action.text.hide.tags.panel") : IdeBundle.message("action.text.show.tags.panel"));
        super.update(e);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
}
