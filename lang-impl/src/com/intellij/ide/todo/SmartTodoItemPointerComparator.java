package com.intellij.ide.todo;

import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.openapi.util.TextRange;

import java.util.Comparator;

/**
 * @author Vladimir Kondratyev
 */
public final class SmartTodoItemPointerComparator implements Comparator{
  public static final SmartTodoItemPointerComparator ourInstance=new SmartTodoItemPointerComparator();

  private SmartTodoItemPointerComparator(){}

  public int compare(Object obj1,Object obj2){
    TextRange range1=((TodoItemNode)obj1).getValue().getTodoItem().getTextRange();
    TextRange range2=((TodoItemNode)obj2).getValue().getTodoItem().getTextRange();
    return range1.getStartOffset()-range2.getStartOffset();
  }
}
