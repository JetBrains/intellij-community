/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import java.util.EventObject;

public class VirtualFileEvent extends EventObject {
  private final VirtualFile myFile;
  private final VirtualFile myParent;
  private final Object myRequestor;
  private final boolean myIsDirectory;
  private final String myFileName;

  private long myOldModificationStamp;
  private long myNewModificationStamp;

  public VirtualFileEvent(Object requestor, VirtualFile file, String fileName, boolean isDirectory, VirtualFile parent){
    super(file);
    myRequestor = requestor;
    myFile = file;
    myFileName = fileName;
    myIsDirectory = isDirectory;
    myParent = parent;
  }

  public VirtualFileEvent(Object requestor, VirtualFile file, VirtualFile parent, long oldModificationStamp, long newModificationStamp){
    super(file);
    myFile = file;
    myFileName = file.getName();
    myIsDirectory = false;
    myParent = parent;
    myRequestor = requestor;
    myOldModificationStamp = oldModificationStamp;
    myNewModificationStamp = newModificationStamp;
  }

  public VirtualFile getFile(){
    return myFile;
  }

  public String getFileName() {
    return myFileName;
  }

  public boolean isDirectory(){
    return myIsDirectory;
  }

  public VirtualFile getParent(){
    return myParent;
  }

  public Object getRequestor(){
    return myRequestor;
  }

  public long getOldModificationStamp(){
    return myOldModificationStamp;
  }

  public long getNewModificationStamp(){
    return myNewModificationStamp;
  }
}
