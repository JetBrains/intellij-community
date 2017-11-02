/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProviderEP;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleFacadeImpl extends CodeStyleFacade {
  private final Project myProject;

  public CodeStyleFacadeImpl() {
    this(null);
  }

  public CodeStyleFacadeImpl(final Project project) {
    myProject = project;
  }

  @Override
  public int getIndentSize(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).getIndentSize(fileType);
  }

  @Override
  @Nullable
  @Deprecated
  public String getLineIndent(@NotNull final Document document, int offset) {
    if (myProject == null) return null;
    PsiDocumentManager.getInstance(myProject).commitDocument(document);
    return CodeStyleManager.getInstance(myProject).getLineIndent(document, offset);
  }

  @Override
  public String getLineIndent(@NotNull Editor editor, @Nullable Language language, int offset, boolean allowDocCommit) {
    if (myProject == null) return null;
    LineIndentProvider lineIndentProvider = LineIndentProviderEP.findLineIndentProvider(language);
    String indent = lineIndentProvider != null ? lineIndentProvider.getLineIndent(myProject, editor, language, offset) : null;
    if (indent == LineIndentProvider.DO_NOT_ADJUST) {
      return allowDocCommit ? null : indent;
    }
    //noinspection deprecation
    return indent != null ? indent : (allowDocCommit ? getLineIndent(editor.getDocument(), offset) : null);
  }

  @Override
  public String getLineSeparator() {
    return CodeStyleSettingsManager.getSettings(myProject).getLineSeparator();
  }

  @Override
  public boolean projectUsesOwnSettings() {
    return myProject != null && CodeStyleSettingsManager.getInstance(myProject).USE_PER_PROJECT_SETTINGS;
  }

  @Override
  public boolean isUnsuitableCodeStyleConfigurable(final Configurable c) {
    return false;
  }

  @Override
  public int getRightMargin(Language language) {
    return CodeStyleSettingsManager.getSettings(myProject).getRightMargin(language);
  }

  @Override
  @Deprecated
  public boolean isWrapWhenTypingReachesRightMargin() {
    return CodeStyleSettingsManager.getSettings(myProject).WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
  }

  @Override
  public boolean isWrapOnTyping(@Nullable Language language) {
    return CodeStyleSettingsManager.getSettings(myProject).isWrapOnTyping(language);
  }

  @Override
  public int getTabSize(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).getTabSize(fileType);
  }

  @Override
  public boolean isSmartTabs(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).isSmartTabs(fileType);
  }

  @Override
  public boolean useTabCharacter(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).useTabCharacter(fileType);
  }
}