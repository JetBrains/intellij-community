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
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class TooltipLinkHandler {
  /**
   * Override to handle mouse clicks on a link.
   *
   * @param refSuffix part of link's href attribute after registered prefix.
   * @param editor    an editor in which tooltip with a link was shown.
   * @return {@code true} if a link was handled.
   */
  public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
    return false;
  }

  /**
   * Override to show extended description on mouse clicks on a link or expand action.
   * This method is only called if {@link #handleLink(String, com.intellij.openapi.editor.Editor)}
   * returned {@code false}.
   *
   * @param refSuffix part of link's href attribute after registered prefix.
   * @param editor    an editor in which tooltip with a link was shown.
   * @return detailed description to show.
   */
  @Nullable
  public String getDescription(@NotNull String refSuffix, @NotNull Editor editor) {
    return null;
  }
}
