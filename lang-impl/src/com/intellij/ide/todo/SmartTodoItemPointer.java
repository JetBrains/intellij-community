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
    myRangeMarker=document.createRangeMarker(textRange.getStartOffset(),textRange.getEndOffset());
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
