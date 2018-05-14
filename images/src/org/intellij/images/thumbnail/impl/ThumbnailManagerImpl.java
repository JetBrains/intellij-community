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
package org.intellij.images.thumbnail.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;
import org.jetbrains.annotations.NotNull;

/**
 * Thumbail manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ThumbnailManagerImpl extends ThumbnailManager implements Disposable {
  private final Project project;
  private ThumbnailView thumbnailView;

  public ThumbnailManagerImpl(Project project) {
    this.project = project;
  }

  @NotNull
  public final ThumbnailView getThumbnailView() {
    if (thumbnailView == null) {
      thumbnailView = new ThumbnailViewImpl(project);
    }
    return thumbnailView;
  }

  public void dispose() {
    if (thumbnailView != null) {
      Disposer.dispose(thumbnailView);
      thumbnailView = null;
    }
  }
}
