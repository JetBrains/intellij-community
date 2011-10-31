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

import com.intellij.openapi.vfs.*;
import com.intellij.util.EventDispatcher;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
* User: ksafonov
*/
class VirtualFileImageContentProvider extends VirtualFileAdapter implements ImageContentProvider {
  private final VirtualFile myFile;
  private final EventDispatcher<ContentChangeListener> myEventDispatcher = EventDispatcher.create(ContentChangeListener.class);

  private static final ImageContent NULL_CONTENT = new ImageContent() {
    public BufferedImage getImage() {
      return null;
    }

    public String getFormat() {
      return null;
    }
  };

  public VirtualFileImageContentProvider(final VirtualFile virtualFile) {
    myFile = virtualFile;
    VirtualFileManager.getInstance().addVirtualFileListener(this);
  }

  public ImageContent getContent() {
    if (ImageFileTypeManager.getInstance().isImage(myFile)) {
      return new ImageContent() {
        @Nullable
        public BufferedImage getImage() {
          try {
            return IfsUtil.getImage(myFile);
          }
          catch (IOException e) {
            return null;
          }
        }

        @Nullable
        public String getFormat() {
          try {
            return IfsUtil.getFormat(myFile);
          }
          catch (IOException e) {
            return null;
          }
        }
      };
    }
    else {
      return NULL_CONTENT;
    }
  }

  public void addContentChangeListener(final ContentChangeListener listener) {
    myEventDispatcher.addListener(listener, this);
  }

  public VirtualFile getVirtualFile() {
    return myFile;
  }

  public long getFileLength() {
    return myFile.getLength();
  }

  public void dispose() {
    VirtualFileManager.getInstance().removeVirtualFileListener(this);
  }

  public void propertyChanged(VirtualFilePropertyEvent event) {
    onFileChange(event);
  }

  public void contentsChanged(VirtualFileEvent event) {
    onFileChange(event);
  }

  private void onFileChange(final VirtualFileEvent event) {
    if (myFile.equals(event.getFile())) {
      // Change document
      myFile.refresh(true, false, new Runnable() {
        public void run() {
          myEventDispatcher.getMulticaster().contentChanged();
        }
      });
    }
  }
}
