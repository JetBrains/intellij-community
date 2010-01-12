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
package com.intellij.lang.java;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class JavaLanguage extends Language {
  public JavaLanguage() {
    super("JAVA", "text/java", "application/x-java", "text/x-java");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(this, new SyntaxHighlighterFactory() {
      @NotNull
      public SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
        return new JavaFileHighlighter(
          virtualFile != null ? LanguageLevelUtil.getLanguageLevelForFile(virtualFile) : LanguageLevel.HIGHEST);
      }
    });
  }

  public String getDisplayName() {
    return "Java";
  }

  public boolean isCaseSensitive() {
    return true;
  }
}
