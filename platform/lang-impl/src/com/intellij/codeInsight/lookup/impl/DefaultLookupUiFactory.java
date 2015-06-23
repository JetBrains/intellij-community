/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

public class DefaultLookupUiFactory extends LookupUiFactory {
  @Override
  public boolean isAvailable(@NotNull Editor editor) {
    return true;
  }

  @NotNull
  @Override
  public LookupUi createLookupUi(@NotNull LookupImpl lookup, @NotNull Advertiser advertiser, @NotNull JBList list, @NotNull Project project) {
    return new LookupUiImpl(lookup, advertiser, list, project);
  }
}
