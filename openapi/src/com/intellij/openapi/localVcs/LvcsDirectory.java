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
package com.intellij.openapi.localVcs;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @deprecated use LocalHistory instead
 */
public interface LvcsDirectory extends LvcsObject {
  LvcsFile[] getFiles();
  LvcsFile[] getFiles(LvcsLabel label);

  LvcsDirectory[] getDirectories();
  LvcsDirectory[] getDirectories(LvcsLabel label);

  LvcsObject[] getChildren();
  LvcsObject[] getChildren(LvcsLabel label);

  LvcsDirectory addDirectory(String name, VirtualFile onDisk);

  LvcsFile addFile(String name, VirtualFileInfo virtualFileInfo);
}
