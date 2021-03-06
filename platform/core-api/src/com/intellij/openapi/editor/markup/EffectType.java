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
package com.intellij.openapi.editor.markup;

import org.jetbrains.annotations.ApiStatus;

public enum EffectType {
  LINE_UNDERSCORE,
  WAVE_UNDERSCORE,
  BOXED,
  STRIKEOUT,
  BOLD_LINE_UNDERSCORE,
  BOLD_DOTTED_LINE,
  SEARCH_MATCH,
  ROUNDED_BOX,
  @ApiStatus.Experimental SLIGHTLY_WIDER_BOX
}
