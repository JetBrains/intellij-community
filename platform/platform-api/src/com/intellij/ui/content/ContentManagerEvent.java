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
package com.intellij.ui.content;

import java.util.EventObject;

public class ContentManagerEvent extends EventObject {
  private Content myContent;
  private int myIndex;
  private boolean myConsumed;
  private ContentOperation myOperation;

  public ContentManagerEvent(Object source, Content content, int index, ContentOperation operation) {
    super(source);
    myContent = content;
    myIndex = index;
    myOperation = operation;
  }

  public ContentManagerEvent(Object source, Content content, int index) {
    this(source, content, index, ContentOperation.undefined);
  }

  public Content getContent() {
    return myContent;
  }

  public int getIndex() {
    return myIndex;
  }

  public boolean isConsumed() {
    return myConsumed;
  }

  public void consume() {
    myConsumed = true;
  }

  public ContentOperation getOperation() {
    return myOperation;
  }

  public static enum ContentOperation {
    add, remove, undefined
  }
}