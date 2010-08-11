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

package com.intellij.injected.editor;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;

/**
 * @author Alexey
 */
public class CaretModelWindow implements CaretModel {
  private final CaretModel myDelegate;
  private final EditorEx myHostEditor;
  private final EditorWindow myEditorWindow;

  public CaretModelWindow(CaretModel delegate, EditorWindow editorWindow) {
    myDelegate = delegate;
    myHostEditor = (EditorEx)editorWindow.getDelegate();
    myEditorWindow = editorWindow;
  }

  public void moveCaretRelatively(final int columnShift,
                                  final int lineShift,
                                  final boolean withSelection,
                                  final boolean blockSelection,
                                  final boolean scrollToCaret) {
    myDelegate.moveCaretRelatively(columnShift, lineShift, withSelection, blockSelection, scrollToCaret);
  }

  public void moveToLogicalPosition(final LogicalPosition pos) {
    LogicalPosition hostPos = myEditorWindow.injectedToHost(pos);
    myDelegate.moveToLogicalPosition(hostPos);
  }

  public void moveToVisualPosition(final VisualPosition pos) {
    LogicalPosition hostPos = myEditorWindow.injectedToHost(myEditorWindow.visualToLogicalPosition(pos));
    myDelegate.moveToLogicalPosition(hostPos);
  }

  @Override
  public void moveToOffset(int offset) {
    moveToOffset(offset, false);
  }

  public void moveToOffset(final int offset, boolean locateBeforeSoftWrap) {
    int hostOffset = myEditorWindow.getDocument().injectedToHost(offset);
    myDelegate.moveToOffset(hostOffset, locateBeforeSoftWrap);
  }

  public LogicalPosition getLogicalPosition() {
    return myEditorWindow.hostToInjected(myHostEditor.offsetToLogicalPosition(myDelegate.getOffset()));
  }

  public VisualPosition getVisualPosition() {
    LogicalPosition logicalPosition = getLogicalPosition();
    return myEditorWindow.logicalToVisualPosition(logicalPosition);
  }

  public int getOffset() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getOffset());
  }

  private final ListenerWrapperMap<CaretListener> myCaretListeners = new ListenerWrapperMap<CaretListener>();
  public void addCaretListener(final CaretListener listener) {
    CaretListener wrapper = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        CaretEvent event = new CaretEvent(myEditorWindow, myEditorWindow.hostToInjected(e.getOldPosition()),
                                          myEditorWindow.hostToInjected(e.getNewPosition()));
        listener.caretPositionChanged(event);
      }
    };
    myCaretListeners.registerWrapper(listener, wrapper);
    myDelegate.addCaretListener(wrapper);
  }

  public void removeCaretListener(final CaretListener listener) {
    CaretListener wrapper = myCaretListeners.removeWrapper(listener);
    if (wrapper != null) {
      myDelegate.removeCaretListener(wrapper);
    }
  }

  public void disposeModel() {
    for (CaretListener wrapper : myCaretListeners.wrappers()) {
      myDelegate.removeCaretListener(wrapper);
    }
    myCaretListeners.clear();
  }

  public int getVisualLineStart() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getVisualLineStart());
  }

  public int getVisualLineEnd() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getVisualLineEnd());
  }

  public TextAttributes getTextAttributes() {
    return myDelegate.getTextAttributes();
  }
}