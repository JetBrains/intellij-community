
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

  @Override
  public PsiFile getFile() {
    return myFile;
  }

  @Override
  public TextRange getTextRange() {
    return new TextRange(myStartOffset, myEndOffset);
  }

  @Override
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