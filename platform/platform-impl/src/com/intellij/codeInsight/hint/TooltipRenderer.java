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
package com.intellij.codeInsight.hint;

import com.intellij.openapi.editor.Editor;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;

import java.awt.*;

/**
 * @author cdr
 */
public interface TooltipRenderer {
  LightweightHint show(final Editor editor, Point p, boolean alignToRight, TooltipGroup group, HintHint intInfo);
}
