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
package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.vcs.impl.FileStatusImpl;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FileStatusFactory {
  private static final FileStatusFactory ourInstance = new FileStatusFactory();
  private final List<FileStatus> myStatuses = new ArrayList<FileStatus>();

  private FileStatusFactory() {
  }

  public synchronized FileStatus createFileStatus(@NonNls String id, String description, Color color) {
    FileStatusImpl result = new FileStatusImpl(id, ColorKey.createColorKey("FILESTATUS_" + id, color), description);
    myStatuses.add(result);
    return result;
  }

  public synchronized FileStatus createOnlyColorForFileStatus(@NonNls String id, final Color color) {
    FileStatus result = new FileStatusImpl.OnlyColorFileStatus(id, ColorKey.createColorKey("FILESTATUS_" + id, color), null);
    myStatuses.add(result);
    return result;
  }

  public synchronized FileStatus[] getAllFileStatuses() {
    return myStatuses.toArray(new FileStatus[myStatuses.size()]);
  }

  public static FileStatusFactory getInstance() {
    return ourInstance;
  }

}