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

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.images.search.ImageTagManager;
import org.intellij.images.search.TagFilter;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FilterByTagActionGroup extends ActionGroup implements PopupAction {

    public FilterByTagActionGroup() {
        setPopup(true);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
        ImageTagManager tagManager = ImageTagManager.getInstance(project);
        e.getPresentation().setVisible(view != null && !tagManager.getAllTags().isEmpty());
        TagFilter[] filters = view != null ? view.getTagFilters() : null;
        e.getPresentation().setText(filters == null ? CommonBundle.message("action.text.all")
                                                    : StringUtil.join(filters, filter -> filter.getDisplayName(), ","));
        e.getPresentation().setIcon(AllIcons.Duplicates.SendToTheRight);
    }


    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

  @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        if (e == null) return AnAction.EMPTY_ARRAY;
        DefaultActionGroup group = new DefaultActionGroup();
        Project project = e.getProject();
        ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
        if (view == null) return AnAction.EMPTY_ARRAY;
        ImageTagManager tagManager = ImageTagManager.getInstance(project);

        List<MyToggleAction> tagActions =
          ContainerUtil.map(tagManager.getAllTags(), (@NlsSafe var tag) -> new MyToggleAction(view, new TagFilter(tag, tagManager)));
        group.add(new AnAction(IdeBundle.messagePointer("action.Anonymous.text.all")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                for (MyToggleAction tagAction : tagActions) {
                    tagAction.setSelected(e, false);
                }
            }
        });
        group.add(Separator.getInstance());

        group.addAll(tagActions);

        return group.getChildren(e);
    }

    private static class MyToggleAction extends ToggleAction {
        private final ThumbnailView myView;
        private final TagFilter myFilter;

        MyToggleAction(ThumbnailView view, TagFilter filter) {
            super(filter.getDisplayName());
            myView = view;
            myFilter = filter;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            TagFilter[] filters = myView.getTagFilters();
            return filters != null && ContainerUtil.exists(filters, f -> myFilter.getDisplayName().equals(f.getDisplayName()));
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.BGT;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            if (state) {
                myFilter.setFilter(myView);
            }
            else {
                myFilter.clearFilter(myView);
            }
        }
    }
}
