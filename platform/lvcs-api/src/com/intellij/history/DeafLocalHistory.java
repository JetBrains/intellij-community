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

/*
 * @author max
 */
package com.intellij.history;

import com.intellij.openapi.vfs.VirtualFile;

public class DeafLocalHistory extends LocalHistory {
  public byte[] getByteContent(final VirtualFile f, final FileRevisionTimestampComparator c) {
    throw new UnsupportedOperationException();
  }

  public boolean hasUnavailableContent(final VirtualFile f) {
    return false;
  }

  public boolean isUnderControl(final VirtualFile f) {
    return false;
  }

  public Label putSystemLabel(final String name, final int color) {
    return Label.NULL_INSTANCE;
  }

  public Label putUserLabel(final VirtualFile f, final String name) {
    return Label.NULL_INSTANCE;
  }

  public Label putUserLabel(final String name) {
    return Label.NULL_INSTANCE;
  }

  public LocalHistoryAction startAction(final String name) {
    return LocalHistoryAction.NULL;
  }

  public void save() {
  }
}
