// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.todo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.TodoItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SmartTodoItemPointer {

  private final @NotNull TodoItem myTodoItem;
  private final @NotNull Document myDocument;
  private final @NotNull RangeMarker myRangeMarker;
  private final @NotNull List<RangeMarker> myAdditionalRangeMarkers;

  public SmartTodoItemPointer(@NotNull TodoItem todoItem,
                              @NotNull Document document) {
    myTodoItem = todoItem;
    myDocument = document;
    TextRange textRange = myTodoItem.getTextRange();
    myRangeMarker = document.createRangeMarker(textRange);
    myAdditionalRangeMarkers = ContainerUtil.map(todoItem.getAdditionalTextRanges(), document::createRangeMarker);
  }

  public @NotNull TodoItem getTodoItem() {
    return myTodoItem;
  }

  public @NotNull Document getDocument() {
    return myDocument;
  }

  public @NotNull RangeMarker getRangeMarker() {
    return myRangeMarker;
  }

  public @NotNull List<RangeMarker> getAdditionalRangeMarkers() {
    return myAdditionalRangeMarkers;
  }

  public boolean equals(Object obj){
    if(!(obj instanceof SmartTodoItemPointer)){
      return false;
    }
    SmartTodoItemPointer pointer=(SmartTodoItemPointer)obj;
    if (!(myTodoItem.getFile().equals(pointer.myTodoItem.getFile())&&
          myRangeMarker.getStartOffset()==pointer.myRangeMarker.getStartOffset()&&
          myRangeMarker.getEndOffset()==pointer.myRangeMarker.getEndOffset()&&
          myTodoItem.getPattern().equals(pointer.myTodoItem.getPattern()) &&
          myAdditionalRangeMarkers.size() == pointer.myAdditionalRangeMarkers.size())) {
      return false;
    }
    for (int i = 0; i < myAdditionalRangeMarkers.size(); i++) {
      RangeMarker m1 = myAdditionalRangeMarkers.get(i);
      RangeMarker m2 = pointer.myAdditionalRangeMarkers.get(i);
      if (m1.getStartOffset() != m2.getStartOffset() || m1.getEndOffset() != m2.getEndOffset()) {
        return false;
      }
    }
    return true;
  }

  public int hashCode(){
    return myTodoItem.getFile().hashCode();
  }
}
