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
import com.intellij.lexer.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.jsp.AbstractJspJavaLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StatementParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.StatementParsing");

  public interface StatementParser {
    @Nullable
    CompositeElement parseStatement(Lexer lexer);
  }

  public interface StatementParsingHandler {
    @Nullable StatementParser getParserForToken(IElementType token);
  }

  private List<StatementParsingHandler> myCustomHandlers = null;

  public StatementParsing(JavaParsingContext context) {
    super(context);
  }

  public void registerCustomStatementParser(StatementParsingHandler handler) {
    if (myCustomHandlers == null) {
      myCustomHandlers = new ArrayList<StatementParsingHandler>();
    }

    myCustomHandlers.add(handler);
  }

  public CompositeElement parseCodeBlockText(PsiManager manager, CharSequence buffer) {
    return parseCodeBlockText(manager, null, buffer, 0, buffer.length(), -1);
  }

  public CompositeElement parseCodeBlockText(PsiManager manager,
                                             Lexer lexer,
                                             CharSequence buffer,
                                             int startOffset,
                                             int endOffset,
                                             int state) {
    if (lexer == null){
      lexer = new JavaLexer(myContext.getLanguageLevel());
    }
    final FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state < 0) filterLexer.start(buffer, startOffset, endOffset);
    else filterLexer.start(buffer, startOffset, endOffset, state);

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();
    CompositeElement block = ASTFactory.lazy(JavaElementType.CODE_BLOCK, null);
    dummyRoot.rawAddChildren(block);
    parseCodeBlockDeep(block, filterLexer, true);
    if (block.getFirstChildNode() == null) return null;

    ParseUtil.insertMissingTokens(block, lexer, startOffset, endOffset, state, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return block;
  }

  public TreeElement parseStatements(PsiManager manager,
                                     Lexer lexer,
                                     CharSequence buffer,
                                     int startOffset,
                                     int endOffset,
                                     int state) {
    if (lexer == null){
      lexer = new JavaLexer(myContext.getLanguageLevel());
    }
    final FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state < 0) filterLexer.start(buffer, startOffset, endOffset);
    else filterLexer.start(buffer, startOffset, endOffset, state);

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();
    parseStatements(dummyRoot, filterLexer, RBRACE_IS_ERROR);

    ParseUtil.insertMissingTokens(dummyRoot,
                                  lexer,
                                  startOffset,
                                  endOffset,
                                  state,
                                  WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return dummyRoot.getFirstChildNode();
  }

  @Nullable
  public TreeElement parseCodeBlock(Lexer lexer, boolean deep) {
    if (lexer.getTokenType() != JavaTokenType.LBRACE) return null;
    Lexer badLexer = lexer instanceof StoppableLexerAdapter ? ((StoppableLexerAdapter)lexer).getDelegate() : lexer;
    if (badLexer instanceof FilterLexer){
      final Lexer original = ((FilterLexer)badLexer).getOriginal();
      if (original instanceof AbstractJspJavaLexer){
        deep = true; // deep parsing of code blocks in JSP would lead to incorrect parsing on transforming
      }
    }

    if (!deep){
      int start = lexer.getTokenStart();
      lexer.advance();
      int braceCount = 1;
      int end;
      while(true){
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == null){
          end = lexer.getTokenStart();
          break;
        }
        if (tokenType == JavaTokenType.LBRACE){
          braceCount++;
        }
        else if (tokenType == JavaTokenType.RBRACE){
          braceCount--;
        }
        if (braceCount == 0){
          end = lexer.getTokenEnd();
          lexer.advance();
          break;
        }

        if (braceCount == 1 && (tokenType == JavaTokenType.SEMICOLON || tokenType == JavaTokenType.RBRACE)) {
          lexer.advance();

          final LexerPosition position = lexer.getCurrentPosition();
          List<IElementType> list = new SmartList<IElementType>();
          while (true) {
            final IElementType type = lexer.getTokenType();
            if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(type) || ElementType.MODIFIER_BIT_SET.contains(type) ||
                type == JavaTokenType.IDENTIFIER || type == JavaTokenType.LT || type == JavaTokenType.GT ||
                type == JavaTokenType.GTGT || type == JavaTokenType.GTGTGT || type == JavaTokenType.COMMA ||
                type == JavaTokenType.DOT || type == JavaTokenType.EXTENDS_KEYWORD || type == JavaTokenType.IMPLEMENTS_KEYWORD) {
              list.add(type);
              lexer.advance();
            } else {
              break;
            }
          }
          if (lexer.getTokenType() == JavaTokenType.LPARENTH && list.size() >= 2) {
            final IElementType last = list.get(list.size() - 1);
            final IElementType prevLast = list.get(list.size() - 2);
            if (last == JavaTokenType.IDENTIFIER && (prevLast == JavaTokenType.IDENTIFIER || ElementType.PRIMITIVE_TYPE_BIT_SET.contains(prevLast))) {
              lexer.restore(position);
              end = lexer.getTokenStart();
              break;
            }
          }
        }
        else {
          lexer.advance();
        }
      }
      final TreeElement chameleon = ASTFactory.lazy(JavaElementType.CODE_BLOCK, myContext.getCharTable().intern(lexer.getBufferSequence(), start, end));
      if (braceCount != 0){
        chameleon.putUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY, "");
      }
      return chameleon;
    }
    else{
      CompositeElement codeBlock = ASTFactory.lazy(JavaElementType.CODE_BLOCK, null);
      parseCodeBlockDeep(codeBlock, lexer, false);
      return codeBlock;
    }
  }

  private void parseCodeBlockDeep(CompositeElement elementToAdd,
                                  Lexer lexer,
                                  boolean parseToEndOfLexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.LBRACE, lexer.getTokenType());

    elementToAdd.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    parseStatements(elementToAdd, lexer, parseToEndOfLexer ? LAST_RBRACE_IS_END : RBRACE_IS_END);

    final TreeElement lastChild = elementToAdd.getLastChildNode();
    if (lastChild == null || lastChild.getElementType() != JavaTokenType.RBRACE){
      CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace"));
      elementToAdd.rawAddChildren(errorElement);
      elementToAdd.putUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY, "");
    }
  }

  private static final int RBRACE_IS_ERROR = 1;
  private static final int RBRACE_IS_END = 2;
  private static final int LAST_RBRACE_IS_END = 3;

  private void parseStatements(CompositeElement elementToAdd, Lexer lexer, int rbraceMode){
    while(lexer.getTokenType() != null){
      TreeElement statement = parseStatement(lexer);
      if (statement != null){
        elementToAdd.rawAddChildren(statement);
        continue;
      }

      IElementType tokenType = lexer.getTokenType();
      TreeElement tokenElement = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();

      if (tokenType == JavaTokenType.RBRACE){
        Label:
        if (rbraceMode == RBRACE_IS_ERROR) {
        }
        else if (rbraceMode == RBRACE_IS_END) {
          elementToAdd.rawAddChildren(tokenElement);
          return;
        }
        else if (rbraceMode == LAST_RBRACE_IS_END) {
          if (lexer.getTokenType() == null) {
            elementToAdd.rawAddChildren(tokenElement);
            return;
          }
          else {
            break Label;
          }
        }
        else {
          LOG.assertTrue(false);
        }
      }

      String error;
      if (tokenType == JavaTokenType.ELSE_KEYWORD){
        error = JavaErrorMessages.message("else.without.if");
      }
      else if (tokenType == JavaTokenType.CATCH_KEYWORD){
        error = JavaErrorMessages.message("catch.without.try");
      }
      else if (tokenType == JavaTokenType.FINAL_KEYWORD){
        error = JavaErrorMessages.message("finally.without.try");
      }
      else{
        error = JavaErrorMessages.message("unexpected.token");
      }
      CompositeElement errorElement = Factory.createErrorElement(error);
      errorElement.rawAddChildren(tokenElement);
      elementToAdd.rawAddChildren(errorElement);
    }
  }

  @Nullable
  public TreeElement parseStatementText(CharSequence buffer) {
    Lexer lexer = new JavaLexer(myContext.getLanguageLevel());
    final FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    filterLexer.start(buffer);

    TreeElement statement = parseStatement(filterLexer);
    if (statement == null) return null;
    if (filterLexer.getTokenType() != null) return null;

    if(statement instanceof CompositeElement)
      ParseUtil.insertMissingTokens((CompositeElement)statement, lexer, 0, buffer.length(), -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return statement;
  }

  @Nullable
  public TreeElement parseStatement(Lexer lexer) {
    IElementType tokenType = lexer.getTokenType();
    if (myCustomHandlers != null) {
      for (StatementParsingHandler handler : myCustomHandlers) {
        final StatementParser parser = handler.getParserForToken(tokenType);
        if (parser != null) {
          return parser.parseStatement(lexer);
        }
      }
    }

    if (tokenType == JavaTokenType.IF_KEYWORD) {
      return parseIfStatement(lexer);
    }
    if (tokenType == JavaTokenType.WHILE_KEYWORD) {
      return parseWhileStatement(lexer);
    }
    if (tokenType == JavaTokenType.FOR_KEYWORD) {
      return parseForStatement(lexer);
    }
    if (tokenType == JavaTokenType.DO_KEYWORD) {
      return parseDoWhileStatement(lexer);
    }
    if (tokenType == JavaTokenType.SWITCH_KEYWORD) {
      return parseSwitchStatement(lexer);
    }
    if (tokenType == JavaTokenType.CASE_KEYWORD || tokenType == JavaTokenType.DEFAULT_KEYWORD) {
      return parseSwitchLabelStatement(lexer);
    }
    if (tokenType == JavaTokenType.BREAK_KEYWORD) {
      return parseBreakStatement(lexer);
    }
    if (tokenType == JavaTokenType.CONTINUE_KEYWORD) {
      return parseContinueStatement(lexer);
    }
    if (tokenType == JavaTokenType.RETURN_KEYWORD) {
      return parseReturnStatement(lexer);
    }
    if (tokenType == JavaTokenType.THROW_KEYWORD) {
      return parseThrowStatement(lexer);
    }
    if (tokenType == JavaTokenType.SYNCHRONIZED_KEYWORD) {
      return parseSynchronizedStatement(lexer);
    }
    if (tokenType == JavaTokenType.TRY_KEYWORD) {
      return parseTryStatement(lexer);
    }
    if (tokenType == JavaTokenType.ASSERT_KEYWORD) {
      return parseAssertStatement(lexer);
    }
    if (tokenType == JavaTokenType.LBRACE) {
      return parseBlockStatement(lexer);
    }
    if (tokenType instanceof ILazyParseableElementType) {
      TreeElement declaration = ASTFactory.lazy((ILazyParseableElementType)tokenType, myContext.tokenText(lexer));
      lexer.advance();
      return declaration;
    }
    if (tokenType == JavaTokenType.SEMICOLON) {
      CompositeElement element = ASTFactory.composite(JavaElementType.EMPTY_STATEMENT);
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      return element;
    }
    if (tokenType == JavaTokenType.IDENTIFIER || tokenType == JavaTokenType.AT) {
      final LexerPosition refPos = lexer.getCurrentPosition();
      parseAnnotationListTo(lexer, null); // @Ann ClassName.field++;
      skipQualifiedName(lexer);
      final IElementType suspectedLT = lexer.getTokenType();
      lexer.restore(refPos);
      LOG.assertTrue(lexer.getTokenType() == JavaTokenType.IDENTIFIER || lexer.getTokenType() == JavaTokenType.AT);
      if (suspectedLT == JavaTokenType.LT) {
        final TreeElement decl = myContext.getDeclarationParsing().parseDeclaration(lexer, DeclarationParsing.Context.CODE_BLOCK_CONTEXT);
        CompositeElement declStatement = ASTFactory.composite(JavaElementType.DECLARATION_STATEMENT);
        if (decl != null) {
          declStatement.rawAddChildren(decl);
        }
        else {
          final CompositeElement type = parseType(lexer, false, false);
          if (type != null) declStatement.rawAddChildren(type);
          final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier"));
          declStatement.rawAddChildren(errorElement);
        }
        return declStatement;
      }
    }

    final LexerPosition pos = lexer.getCurrentPosition();
    CompositeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    final LexerPosition pos1 = lexer.getCurrentPosition();
    if (expr != null) {
      int count = 1;
      CompositeElement element = null;
      while (lexer.getTokenType() == JavaTokenType.COMMA) {
        CompositeElement list = ASTFactory.composite(JavaElementType.EXPRESSION_LIST);
        element = ASTFactory.composite(JavaElementType.EXPRESSION_LIST_STATEMENT);
        element.rawAddChildren(list);
        list.rawAddChildren(expr);
        final LexerPosition commaPos = lexer.getCurrentPosition();
        TreeElement comma = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
        lexer.advance();
        CompositeElement expr1 = myContext.getExpressionParsing().parseExpression(lexer);
        if (expr1 == null) {
          lexer.restore(commaPos);
          break;
        }
        list.rawAddChildren(comma);
        list.rawAddChildren(expr1);
        count++;
      }
      if (count > 1) {
        processClosingSemicolon(element, lexer);
        return element;
      }
      if (expr.getElementType() != JavaElementType.REFERENCE_EXPRESSION) {
        element = ASTFactory.composite(JavaElementType.EXPRESSION_STATEMENT);
        element.rawAddChildren(expr);
        processClosingSemicolon(element, lexer);
        return element;
      }
      lexer.restore(pos);
    }

    TreeElement decl = myContext.getDeclarationParsing().parseDeclaration(lexer, DeclarationParsing.Context.CODE_BLOCK_CONTEXT);
    if (decl != null) {
      CompositeElement declStatement = ASTFactory.composite(JavaElementType.DECLARATION_STATEMENT);
      declStatement.rawAddChildren(decl);
      return declStatement;
    }

    if (lexer.getTokenType() == JavaTokenType.IDENTIFIER) {
      TreeElement identifier = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
      if (lexer.getTokenType() == JavaTokenType.COLON) {
        CompositeElement element = ASTFactory.composite(JavaElementType.LABELED_STATEMENT);
        element.rawAddChildren(identifier);
        element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        TreeElement statement = parseStatement(lexer);
        if (statement != null) {
          element.rawAddChildren(statement);
        }
        return element;
      }
      lexer.restore(pos);
    }

    if (expr != null) {
      lexer.restore(pos1);
      CompositeElement element = ASTFactory.composite(JavaElementType.EXPRESSION_STATEMENT);
      element.rawAddChildren(expr);
      processClosingSemicolon(element, lexer);
      return element;
    }

    return null;
  }

  private static void skipQualifiedName(Lexer lexer) {
    if (lexer.getTokenType() != JavaTokenType.IDENTIFIER) return;
    while(true){
      lexer.advance();
      if (lexer.getTokenType() != JavaTokenType.DOT) return;
      final LexerPosition position = lexer.getCurrentPosition();
      lexer.advance();
      if (lexer.getTokenType() != JavaTokenType.IDENTIFIER){
        lexer.restore(position);
        return;
      }
    }
  }

  private CompositeElement parseIfStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.IF_KEYWORD);
    CompositeElement element = ASTFactory.composite(JavaElementType.IF_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    TreeElement thenStatement = parseStatement(lexer);
    if (thenStatement == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.statement")));
      return element;
    }

    element.rawAddChildren(thenStatement);

    if (lexer.getTokenType() != JavaTokenType.ELSE_KEYWORD){
      return element;
    }

    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    TreeElement elseStatement = parseStatement(lexer);
    if (elseStatement == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.statement")));
      return element;
    }
    element.rawAddChildren(elseStatement);
    return element;
  }

  private CompositeElement parseWhileStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.WHILE_KEYWORD);
    CompositeElement element = ASTFactory.composite(JavaElementType.WHILE_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    TreeElement statement = parseStatement(lexer);
    if (statement == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.statement")));
      return element;
    }

    element.rawAddChildren(statement);
    return element;
  }

  private CompositeElement parseForStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.FOR_KEYWORD);
    final TreeElement forKeyword = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();

    if (lexer.getTokenType() != JavaTokenType.LPARENTH){
      CompositeElement errorForElement = ASTFactory.composite(JavaElementType.FOR_STATEMENT);
      errorForElement.rawAddChildren(forKeyword);
      errorForElement.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.lparen")));
      return errorForElement;
    }

    final TreeElement lparenth = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();

    final LexerPosition afterLParenth = lexer.getCurrentPosition();
    final TreeElement parameter = myContext.getDeclarationParsing().parseParameter(lexer, false);
    if (parameter == null || parameter.getElementType() != JavaElementType.PARAMETER || lexer.getTokenType() != JavaTokenType.COLON) {
      lexer.restore(afterLParenth);
      return parseForLoopFromInitialization(forKeyword, lparenth, lexer);
    }
    else {
      return parseForEachFromColon(forKeyword, lparenth, parameter, lexer);
    }
  }

  private CompositeElement parseForEachFromColon(TreeElement forKeyword,
                                                 TreeElement lparenth,
                                                 TreeElement parameter,
                                                 Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.COLON);
    final CompositeElement element = ASTFactory.composite(JavaElementType.FOREACH_STATEMENT);
    element.rawAddChildren(forKeyword);
    element.rawAddChildren(lparenth);
    element.rawAddChildren(parameter);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    final CompositeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr != null) {
      element.rawAddChildren(expr);
    }
    else {
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
    }
    if (lexer.getTokenType() == JavaTokenType.RPARENTH) {
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      final TreeElement body = parseStatement(lexer);
      if (body != null) {
        element.rawAddChildren(body);
      } else {
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.statement")));
      }
    }
    else {
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
    }
    return element;
  }

  private CompositeElement parseForLoopFromInitialization(final TreeElement forKeyword,
                                                          final TreeElement lparenth,
                                                          Lexer lexer) {// parsing normal for statement for statement
    CompositeElement element = ASTFactory.composite(JavaElementType.FOR_STATEMENT);
    element.rawAddChildren(forKeyword);
    element.rawAddChildren(lparenth);
    TreeElement init = parseStatement(lexer);
    if (init == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.statement")));
      if (lexer.getTokenType() != JavaTokenType.RPARENTH){
        return element;
      }
    }
    else{
      element.rawAddChildren(init);

      CompositeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
      if (expr != null){
        element.rawAddChildren(expr);
      }

      if (lexer.getTokenType() != JavaTokenType.SEMICOLON){
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
        if (lexer.getTokenType() != JavaTokenType.RPARENTH){
          return element;
        }
      }
      else{
        element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        parseExpressionOrExpressionList(lexer, element);
        if (lexer.getTokenType() != JavaTokenType.RPARENTH){
          element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
          return element;
        }
      }
    }

    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement statement = parseStatement(lexer);
    if (statement == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.statement")));
      return element;
    }

    element.rawAddChildren(statement);

    return element;
  }

  private void parseExpressionOrExpressionList(Lexer lexer, CompositeElement element) {
    CompositeElement expression = myContext.getExpressionParsing().parseExpression(lexer);
    if (expression != null) {
      final CompositeElement expressionStatement;
      if (lexer.getTokenType() != JavaTokenType.COMMA) {
        expressionStatement = ASTFactory.composite(JavaElementType.EXPRESSION_STATEMENT);
        expressionStatement.rawAddChildren(expression);
      } else {
        expressionStatement = ASTFactory.composite(JavaElementType.EXPRESSION_LIST_STATEMENT);
        final CompositeElement expressionList = ASTFactory.composite(JavaElementType.EXPRESSION_LIST);
        expressionList.rawAddChildren(expression);
        do {
          expressionList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          final CompositeElement nextExpression = myContext.getExpressionParsing().parseExpression(lexer);
          if (nextExpression != null) {
            expressionList.rawAddChildren(nextExpression);
          } else {
            expressionList.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
          }
        } while (lexer.getTokenType() == JavaTokenType.COMMA);
        expressionStatement.rawAddChildren(expressionList);
      }
      element.rawAddChildren(expressionStatement);
    }
  }

  private CompositeElement parseDoWhileStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.DO_KEYWORD);
    CompositeElement element = ASTFactory.composite(JavaElementType.DO_WHILE_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement statement = parseStatement(lexer);
    if (statement == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.statement")));
      return element;
    }

    element.rawAddChildren(statement);

    if (lexer.getTokenType() != JavaTokenType.WHILE_KEYWORD){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.while")));
      return element;
    }

    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    processClosingSemicolon(element, lexer);

    return element;
  }

  private CompositeElement parseSwitchStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.SWITCH_KEYWORD);
    CompositeElement element = ASTFactory.composite(JavaElementType.SWITCH_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    TreeElement codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    if (codeBlock == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace")));
      return element;
    }

    element.rawAddChildren(codeBlock);
    return element;
  }

  @Nullable
  private CompositeElement parseSwitchLabelStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.CASE_KEYWORD || lexer.getTokenType() == JavaTokenType.DEFAULT_KEYWORD);
    IElementType tokenType = lexer.getTokenType();
    final LexerPosition pos = lexer.getCurrentPosition();
    CompositeElement element = ASTFactory.composite(JavaElementType.SWITCH_LABEL_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    if (tokenType == JavaTokenType.CASE_KEYWORD){
      TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
      if (expr == null){
        lexer.restore(pos);
        return null;
      }
      element.rawAddChildren(expr);
    }
    if (lexer.getTokenType() == JavaTokenType.COLON){
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.colon")));
    }
    return element;
  }

  private CompositeElement parseBreakStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.BREAK_KEYWORD);
    CompositeElement element = ASTFactory.composite(JavaElementType.BREAK_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (lexer.getTokenType() == JavaTokenType.IDENTIFIER){
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseContinueStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.CONTINUE_KEYWORD);
    CompositeElement element = ASTFactory.composite(JavaElementType.CONTINUE_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (lexer.getTokenType() == JavaTokenType.IDENTIFIER){
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseReturnStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.RETURN_KEYWORD);
    CompositeElement element = ASTFactory.composite(JavaElementType.RETURN_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr != null){
      element.rawAddChildren(expr);
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseThrowStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.THROW_KEYWORD);

    CompositeElement element = ASTFactory.composite(JavaElementType.THROW_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr != null){
      element.rawAddChildren(expr);
    }
    else{
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
      return element;
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseSynchronizedStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.SYNCHRONIZED_KEYWORD);
    CompositeElement element = ASTFactory.composite(JavaElementType.SYNCHRONIZED_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    TreeElement codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    if (codeBlock == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace")));
      return element;
    }

    element.rawAddChildren(codeBlock);
    return element;
  }

  private CompositeElement parseTryStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.TRY_KEYWORD);

    //int pos = ParseUtil.savePosition(lexer);
    CompositeElement element = ASTFactory.composite(JavaElementType.TRY_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    if (codeBlock == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace")));
      return element;
    }
    element.rawAddChildren(codeBlock);

    if (lexer.getTokenType() != JavaTokenType.CATCH_KEYWORD && lexer.getTokenType() != JavaTokenType.FINALLY_KEYWORD){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.catch.or.finally")));
      return element;
    }

    while(lexer.getTokenType() == JavaTokenType.CATCH_KEYWORD){
      CompositeElement catchSection = parseCatchSection(lexer);
      element.rawAddChildren(catchSection);
      final TreeElement lastChild = catchSection.getLastChildNode();
      assert lastChild != null;
      if (lastChild.getElementType() == TokenType.ERROR_ELEMENT) break;
    }

    if (lexer.getTokenType() == JavaTokenType.FINALLY_KEYWORD){
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
      if (codeBlock == null){
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace")));
        return element;
      }
      element.rawAddChildren(codeBlock);
    }

    return element;
  }

  private CompositeElement parseCatchSection(Lexer lexer) {
    CompositeElement catchSection = ASTFactory.composite(JavaElementType.CATCH_SECTION);
    catchSection.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (lexer.getTokenType() != JavaTokenType.LPARENTH) {
      catchSection.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.lparen")));
      return catchSection;
    }

    catchSection.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement parm = myContext.getDeclarationParsing().parseParameter(lexer, false);
    if (parm == null) {
      catchSection.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.parameter")));
    } else {
      catchSection.rawAddChildren(parm);
    }

    if (lexer.getTokenType() != JavaTokenType.RPARENTH) {
      catchSection.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
      return catchSection;
    }

    catchSection.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    if (codeBlock == null) {
      catchSection.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace")));
      return catchSection;
    }
    catchSection.rawAddChildren(codeBlock);
    return catchSection;
  }

  private CompositeElement parseAssertStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.ASSERT_KEYWORD);

    CompositeElement element = ASTFactory.composite(JavaElementType.ASSERT_STATEMENT);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr == null){
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.boolean.expression")));
      return element;
    }

    element.rawAddChildren(expr);

    if (lexer.getTokenType() == JavaTokenType.COLON){
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      TreeElement expr2 = myContext.getExpressionParsing().parseExpression(lexer);
      if (expr2 == null){
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
        return element;
      }

      element.rawAddChildren(expr2);
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseBlockStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.LBRACE);
    CompositeElement element = ASTFactory.composite(JavaElementType.BLOCK_STATEMENT);
    TreeElement codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    element.rawAddChildren(codeBlock);
    return element;
  }

  private boolean processExpressionInParens(Lexer lexer, CompositeElement parent) {
    if (lexer.getTokenType() != JavaTokenType.LPARENTH){
      parent.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.lparen")));
      return false;
    }

    parent.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    final LexerPosition beforeExprPos = lexer.getCurrentPosition();
    CompositeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr == null || lexer.getTokenType() == JavaTokenType.SEMICOLON){
      if (expr != null){
        lexer.restore(beforeExprPos);
      }
      parent.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
      if (lexer.getTokenType() != JavaTokenType.RPARENTH){
        return false;
      }
    }
    else{
      parent.rawAddChildren(expr);

      if (lexer.getTokenType() != JavaTokenType.RPARENTH){
        parent.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
        return false;
      }
    }

    // add ')'
    parent.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    return true;
  }

  private void processClosingSemicolon(CompositeElement statement, Lexer lexer) {
    if (lexer.getTokenType() == JavaTokenType.SEMICOLON){
      statement.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      statement.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
    }
  }

  @Nullable
  public TreeElement parseCatchSectionText(CharSequence buffer) {
    Lexer lexer = new JavaLexer(myContext.getLanguageLevel());
    final FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    filterLexer.start(buffer);

    CompositeElement catchSection = parseCatchSection(filterLexer);
    if (catchSection == null) return null;
    if (filterLexer.getTokenType() != null) return null;

    ParseUtil.insertMissingTokens(catchSection, lexer, 0, buffer.length(), -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return catchSection;
  }
}
