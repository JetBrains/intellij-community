/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

public class VirtualFilePropertyEvent extends VirtualFileEvent {
  private final String myPropertyName;
  private final Object myOldValue;
  private final Object myNewValue;

  public VirtualFilePropertyEvent(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue){
    super(requestor, file, file.getName(), file.isDirectory(), file.getParent());
    myPropertyName = propertyName;
    myOldValue = oldValue;
    myNewValue = newValue;
  }

  public String getPropertyName(){
    return myPropertyName;
  }

  public Object getOldValue(){
    return myOldValue;
  }

  public Object getNewValue(){
    return myNewValue;
  }
}
