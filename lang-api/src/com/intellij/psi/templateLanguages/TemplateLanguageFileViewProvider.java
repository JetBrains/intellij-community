/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.lang.Language;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface TemplateLanguageFileViewProvider extends FileViewProvider {

  /**
   * @return instanceof {@link com.intellij.psi.templateLanguages.TemplateLanguage}
   */
  @NotNull
  Language getBaseLanguage();

  /**
   * @return not instanceof {@link com.intellij.lang.DependentLanguage}
   */
  @NotNull
  Language getTemplateDataLanguage();
}
