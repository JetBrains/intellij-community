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
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.html.StyleSheet;

/**
 * Enumerates common font size values (inspired by CSS {@code 'font-size'} property values).
 * <p/>
 * Note that such elements selection (and this enum existence at all) is based on the fact that standard Swing {@link JEditorPane}
 * used by IJ for providing quick doc works only with predefined set of font sizes (see {@link StyleSheet#sizeMapDefault}).
 *
 * @author Denis Zhdanov
 * @since 1/26/11 10:22 AM
 */
public enum FontSize {

  XX_SMALL(8), X_SMALL(10), SMALL(12), MEDIUM(14), LARGE(18), X_LARGE(24), XX_LARGE(36);

  private final int    mySize;

  FontSize(int size) {
    mySize = size;
  }

  public int getSize() {
    return mySize;
  }

  /**
   * @return    {@link FontSize} that is one unit large than the current one; current object if it already stands for a maximum size
   */
  @NotNull
  public FontSize larger() {
    int i = ordinal();
    return i >= values().length - 1 ? this : values()[i + 1];
  }

  /**
   * @return    {@link FontSize} that is one unit smaller than the current one; current object if it already stands for a minimum size
   */
  @NotNull
  public FontSize smaller() {
    int i = ordinal();
    return i > 0 ? values()[i - 1] : this;
  }
}
