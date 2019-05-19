/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.diff;

/**
 * Can be used in pair with {@link com.intellij.diff.util.DiffUtil#runPreservingFocus(FocusableContext, Runnable))
 * to keep focus on modifications of component tree.
 * <p>
 * NB: focus requested via {@link java.awt.Component#requestFocusInWindow()}, ignoring {@link com.intellij.openapi.wm.IdeFocusManager}.
 */
public interface FocusableContext {
  boolean isFocusedInWindow();

  void requestFocusInWindow();
}
