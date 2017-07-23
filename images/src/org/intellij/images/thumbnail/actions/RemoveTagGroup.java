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
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.search.ImageTagManager;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RemoveTagGroup extends ActionGroup {
  public RemoveTagGroup() {
    setPopup(true);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    Project project = e.getProject();
    ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
    ImageTagManager tagManager = ImageTagManager.getInstance(project);
    if (view != null) {
      List<String> tags = null;
      for (VirtualFile file : view.getSelection()) {
        List<String> newTags = tagManager.getTags(file);
        if (newTags.isEmpty()) return EMPTY_ARRAY;
        if (tags == null) {
          tags = new ArrayList<>(newTags);
        }
        else {
          tags.retainAll(newTags);
        }
      }
      if (tags == null) return EMPTY_ARRAY;
      return tags.stream().map(tag -> new AnAction(tag) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          for (VirtualFile file : view.getSelection()) {
            tagManager.removeTag(tag, file);
          }
        }
      }).toArray(AnAction[]::new);
    }

    return EMPTY_ARRAY;
  }
}
