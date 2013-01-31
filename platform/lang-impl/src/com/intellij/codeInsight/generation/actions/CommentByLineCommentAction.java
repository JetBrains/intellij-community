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

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.CommentByLineCommentHandler;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.lang.LanguageCommenters;
import org.jetbrains.annotations.NotNull;

public class CommentByLineCommentAction extends BaseCodeInsightAction implements DumbAware {
  public CommentByLineCommentAction() {
    setEnabledInModalContext(true);
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new CommentByLineCommentHandler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull final PsiFile file) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof AbstractFileType) {
      return ((AbstractFileType)fileType).getCommenter() != null;
    }
    return LanguageCommenters.INSTANCE.forLanguage(file.getLanguage()) != null || LanguageCommenters.INSTANCE.forLanguage(file.getViewProvider().getBaseLanguage()) != null;
  }
}
