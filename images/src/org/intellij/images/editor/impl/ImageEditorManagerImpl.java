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
package org.intellij.images.editor.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.editor.ImageEditor;
import org.jetbrains.annotations.NotNull;

/**
 * Image viewer manager implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class ImageEditorManagerImpl {
  private ImageEditorManagerImpl() {}

  /**
   * Create image viewer editor. Don't forget release editor by {@link #releaseImageEditor(ImageEditor)} method.
   *
   * @param project Project
   * @param file    File
   * @return Image editor for file
   */
  @NotNull
  public static ImageEditor createImageEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new ImageEditorImpl(project, file);
  }

  /**
   * Release editor. Disposing caches and other resources allocated in creation.
   *
   * @param editor Editor to release.
   */
  public static void releaseImageEditor(@NotNull ImageEditor editor) {
    if (!editor.isDisposed()) {
      editor.dispose();
    }
  }
}
