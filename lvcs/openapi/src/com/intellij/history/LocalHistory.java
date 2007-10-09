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

package com.intellij.history;

import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistory implements SettingsSavingComponent {
  public static LocalHistoryAction startAction(Project p, String name) {
    return getInstance(p).startAction(name);
  }

  public static void putUserLabel(Project p, String name) {
    getInstance(p).putUserLabel(name);
  }

  public static void putUserLabel(Project p, VirtualFile f, String name) {
    getInstance(p).putUserLabel(f, name);
  }

  public static void putSystemLabel(Project p, String name) {
    getInstance(p).putSystemLabel(name, -1);
  }

  public static void putSystemLabel(Project p, String name, int color) {
    getInstance(p).putSystemLabel(name, color);
  }

  public static Checkpoint putCheckpoint(Project p) {
    return getInstance(p).putCheckpoint();
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

  private static LocalHistory getInstance(Project p) {
    return p.getComponent(LocalHistory.class);
  }

  protected abstract LocalHistoryAction startAction(String name);

  protected abstract void putUserLabel(String name);

  protected abstract void putUserLabel(VirtualFile f, String name);

  protected abstract void putSystemLabel(String name, int color);

  protected abstract Checkpoint putCheckpoint();

  protected abstract byte[] getByteContent(VirtualFile f, FileRevisionTimestampComparator c);

  protected abstract boolean isUnderControl(VirtualFile f);

  protected abstract boolean hasUnavailableContent(VirtualFile f);
}