// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.java.syntax.element.lazyParser.IncompleteFragmentParsingException;
import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dummy file element for using together with DummyHolder.
 * See {@link com.intellij.psi.impl.PsiJavaParserFacadeImpl} for details.
 */
public class BasicJavaDummyElement extends FileElement {
  private final BasicJavaParserUtil.ParserWrapper myParser;
  private final LanguageLevel myLanguageLevel;
  private final boolean myConsumeAll;
  private Throwable myParserError;

  public BasicJavaDummyElement(@Nullable CharSequence text,
                               @NotNull BasicJavaParserUtil.ParserWrapper parser,
                               @NotNull LanguageLevel level,
                               @NotNull AbstractBasicJavaElementTypeFactory javaElementTypeFactory) {
    this(text, parser, level, javaElementTypeFactory, false);
  }

  public BasicJavaDummyElement(@Nullable CharSequence text,
                               @NotNull BasicJavaParserUtil.ParserWrapper parser,
                               @NotNull LanguageLevel level,
                               @NotNull AbstractBasicJavaElementTypeFactory javaElementTypeFactory,
                               boolean consumeAll) {
    super(javaElementTypeFactory.getContainer().DUMMY_ELEMENT, text);
    myParser = parser;
    myLanguageLevel = level;
    myConsumeAll = consumeAll;
  }

  public @NotNull BasicJavaParserUtil.ParserWrapper getParser() {
    return myParser;
  }

  public boolean consumeAll() {
    return myConsumeAll;
  }

  public @NotNull LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public TreeElement getFirstChildNode() {
    try {
      return super.getFirstChildNode();
    }
    catch (IncompleteFragmentParsingException e) {
      myParserError = e;
      return null;  // masquerade parser errors
    }
  }

  @Override
  public TreeElement getLastChildNode() {
    try {
      return super.getLastChildNode();
    }
    catch (IncompleteFragmentParsingException e) {
      myParserError = e;
      return null;  // masquerade parser errors
    }
  }

  public @Nullable Throwable getParserError() {
    return myParserError;
  }
}