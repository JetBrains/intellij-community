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

package com.intellij.historyIntegrTests.revertion;

import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;

public class IsFromRefreshFileListener extends VirtualFileAdapter {
  private String myLog = "";

  @Override
  public void fileCreated(VirtualFileEvent e) {
    log(e.getFile().isDirectory() ? "createDir" : "createFile", e);
  }

  @Override
  public void fileDeleted(VirtualFileEvent e) {
    log("delete", e);
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent e) {
    log("move", e);
  }

  @Override
  public void contentsChanged(VirtualFileEvent e) {
    log("content", e);
  }

  @Override
  public void propertyChanged(VirtualFilePropertyEvent e) {
    log("rename", e);
  }

  private void log(String s, VirtualFileEvent e) {
    myLog += s + " " + e.isFromRefresh() + " ";
  }

  public String getLog() {
    return myLog;
  }
}
