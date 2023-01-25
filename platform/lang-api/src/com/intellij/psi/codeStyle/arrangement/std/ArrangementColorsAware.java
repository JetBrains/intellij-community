/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Callback which allows to customize colors used at the arrangement UI on the basis of existing coloring scheme.
 * <p/>
 * It's save to return 'null' from all color retrieval services - default values will be used then.
 */
public interface ArrangementColorsAware {
  
  @Nullable
  TextAttributes getTextAttributes(@NotNull EditorColorsScheme scheme, @NotNull ArrangementSettingsToken token, boolean selected);
  
  @Nullable
  Color getBorderColor(@NotNull EditorColorsScheme scheme, boolean selected);
}
