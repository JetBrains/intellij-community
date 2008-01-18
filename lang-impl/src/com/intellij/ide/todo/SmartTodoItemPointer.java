package com.intellij.ide.todo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.TodoItem;

/**
 * @author Vladimir Kondratyev
 */
public final class SmartTodoItemPointer {
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.todo.SmartTodoItemPointer");

  private final TodoItem myTodoItem;
  private final Document myDocument;
  private final RangeMarker myRangeMarker;

  public SmartTodoItemPointer(TodoItem todoItem,Document document){
    LOG.assertTrue(todoItem!=null);
    myTodoItem=todoItem;
    LOG.assertTrue(document!=null);
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
