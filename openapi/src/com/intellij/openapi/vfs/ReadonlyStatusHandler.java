/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.CommonBundle;
import com.intellij.openapi.components.ServiceManager;
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
          for (VirtualFile file : myReadonlyFiles) {
            buf.append('\n');
            buf.append(file.getPresentableUrl());
          }

          return CommonBundle.message("failed.to.make.the.following.files.writable.error.message", buf.toString());
        }
        else {
          return CommonBundle.message("failed.to.make.file.writeable.error.message", myReadonlyFiles[0].getPresentableUrl());
        }
      }
      return null;
    }
  }

  public abstract OperationStatus ensureFilesWritable(VirtualFile... files);

  public static ReadonlyStatusHandler getInstance(Project project) {
    return ServiceManager.getService(project, ReadonlyStatusHandler.class);
  }
}
