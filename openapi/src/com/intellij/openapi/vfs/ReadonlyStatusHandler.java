/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.project.Project;

public abstract class ReadonlyStatusHandler {

  public static class UnsuccessfulOperation extends Exception {
    public UnsuccessfulOperation(String message) {
      super(message);
    }
  }

  public static class OperationStatus {
    private final VirtualFile[] myReadonlyFiles;
    private final VirtualFile[] myUpdatedFiles;

    public OperationStatus(final VirtualFile[] readonlyFiles, final VirtualFile[] updatedFiles) {
      myReadonlyFiles = readonlyFiles;
      myUpdatedFiles = updatedFiles;
    }

    public VirtualFile[] getReadonlyFiles() {
      return myReadonlyFiles;
    }

    public VirtualFile[] getUpdatedFiles() {
      return myUpdatedFiles;
    }

    public boolean hasUpdatedFiles() {
      return myUpdatedFiles.length > 0;
    }

    public boolean hasReadonlyFiles() {
      return myReadonlyFiles.length > 0;
    }

    public String getReadonlyFilesMessage() {
      if (hasReadonlyFiles()) {
        StringBuffer buf = new StringBuffer();
        if (myReadonlyFiles.length > 1) {
          buf.append("Failed to make the following files writeable:");
          for (int i = 0; i < myReadonlyFiles.length; i++) {
            VirtualFile file = myReadonlyFiles[i];
            buf.append('\n');
            buf.append(file.getPresentableUrl());
          }
        }
        else {
          buf.append("Failed to make ");
          buf.append(myReadonlyFiles[0].getPresentableUrl());
          buf.append(" writeable.");
        }
        return buf.toString();
      }
      return null;
    }
  }

  public abstract OperationStatus ensureFilesWriteable(VirtualFile[] files);

  public static ReadonlyStatusHandler getInstance(Project project) {
    return project.getComponent(ReadonlyStatusHandler.class);
  }
}
