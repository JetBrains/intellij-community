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

import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistory implements SettingsSavingComponent {
  public static LocalHistoryAction startAction(Project p, String name) {
    return getInstance(p).startAction(name);
  }

  public static Label putUserLabel(Project p, String name) {
    return getInstance(p).putUserLabel(name);
  }

  public static Label putUserLabel(Project p, VirtualFile f, String name) {
    return getInstance(p).putUserLabel(f, name);
  }

  public static Label putSystemLabel(Project p, String name) {
    return getInstance(p).putSystemLabel(name);
  }

  public static Label putSystemLabel(Project p, String name, int color) {
    return getInstance(p).putSystemLabel(name, color);
  }

  public static byte[] getByteContent(Project p, VirtualFile f, FileRevisionTimestampComparator c) {
    return getInstance(p).getByteContent(f, c);
  }

  public static boolean isUnderControl(Project p, VirtualFile f) {
    return getInstance(p).isUnderControl(f);
  }

  public static boolean hasUnavailableContent(Project p, VirtualFile f) {
    return getInstance(p).hasUnavailableContent(f);
  }

  public static LocalHistory getInstance(Project p) {
    return p.getComponent(LocalHistory.class);
  }

  public abstract LocalHistoryAction startAction(String name);

  public abstract Label putUserLabel(String name);

  public abstract Label putUserLabel(VirtualFile f, String name);

  public abstract Label putSystemLabel(String name, int color);

  public Label putSystemLabel(String name) {
    return putSystemLabel(name, -1);
  }

  public abstract byte[] getByteContent(VirtualFile f, FileRevisionTimestampComparator c);

  public abstract boolean isUnderControl(VirtualFile f);

  public abstract boolean hasUnavailableContent(VirtualFile f);
}
