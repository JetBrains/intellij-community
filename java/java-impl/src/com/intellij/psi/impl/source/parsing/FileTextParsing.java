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
package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.psi.PsiManager;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class FileTextParsing extends Parsing {
  public FileTextParsing(JavaParsingContext context) {
    super(context);
  }

  public static TreeElement parseFileText(PsiManager manager, Lexer lexer, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    return parseFileText(manager, lexer, buffer, startOffset, endOffset, false, table);
  }

  public static TreeElement parseFileText(PsiManager manager, @NotNull Lexer lexer, CharSequence buffer, int startOffset, int endOffset, boolean skipHeader, CharTable table) {
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    filterLexer.start(buffer, startOffset, endOffset);
    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, table).getTreeElement();
    JavaParsingContext context = new JavaParsingContext(dummyRoot.getCharTable(),
                                                        LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());

    if (!skipHeader){
      TreeElement packageStatement = (TreeElement)context.getFileTextParsing().parsePackageStatement(filterLexer);
      if (packageStatement != null) {
        dummyRoot.rawAddChildren(packageStatement);
      }

      final TreeElement importList = (TreeElement)parseImportList(filterLexer, context);
      dummyRoot.rawAddChildren(importList);
    }

    CompositeElement invalidElementsGroup = null;
    while (true) {
      if (filterLexer.getTokenType() == null) break;

      if (filterLexer.getTokenType() == JavaTokenType.SEMICOLON){
        dummyRoot.rawAddChildren(ParseUtil.createTokenElement(filterLexer, dummyRoot.getCharTable()));
        filterLexer.advance();
        invalidElementsGroup = null;
        continue;
      }

      TreeElement first = context.getDeclarationParsing().parseDeclaration(filterLexer, DeclarationParsing.Context.FILE_CONTEXT);
      if (first != null) {
        dummyRoot.rawAddChildren(first);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("expected.class.or.interface"));
        dummyRoot.rawAddChildren(invalidElementsGroup);
      }
      invalidElementsGroup.rawAddChildren(ParseUtil.createTokenElement(filterLexer, context.getCharTable()));
      filterLexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, lexer, startOffset, endOffset, -1, WhiteSpaceAndCommentsProcessor.INSTANCE, context);
    return dummyRoot.getFirstChildNode();
  }

  public static ASTNode parseImportList(Lexer lexer, final JavaParsingContext context) {
    CompositeElement importList = ASTFactory.composite(JavaElementType.IMPORT_LIST);

    if (lexer.getTokenType() == JavaTokenType.IMPORT_KEYWORD) {
      context.getImportsTextParsing().parseImportStatements(lexer, importList);
    }

    return importList;
  }

  @Nullable
  private ASTNode parsePackageStatement(Lexer lexer) {
    final LexerPosition startPos = lexer.getCurrentPosition();
    CompositeElement packageStatement = ASTFactory.composite(JavaElementType.PACKAGE_STATEMENT);

    if (lexer.getTokenType() != JavaTokenType.PACKAGE_KEYWORD) {
      FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
      CompositeElement list = myContext.getDeclarationParsing().parseAnnotationList(filterLexer, ASTFactory.composite(JavaElementType.MODIFIER_LIST));
      packageStatement.rawAddChildren(list);
      if (lexer.getTokenType() != JavaTokenType.PACKAGE_KEYWORD) {
        lexer.restore(startPos);
        return null;
      }
    }

    packageStatement.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    TreeElement packageReference = parseJavaCodeReference(lexer, true, false, false, false);
    if (packageReference == null) {
      lexer.restore(startPos);
      return null;
    }
    packageStatement.rawAddChildren(packageReference);
    if (lexer.getTokenType() == JavaTokenType.SEMICOLON) {
      packageStatement.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    } else {
      packageStatement.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
    }
    return packageStatement;
  }
}



