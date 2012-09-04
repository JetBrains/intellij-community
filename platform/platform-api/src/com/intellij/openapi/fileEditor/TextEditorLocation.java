/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class TextEditorLocation implements FileEditorLocation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.TextEditorLocation");

  private final TextEditor myEditor;
  private final LogicalPosition myPosition;

  public TextEditorLocation(int offset, @NotNull TextEditor editor) {
    this(editor.getEditor().offsetToLogicalPosition(offset), editor);
  }

  public TextEditorLocation(@NotNull LogicalPosition position, @NotNull TextEditor editor) {
    myEditor = editor;
    myPosition = position;
  }


  @NotNull
  @Override
  public FileEditor getEditor() {
    return myEditor;
  }

  @NotNull
  public LogicalPosition getPosition() {
    return myPosition;
  }

  @Override
  public int compareTo(FileEditorLocation fileEditorLocation) {
    TextEditorLocation otherLocation = (TextEditorLocation)fileEditorLocation;
    if (myEditor != otherLocation.myEditor) {
      LOG.error("Different editors: " + myEditor + "; and " + otherLocation.myEditor);
    }

    return myPosition.compareTo(otherLocation.myPosition);
  }

  public String toString() {
    return myPosition.toString();
  }
}
