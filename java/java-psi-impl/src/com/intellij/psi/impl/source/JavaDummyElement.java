/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.java.syntax.element.lazyParser.IncompleteFragmentParsingException;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.tree.JavaElementType.DUMMY_ELEMENT;

/**
 * Dummy file element for using together with DummyHolder.
 * See {@link com.intellij.psi.impl.PsiJavaParserFacadeImpl} for details.
 */
public class JavaDummyElement extends FileElement {
  private final JavaParserUtil.ParserWrapper myParser;
  private final LanguageLevel myLanguageLevel;
  private final boolean myConsumeAll;
  private Throwable myParserError;

  public JavaDummyElement(@Nullable CharSequence text,
                          JavaParserUtil.@NotNull ParserWrapper parser,
                          @NotNull LanguageLevel level) {
    this(text, parser, level, false);
  }

  public JavaDummyElement(@Nullable CharSequence text,
                          JavaParserUtil.@NotNull ParserWrapper parser,
                          @NotNull LanguageLevel level,
                          boolean consumeAll) {
    super(DUMMY_ELEMENT, text);
    this.myParser = parser;
    this.myLanguageLevel = level;
    this.myConsumeAll = consumeAll;
  }

  public @NotNull JavaParserUtil.ParserWrapper getParser() {
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