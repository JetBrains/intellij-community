// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.Language;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface TemplateLanguageFileViewProvider extends FileViewProvider {

  /**
   * e.g. JSP
   * @return instanceof {@link TemplateLanguage}
   */
  @Override
  @NotNull
  Language getBaseLanguage();

  /**
   * e.g. HTML for JSP files
   * @return not instanceof {@link com.intellij.lang.DependentLanguage}
   */
  @NotNull
  Language getTemplateDataLanguage();

  /**
   * Should return content type that is used to override file content type for template data language.
   * It is required for template language injections to override non-base language content type properly
   *
   * @param language for which we want to create a file
   * @return content element type for non-base language, null otherwise
   */
  @ApiStatus.Experimental
  default @Nullable IElementType getContentElementType(@NotNull Language language) {
    return null;
  }
}
