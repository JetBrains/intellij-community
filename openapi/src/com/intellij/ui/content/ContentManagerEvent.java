package com.intellij.ui.content;

import com.intellij.ui.content.Content;

import java.util.EventObject;

public class ContentManagerEvent extends EventObject {
  private Content myContent;
  private int myIndex;
  private boolean myConsumed;

  public ContentManagerEvent(Object source, Content content, int index) {
    super(source);
    myContent = content;
    myIndex = index;
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
}