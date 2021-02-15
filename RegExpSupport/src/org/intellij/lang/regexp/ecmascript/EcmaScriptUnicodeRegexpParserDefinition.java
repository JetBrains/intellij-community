// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.ecmascript;

import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import org.intellij.lang.regexp.*;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * @author Bas Leijdekkers
 */
public class EcmaScriptUnicodeRegexpParserDefinition extends RegExpParserDefinition {

  public static final IFileElementType JS_REGEXP_FILE = new IFileElementType("JS_UNICODE_REGEXP_FILE", EcmaScriptUnicodeRegexpLanguage.INSTANCE);
  private final EnumSet<RegExpCapability> CAPABILITIES = EnumSet.of(RegExpCapability.OCTAL_NO_LEADING_ZERO,
                                                                    RegExpCapability.ALLOW_EMPTY_CHARACTER_CLASS,
                                                                    RegExpCapability.NO_DANGLING_METACHARACTERS,
                                                                    RegExpCapability.PROPERTY_VALUES,
                                                                    RegExpCapability.EXTENDED_UNICODE_CHARACTER);

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new RegExpLexer(CAPABILITIES);
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
    return new RegExpFile(viewProvider, EcmaScriptUnicodeRegexpLanguage.INSTANCE);
  }
}
