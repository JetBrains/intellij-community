/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;

public interface VirtualFilePointer extends JDOMExternalizable, UserDataHolder {
  String getFileName();

  VirtualFile getFile();

  String getUrl();

  String getPresentableUrl();

  boolean isValid();
}
