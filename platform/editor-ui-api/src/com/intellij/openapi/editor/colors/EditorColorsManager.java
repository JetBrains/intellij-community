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
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class EditorColorsManager {
  @NonNls public static final String DEFAULT_SCHEME_NAME = "Default";

  public static EditorColorsManager getInstance() {
    return ServiceManager.getService(EditorColorsManager.class);
  }

  public abstract void addColorsScheme(@NotNull EditorColorsScheme scheme);

  public abstract void removeAllSchemes();

  @NotNull
  public abstract EditorColorsScheme[] getAllSchemes();

  public abstract void setGlobalScheme(EditorColorsScheme scheme);

  @NotNull
  public abstract EditorColorsScheme getGlobalScheme();

  public abstract EditorColorsScheme getScheme(@NonNls String schemeName);

  public abstract boolean isDefaultScheme(EditorColorsScheme scheme);

  public abstract void addEditorColorsListener(@NotNull EditorColorsListener listener);
  public abstract void removeEditorColorsListener(@NotNull EditorColorsListener listener);
  public abstract void addEditorColorsListener(@NotNull EditorColorsListener listener, @NotNull Disposable disposable);

  public abstract boolean isUseOnlyMonospacedFonts();
  public abstract void setUseOnlyMonospacedFonts(boolean b);
}
