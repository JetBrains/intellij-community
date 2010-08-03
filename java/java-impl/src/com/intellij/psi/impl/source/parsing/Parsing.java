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
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Parsing  {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.Parsing");
  protected static final boolean DEEP_PARSE_BLOCKS_IN_STATEMENTS = false;
  protected final JavaParsingContext myContext;

  public Parsing(JavaParsingContext context) {
    myContext = context;
  }

  @Nullable
  public static CompositeElement parseJavaCodeReferenceText(PsiManager manager, @NotNull CharSequence buffer, CharTable table) {
    return (CompositeElement)parseJavaCodeReferenceText(manager, buffer, 0, buffer.length(), table, false);
  }

  //Since we are to parse greedily (up to the end) in case eatAll=true,
  //  we are not guaranteed to return reference actually
  @Nullable
  public static TreeElement parseJavaCodeReferenceText(PsiManager manager,
                                                       CharSequence buffer,
                                                       int startOffset,
                                                       int endOffset,
                                                       CharTable table,
                                                       boolean eatAll) {
    Lexer originalLexer = new JavaLexer(LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, startOffset, endOffset);

    JavaParsingContext context = new JavaParsingContext(table, LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());
    CompositeElement ref = context.getStatementParsing().parseJavaCodeReference(lexer, false, true, false, false);
    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, table).getTreeElement();
    if (ref == null) {
      if (!eatAll) return null;
    } else {
      dummyRoot.rawAddChildren(ref);
    }

    if (lexer.getTokenType() != null) {
      if (!eatAll) return null;
      final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("unexpected.tokens"));
      while (lexer.getTokenType() != null) {
        final TreeElement token = ParseUtil.createTokenElement(lexer, context.getCharTable());
        errorElement.rawAddChildren(token);
        lexer.advance();
      }
      dummyRoot.rawAddChildren(errorElement);
    }

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, startOffset, endOffset, -1, WhiteSpaceAndCommentsProcessor.INSTANCE, context);
    return dummyRoot.getFirstChildNode();
  }

  public CompositeElement parseJavaCodeReference(Lexer lexer, boolean allowIncomplete, final boolean parseParameterList,
                                                 boolean parseAnnotations, boolean parseNewExpression) {
    CompositeElement refElement = ASTFactory.composite(JavaElementType.JAVA_CODE_REFERENCE);
    LexerPosition beforeAnnos = lexer.getCurrentPosition();
    if (parseAnnotations) {
      parseAnnotationListTo(lexer, refElement);
    }
    if (lexer.getTokenType() != JavaTokenType.IDENTIFIER) {
      if (parseAnnotations) lexer.restore(beforeAnnos);
      return null;
    }

    TreeElement identifier = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();

    refElement.rawAddChildren(identifier);
    CompositeElement parameterList;
    if (parseParameterList) {
      parameterList = parseReferenceParameterList(lexer, true, parseNewExpression);
    }
    else {
      parameterList = ASTFactory.composite(JavaElementType.REFERENCE_PARAMETER_LIST);
    }
    refElement.rawAddChildren(parameterList);

    while (lexer.getTokenType() == JavaTokenType.DOT) {
      final LexerPosition dotPos = lexer.getCurrentPosition();
      TreeElement dot = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
      if (lexer.getTokenType() == JavaTokenType.IDENTIFIER) {
        identifier = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
        lexer.advance();
      }
      else{
        if (!allowIncomplete){
          lexer.restore(dotPos);
          return refElement;
        }
        identifier = null;
      }

      CompositeElement refElement1 = ASTFactory.composite(JavaElementType.JAVA_CODE_REFERENCE);
      refElement1.rawAddChildren(refElement);
      refElement1.rawAddChildren(dot);
      if (identifier == null){
        refElement1.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
        refElement1.rawAddChildren(ASTFactory.composite(JavaElementType.REFERENCE_PARAMETER_LIST));
        return refElement1;
      }
      refElement1.rawAddChildren(identifier);
      CompositeElement parameterList1;
      if (parseParameterList) {
        parameterList1 = parseReferenceParameterList(lexer, true, parseNewExpression);
      }
      else {
        parameterList1 = ASTFactory.composite(JavaElementType.REFERENCE_PARAMETER_LIST);
      }
      refElement1.rawAddChildren(parameterList1);
      refElement = refElement1;
    }

    return refElement;
  }

  public CompositeElement parseReferenceParameterList(Lexer lexer, boolean allowWildcard, boolean allowDiamonds) {
    final CompositeElement list = ASTFactory.composite(JavaElementType.REFERENCE_PARAMETER_LIST);
    if (lexer.getTokenType() != JavaTokenType.LT) return list;
    final TreeElement lt = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    list.rawAddChildren(lt);
    lexer.advance();
    while (true) {
      final CompositeElement typeElement = parseType(lexer, true, allowWildcard, allowDiamonds);
      if (typeElement != null) {
        list.rawAddChildren(typeElement);
      }
      else {
        final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier"));
        list.rawAddChildren(errorElement);
      }

      if (lexer.getTokenType() == JavaTokenType.GT) {
        final TreeElement gt = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
        list.rawAddChildren(gt);
        lexer.advance();
        return list;
      }
      else if (lexer.getTokenType() == JavaTokenType.COMMA) {
        final TreeElement comma = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
        list.rawAddChildren(comma);
        lexer.advance();
      }
      else {
        final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.gt.or.comma"));
        list.rawAddChildren(errorElement);
        return list;
      }
    }
  }

  public static CompositeElement parseTypeText(PsiManager manager, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    Lexer originalLexer = new JavaLexer(LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, startOffset, endOffset);
    final JavaParsingContext context = new JavaParsingContext(table, LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());
    CompositeElement type = context.getStatementParsing().parseTypeWithEllipsis(lexer);
    if (type == null) return null;
    if (lexer.getTokenType() != null) return null;
    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, table).getTreeElement();
    dummyRoot.rawAddChildren(type);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, startOffset, endOffset, -1, WhiteSpaceAndCommentsProcessor.INSTANCE, context);
    return type;
  }

  public CompositeElement parseTypeWithEllipsis(Lexer lexer, boolean eatLastDot, boolean allowWilcard) {
    CompositeElement type = parseType(lexer, eatLastDot, allowWilcard, false);
    if (type == null) return null;
    if (lexer.getTokenType() == JavaTokenType.ELLIPSIS) {
      CompositeElement type1 = ASTFactory.composite(JavaElementType.TYPE);
      type1.rawAddChildren(type);
      type1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      type = type1;
    }

    return type;

  }
  public CompositeElement parseTypeWithEllipsis(Lexer lexer) {
    return parseTypeWithEllipsis(lexer, true, true);
  }

  public CompositeElement parseType(Lexer lexer){
    return parseType(lexer, true, true, false);
  }

  protected void parseAnnotationListTo(@NotNull Lexer lexer, @Nullable CompositeElement element) {
    IElementType tokenType = lexer.getTokenType();
    while (tokenType == JavaTokenType.AT) {
      CompositeElement anno = myContext.getDeclarationParsing().parseAnnotation(lexer);
      if (element != null) element.rawAddChildren(anno);
      tokenType = lexer.getTokenType();
    }
  }

  public CompositeElement parseType(Lexer lexer, boolean eatLastDot, boolean allowWildcard, boolean allowDiamonds){
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == null) {
      return null;
    }

    CompositeElement type = ASTFactory.composite(JavaElementType.TYPE);
    if (areTypeAnnotationsSupported()) parseAnnotationListTo(lexer, type);
    tokenType = lexer.getTokenType();
    TreeElement refElement;
    if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(tokenType)){
      refElement = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
    }
    else if (tokenType == JavaTokenType.IDENTIFIER){
      refElement = parseJavaCodeReference(lexer, eatLastDot, true, false, allowDiamonds);
    }
    else if (allowWildcard && lexer.getTokenType() == JavaTokenType.QUEST) {
      return parseWildcardType(lexer);
    }
    else if (allowDiamonds && lexer.getTokenType() == JavaTokenType.GT) {
      final CompositeElement typeElement = ASTFactory.composite(JavaElementType.TYPE);
      typeElement.rawAddChildren(ASTFactory.composite(JavaElementType.DIAMOND_TYPE));
      return typeElement;
    }
    else {
      return null;
    }
    type.rawAddChildren(refElement);

    CompositeElement arrayTypeElement = ASTFactory.composite(JavaElementType.TYPE);

    while(true) {
      if (areTypeAnnotationsSupported()) parseAnnotationListTo(lexer, arrayTypeElement);
      if (lexer.getTokenType() != JavaTokenType.LBRACKET) {
        if (arrayTypeElement.getFirstChildNode() != null) {
          type.rawAddChildren(arrayTypeElement.getFirstChildNode());
          type.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.lbracket")));
          return type;
        }
        break;
      }

      final LexerPosition lbracketPos = lexer.getCurrentPosition();
      TreeElement lbracket = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
      if (lexer.getTokenType() != JavaTokenType.RBRACKET){
        lexer.restore(lbracketPos);
        break;
      }
      if (arrayTypeElement.getFirstChildNode() == null) {
        arrayTypeElement.rawAddChildren(type);
      }
      else {
        arrayTypeElement.getFirstChildNode().rawInsertBeforeMe(type);
      }
      arrayTypeElement.rawAddChildren(lbracket);
      TreeElement rBracket = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      arrayTypeElement.rawAddChildren(rBracket);
      lexer.advance();
      type = arrayTypeElement;

      arrayTypeElement = ASTFactory.composite(JavaElementType.TYPE);
    }

    return type;
  }

  protected boolean areTypeAnnotationsSupported() {
    return myContext.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_7);
  }

  private CompositeElement parseWildcardType(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == JavaTokenType.QUEST);
    CompositeElement type = ASTFactory.composite(JavaElementType.TYPE);
    type.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    if (lexer.getTokenType() == JavaTokenType.SUPER_KEYWORD || lexer.getTokenType() == JavaTokenType.EXTENDS_KEYWORD) {
      type.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      CompositeElement boundType = parseType(lexer, true, false, false);
      if (boundType != null) {
        type.rawAddChildren(boundType);
      }
      else {
        type.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.type")));
      }
    }
    return type;
  }

  public static TreeElement parseTypeText(PsiManager manager,
                                          Lexer lexer,
                                          CharSequence buffer,
                                          int startOffset,
                                          int endOffset,
                                          int state,
                                          CharTable table) {
    if (lexer == null){
      lexer = new JavaLexer(LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());
    }
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state < 0) filterLexer.start(buffer, startOffset, endOffset);
    else filterLexer.start(buffer, startOffset, endOffset, state);
    final JavaParsingContext context = new JavaParsingContext(table,
                                                              LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());
    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, context.getCharTable()).getTreeElement();
    final CompositeElement root = context.getStatementParsing().parseType(filterLexer);

    if (root != null) {
      dummyRoot.rawAddChildren(root);
    }

    if (filterLexer.getTokenType() == JavaTokenType.ELLIPSIS) {
      dummyRoot.rawAddChildren(ParseUtil.createTokenElement(filterLexer, context.getCharTable()));
      filterLexer.advance();
    }

    if (filterLexer.getTokenType() != null) {
      final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("unexpected.tokens"));
      while (filterLexer.getTokenType() != null) {
        final TreeElement token = ParseUtil.createTokenElement(lexer, context.getCharTable());
        errorElement.rawAddChildren(token);
        filterLexer.advance();
      }
      dummyRoot.rawAddChildren(errorElement);
    }

    ParseUtil.insertMissingTokens(
      dummyRoot,
      lexer,
      startOffset,
      endOffset, state,
      WhiteSpaceAndCommentsProcessor.INSTANCE, context);
    return dummyRoot.getFirstChildNode();
 }
}
