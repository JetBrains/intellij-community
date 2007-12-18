
package com.intellij.psi.impl.search;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;

public class TodoItemImpl implements TodoItem {
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  private final TodoPattern myPattern;

  public TodoItemImpl(PsiFile file, int startOffset, int endOffset, TodoPattern pattern) {
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPattern = pattern;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public TextRange getTextRange() {
    return new TextRange(myStartOffset, myEndOffset);
  }

  public TodoPattern getPattern() {
    return myPattern;
  }

  public int hashCode(){
    return myFile.hashCode()+myStartOffset+myEndOffset+myPattern.hashCode();
  }

  public boolean equals(Object obj){
    if(!(obj instanceof TodoItemImpl)){
      return false;
    }
    TodoItemImpl todoItem=(TodoItemImpl)obj;
    if(
      !myFile.equals(todoItem.myFile)||
      myStartOffset!=todoItem.myStartOffset||
      myEndOffset!=todoItem.myEndOffset||
      !myPattern.equals(todoItem.myPattern)
    ){
      return false;
    }
    return true;
  }
}