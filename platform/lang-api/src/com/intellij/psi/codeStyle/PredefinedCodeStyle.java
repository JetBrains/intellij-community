/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;


import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @author Rustam Vishnyakov
 */
public abstract class PredefinedCodeStyle {
  public static final ExtensionPointName<PredefinedCodeStyle> EP_NAME =
    ExtensionPointName.create("com.intellij.predefinedCodeStyle");

  public static final PredefinedCodeStyle[] EMPTY_ARRAY = {};
  private final String myName;
  private final Language myLanguage;

  public PredefinedCodeStyle(@NotNull String name, @NotNull Language language) {
    myName = name;
    myLanguage = language;
  }

  /**
   * Applies the predefined code style to given settings. Code style settings which are not specified by
   * the code style may be left unchanged (as defined by end-user). If the name doesn't match any predefined styles,
   * the method does nothing.
   *
   * @param settings      The settings to change.
   */
  public abstract void apply(CodeStyleSettings settings);

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof PredefinedCodeStyle)) return false;
    PredefinedCodeStyle otherStyle = (PredefinedCodeStyle)obj;
    return myName.equals(otherStyle.getName()) &&
           myLanguage.equals(otherStyle.getLanguage());
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myLanguage.hashCode();
    return result;
  }

  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return myName;
  }

  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }
}
