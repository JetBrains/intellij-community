/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Represents some data that probably can be compared with some other.
 *
 * @see com.intellij.openapi.diff.DiffRequest
 * @deprecated use {@link com.intellij.diff.contents.DiffContent} instead
 */
@Deprecated
public abstract class DiffContent {
  private boolean myIsEmpty;

  /**
   * Means this content represents binary data. It should be used only for byte by byte comparison.
   * E.g. directories aren't binary (in spite of they aren't text)
   *
   * @return true if this content represents binary data
   */
  public boolean isBinary() {
    return false;
  }

  public void setIsEmpty(boolean isEmpty) {
    myIsEmpty = isEmpty;
  }

  public boolean isEmpty() {
    return myIsEmpty;
  }

  /**
   * Represents this content as Document
   * null means content has no text representation
   *
   * @return document associated with this content
   */
  public abstract Document getDocument();

  /**
   * Provides a way to open given text place in editor
   * null means given offset can't be opened in editor
   *
   * @param offset in document returned by {@link #getDocument()}
   * @return {@link com.intellij.openapi.fileEditor.OpenFileDescriptor} to open this content in editor
   */
  public abstract Navigatable getOpenFileDescriptor(int offset);

  /**
   * @return VirtualFile from which this content gets data.
   *         null means this content has no file associated
   */
  @Nullable
  public abstract VirtualFile getFile();

  /**
   * @return FileType of content.
   *         null means use other content's type for this one
   */
  @Nullable 
  public abstract FileType getContentType();

  /**
   * @return Binary representation of content.
   *         Should not be null if {@link #getFile()} returns existing not directory file
   * @throws java.io.IOException
   */
  public abstract byte[] getBytes() throws IOException;

  /**
   * Creates DiffContent associated with given file.
   *
   * @return content associated with file
   */
  public static FileContent fromFile(Project project, VirtualFile file) {
    return file != null ? new FileContent(project, file) : null;
  }

  /**
   * Creates DiffContent associated with given document
   *
   * @return content associated with document
   */
  public static DocumentContent fromDocument(Project project, Document document) {
    return new DocumentContent(project, document);
  }

  /**
   * @return line separator used in this content, or null if it is unknown.
   */
  @Nullable
  public LineSeparator getLineSeparator() {
    return null;
  }
}
