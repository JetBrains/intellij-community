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
package com.intellij.openapi.vfs;

import java.util.EventObject;

public class ModificationAttemptEvent extends EventObject{
  private final VirtualFile[] myFiles;
  private boolean myConsumed = false;

  public ModificationAttemptEvent(VirtualFileManager manager, VirtualFile[] files) {
    super(manager);
    myFiles = files;
  }

  public VirtualFile[] getFiles() {
    return myFiles;
  }

  public void consume(){
    myConsumed = true;
  }

  public boolean isConsumed() {
    return myConsumed;
  }
}
