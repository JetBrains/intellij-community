/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ListWithSelection;

class FileInfo {
  private final VirtualFile myFile;
  private final ListWithSelection<HandleType> myHandleType = new ListWithSelection<HandleType>();

  public FileInfo(VirtualFile file, Project project) {
    myFile = file;
    myHandleType.add(HandleType.USE_FILE_SYSTEM);
    myHandleType.selectFirst();
    final HandleTypeFactory[] typeFactories = Extensions.getExtensions(HandleTypeFactory.EP_NAME, project);
    for(HandleTypeFactory factory: typeFactories) {
      final HandleType handleType = factory.createHandleType(file);
      if (handleType != null) {
        myHandleType.add(handleType);
        myHandleType.select(handleType);
      }
    }
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public HandleType getSelectedHandleType() {
    return myHandleType.getSelection();
  }

  public boolean hasVersionControl() {
    return myHandleType.size() > 1;
  }

  public ListWithSelection<HandleType> getHandleType(){
    return myHandleType;
  }
}
