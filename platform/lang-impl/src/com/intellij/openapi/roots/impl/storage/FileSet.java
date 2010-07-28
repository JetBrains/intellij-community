/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public interface FileSet {
  void listFiles(List<VirtualFile> list);

  boolean hasChanged();

  void commit() throws IOException;

  void listModifiedFiles(List<VirtualFile> list);
}
