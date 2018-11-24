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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.search.ImageTagManager;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class AddTagGroup extends ActionGroup {
  public AddTagGroup() {
    setPopup(true);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    Project project = e.getProject();
    ImageTagManager tagManager = ImageTagManager.getInstance(project);
    List<String> tags = tagManager.getAllTags();
    int tagsNumber = tags.size();
    AnAction[] actions = new AnAction[tagsNumber + 1];
    for (int i = 0; i < tagsNumber; i++) {
      String tag = tags.get(i);
      actions[i] = new AnAction(tag) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
          if (view != null) {
            for (VirtualFile file : view.getSelection()) {
              tagManager.addTag(tag, file);
            }
          }
        }

        @Override
        public void update(AnActionEvent e) {
          e.getPresentation().setEnabledAndVisible(false);
          ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
          if (view != null) {
            e.getPresentation().setEnabledAndVisible(Arrays.stream(view.getSelection()).noneMatch(file -> tagManager.hasTag(tag, file)));
          }
        }
      };
    }
    actions[tagsNumber] = new AnAction("New Tag") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
        if (view != null) {
          VirtualFile[] selection = view.getSelection();
          if (selection.length > 0) {
            String tag = Messages.showInputDialog("", "New Tag Name", null);
            if (tag != null) {
              for (VirtualFile file : selection) {
                tagManager.addTag(tag, file);
              }
            }
          }
        }
      }
    };

    return actions;
  }
}
