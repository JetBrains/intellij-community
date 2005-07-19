/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Represents some data that probably can be compared with some other.
 * @see com.intellij.openapi.diff.DiffRequest
 */
public abstract class DiffContent {
  private final ArrayList<Listener> myListeners = new ArrayList<Listener>();

  public void addListener(Listener listener) { myListeners.add(listener); }
  public void removeListener(Listener listener) { myListeners.remove(listener); }

  /**
   * This content becomes invalid for some reason. Diff tool should stop show it.
   */
  protected void fireContentInvalid() {
    Listener[] listeners = myListeners.toArray(new Listener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      Listener listener = listeners[i];
      listener.contentInvalid();
    }
  }

  /**
   * Means this content represents binary data. It should be used only for byte by byte comparison.
   * E.g. directories aren't binary (in spite of they aren't text)
   */
  public boolean isBinary() { return false; }

  /**
   * Called by {@link com.intellij.openapi.diff.DiffTool}
   * when document returned by {@link #getDocument()} is opened in editor. Implementors may use this notification to
   * add listeners when document is editing and remove when editing done to avoid memory leaks.
   * @param isAssigned true means editing started, false means editing stopped.
   * Total number of calls with true should be same as for false
   */
  public void onAssigned(boolean isAssigned) {}

  /**
   * Represents this content as Document
   * null means content has no text representation
   */
  public abstract Document getDocument();

  /**
   * Provides a way to open given text place in editor
   * null means given offset can't be opened in editor
   * @param offset in document returned by {@link #getDocument()}
   */
  public abstract OpenFileDescriptor getOpenFileDescriptor(int offset);

  /**
   *
   * @return VirtualFile from which this content gets data.
   * null means this content has no file associated
   */
  public abstract VirtualFile getFile();

  /**
   *
   * @return FileType of content.
   * null means use other content's type for this one
   */
  public abstract FileType getContentType();

  /**
   *
   * @return Binary represntation of content.
   * Should not be null if {@link #getFile()} returns existing not directory file
   * @throws IOException
   */
  public abstract byte[] getBytes() throws IOException;

  /**
   * Creates DiffContent associated with given file.
   * @param project
   * @return {@link FileContent} iff file not null, or null;
   * @param file
   */
  public static DiffContent fromFile(Project project, VirtualFile file) {
    return file != null ? new FileContent(project, file) : null;
  }

  /**
   * Creates DiffContent associated with given document
   * @param project
   * @return
   * @param document
   */
  public static DiffContent fromDocument(Project project, Document document) {
    return new DocumentContent(project, document);
  }

  public interface Listener {
    void contentInvalid();
  }
}
