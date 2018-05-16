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
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.ui.ColorUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public abstract class EditorColorsManager {
  public static final Topic<EditorColorsListener> TOPIC = Topic.create("EditorColorsListener", EditorColorsListener.class);

  @NonNls public static final String DEFAULT_SCHEME_NAME = "Default";

  @NonNls public static final String COLOR_SCHEME_FILE_EXTENSION = ".icls";

  public static EditorColorsManager getInstance() {
    return ServiceManager.getService(EditorColorsManager.class);
  }

  public abstract void addColorsScheme(@NotNull EditorColorsScheme scheme);

  @Deprecated
  public abstract void removeAllSchemes();

  @Deprecated
  public abstract void setSchemes(@NotNull List<EditorColorsScheme> schemes);

  @NotNull
  public abstract EditorColorsScheme[] getAllSchemes();

  public abstract void setGlobalScheme(EditorColorsScheme scheme);

  @NotNull
  public abstract EditorColorsScheme getGlobalScheme();

  public abstract EditorColorsScheme getScheme(@NotNull String schemeName);

  public abstract boolean isDefaultScheme(EditorColorsScheme scheme);

  /**
   * @deprecated use {@link #TOPIC} instead
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated
  public final void addEditorColorsListener(@NotNull EditorColorsListener listener) {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(TOPIC, listener);
  }

  /**
   * @deprecated use {@link #TOPIC} instead
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated
  public final void addEditorColorsListener(@NotNull EditorColorsListener listener, @NotNull Disposable disposable) {
    ApplicationManager.getApplication().getMessageBus().connect(disposable).subscribe(TOPIC, listener);
  }

  public abstract boolean isUseOnlyMonospacedFonts();

  public abstract void setUseOnlyMonospacedFonts(boolean b);

  @NotNull
  public EditorColorsScheme getSchemeForCurrentUITheme() {
    return getGlobalScheme();
  }

  public boolean isDarkEditor() {
    Color bg = getGlobalScheme().getDefaultBackground();
    return ColorUtil.isDark(bg);
  }
}
