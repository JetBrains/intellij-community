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
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.EventObject;

public class EditorMouseEvent extends EventObject {
  private final MouseEvent myMouseEvent;
  private final EditorMouseEventArea myEditorArea;

  public EditorMouseEvent(@NotNull Editor editor, MouseEvent mouseEvent, EditorMouseEventArea area) {
    super(editor);

    myMouseEvent = mouseEvent;
    myEditorArea = area;
  }

  @NotNull
  public Editor getEditor() {
    return (Editor) getSource();
  }

  public MouseEvent getMouseEvent() {
    return myMouseEvent;
  }

  public void consume() {
    myMouseEvent.consume();
  }

  public boolean isConsumed() {
    return myMouseEvent.isConsumed();
  }

  public EditorMouseEventArea getArea() {
    return myEditorArea;
  }
}
