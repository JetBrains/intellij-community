// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.enter;

import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnterInCommentUtil {
  @Nullable
  public static CodeDocumentationAwareCommenter getDocumentationAwareCommenter(@NotNull DataContext dataContext) {
    Language language = EnterHandler.getLanguage(dataContext);
    if (language == null) return null;

    Commenter languageCommenter = LanguageCommenters.INSTANCE.forLanguage(language);
    return languageCommenter instanceof CodeDocumentationAwareCommenter
           ? (CodeDocumentationAwareCommenter)languageCommenter : null;
  }
}
