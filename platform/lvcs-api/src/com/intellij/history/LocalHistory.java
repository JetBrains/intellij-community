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

package com.intellij.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistory {
  public static final Object VFS_EVENT_REQUESTOR = new Object();

  public static LocalHistory getInstance() {
    return ApplicationManager.getApplication().getComponent(LocalHistory.class);
  }

  public abstract LocalHistoryAction startAction(String name);

  public abstract Label putSystemLabel(Project p, String name, int color);

  public Label putSystemLabel(Project p, String name) {
    return putSystemLabel(p, name, -1);
  }

  public abstract Label putUserLabel(Project p, String name);

  public abstract byte[] getByteContent(VirtualFile f, FileRevisionTimestampComparator c);

  public abstract boolean isUnderControl(VirtualFile f);
}
