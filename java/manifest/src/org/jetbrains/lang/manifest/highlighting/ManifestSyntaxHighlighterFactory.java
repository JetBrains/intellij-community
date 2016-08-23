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
package org.jetbrains.lang.manifest.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.parser.ManifestLexer;
import org.jetbrains.lang.manifest.psi.ManifestTokenType;

import java.util.HashMap;
import java.util.Map;

public class ManifestSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  public static final SyntaxHighlighter HIGHLIGHTER = new SyntaxHighlighterBase() {
    private final Map<IElementType, TextAttributesKey> myAttributes;
    {
      myAttributes = new HashMap<>();
      myAttributes.put(ManifestTokenType.HEADER_NAME, ManifestColorsAndFonts.HEADER_NAME_KEY);
      myAttributes.put(ManifestTokenType.COLON, ManifestColorsAndFonts.HEADER_ASSIGNMENT_KEY);
      myAttributes.put(ManifestTokenType.HEADER_VALUE_PART, ManifestColorsAndFonts.HEADER_VALUE_KEY);
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
      return new ManifestLexer();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
      return pack(myAttributes.get(tokenType));
    }
  };

  @NotNull
  @Override
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
    return HIGHLIGHTER;
  }
}
