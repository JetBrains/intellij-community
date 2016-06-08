/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import org.jetbrains.annotations.NotNull;

public class SmartClassAnchor {
  public static final SmartClassAnchor[] EMPTY_ARRAY = new SmartClassAnchor[0];

  public final int myId;
  public final int myFileId;
  final int myStubId;

  SmartClassAnchor(int symbolId, ClassAnchor classAnchor) {
    myId = symbolId;
    myFileId = classAnchor.myFileId;
    myStubId = classAnchor.myStubId;
  }

  @NotNull
  VirtualFile retrieveFile() {
    VirtualFile file = PersistentFS.getInstance().findFileById(myFileId);
    assert file != null;
    return file;
  }

  @Override
  public String toString() {
    return myStubId + " in " + retrieveFile().getPath();
  }
}
