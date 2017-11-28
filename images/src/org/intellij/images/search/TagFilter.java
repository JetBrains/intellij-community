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
import com.intellij.util.ArrayUtil;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actions.Filter;

import java.util.Objects;

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
    TagFilter[] filters = view.getTagFilters();
    view.setTagFilters(filters != null ? ArrayUtil.append(filters, this) : new TagFilter[] {this});
  }

  @Override
  public void clearFilter(ThumbnailView view) {
    TagFilter[] filters = view.getTagFilters();
    if (filters != null) {
      filters = ArrayUtil.remove(filters, this);
      view.setTagFilters(filters.length == 0 ? null : filters);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TagFilter filter = (TagFilter)o;
    return Objects.equals(myTag, filter.myTag);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myTag);
  }
}
