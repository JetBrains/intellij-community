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
package com.intellij.lang.java;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaSyntaxHighlighterFactory extends SyntaxHighlighterFactory implements SyntaxHighlighterProvider {
  /**
   * SyntaxHighlighterFactory implementation (for Java source files).
   */
  @Override
  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile file) {
    return new JavaFileHighlighter(project == null ? LanguageLevel.HIGHEST : JavaPsiImplementationHelper.getInstance(project).getEffectiveLanguageLevel(file));
  }

  /**
   * SyntaxHighlighterProvider implementation (for .class files).
   */
  @Nullable
  @Override
  public SyntaxHighlighter create(@NotNull FileType fileType, @Nullable Project project, @Nullable VirtualFile file) {
    if (project != null && file != null) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

      if (fileType == JavaClassFileType.INSTANCE && psiFile != null) {
        Language language = psiFile.getLanguage();
        if (language != JavaLanguage.INSTANCE) {
          return SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file);
        }
      }

      if (psiFile instanceof ClsFileImpl) {
        LanguageLevel sourceLevel = ((ClsFileImpl)psiFile).getLanguageLevel();
        return new JavaFileHighlighter(sourceLevel);
      }
    }

    return new JavaFileHighlighter();
  }
}
