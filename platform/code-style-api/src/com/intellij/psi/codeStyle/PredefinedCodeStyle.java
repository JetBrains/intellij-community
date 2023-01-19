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
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

/**
 * An extension point containing {@code apply()} methods to update the current style settings with a set of predefined values when
 * a user chooses "Set from|Predefined style" in Settings. In most cases it is sufficient to define {@link #apply(CodeStyleSettings)}
 * method. For example:
 * <pre><code>
 *   public class MyPredefinedStyle extends PredefinedCodeStyle {
 *     public MyPredefinedStyle() {
 *       super("MyStyle", MyLanguage.INSTANCE);
 *     }
 *
 *    {@code @Override}
 *     public void apply(CodeStyleSettings settings) {
 *       CommonCodeStyleSettings langSettings = settings.getCommonSettings(getLanguage());
 *       // Set up language settings ...
 *     }
 *   }
 * </code></pre>
 * to define the extension point add the following line to {@code plugin.xml}:
 * <pre><code>
 *   {@code <predefinedCodeStyle implementation="com.company.MyPredefinedStyle"/>}
 * </code></pre>
 */
public abstract class PredefinedCodeStyle {
  public static final ExtensionPointName<PredefinedCodeStyle> EP_NAME =
    ExtensionPointName.create("com.intellij.predefinedCodeStyle");

  public static final PredefinedCodeStyle[] EMPTY_ARRAY = {};
  private final @NlsContexts.ListItem String myName;
  private final Language myLanguage;

  public PredefinedCodeStyle(@NotNull @NlsContexts.ListItem String name, @NotNull Language language) {
    myName = name;
    myLanguage = language;
  }

  /**
   * Applies the predefined code style to given settings. Code style settings which are not specified by
   * the code style may be left unchanged (as defined by end-user).
   *
   * @param settings The settings to change.
   */
  public void apply(CodeStyleSettings settings) { }

  /**
   * Applies the predefined code style to given settings. Code style settings which are not specified by
   * the code style may be left unchanged (as defined by end-user).
   *
   * @param settings The settings to change.
   * @param language The language the given settings should be applied to.
   */
  public void apply(@NotNull CodeStyleSettings settings, @NotNull Language language) {
    apply(settings);
  }

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

  public @NlsContexts.ListItem String getName() {
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

  /**
   * Check whether this style is applicable to the given language.
   * Inheritor can override this method when the style is applicable to more than one language;
   * the default implementation just checks that the given language is equals to the {@link #getLanguage()} one.
   *
   * @param language the language to check.
   */
  public boolean isApplicableToLanguage(@NotNull Language language) {
    return myLanguage.equals(language);
  }
}
