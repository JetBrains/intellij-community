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
package com.intellij.openapi.command.undo

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use {@link UndoUtil} instead")
interface UndoConstants {

  companion object {

    @JvmField
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use {@link UndoUtil#forceUndoIn(VirtualFile, Runnable)} instead")
    val FORCE_RECORD_UNDO: Key<Boolean> = Key.create<Boolean>("FORCE_RECORD_UNDO")

    @JvmField
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use {@link UndoUtil#disableUndoIn(Document, Runnable)} instead")
    val DONT_RECORD_UNDO: Key<Boolean> = Key.create<Boolean>("DONT_RECORD_UNDO")
  }
}
