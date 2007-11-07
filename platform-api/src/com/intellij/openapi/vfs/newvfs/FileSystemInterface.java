/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileSystemInterface {
  boolean exists(VirtualFile fileOrDirectory);

  String[] list(VirtualFile file);

  boolean isDirectory(VirtualFile file);

  long getTimeStamp(VirtualFile file);
  void setTimeStamp(VirtualFile file, long modstamp) throws IOException;

  boolean isWritable(VirtualFile file);
  void setWritable(VirtualFile file, boolean writableFlag) throws IOException;

  VirtualFile createChildDirectory(final Object requestor, VirtualFile parent, String dir) throws IOException;
  VirtualFile createChildFile(final Object requestor, VirtualFile parent, String file) throws IOException;

  void deleteFile(final Object requestor, VirtualFile file) throws IOException;
  void moveFile(final Object requestor, VirtualFile file, VirtualFile newParent) throws IOException;
  void renameFile(final Object requestor, VirtualFile file, String newName) throws IOException;
  VirtualFile copyFile(final Object requestor, VirtualFile file, VirtualFile newParent, final String copyName) throws IOException;

  InputStream getInputStream(VirtualFile file) throws IOException;
  OutputStream getOutputStream(VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException;

  long getLength(VirtualFile file);
}