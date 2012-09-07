/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;

import java.io.IOException;

public class ReadOnlyAttributeUtil {
  /**
   * Sets specified read-only status for the spcified <code>file</code>.
   * This method can be performed only for files which are in local file system.
   *
   * @param file           file which read-only attribute to be changed.
   * @param readOnlyStatus new read-only status.
   * @throws java.lang.IllegalArgumentException
   *                     if passed <code>file</code> doesn't
   *                     belong to the local file system.
   * @throws IOException if some <code>IOException</code> occurred.
   */
  public static void setReadOnlyAttribute(VirtualFile file, boolean readOnlyStatus) throws IOException {
    if (file.getFileSystem().isReadOnly()) {
      throw new IllegalArgumentException("Wrong file system: " + file.getFileSystem());
    }

    if (file.isWritable() == !readOnlyStatus) {
      return;
    }

    if (file instanceof NewVirtualFile) {
      ((NewVirtualFile)file).setWritable(!readOnlyStatus);
    }
    else {
      String path = file.getPresentableUrl();
      setReadOnlyAttribute(path, readOnlyStatus);
      file.refresh(false, false);
    }
  }

  public static void setReadOnlyAttribute(String path, boolean readOnlyStatus) throws IOException {
    FileUtil.setReadOnlyAttribute(path, readOnlyStatus);
  }
}
