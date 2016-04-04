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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface Label {
  Label NULL_INSTANCE = new Label() {

    @Override
    public void revert(@NotNull Project project, @NotNull VirtualFile file) {
    }

    public ByteContent getByteContent(String path) {
      return null;
    }
  };

  /**
   * Revert all changes up to this Label according to the local history
   *
   * @param file file or directory that should be reverted
   * @throws LocalHistoryException
   */
  void revert(@NotNull Project project, @NotNull VirtualFile file) throws LocalHistoryException;

  ByteContent getByteContent(String path);
}
