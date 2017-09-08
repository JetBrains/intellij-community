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

package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;

import java.awt.*;

public interface ParameterInfoUIContext {
  String setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout,
                                      boolean isDisabledBeforeHighlight, Color background);
  boolean isUIComponentEnabled();
  void setUIComponentEnabled(boolean enabled);

  int getCurrentParameterIndex();
  PsiElement getParameterOwner();

  boolean isSingleOverload();
  boolean isSingleParameterInfo();
  Color getDefaultParameterColor();
}
