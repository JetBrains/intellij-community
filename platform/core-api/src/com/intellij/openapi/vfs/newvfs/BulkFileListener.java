/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface BulkFileListener {
  /**
   * @deprecated obsolete, implement {@link BulkFileListener} directly (to be removed in IDEA 2019)
   */
  @Deprecated
  class Adapter implements BulkFileListener {

  }

  default void before(@NotNull List<? extends VFileEvent> events) { }

  default void after(@NotNull List<? extends VFileEvent> events) { }
}