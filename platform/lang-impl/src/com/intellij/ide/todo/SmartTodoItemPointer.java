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

package com.intellij.ide.todo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.TodoItem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladimir Kondratyev
 */
public final class SmartTodoItemPointer {
  private final TodoItem myTodoItem;
  private final Document myDocument;
  private final RangeMarker myRangeMarker;

  public SmartTodoItemPointer(@NotNull TodoItem todoItem,@NotNull Document document){
    myTodoItem=todoItem;
    myDocument=document;
    TextRange textRange=myTodoItem.getTextRange();
    myRangeMarker=document.createRangeMarker(textRange);
  }

  public TodoItem getTodoItem(){
    return myTodoItem;
  }

  public Document getDocument(){
    return myDocument;
  }

  public RangeMarker getRangeMarker(){
    return myRangeMarker;
  }

  public boolean equals(Object obj){
    if(!(obj instanceof SmartTodoItemPointer)){
      return false;
    }
    SmartTodoItemPointer pointer=(SmartTodoItemPointer)obj;
    return myTodoItem.getFile().equals(pointer.myTodoItem.getFile())&&
          myRangeMarker.getStartOffset()==pointer.myRangeMarker.getStartOffset()&&
          myRangeMarker.getEndOffset()==pointer.myRangeMarker.getEndOffset()&&
          myTodoItem.getPattern().equals(pointer.myTodoItem.getPattern());
  }

  public int hashCode(){
    return myTodoItem.getFile().hashCode();
  }
}
