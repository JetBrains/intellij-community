/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.command.undo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated Use {@link UndoUtil} instead
 */
@ApiStatus.ScheduledForRemoval
@Deprecated
public interface UndoConstants {
  /**
   * @deprecated Use {@link UndoUtil#forceUndoIn(VirtualFile, Runnable)} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  Key<Boolean> FORCE_RECORD_UNDO = Key.create("FORCE_RECORD_UNDO");

  /**
   * @deprecated Use {@link UndoUtil#disableUndoIn(Document, Runnable)} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  Key<Boolean> DONT_RECORD_UNDO = Key.create("DONT_RECORD_UNDO");
}
