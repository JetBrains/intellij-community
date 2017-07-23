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
package org.intellij.images.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actions.Filter;

public class TagFilter implements Filter {
  private String myTag;
  private ImageTagManager myManager;

  public TagFilter(String tag, ImageTagManager manager) {
    myTag = tag;
    myManager = manager;
  }

  @Override
  public String getDisplayName() {
    return myTag;
  }

  @Override
  public boolean accepts(VirtualFile file) {
    return myManager.hasTag(myTag, file);
  }

  @Override
  public boolean isApplicableToProject(Project project) {
    return true;
  }

  @Override
  public void setFilter(ThumbnailView view) {
    view.setTagFilter(this);
  }
}
