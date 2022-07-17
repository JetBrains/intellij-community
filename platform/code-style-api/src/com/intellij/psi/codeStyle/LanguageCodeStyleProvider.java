// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static java.lang.Character.isLetterOrDigit;

@ApiStatus.Internal
public interface LanguageCodeStyleProvider extends CustomCodeStyleSettingsFactory {
  @Nullable
  static LanguageCodeStyleProvider forLanguage(Language language) {
    for (LanguageCodeStyleProvider provider : CodeStyleSettingsService.getInstance().getLanguageCodeStyleProviders()) {
      if (provider.getLanguage().equals(language)) {
        return provider;
      }
    }
    return null;
  }

  @Nullable
  static LanguageCodeStyleProvider findUsingBaseLanguage(@NotNull Language language) {
    for (Language currLang = language; currLang != null;  currLang = currLang.getBaseLanguage()) {
      LanguageCodeStyleProvider curr = forLanguage(currLang);
      if (curr != null) return curr;
    }
    return null;
  }

  @NotNull
  Language getLanguage();

  @NotNull
  CommonCodeStyleSettings getDefaultCommonSettings();

  @NotNull
  DocCommentSettings getDocCommentSettings(@NotNull CodeStyleSettings rootSettings);

  Set<String> getSupportedFields();

  /**
   * Return true if formatter for this language uses {@link CommonCodeStyleSettings#KEEP_LINE_BREAKS} flag
   * for custom line breaks processing
   */
  @ApiStatus.Experimental
  default boolean usesCommonKeepLineBreaks() {
    return false;
  }

  /**
   * Checks if formatter is allowed to enforce a leading space in the
   * line comment. Formatter will make a transformation like:
   * <pre>//comment</pre>  =>  <pre>// comment</pre>
   * in case of {@link CommenterOption#LINE_COMMENT_ADD_SPACE_ON_REFORMAT}
   * is enabled.
   * <br/>
   * <br/>
   * This method will be called before transformation to ensure if transformation is possible
   * to avoid breaking of some compiler directives. For example, Go compiler accepts directives
   * in the code starts from {@code //go:...} and the space between comment prefix and {@code go}
   * keyword is not allowed.
   * <br/>
   * <br/>
   * The default implementation checks whether comment is not empty and starts from
   * alphanumeric character. The typical implementation should add its own guard conditions
   * first and then return the super-call.
   *
   * @param commentContents Text of the comment <b>without</b> a comment prefix
   * @return {@code true} if and only if the transformation is allowed
   */
  default boolean canInsertSpaceInLineComment(@NotNull String commentContents) {
    if (commentContents.isBlank()) return false;
    if (!isLetterOrDigit(commentContents.charAt(0))) return false;
    return true;
  }
}
