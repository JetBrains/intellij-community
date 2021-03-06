// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.ecmascript;

import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.intellij.lang.regexp.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * @author Konstantin.Ulitin
 */
public class EcmaScriptRegexpParserDefinition extends RegExpParserDefinition {

  public static final IFileElementType JS_REGEXP_FILE = new IFileElementType("JS_REGEXP_FILE", EcmaScriptRegexpLanguage.INSTANCE);
  private final EnumSet<RegExpCapability> CAPABILITIES = EnumSet.of(RegExpCapability.OCTAL_NO_LEADING_ZERO,
                                                                    RegExpCapability.DANGLING_METACHARACTERS,
                                                                    RegExpCapability.ALLOW_EMPTY_CHARACTER_CLASS,
                                                                    RegExpCapability.PROPERTY_VALUES,
                                                                    RegExpCapability.MAX_OCTAL_377);

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new RegExpLexer(CAPABILITIES) {
      @Nullable
      @Override
      public IElementType getTokenType() {
        final IElementType baseType = super.getTokenType();
        if (baseType == StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN ||
            baseType == StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN) {
          return RegExpTT.REDUNDANT_ESCAPE;
        }
        else if (baseType == RegExpTT.BAD_OCT_VALUE) {
          return RegExpTT.OCT_CHAR;
        }
        return baseType;
      }
    };
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new RegExpParser(CAPABILITIES);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return JS_REGEXP_FILE;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new RegExpFile(viewProvider, EcmaScriptRegexpLanguage.INSTANCE);
  }
}
