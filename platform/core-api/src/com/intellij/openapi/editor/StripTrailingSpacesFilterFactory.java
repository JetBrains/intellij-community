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
package com.intellij.openapi.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StripTrailingSpacesFilterFactory {
  public static final ExtensionPointName<StripTrailingSpacesFilterFactory> LINE_FILTER_EXTENSION_POINT
    = new ExtensionPointName<StripTrailingSpacesFilterFactory>("com.intellij.stripTrailingSpacesFilterFactory");

  public abstract StripTrailingSpacesFilter createFilter(@Nullable Project project, @NotNull Document document);
}
