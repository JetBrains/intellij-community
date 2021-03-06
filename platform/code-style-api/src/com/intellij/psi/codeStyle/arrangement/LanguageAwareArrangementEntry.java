// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

/**
 * Provides a language that will be used to fetch arrangement and code style settings instead of the file's language
 * when arranging this entry and its' children.<p>
 * 
 * Implement this, for example, if entries are expected to be present in HTML 'script' or 'style' tags 
 * and should be arranged according to {@link #getLanguage()} settings.
 */
public interface LanguageAwareArrangementEntry extends ArrangementEntry {
  
  @NotNull
  Language getLanguage();
}
