/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diff.actions.impl;

import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DataSnapshot;
import com.intellij.openapi.actionSystem.UiDataRule;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class DiffUiDataRule implements UiDataRule {
  @Override
  public void uiDataSnapshot(@NotNull DataSink sink, @NotNull DataSnapshot snapshot) {
    Navigatable navigatable = snapshot.get(DiffDataKeys.NAVIGATABLE);
    if (navigatable != null) {
      sink.set(DiffDataKeys.NAVIGATABLE_ARRAY, new Navigatable[]{navigatable});
    }
  }
}

