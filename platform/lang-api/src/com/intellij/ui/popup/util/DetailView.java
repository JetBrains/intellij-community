/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.popup.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author zajac
 * @since 9.05.2012
 */
public interface DetailView extends UserDataHolder {

  Editor getEditor();

  void navigateInPreviewEditor(PreviewEditorState editorState);

  JPanel getPropertiesPanel();

  void setPropertiesPanel(@Nullable JPanel panel);

  void clearEditor();

  PreviewEditorState getEditorState();

  ItemWrapper getCurrentItem();

  boolean hasEditorOnly();

  void setCurrentItem(@Nullable ItemWrapper item);


  class PreviewEditorState {
    public static PreviewEditorState EMPTY = new PreviewEditorState(null, null, null);

    public static PreviewEditorState create(VirtualFile file, int line) {
      return new PreviewEditorState(file, line < 0 ? null : new LogicalPosition(line, 0), null);
    }

    public static PreviewEditorState create(VirtualFile file, int line, TextAttributes attributes) {
      return new PreviewEditorState(file, line < 0 ? null : new LogicalPosition(line, 0), attributes);
    }

    private final VirtualFile myFile;
    private final LogicalPosition myNavigate;
    private final TextAttributes myAttributes;

    public PreviewEditorState(VirtualFile file, @Nullable LogicalPosition navigate, TextAttributes attributes) {
      myFile = file;
      myNavigate = navigate;
      myAttributes = attributes;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    @Nullable
    public LogicalPosition getNavigate() {
      return myNavigate;
    }

    public TextAttributes getAttributes() {
      return myAttributes;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PreviewEditorState state = (PreviewEditorState)o;

      if (myAttributes != null ? !myAttributes.equals(state.myAttributes) : state.myAttributes != null) return false;
      if (myFile != null ? !myFile.equals(state.myFile) : state.myFile != null) return false;
      if (myNavigate != null ? !myNavigate.equals(state.myNavigate) : state.myNavigate != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myFile != null ? myFile.hashCode() : 0;
      result = 31 * result + (myNavigate != null ? myNavigate.hashCode() : 0);
      result = 31 * result + (myAttributes != null ? myAttributes.hashCode() : 0);
      return result;
    }
  }
}
