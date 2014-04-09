/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.richcopy.model;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 3/25/13 1:19 PM
 */
public class SyntaxInfo implements Serializable {

  private static final long serialVersionUID = 1L;

  @NotNull private final List<OutputInfo> myOutputInfos;
  @NotNull private final ColorRegistry    myColorRegistry;
  @NotNull private final FontNameRegistry myFontNameRegistry;

  private final int myDefaultForeground;
  private final int myDefaultBackground;
  private final int mySingleFontSize;

  public SyntaxInfo(@NotNull List<OutputInfo> infos,
                    int defaultForeground,
                    int defaultBackground,
                    int singleFontSize,
                    @NotNull FontNameRegistry fontNameRegistry,
                    @NotNull ColorRegistry colorRegistry)
  {
    myOutputInfos = infos;
    myDefaultForeground = defaultForeground;
    myDefaultBackground = defaultBackground;
    mySingleFontSize = singleFontSize;
    myFontNameRegistry = fontNameRegistry;
    myColorRegistry = colorRegistry;
  }

  @NotNull
  public List<OutputInfo> getOutputInfos() {
    return myOutputInfos;
  }

  @NotNull
  public ColorRegistry getColorRegistry() {
    return myColorRegistry;
  }

  @NotNull
  public FontNameRegistry getFontNameRegistry() {
    return myFontNameRegistry;
  }

  public int getDefaultForeground() {
    return myDefaultForeground;
  }

  public int getDefaultBackground() {
    return myDefaultBackground;
  }

  /**
   * @return    positive value if all tokens have the same font size (returned value);
   *            non-positive value otherwise
   */
  public int getSingleFontSize() {
    return mySingleFontSize;
  }

  @Override
  public String toString() {
    return String.format("default colors: foreground=%d, background=%d; output infos: %s",
                         myDefaultForeground, myDefaultBackground, myOutputInfos);
  }
}
