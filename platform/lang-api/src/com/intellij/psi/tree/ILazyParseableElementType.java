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
package com.intellij.psi.tree;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * A token type which represents a fragment of text (possibly in a different language)
 * which is not parsed during the current lexer or parser pass and can be parsed later when
 * its contents is requested.
 *
 * @author max
 */

public class ILazyParseableElementType extends IElementType {
  public ILazyParseableElementType(@NonNls String debugName) {
    this(debugName, null);
  }

  public ILazyParseableElementType(@NonNls String debugName, Language language) {
    super(debugName, language);
  }

  public ILazyParseableElementType(@NonNls String debugName, Language language, boolean register) {
    super(debugName, language, register);
  }

  /**
   * Parses the contents of the specified chameleon node and returns the AST tree
   * representing the parsed contents.
   *
   * @param chameleon the node to parse.
   * @return the parsed contents of the node.
   */
  public ASTNode parseContents(ASTNode chameleon) {
    final PsiElement parentElement = chameleon.getTreeParent().getPsi();
    assert parentElement != null : "Bad chameleon: " + chameleon;
    final Project project = parentElement.getProject();
    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();

    final PsiBuilder builder = factory.createBuilder(project, chameleon);

    final PsiParser parser = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage()).createParser(project);
    return parser.parse(this, builder).getFirstChildNode();
  }

  @Nullable
  public ASTNode createNode(CharSequence text) {
    return null;
  }
}
