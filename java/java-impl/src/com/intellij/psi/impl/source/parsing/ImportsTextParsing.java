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
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class ImportsTextParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ImportsTextParsing");
  private static final TokenSet IMPORT_LIST_STOPPER_BIT_SET = TokenSet.create(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD,
                                                                              JavaTokenType.ENUM_KEYWORD, JavaTokenType.AT);

  public ImportsTextParsing(JavaParsingContext context) {
    super(context);
  }

  public void parseImportStatements(final Lexer filterLexer, final CompositeElement parentNode) {
    CompositeElement invalidElementsGroup = null;
    while(true){
      IElementType tt = filterLexer.getTokenType();
      if (tt == null || IMPORT_LIST_STOPPER_BIT_SET.contains(tt) || ElementType.MODIFIER_BIT_SET.contains(tt)) {
        break;
      }

      TreeElement element = (TreeElement)parseImportStatement(filterLexer);
      if (element != null){
        parentNode.rawAddChildren(element);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
        parentNode.rawAddChildren(invalidElementsGroup);
      }

      invalidElementsGroup.rawAddChildren(ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
      filterLexer.advance();
    }
  }

  @Nullable
  private ASTNode parseImportStatement(Lexer lexer) {
    if (lexer.getTokenType() != JavaTokenType.IMPORT_KEYWORD) return null;

    final TreeElement importToken = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();
    final CompositeElement statement;
    final boolean isStatic;
    if (lexer.getTokenType() != JavaTokenType.STATIC_KEYWORD) {
      statement = ASTFactory.composite(JavaElementType.IMPORT_STATEMENT);
      statement.rawAddChildren(importToken);
      isStatic = false;
    }
    else {
      statement = ASTFactory.composite(JavaElementType.IMPORT_STATIC_STATEMENT);
      statement.rawAddChildren(importToken);
      statement.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      isStatic = true;
    }

    if (lexer.getTokenType() != JavaTokenType.IDENTIFIER){
      statement.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
      return statement;
    }

    CompositeElement refElement = parseJavaCodeReference(lexer, true, false, false, false);
    final TreeElement refParameterList = refElement.getLastChildNode();
    if (refParameterList.getTreePrev().getElementType() == TokenType.ERROR_ELEMENT){
      final ASTNode qualifier = refElement.findChildByRole(ChildRole.QUALIFIER);
      LOG.assertTrue(qualifier != null);
      refParameterList.getTreePrev().rawRemove();
      refParameterList.rawRemove();
      statement.rawAddChildren((TreeElement)qualifier);
      if (lexer.getTokenType() == JavaTokenType.ASTERISK){
        statement.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
      else{
        statement.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("import.statement.identifier.or.asterisk.expected.")));
        return statement;
      }
    }
    else{
      if (isStatic) {
        // convert JAVA_CODE_REFERENCE into IMPORT_STATIC_REFERENCE
        refElement = convertToImportStaticReference(refElement);
      }
      statement.rawAddChildren(refElement);
    }

    if (lexer.getTokenType() == JavaTokenType.SEMICOLON){
      statement.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      statement.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
    }

    return statement;
  }

  public static CompositeElement convertToImportStaticReference(CompositeElement refElement) {
    final CompositeElement importStaticReference = ASTFactory.composite(JavaElementType.IMPORT_STATIC_REFERENCE);
    final CompositeElement referenceParameterList = (CompositeElement)refElement.findChildByRole(ChildRole.REFERENCE_PARAMETER_LIST);
    importStaticReference.rawAddChildren(refElement.getFirstChildNode());
    if (referenceParameterList != null) {
      if (referenceParameterList.getFirstChildNode() == null) {
        referenceParameterList.rawRemove();
      }
      else {
        final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
        referenceParameterList.rawReplaceWithList(errorElement);
        errorElement.rawAddChildren(referenceParameterList);
      }
    }
    refElement = importStaticReference;
    return refElement;
  }
}
