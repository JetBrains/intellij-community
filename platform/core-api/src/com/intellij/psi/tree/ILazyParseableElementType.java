// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.ParsingDiagnostics;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A token type which represents a fragment of text (possibly in a different language)
 * which is not parsed during the current lexer or parser pass and can be parsed later when
 * its contents are requested.
 *
 * @author max
 */

public class ILazyParseableElementType extends IElementType implements ILazyParseableElementTypeBase {

  public static final Key<Language> LANGUAGE_KEY = Key.create("LANGUAGE_KEY");

  public ILazyParseableElementType(final @NotNull @NonNls String debugName) {
    this(debugName, null);
  }

  public ILazyParseableElementType(final @NotNull @NonNls String debugName, final @Nullable Language language) {
    super(debugName, language);
  }

  /**
   * Allows constructing element types without registering them, as in {@link IElementType#IElementType(String, Language, boolean)}.
   */
  public ILazyParseableElementType(final @NotNull @NonNls String debugName, final @Nullable Language language, final boolean register) {
    super(debugName, language, register);
  }

  /**
   * Parses the contents of the specified chameleon node and returns the AST tree
   * representing the parsed contents.
   *
   * @param chameleon the node to parse.
   * @return the parsed contents of the node.
   */
  @Override
  public ASTNode parseContents(@NotNull ASTNode chameleon) {
    PsiElement parentElement = chameleon.getTreeParent().getPsi();
    assert parentElement != null : "parent psi is null: " + chameleon;
    return doParseContents(chameleon, parentElement);
  }

  protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi) {
    Project project = psi.getProject();
    Language languageForParser = getLanguageForParser(psi);
    PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, null, languageForParser, chameleon.getChars());
    PsiParser parser = LanguageParserDefinitions.INSTANCE.forLanguage(languageForParser).createParser(project);
    long startTime = System.nanoTime();
    ASTNode node = parser.parse(this, builder);
    ParsingDiagnostics.registerParse(builder, languageForParser, System.nanoTime() - startTime);
    return node.getFirstChildNode();
  }

  protected @NotNull Language getLanguageForParser(@NotNull PsiElement psi) {
    return getLanguage();
  }

  public ASTNode createNode(CharSequence text) {
    return null;
  }

  // Please, add no more public methods here. Add them to `ILazyParseableElementTypeBase` instead.
  // If you are not sure about the API stability, use `ApiStatus.Experimental` annotation
}
