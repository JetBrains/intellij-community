/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileTypes;

import java.util.EventObject;

public class FileTypeEvent extends EventObject{
  public FileTypeEvent(FileTypeManager manager) {
    super(manager);
  }

  public FileTypeManager getManager(){
    return (FileTypeManager) getSource();
  }
}
