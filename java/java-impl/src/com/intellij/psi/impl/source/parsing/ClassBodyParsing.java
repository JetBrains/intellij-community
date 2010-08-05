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
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;

/**
 *
 */
public class ClassBodyParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ClassBodyParsing");
  public static final int CLASS = 0;
  public static final int ANNOTATION = 1;
  public static final int ENUM = 2;

  public ClassBodyParsing(JavaParsingContext context) {
    super(context);
  }


  private void parseEnumConstants(Lexer lexer, CompositeElement dummyRoot) {
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() == JavaTokenType.SEMICOLON) {
        dummyRoot.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return;
      }

      if (lexer.getTokenType() == JavaTokenType.PRIVATE_KEYWORD || lexer.getTokenType() == JavaTokenType.PROTECTED_KEYWORD) {
        dummyRoot.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
        return;
      }


      final TreeElement enumConstant = myContext.getDeclarationParsing().parseEnumConstant(lexer);
      if (enumConstant != null) {
        dummyRoot.rawAddChildren(enumConstant);
      }
      else {
        dummyRoot.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
      }

      if (lexer.getTokenType() == JavaTokenType.COMMA) {
        dummyRoot.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
      else if (lexer.getTokenType() != null && lexer.getTokenType() != JavaTokenType.SEMICOLON) {
        dummyRoot.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.comma.or.semicolon")));
        return;
      }
    }
  }

  private void parseClassBodyDeclarations(int context, Lexer filterLexer, CompositeElement dummyRoot) {
    CompositeElement invalidElementsGroup = null;
    final DeclarationParsing.Context declarationParsingContext = calcDeclarationContext(context);

    while(true){
      IElementType tokenType = filterLexer.getTokenType();
      if (tokenType == null || tokenType == JavaTokenType.RBRACE) break;

      if (tokenType == JavaTokenType.SEMICOLON){
        dummyRoot.rawAddChildren(ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
        filterLexer.advance();
        invalidElementsGroup = null;
        continue;
      }

      TreeElement declaration = myContext.getDeclarationParsing().parseDeclaration(filterLexer,
                                                                                   declarationParsingContext);
      if (declaration != null){
        dummyRoot.rawAddChildren(declaration);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
        dummyRoot.rawAddChildren(invalidElementsGroup);
      }

      // adding a reference, not simple tokens allows "Browse .." to work well
      CompositeElement ref = parseJavaCodeReference(filterLexer, true, true, false, false);
      if (ref != null){
        invalidElementsGroup.rawAddChildren(ref);
        continue;
      }

      invalidElementsGroup.rawAddChildren(ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
      filterLexer.advance();
    }
  }

  private static DeclarationParsing.Context calcDeclarationContext(int context) {
    DeclarationParsing.Context declarationParsingContext = DeclarationParsing.Context.CLASS_CONTEXT;
    switch(context) {
      default:
        LOG.assertTrue(false);
        break;
      case CLASS:
      case ENUM:
        declarationParsingContext = DeclarationParsing.Context.CLASS_CONTEXT;
        break;
      case ANNOTATION:
        declarationParsingContext = DeclarationParsing.Context.ANNOTATION_INTERFACE_CONTEXT;
        break;
    }
    return declarationParsingContext;
  }

  public void parseClassBody(CompositeElement root, Lexer lexer, int context) {
    if (context == ENUM) {
      parseEnumConstants (lexer, root);
    }
    parseClassBodyDeclarations(context, lexer, root);
  }
}


