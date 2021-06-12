// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.tree;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A token type which represents a fragment of text (possibly in a different language)
 * which is not parsed during the current lexer or parser pass and can be parsed later when
 * its contents is requested.
 *
 * @author max
 */

public class ILazyParseableElementType extends IElementType implements ILazyParseableElementTypeBase {

  public static final Key<Language> LANGUAGE_KEY = Key.create("LANGUAGE_KEY");

  public ILazyParseableElementType(@NotNull @NonNls final String debugName) {
    this(debugName, null);
  }

  public ILazyParseableElementType(@NotNull @NonNls final String debugName, @Nullable final Language language) {
    super(debugName, language);
  }

  /**
   * Allows to construct element types without registering them, as in {@link IElementType#IElementType(String, Language, boolean)}.
   */
  public ILazyParseableElementType(@NotNull @NonNls final String debugName, @Nullable final Language language, final boolean register) {
    super(debugName, language, register);
  }

  /**
   * Parses the contents of the specified chameleon node and returns PsiBuilder.
   * In future this method should deprecate all other parsing methods: parseContents(), doParseContents(), etc.
   * It provides more flexible and CPU/memory efficient access to parser algorithms for all needs:
   * editing, indexing and analysis.
   * <p/>
   *
   * The parseContent() implementation in terms of parseLight() is just the following:
   * {@code}parseLight().getTreeBuilt().getFirstChildNode(){@code}
   *
   * @param chameleon the node to parse.
   * @return the parsed contents of the node in the form PsiBuilder.
   *
   * @deprecated Not needed anymore, override {@link ILazyParseableElementType#parseContents(ASTNode)}
   *             or implement {@link ILightLazyParseableElementType} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public PsiBuilder parseLight(ASTNode chameleon) {
    throw new UnsupportedOperationException(String.valueOf(chameleon));
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
    ASTNode node = parser.parse(this, builder);
    return node.getFirstChildNode();
  }

  protected Language getLanguageForParser(PsiElement psi) {
    return getLanguage();
  }

  @Nullable
  public ASTNode createNode(CharSequence text) {
    return null;
  }

  // Please, add no more public methods here. Add them to `ILazyParseableElementTypeBase` instead.
  // If you are not sure about the API stability, use `ApiStatus.Experimental` annotation
}
