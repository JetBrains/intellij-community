/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.EventListener;

/**
 * User: ksafonov
 */
public interface ImageContentProvider extends Disposable {

  interface ImageContent {
    @Nullable
    BufferedImage getImage();

    @Nullable
    String getFormat();
  }

  interface ContentChangeListener extends EventListener {
    void contentChanged();
  }

  ImageContent getContent();

  void addContentChangeListener(ContentChangeListener listener);

  @Nullable
  VirtualFile getVirtualFile();

  long getFileLength();

}
