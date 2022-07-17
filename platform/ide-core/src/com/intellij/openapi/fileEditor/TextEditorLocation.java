// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.NotNull;

public class TextEditorLocation implements FileEditorLocation {
  private static final Logger LOG = Logger.getInstance(TextEditorLocation.class);

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
  public int compareTo(@NotNull FileEditorLocation fileEditorLocation) {
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
