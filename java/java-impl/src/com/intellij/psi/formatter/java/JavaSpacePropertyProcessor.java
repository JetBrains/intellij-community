/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.formatting.Block;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.blocks.CStyleCommentBlock;
import com.intellij.formatting.blocks.TextLineBlock;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.impl.source.jsp.jspJava.JspJavaComment;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.*;

public class JavaSpacePropertyProcessor extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.java.JavaSpacePropertyProcessor");

  private static final TokenSet REF_LIST_KEYWORDS = TokenSet.create(
    JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.IMPLEMENTS_KEYWORD, JavaTokenType.THROWS_KEYWORD, JavaTokenType.WITH_KEYWORD);

  private PsiElement myParent;
  private int myRole1;
  private int myRole2;
  private CommonCodeStyleSettings mySettings;
  private JavaCodeStyleSettings myJavaSettings;

  private Spacing myResult;
  private ASTNode myChild1;
  private ASTNode myChild2;

  private IElementType myType1;
  private IElementType myType2;

  private ImportHelper myImportHelper;

  private static final ThreadLocal<JavaSpacePropertyProcessor> mySharedProcessorAllocator = new ThreadLocal<>();

  private void doInit(Block block, CommonCodeStyleSettings settings, JavaCodeStyleSettings javaSettings) {
    ASTNode child = AbstractJavaBlock.getTreeNode(block);
    if (isErrorElement(child)) {
      myResult = Spacing.getReadOnlySpacing();
      return;
    }

    init(child);
    mySettings = settings;
    myJavaSettings = javaSettings;

    if (myChild1 == null) {
      // Given node corresponds to the first document block.
      if (FormatterUtil.isFormatterCalledExplicitly()) {
        createSpaceInCode(false);
      }
      return;
    }

    final PsiElement myChild1Psi1 = myChild1.getPsi();
    final PsiElement myChild1Psi2 = myChild2.getPsi();
    if (myChild1Psi1 == null || myChild1Psi1.getLanguage() != JavaLanguage.INSTANCE ||
        myChild1Psi2 == null || myChild1Psi2.getLanguage() != JavaLanguage.INSTANCE) {
      return;
    }

    if (block instanceof TextLineBlock) {
      myResult = ((TextLineBlock)block).getSpacing();
      return;
    }
    if (block instanceof CStyleCommentBlock) {
      myResult = ((CStyleCommentBlock)block).getSpacing();
      return;
    }

    if (myChild2 != null && StdTokenSets.COMMENT_BIT_SET.contains(myChild2.getElementType())) {
      if (mySettings.KEEP_FIRST_COLUMN_COMMENT) {
        myResult = Spacing.createKeepingFirstColumnSpacing(0, Integer.MAX_VALUE, true, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 0,  true, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
    else if (myParent != null) {
      myParent.accept(this);

      if (myResult == null) {
        final ASTNode prev = getPrevElementType(myChild2);
        if (prev != null && prev.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
          myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        }
        else if (!canStickChildrenTogether(myChild1, myChild2)) {
          myResult = Spacing.createSpacing(1, Integer.MIN_VALUE, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        }
        else if (myChild1.getElementType() == JavaTokenType.C_STYLE_COMMENT) {
          myResult = null;
        }
        else if (!shouldKeepSpace(myParent)) {
          myResult = Spacing.createSpacing(0, 0, 0, true, mySettings.KEEP_BLANK_LINES_IN_CODE);
        }
      }
    }
  }

  private static boolean isErrorElement(ASTNode child) {
    return child != null && child.getPsi() instanceof PsiErrorElement;
  }

  private void clear() {
    myResult = null;
    myChild2 = myChild1 = null;
    myParent = null;
    myImportHelper = null;
    myRole1 = myRole2 = -1;
    myType1 = myType2 = null;
  }

  private static boolean shouldKeepSpace(final PsiElement parent) {
    ASTNode node = parent.getNode();
    if (node == null) {
      return true;
    }

    final IElementType type = node.getElementType();
    if (type == JavaDocElementType.DOC_TAG_VALUE_ELEMENT) {
      return PsiTreeUtil.getParentOfType(parent, PsiDocMethodOrFieldRef.class) != null;
    }

    return type == JavaDocElementType.DOC_COMMENT || type == JavaDocElementType.DOC_TAG || type == JavaDocElementType.DOC_INLINE_TAG;
  }

  private void init(final ASTNode child) {
    if (child == null) return;
    ASTNode treePrev = child.getTreePrev();
    while (treePrev != null && isWhiteSpace(treePrev)) {
      treePrev = treePrev.getTreePrev();
    }
    if (treePrev == null) {
      init(child.getTreeParent());
    }
    else {
      myChild2 = child;
      myChild1 = treePrev;
      final CompositeElement parent = (CompositeElement)treePrev.getTreeParent();
      myParent = SourceTreeToPsiMap.treeElementToPsi(parent);
      myRole1 = parent.getChildRole(treePrev);
      myType1 = treePrev.getElementType();
      myRole2 = parent.getChildRole(child);
      myType2 = child.getElementType();
    }
  }

  private static boolean isWhiteSpace(final ASTNode treePrev) {
    return treePrev != null && (treePrev.getElementType() == TokenType.WHITE_SPACE || treePrev.getTextLength() == 0);
  }

  private Spacing getResult() {
    final Spacing result = myResult;
    clear();
    return result;
  }

  @Override
  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    if (myRole1 == ChildRole.ARRAY && myRole2 == ChildRole.LBRACKET) {
      final boolean space = false;
      createSpaceInCode(space);
    }
    else if (myRole1 == ChildRole.LBRACKET || myRole2 == ChildRole.RBRACKET) {
      createSpaceInCode(mySettings.SPACE_WITHIN_BRACKETS);
    }
  }

  private void createSpaceInCode(final boolean space) {
    createSpaceProperty(space, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  @Override
  public void visitNewExpression(PsiNewExpression expression) {
    if (myRole2 == ChildRole.ARRAY_INITIALIZER) {
      createSpaceInCode(mySettings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE);
    }
    else if (myRole1 == ChildRole.NEW_KEYWORD) {
      createSpaceInCode(true);
    }
    else if (myRole2 == ChildRole.ARGUMENT_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    }
    // We don't want to insert space between brackets in case of expression like 'new int[] {1}', hence, we check that exactly
    // one of the children is bracket.
    else if (myRole1 == ChildRole.LBRACKET ^ myRole2 == ChildRole.RBRACKET) {
      createSpaceInCode(mySettings.SPACE_WITHIN_BRACKETS);
    }
  }

  @Override
  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    visitArrayInitializer();
  }

  @Override
  public void visitClass(PsiClass aClass) {
    if (myChild1.getElementType() == JavaDocElementType.DOC_COMMENT) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      return;
    }
    if (myRole2 == ChildRole.LBRACE) {
      myResult = getSpaceBeforeClassLBrace(aClass);
    }
    else if (myRole1 == ChildRole.LBRACE || isEndOfLineCommentAfterLBrace(myChild1)) {
      if (aClass.isEnum()) {
        createParenthSpace(true, false);
      }
      else if (myRole2 == ChildRole.RBRACE && mySettings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE) {
        int spaces = mySettings.SPACE_WITHIN_BRACES ? 1 : 0;
        myResult = Spacing.createSpacing(spaces, spaces, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
      else if (aClass instanceof PsiAnonymousClass) {
        if (myRole2 == ChildRole.CLASS_INITIALIZER && isTheOnlyClassMember(myChild2)) {
          myResult = Spacing.createSpacing(0, 0, 0,
                                           mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
        }
        else {
          myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER + 1,
                                           mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
        }
      }
      else {
        myResult = Spacing.createSpacing(1, 1, mySettings.BLANK_LINES_AFTER_CLASS_HEADER + 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (myRole2 == ChildRole.RBRACE && aClass.isEnum()) {
      createParenthSpace(true, false);
    }
    else if (aClass instanceof PsiAnonymousClass && ElementType.JAVA_PLAIN_COMMENT_BIT_SET.contains(myChild1.getElementType())) {
      ASTNode prev = myChild1.getTreePrev();
      if (prev.getElementType() == TokenType.WHITE_SPACE && !StringUtil.containsLineBreak(prev.getChars())) {
        prev = prev.getTreePrev();
      }
      if (prev.getElementType() == JavaTokenType.LBRACE) {
        myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER + 1,
                                         mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
      else {
        processClassBody();
      }
    }
    else if (aClass instanceof PsiAnonymousClass && myRole2 == ChildRole.ARGUMENT_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    }
    else {
      processClassBody();
    }
  }

  @NotNull
  private Spacing getSpaceBeforeMethodLBrace(@NotNull PsiMethod method) {
    final int space = mySettings.SPACE_BEFORE_METHOD_LBRACE ? 1 : 0;
    final int methodBraceStyle = mySettings.METHOD_BRACE_STYLE;

    if (methodBraceStyle == END_OF_LINE) {
      return createNonLFSpace(space, null);
    }
    else if (methodBraceStyle == NEXT_LINE_IF_WRAPPED) {
      TextRange headerRange = new TextRange(getMethodHeaderStartOffset(method), getMethodHeaderEndOffset(method));
      return createNonLFSpace(space, headerRange);
    }
    else if (shouldHandleAsSimpleMethod(method)) {
      TextRange rangeWithoutAnnotations = new TextRange(getMethodHeaderStartOffset(method), method.getTextRange().getEndOffset());
      return createNonLFSpace(space, rangeWithoutAnnotations);
    }

    return Spacing.createSpacing(space, space, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  private static int getMethodHeaderEndOffset(@NotNull PsiMethod method) {
    PsiElement headerEnd = method.getBody() != null ? method.getBody().getPrevSibling() : null;
    if (headerEnd != null) {
      return headerEnd.getTextRange().getEndOffset();
    }
    return method.getTextRange().getEndOffset();
  }

  @NotNull
  private Spacing getSpaceBeforeClassLBrace(@NotNull PsiClass aClass) {
    final int space = mySettings.SPACE_BEFORE_CLASS_LBRACE ? 1 : 0;
    final int classBraceStyle = mySettings.CLASS_BRACE_STYLE;

    if (classBraceStyle == END_OF_LINE || shouldHandleAsSimpleClass(aClass)) {
      return createNonLFSpace(space, null);
    }
    else if (classBraceStyle == NEXT_LINE_IF_WRAPPED) {
      final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      final int startOffset = nameIdentifier == null ? myParent.getTextRange().getStartOffset() : nameIdentifier.getTextRange().getStartOffset();
      TextRange range = new TextRange(startOffset, myChild1.getTextRange().getEndOffset());
      return createNonLFSpace(space, range);
    }

    return Spacing.createSpacing(space, space, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  private Spacing getSpaceBeforeLBrace(@NotNull ASTNode lBraceBlock, boolean spaceBeforeLbrace, @Nullable TextRange nextLineIfWrappedOptionRange) {
    int space = spaceBeforeLbrace ? 1 : 0;

    if (mySettings.BRACE_STYLE == END_OF_LINE) {
      return createNonLFSpace(space, null);
    }
    else if (mySettings.BRACE_STYLE == NEXT_LINE_IF_WRAPPED) {
      return createNonLFSpace(space, nextLineIfWrappedOptionRange);
    }
    else if (shouldHandleAsSimpleBlock(lBraceBlock)) {
      return createNonLFSpace(space, lBraceBlock.getTextRange());
    }

    return Spacing.createSpacing(space, space, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  private boolean shouldHandleAsSimpleClass(@NotNull PsiClass aClass) {
    if (!mySettings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE) return false;

    final PsiElement lBrace = aClass.getLBrace();
    final PsiElement rBrace = aClass.getRBrace();
    if (lBrace != null && rBrace != null) {
      PsiElement beforeLBrace = lBrace.getPrevSibling();
      if (beforeLBrace instanceof PsiWhiteSpace && beforeLBrace.textContains('\n')) {
        return false;
      }

      PsiElement betweenBraces = lBrace.getNextSibling();
      if (betweenBraces == rBrace || isWhiteSpaceWithoutLineFeeds(betweenBraces) && betweenBraces.getNextSibling() == rBrace) {
        return true;
      }
    }

    return false;
  }

  private static boolean isWhiteSpaceWithoutLineFeeds(@Nullable PsiElement betweenBraces) {
    return betweenBraces instanceof PsiWhiteSpace && !betweenBraces.textContains('\n');
  }

  private boolean shouldHandleAsSimpleBlock(@NotNull ASTNode node) {
    if (!mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE) return false;

    PsiElement prev = node.getPsi().getPrevSibling();
    if (prev instanceof PsiWhiteSpace && prev.textContains('\n')) {
      return false;
    }
    return !node.textContains('\n');
  }

  private boolean shouldHandleAsSimpleMethod(@NotNull PsiMethod method) {
    if (!mySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE) return false;
    PsiCodeBlock body = method.getBody();
    return body != null && !body.textContains('\n');
  }

  private static int getMethodHeaderStartOffset(@NotNull PsiMethod method) {
    PsiTypeParameterList typeParameterList = PsiTreeUtil.findChildOfType(method, PsiTypeParameterList.class);
    if (typeParameterList != null) {
      PsiElement nextNonWsElem = PsiTreeUtil.skipWhitespacesForward(typeParameterList);
      if (nextNonWsElem != null) {
        return nextNonWsElem.getTextRange().getStartOffset();
      }
    }
    return method.getTextRange().getStartOffset();
  }

  private static boolean isEndOfLineCommentAfterLBrace(@NotNull ASTNode node) {
    if (node.getPsi() instanceof PsiComment) {
      PsiElement ws = node.getPsi().getPrevSibling();
      if (isWhiteSpaceWithoutLineFeeds(ws)) {
        PsiElement beforeWs = ws.getPrevSibling();
        if (PsiUtil.isJavaToken(beforeWs, JavaTokenType.LBRACE)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isTheOnlyClassMember(final ASTNode node) {
    ASTNode next = node.getTreeNext();
    if (next == null || !(next.getElementType() == JavaTokenType.RBRACE)) return false;

    ASTNode prev = node.getTreePrev();
    if (prev == null || !(prev.getElementType() == JavaTokenType.LBRACE)) return false;

    return true;
  }

  @SuppressWarnings("StatementWithEmptyBody")
  private void processClassBody() {
    if (myChild1 instanceof JspJavaComment || myChild2 instanceof JspJavaComment) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, 0);
    }
    else if (processMethod()) {
    }
    else if (myRole2 == ChildRole.CLASS_INITIALIZER) {
      if (myRole1 == ChildRole.LBRACE) {
        myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else if (myRole1 == ChildRole.FIELD) {
        int blankLines = myJavaSettings.BLANK_LINES_AROUND_INITIALIZER + 1;
        myResult = Spacing.createSpacing(0, mySettings.SPACE_BEFORE_CLASS_LBRACE ? 1 : 0, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
      else if (myRole1 == ChildRole.CLASS) {
        setAroundClassSpacing();
      }
      else {
        final int blankLines = getLinesAroundMethod() + 1;
        myResult = Spacing.createSpacing(0, 0, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (myRole1 == ChildRole.CLASS_INITIALIZER) {
      if (myRole2 == ChildRole.RBRACE) {
        int minLineFeeds = getMinLineFeedsBetweenRBraces(myChild1);
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, minLineFeeds, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
      else if (myRole2 == ChildRole.CLASS) {
        setAroundClassSpacing();
      }
      else {
        final int blankLines = myJavaSettings.BLANK_LINES_AROUND_INITIALIZER + 1;
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (myRole1 == ChildRole.CLASS) {
      if (myRole2 == ChildRole.RBRACE) {
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
      else {
        final int blankLines = mySettings.BLANK_LINES_AROUND_CLASS + 1;
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (myRole2 == ChildRole.CLASS) {
      if (myRole1 == ChildRole.LBRACE) {
        myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, 0);
      }
      else {
        final int blankLines = mySettings.BLANK_LINES_AROUND_CLASS + 1;
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }

    else if (myRole2 == ChildRole.FIELD) {
      if (myRole1 == ChildRole.COMMA) {
        createSpaceProperty(true, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else if (myRole1 == ChildRole.LBRACE) {
        myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, 0);
      }
      else {
        final int blankLines = getLinesAroundField() + 1;
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }

    else if (myRole1 == ChildRole.FIELD) {
      if (myRole2 == ChildRole.COMMA) {
        ASTNode lastChildNode = myChild1.getLastChildNode();
        if (lastChildNode != null && lastChildNode.getElementType() == JavaTokenType.SEMICOLON) {
          myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
        }
        else {
          createSpaceProperty(false, false, 0);
        }
      }
      else if (myRole2 == ChildRole.RBRACE) {
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
      else {
        final int blankLines = getLinesAroundField() + 1;
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (myRole2 == ChildRole.COMMA || myChild2.getElementType() == JavaTokenType.SEMICOLON) {
      createSpaceProperty(false, false, 0);
    }
    else if (myRole1 == ChildRole.COMMA) {
      createSpaceProperty(mySettings.SPACE_AFTER_COMMA, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

    else if (myRole1 == ChildRole.MODIFIER_LIST) {
      processModifierList();
    }

    else if (myRole1 == ChildRole.LBRACE && myRole2 == ChildRole.RBRACE) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    }

    else if (myRole2 == ChildRole.EXTENDS_LIST || myRole2 == ChildRole.IMPLEMENTS_LIST) {
      createSpaceInCode(true);
    }

    else if (myRole2 == ChildRole.TYPE_PARAMETER_LIST) {
      createSpaceInCode(myJavaSettings.SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER);
    }

    else if (myRole2 == ChildRole.ARGUMENT_LIST) {
      createSpaceInCode(false);
    }
    else if (myRole2 == ChildRole.RBRACE) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    }
  }

  /**
   * Initializes {@link #myResult} property with {@link Spacing} which {@code 'min line feeds'} property is defined
   * from {@link CodeStyleSettings#BLANK_LINES_AROUND_CLASS} value.
   */
  private void setAroundClassSpacing() {
    myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, mySettings.BLANK_LINES_AROUND_CLASS + 1,
                                     mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
  }

  private boolean processMethod() {
    if (myRole2 == ChildRole.METHOD || myChild2.getElementType() == JavaElementType.METHOD) {
      if (myRole1 == ChildRole.LBRACE) {
        myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, 0);
      }
      else if (myRole1 == ChildRole.CLASS_INITIALIZER) {
        int blankLines = myJavaSettings.BLANK_LINES_AROUND_INITIALIZER + 1;
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
      else {
        final int blankLines = getLinesAroundMethod() + 1;
        myResult = Spacing.createSpacing(0, 0, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (myRole1 == ChildRole.METHOD || myChild1.getElementType() == JavaElementType.METHOD) {
      if (myRole1 == ChildRole.LBRACE) {
        myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, 0);
      }
      else {
        final int blankLines = getLinesAroundMethod() + 1;
        myResult = Spacing.createSpacing(0, 0, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
      if (myRole2 == ChildRole.RBRACE) {
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
      else {
        final int blankLines = getLinesAroundMethod() + 1;
        myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    return myResult != null;
  }

  /**
   * Allows to calculate {@code 'min line feed'} setting of the {@link Spacing} to be used between two closing braces
   * (assuming that left AST node that ends with closing brace is given to this method).
   *
   * @param leftNode    left AST node that ends with closing brace
   * @return            {@code 'min line feed'} setting of {@link Spacing} object to use for the given AST node and
   *                    closing brace
   */
  private static int getMinLineFeedsBetweenRBraces(ASTNode leftNode) {

    // The general idea is to return zero in situation when opening curly braces goes one after other, e.g.
    //     new Expectations() {{
    //         foo();}}
    // We don't want line feed between closing curly braces here.

    if (leftNode == null || leftNode.getElementType() != JavaElementType.CLASS_INITIALIZER) {
      return 1;
    }

    ASTNode lbraceCandidate = leftNode.getTreePrev();
    return lbraceCandidate != null && lbraceCandidate.getElementType() == JavaTokenType.LBRACE ? 0 : 1;
  }

  private int getLinesAroundMethod() {
    boolean useInterfaceMethodSpacing = !isClass(myParent) || isAbstractMethod(myChild1) && isAbstractMethod(myChild2);
    return useInterfaceMethodSpacing ? mySettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE : mySettings.BLANK_LINES_AROUND_METHOD;
  }

  private int getLinesAroundField() {
    if (isClass(myParent)) {
      return mySettings.BLANK_LINES_AROUND_FIELD;
    }
    else {
      return mySettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE;
    }
  }

  private static boolean isClass(final PsiElement parent) {
    if (parent instanceof PsiClass) {
      return !((PsiClass)parent).isInterface();
    }
    return false;
  }

  private static boolean isAbstractMethod(ASTNode node) {
    PsiElement element = node.getPsi();
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      return method.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT);
    }
    return false;
  }

  @Override
  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    createSpaceInCode(true);
  }

  @Override
  public void visitEnumConstantInitializer(PsiEnumConstantInitializer enumConstantInitializer) {
    if (myRole2 == ChildRole.EXTENDS_LIST || myRole2 == ChildRole.IMPLEMENTS_LIST) {
      createSpaceInCode(true);
    } else {
      processMethod();
    }
  }

  @Override
  public void visitImportList(PsiImportList list) {
    if (ElementType.IMPORT_STATEMENT_BASE_BIT_SET.contains(myChild1.getElementType()) &&
        ElementType.IMPORT_STATEMENT_BASE_BIT_SET.contains(myChild2.getElementType())) {
      if (myImportHelper == null) myImportHelper = new ImportHelper(mySettings.getRootSettings());
      int emptyLines = myImportHelper.getEmptyLinesBetween(
        SourceTreeToPsiMap.treeToPsiNotNull(myChild1),
        SourceTreeToPsiMap.treeToPsiNotNull(myChild2)
      ) + 1;
      myResult = Spacing.createSpacing(0, 0, emptyLines,
                                       mySettings.KEEP_LINE_BREAKS,
                                       mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

  }

  @Override
  public void visitFile(PsiFile file) {
    if (myType1 == JavaElementType.PACKAGE_STATEMENT) {
      int lf = mySettings.BLANK_LINES_AFTER_PACKAGE + 1;
      myResult = Spacing.createSpacing(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

    else if (myType2 == JavaElementType.PACKAGE_STATEMENT) {
      int lf = mySettings.BLANK_LINES_BEFORE_PACKAGE + 1;
      myResult = Spacing.createSpacing(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

    else if (myType1 == JavaElementType.IMPORT_LIST) {
      int lf = mySettings.BLANK_LINES_AFTER_IMPORTS + 1;
      myResult = Spacing.createSpacing(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

    else if (myType2 == JavaElementType.IMPORT_LIST) {
      int lf = mySettings.BLANK_LINES_BEFORE_IMPORTS + 1;
      myResult = Spacing.createSpacing(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }
    else if (myType2 == JavaElementType.CLASS) {
      int lf = mySettings.BLANK_LINES_AROUND_CLASS + 1;
      myResult = Spacing.createSpacing(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

  }

  @Override
  public void visitWhileStatement(PsiWhileStatement statement) {
    if (myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_WHILE_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_WHILE_PARENTHESES);
    }
    else if (myRole2 == ChildRole.LOOP_BODY || myChild2.getElementType() == JavaElementType.CODE_BLOCK) {
      if (myChild2.getElementType() == JavaElementType.BLOCK_STATEMENT) {
        myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_WHILE_LBRACE, null);
      } else {
        createSpacingBeforeElementInsideControlStatement();
      }
    }
  }

  @Override
  public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    if (myRole1 == ChildRole.WHILE_KEYWORD && myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_WHILE_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_WHILE_PARENTHESES);
    }
    else if (myRole2 == ChildRole.LOOP_BODY) {
      if (myChild2.getElementType() == JavaElementType.BLOCK_STATEMENT) {
        myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_DO_LBRACE, null);
      } else {
        createSpacingBeforeElementInsideControlStatement();
      }
    }
    else if (myRole1 == ChildRole.LOOP_BODY || myChild2.getElementType() == JavaElementType.CODE_BLOCK) {
      processOnNewLineCondition(mySettings.WHILE_ON_NEW_LINE, mySettings.SPACE_BEFORE_WHILE_KEYWORD);
    }
  }

  private void processOnNewLineCondition(boolean onNewLine, boolean createSpaceInline) {
    if (onNewLine) {
      if (!mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE) {
        myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        myResult = Spacing.createDependentLFSpacing(0, 1, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
    else {
      createSpaceProperty(createSpaceInline, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  @Override
  public void visitThrowStatement(PsiThrowStatement statement) {
    if (myChild1.getElementType() == JavaTokenType.THROW_KEYWORD) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    if (myRole2 == ChildRole.FINALLY_KEYWORD || myRole2 == ChildRole.CATCH_SECTION) {
      boolean putRightChildOnNewLine = myRole2 == ChildRole.FINALLY_KEYWORD ? mySettings.FINALLY_ON_NEW_LINE : mySettings.CATCH_ON_NEW_LINE;
      if (putRightChildOnNewLine) {
        processOnNewLineCondition(true, true);
      }
      else {
        boolean useSpace = myRole2 == ChildRole.CATCH_SECTION && mySettings.SPACE_BEFORE_CATCH_KEYWORD
                           || myRole2 == ChildRole.FINALLY_KEYWORD && mySettings.SPACE_BEFORE_FINALLY_KEYWORD;
        createSpaceProperty(useSpace, false, 0);
      }
      return;
    }

    if (myRole2 == ChildRole.TRY_BLOCK) {
      TextRange dependentRange = null;
      if (myChild1 instanceof PsiResourceList && mySettings.BRACE_STYLE == NEXT_LINE_IF_WRAPPED) {
          dependentRange = myChild1.getTextRange();
      }
      myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_TRY_LBRACE, dependentRange);
    }
    else if (myRole2 == ChildRole.FINALLY_BLOCK) {
      myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_FINALLY_LBRACE, null);
    }
    else if (myType2 == JavaElementType.RESOURCE_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_TRY_PARENTHESES);
    }
  }

  @Override
  public void visitForeachStatement(PsiForeachStatement statement) {
    if (myRole1 == ChildRole.FOR_KEYWORD && myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_FOR_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
    }
    else if (myRole1 == ChildRole.FOR_ITERATION_PARAMETER && myRole2 == ChildRole.COLON ||
             myRole1 == ChildRole.COLON && myRole2 == ChildRole.FOR_ITERATED_VALUE) {
      createSpaceInCode(true);
    }
    else if (myRole2 == ChildRole.LOOP_BODY) {
      processLoopBody();
    }
  }

  @Override
  public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    if (myRole1 == ChildRole.OPERATION_SIGN || myRole2 == ChildRole.OPERATION_SIGN) {
      createSpaceInCode(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    }
  }

  @Override
  public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {

    if (myRole1 == ChildRole.LPARENTH) {
      createParenthSpace(mySettings.PARENTHESES_EXPRESSION_LPAREN_WRAP, mySettings.SPACE_WITHIN_PARENTHESES);
    }
    else if (myRole2 == ChildRole.RPARENTH) {
      createParenthSpace(mySettings.PARENTHESES_EXPRESSION_RPAREN_WRAP, mySettings.SPACE_WITHIN_PARENTHESES);
    }

  }

  @Override
  public void visitCodeBlock(PsiCodeBlock block) {
    processCodeBlock(keepInOneLine(block), block.getTextRange());
  }

  @Override
  public void visitCodeFragment(JavaCodeFragment codeFragment) {
    if (myChild1.getPsi() instanceof PsiStatement && myChild2.getPsi() instanceof PsiStatement) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  private void processCodeBlock(final boolean keepInOneLine, final TextRange textRange) {
    final boolean lhsStatement = myChild1.getPsi() instanceof PsiStatement;
    final boolean rhsStatement = myChild2.getPsi() instanceof PsiStatement;

    if (myParent instanceof JspCodeBlock) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    else if (myRole1 == ChildRoleBase.NONE && !lhsStatement || myRole2 == ChildRoleBase.NONE && !rhsStatement) {
      final IElementType firstElementType = myChild1.getElementType();
      if (
        firstElementType == JavaTokenType.END_OF_LINE_COMMENT
        ||
        firstElementType == JavaTokenType.C_STYLE_COMMENT) {
        myResult = Spacing.createDependentLFSpacing(0, 1, myParent.getTextRange(),
                                                    mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        myResult = null;
      }
    }
    else if (myRole1 == ChildRole.LBRACE) {
      if (!keepInOneLine) {
        int blankLines = 1;
        if (myParent != null) {
          ASTNode parentNode = myParent.getNode();
          if (parentNode != null) {
            ASTNode grandPa = parentNode.getTreeParent();
            if (grandPa != null && grandPa.getElementType() == JavaElementType.METHOD) {
              blankLines += mySettings.BLANK_LINES_BEFORE_METHOD_BODY;
            }
          }
        }
        myResult = Spacing.createSpacing(1, 1, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        myResult = Spacing.createDependentLFSpacing(0, 1, textRange, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
    else if (myRole2 == ChildRole.RBRACE) {
      if (!keepInOneLine) {
        myResult = Spacing.createSpacing(1, 1, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
      else {
        myResult = Spacing.createDependentLFSpacing(0, 1, textRange, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
    }
    else if (myChild1.getElementType() == JavaElementType.SWITCH_LABEL_STATEMENT) {
      if (myChild2.getElementType() == JavaElementType.BLOCK_STATEMENT) {
        myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_SWITCH_LBRACE, null);
      }
      else {
        int lineFeeds = mySettings.CASE_STATEMENT_ON_NEW_LINE ? 1 : 0;
        myResult = Spacing.createSpacing(1, 1, lineFeeds, true, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
    else if (lhsStatement && rhsStatement) {
      int minSpaces = 0;
      int minLineFeeds = 1;
      PsiElement psi = myChild1.getPsi();

      // We want to avoid situations like below:
      //   1. Call 'introduce variable' refactoring for the code like 'System.out.println(1);';
      //   2. When KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE is on, the output looks like 'int i = 1; System.out.println(i);';
      // That's why we process the option only during the explicit reformat (directly invoked by an user).
      if ((mySettings.KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE
             && (FormatterUtil.isFormatterCalledExplicitly() || ApplicationManager.getApplication().isUnitTestMode()))
          || psi != null && PsiTreeUtil.hasErrorElements(psi))
      {
        minSpaces = 1;
        minLineFeeds = 0;
        if (myChild1 != null) {
          ASTNode lastElement = myChild1;
          while (lastElement.getLastChildNode() != null) lastElement = lastElement.getLastChildNode();
          //Not to place second statement on the same line with first one, if last ends with single line comment
          if (lastElement instanceof PsiComment && lastElement.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) minLineFeeds = 1;
        }
      }
      myResult = Spacing.createSpacing(minSpaces, 0, minLineFeeds, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  private boolean keepInOneLine(final PsiCodeBlock block) {
    if (block.getParent() instanceof PsiMethod) {
      return shouldHandleAsSimpleMethod((PsiMethod)block.getParent());
    }
    else if (block.getParent() instanceof PsiLambdaExpression) {
      return shouldHandleAsSimpleLambda((PsiLambdaExpression)block.getParent());
    }
    else {
      return shouldHandleAsSimpleBlock(block.getNode());
    }
  }

  private boolean shouldHandleAsSimpleLambda(PsiLambdaExpression lambda) {
    return mySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE && !lambda.textContains('\n');
  }

  @Override
  public void visitIfStatement(PsiIfStatement statement) {
    if (myRole2 == ChildRole.ELSE_KEYWORD) {
      if (myChild1.getElementType() != JavaElementType.BLOCK_STATEMENT) {
        myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        if (mySettings.ELSE_ON_NEW_LINE) {
          myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        }
        else {
          createSpaceProperty(mySettings.SPACE_BEFORE_ELSE_KEYWORD, false, 0);
        }
      }
    }
    else if (myRole1 == ChildRole.ELSE_KEYWORD) {
      if (myChild2.getElementType() == JavaElementType.IF_STATEMENT) {
        if (mySettings.SPECIAL_ELSE_IF_TREATMENT) {
          createSpaceProperty(false, false, 0);
        }
        else {
          myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        }
      }
      else {
        if (myChild2.getElementType() == JavaElementType.BLOCK_STATEMENT || myChild2.getElementType() == JavaElementType.CODE_BLOCK) {
          myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_ELSE_LBRACE, null);
        }
        else {
          createSpacingBeforeElementInsideControlStatement();
        }
      }
    }
    else if (myChild2.getElementType() == JavaElementType.BLOCK_STATEMENT || myChild2.getElementType() == JavaElementType.CODE_BLOCK) {
      boolean space = myRole2 == ChildRole.ELSE_BRANCH ? mySettings.SPACE_BEFORE_ELSE_LBRACE : mySettings.SPACE_BEFORE_IF_LBRACE;
      TextRange dependentRange = null;
      if (myRole2 == ChildRole.THEN_BRANCH) {
        PsiExpression condition = statement.getCondition();
        if (condition != null) dependentRange = condition.getTextRange();
      }
      myResult = getSpaceBeforeLBrace(myChild2, space, dependentRange);
    }
    else if (myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_IF_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_IF_PARENTHESES);
    }
    else if (myRole2 == ChildRole.THEN_BRANCH) {
      createSpacingBeforeElementInsideControlStatement();

    }
  }

  private void createSpacingBeforeElementInsideControlStatement() {
    if (mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE && myChild1.getElementType() != JavaTokenType.END_OF_LINE_COMMENT) {
      createSpaceProperty(true, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      myResult = Spacing.createSpacing(1, 1, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  private Spacing createNonLFSpace(int spaces, @Nullable TextRange dependantRange) {
    final ASTNode prev = getPrevElementType(myChild2);
    if (prev != null && prev.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
      return Spacing.createSpacing(0, Integer.MAX_VALUE, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (dependantRange != null) {
      return Spacing.createDependentLFSpacing(spaces, spaces, dependantRange, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      return Spacing.createSpacing(spaces, spaces, 0, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  @Nullable
  private static ASTNode getPrevElementType(final ASTNode child) {
    return FormatterUtil.getPreviousNonWhitespaceLeaf(child);
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    if (myRole1 == ChildRole.OPERATION_SIGN || myRole2 == ChildRole.OPERATION_SIGN) {
      IElementType i = expression.getOperationTokenType();
      if (i == JavaTokenType.OROR || i == JavaTokenType.ANDAND) {
        createSpaceInCode(mySettings.SPACE_AROUND_LOGICAL_OPERATORS);
      }
      else if (i == JavaTokenType.OR || i == JavaTokenType.AND || i == JavaTokenType.XOR) {
        createSpaceInCode(mySettings.SPACE_AROUND_BITWISE_OPERATORS);
      }
      else if (i == JavaTokenType.EQEQ || i == JavaTokenType.NE) {
        createSpaceInCode(mySettings.SPACE_AROUND_EQUALITY_OPERATORS);
      }
      else if (i == JavaTokenType.GT || i == JavaTokenType.LT || i == JavaTokenType.GE || i == JavaTokenType.LE) {
        createSpaceInCode(mySettings.SPACE_AROUND_RELATIONAL_OPERATORS);
      }
      else if (i == JavaTokenType.PLUS || i == JavaTokenType.MINUS) {
        createSpaceInCode(mySettings.SPACE_AROUND_ADDITIVE_OPERATORS);
      }
      else if (i == JavaTokenType.ASTERISK || i == JavaTokenType.DIV || i == JavaTokenType.PERC) {
        createSpaceInCode(mySettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS);
      }
      else if (i == JavaTokenType.LTLT || i == JavaTokenType.GTGT || i == JavaTokenType.GTGTGT) {
        createSpaceInCode(mySettings.SPACE_AROUND_SHIFT_OPERATORS);
      }
      else {
        createSpaceInCode(false);
      }
    }
  }

  @Override
  public void visitField(PsiField field) {
    if (myChild1.getElementType() == JavaDocElementType.DOC_COMMENT) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      return;
    }

    if (myRole1 == ChildRole.INITIALIZER_EQ || myRole2 == ChildRole.INITIALIZER_EQ) {
      createSpaceInCode(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    }
    else if (myRole1 == ChildRole.TYPE || myRole2 == ChildRole.TYPE) {
      createSpaceInCode(true);
    }
    else if (myChild2.getElementType() == JavaTokenType.SEMICOLON) {
      createSpaceProperty(false, false, 0);
    }
    else if (myRole1 == ChildRole.MODIFIER_LIST) {
      createSpaceProperty(true, false, 0);
    }
  }

  @Override
  public void visitLocalVariable(PsiLocalVariable variable) {
    if (myRole1 == ChildRole.INITIALIZER_EQ || myRole2 == ChildRole.INITIALIZER_EQ) {
      createSpaceInCode(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    }
    else if (isFinalKeywordBefore(myChild2) && myType2 == JavaElementType.TYPE) {
      myResult = Spacing.createSpacing(1, 1, 0, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (myRole1 == ChildRole.MODIFIER_LIST
             || myRole2 == ChildRole.TYPE_REFERENCE
             || myRole1 == ChildRole.TYPE_REFERENCE
             || myRole2 == ChildRole.TYPE
             || myRole1 == ChildRole.TYPE) {
      createSpaceInCode(true);
    }
    else if (myChild2.getElementType() == JavaTokenType.SEMICOLON) {
      final PsiElement pp = myParent.getParent();
      if (pp instanceof PsiDeclarationStatement) {
        final PsiElement ppp = pp.getParent();
        if (ppp instanceof PsiForStatement) {
          createSpaceInCode(mySettings.SPACE_BEFORE_SEMICOLON);
          return;
        }
      }

      createSpaceProperty(false, false, 0);
    }
  }

  private static boolean isFinalKeywordBefore(ASTNode node) {
    ASTNode prevLeaf = TreeUtil.prevLeaf(node);
    if (prevLeaf != null && prevLeaf.getElementType() == TokenType.WHITE_SPACE) {
      prevLeaf = TreeUtil.prevLeaf(prevLeaf);
    }
    return prevLeaf != null && prevLeaf.getElementType() == JavaTokenType.FINAL_KEYWORD;
  }

  @Override
  public void visitMethod(PsiMethod method) {
    if (myChild1.getElementType() == JavaDocElementType.DOC_COMMENT) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      return;
    }


    if (myRole2 == ChildRole.PARAMETER_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_PARENTHESES);
    }
    else if (myRole1 == ChildRole.PARAMETER_LIST && myRole2 == ChildRole.THROWS_LIST || myRole1 == ChildRole.TYPE_PARAMETER_LIST) {
      createSpaceInCode(true);
    }
    else if (myRole2 == ChildRole.METHOD_BODY) {
      myResult = getSpaceBeforeMethodLBrace(method);
    }
    else if (myRole1 == ChildRole.MODIFIER_LIST) {
      processModifierList();
    }
    else if (StdTokenSets.COMMENT_BIT_SET.contains(myChild1.getElementType())
             && (myRole2 == ChildRole.MODIFIER_LIST || myRole2 == ChildRole.TYPE_REFERENCE)) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, 0);
    }
    else if (myRole2 == ChildRole.DEFAULT_KEYWORD || myRole2 == ChildRole.ANNOTATION_DEFAULT_VALUE) {
      createSpaceInCode(true);
    }
    else if (myChild2.getElementType() == JavaTokenType.SEMICOLON) {
      createSpaceInCode(false);
    }
    else if (myRole1 == ChildRole.TYPE) {
      createSpaceInCode(true);
    }
  }

  private void processModifierList() {
    if (mySettings.MODIFIER_LIST_WRAP) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (myRole1 == ChildRole.MODIFIER_LIST && myRole2 == ChildRole.PACKAGE_KEYWORD) {
      myResult = Spacing.createSpacing(1, 1, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (myRole2 == ChildRole.TYPE
             && myChild1.getLastChildNode() != null
             && myChild1.getLastChildNode().getElementType() == JavaElementType.ANNOTATION
             || myRole1 == ChildRole.MODIFIER_LIST && myRole2 == ChildRole.RBRACE) {
      createSpaceProperty(true, mySettings.KEEP_LINE_BREAKS, 0);
    }
    else {
      createSpaceProperty(true, false, 0);
    }
  }

  @Override
  public void visitModifierList(PsiModifierList list) {
    if (myType1 == JavaElementType.ANNOTATION && myType2 == JavaTokenType.FINAL_KEYWORD) {
      myResult = Spacing.createSpacing(1, 1, 0, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (myType1 == JavaTokenType.END_OF_LINE_COMMENT) {
      myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      myResult = Spacing.createSpacing(1, 1, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  @Override
  public void visitParameterList(PsiParameterList list) {
    if (myRole1 == ChildRole.LPARENTH && myRole2 == ChildRole.RPARENTH) {
      createParenthSpace(mySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_EMPTY_METHOD_PARENTHESES);
    }
    else if (myRole2 == ChildRole.RPARENTH) {
      createParenthSpace(mySettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_METHOD_PARENTHESES);
    }
    else if (myRole2 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COMMA);
    }
    else if (myRole1 == ChildRole.LPARENTH) {
      createParenthSpace(mySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_METHOD_PARENTHESES);
    }
    else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_AFTER_COMMA);
    }
  }

  private void createParenthSpace(final boolean onNewLine, final boolean space) {
    createParenthSpace(onNewLine, space, myParent.getTextRange());
  }

  private void createParenthSpace(final boolean onNewLine, final boolean space, final TextRange dependence) {
    if (onNewLine) {
      final int spaces = space ? 1 : 0;
      myResult = Spacing.createDependentLFSpacing(spaces, spaces, dependence, mySettings.KEEP_LINE_BREAKS,
                                                  mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      createSpaceInCode(space);
    }
  }

  @Override
  public void visitElement(PsiElement element) {
    if (myRole1 == ChildRole.MODIFIER_LIST) {
      processModifierList();
    }
    else if (myRole1 == ChildRole.OPERATION_SIGN) {
      createSpaceInCode(mySettings.SPACE_AROUND_UNARY_OPERATOR);
    }
    else if ((myType1 == JavaDocTokenType.DOC_TAG_VALUE_TOKEN || myType1 == JavaDocElementType.DOC_TAG_VALUE_ELEMENT) &&
             (myType2 == JavaDocTokenType.DOC_TAG_VALUE_TOKEN || myType2 == JavaDocElementType.DOC_TAG_VALUE_ELEMENT)) {
      createSpaceInCode(true);
    }
    else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_AFTER_COMMA);
    }
    else if (myRole2 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COMMA);
    }
  }

  @Override
  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    if (myRole1 == ChildRole.TYPE && myRole2 == ChildRole.DOT
        || myRole1 == ChildRole.DOT && myRole2 == ChildRole.CLASS_KEYWORD)
    {
      createSpaceInCode(false);
    }
  }

  @Override
  public void visitExpressionList(PsiExpressionList list) {
    if (myRole1 == ChildRole.LPARENTH && myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES);
    }
    else if (myRole2 == ChildRole.RPARENTH) {
      boolean space = myRole1 == ChildRole.COMMA || mySettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES;
      if (mySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE && list.getExpressions().length > 1) {
        createSpaceWithLinefeedIfListWrapped(list, space);
        return;
      }
      createSpaceInCode(space);
    }
    else if (myRole1 == ChildRole.LPARENTH) {
      boolean space = mySettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES;
      if (mySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE && list.getExpressions().length > 1) {
        createSpaceWithLinefeedIfListWrapped(list, space);
        return;
      }
      createSpaceInCode(space);
    }
    else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_AFTER_COMMA);
    }
    else if (myRole2 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COMMA);
    }
  }

  private void createSpaceWithLinefeedIfListWrapped(@NotNull PsiExpressionList list, boolean space) {
    PsiExpression[] expressions = list.getExpressions();
    int length = expressions.length;
    assert length > 1;

    List<TextRange> ranges = ContainerUtil.newArrayList();
    for (int i = 0; i < length - 1; i++) {
      int startOffset = expressions[i].getTextRange().getEndOffset();
      int endOffset = expressions[i + 1].getTextRange().getStartOffset();
      ranges.add(new TextRange(startOffset, endOffset));
    }

    int spaces = space ? 1 : 0;
    myResult = Spacing.createDependentLFSpacing(spaces, spaces, ranges, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  @Override
  public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    if (myRole1 == ChildRole.SYNCHRONIZED_KEYWORD || myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES);
    }
    else if (myRole2 == ChildRole.BLOCK) {
      myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_SYNCHRONIZED_LBRACE, null);
    }
  }

  @Override
  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    if (myRole1 == ChildRole.CASE_KEYWORD || myRole2 == ChildRole.CASE_EXPRESSION) {
      createSpaceProperty(true, false, 0);
    }
  }

  @Override
  public void visitSwitchStatement(PsiSwitchStatement statement) {
    if (myRole1 == ChildRole.SWITCH_KEYWORD && myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SWITCH_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SWITCH_PARENTHESES);
    }
    else if (myRole2 == ChildRole.SWITCH_BODY) {
      myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_SWITCH_LBRACE, null);
    }
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    boolean spaceAroundArrow = mySettings.SPACE_AROUND_LAMBDA_ARROW;

    if (myRole1 == ChildRole.PARAMETER_LIST && myRole2 == ChildRole.ARROW) {
      createSpaceInCode(spaceAroundArrow);
    }
    else if (myRole1 == ChildRole.ARROW) {
      if (myRole2 == ChildRole.LBRACE) {
        switch (mySettings.LAMBDA_BRACE_STYLE) {
          case NEXT_LINE:
          case NEXT_LINE_SHIFTED:
          case NEXT_LINE_SHIFTED2:
            int space = spaceAroundArrow ? 1 : 0;
            myResult = Spacing.createSpacing(space, space, 1, mySettings.KEEP_LINE_BREAKS, 0);
            break;
          default:
            createSpaceInCode(spaceAroundArrow);
        }
      }
      else if (myRole2 == ChildRole.EXPRESSION) {
        createSpaceInCode(spaceAroundArrow);
      }
    }
  }

  @Override
  public void visitModule(PsiJavaModule module) {
    if (myType2 == JavaTokenType.RBRACE || ElementType.JAVA_MODULE_STATEMENT_BIT_SET.contains(myType2)) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (myType1 == JavaElementType.MODULE_REFERENCE || myType2 == JavaElementType.MODULE_REFERENCE) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitModuleStatement(PsiStatement statement) {
    if (myType1 == JavaElementType.MODULE_REFERENCE) {
      createSpaceInCode(myType2 != JavaTokenType.SEMICOLON && myType2 != JavaTokenType.COMMA);
    }
    if (myType2 == JavaElementType.MODULE_REFERENCE) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    if ((myRole1 == ChildRole.DOUBLE_COLON && myRole2 == ChildRole.REFERENCE_NAME) ||
        (myRole1 == ChildRole.EXPRESSION && myRole2 == ChildRole.DOUBLE_COLON)) {
      createSpaceInCode(mySettings.SPACE_AROUND_METHOD_REF_DBL_COLON);
    }
  }

  @Override
  public void visitForStatement(PsiForStatement statement) {
    if (myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_FOR_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH) {
      ASTNode rparenth = findFrom(myChild2, JavaTokenType.RPARENTH, true);
      if (rparenth == null) {
        createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
      }
      else {
        createParenthSpace(mySettings.FOR_STATEMENT_LPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_FOR_PARENTHESES,
                           new TextRange(myChild1.getTextRange().getStartOffset(), rparenth.getTextRange().getEndOffset()));
        if (myChild2.getElementType() == JavaElementType.EMPTY_STATEMENT) {
          createSpaceInCode(mySettings.SPACE_BEFORE_SEMICOLON || mySettings.SPACE_WITHIN_FOR_PARENTHESES);
        }
      }
    }
    else if (myRole2 == ChildRole.RPARENTH) {
      ASTNode lparenth = findFrom(myChild2, JavaTokenType.LPARENTH, false);
      if (lparenth == null) {
        createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
      }
      else {
        ASTNode prev = FormatterUtil.getPreviousLeaf(myChild2, TokenType.WHITE_SPACE, TokenType.ERROR_ELEMENT);
        if (prev != null && prev.getElementType() == JavaTokenType.SEMICOLON) {
          // Handle empty 'initialization' or 'condition' section.
          createSpaceInCode(mySettings.SPACE_AFTER_SEMICOLON);
        }
        else {
          createParenthSpace(mySettings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_FOR_PARENTHESES,
                             new TextRange(lparenth.getTextRange().getStartOffset(), myChild2.getTextRange().getEndOffset()));
        }
      }
    }
    else if (myRole1 == ChildRole.FOR_INITIALIZATION) {
      createSpaceInCode(mySettings.SPACE_AFTER_SEMICOLON);
    }
    else if (myRole1 == ChildRole.CONDITION) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SEMICOLON);
    }
    else if (myRole1 == ChildRole.FOR_SEMICOLON) {
      createSpaceInCode(mySettings.SPACE_AFTER_SEMICOLON);
    }
    else if (myRole2 == ChildRole.LOOP_BODY || myChild2.getElementType() == JavaElementType.CODE_BLOCK) {
      processLoopBody();
    }
  }

  protected void processLoopBody() {
    if (myChild2.getElementType() == JavaElementType.BLOCK_STATEMENT) {
      myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_FOR_LBRACE, null);
    }
    else if (mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE) {
      myResult = Spacing.createDependentLFSpacing(1, 1, myParent.getTextRange(), false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      myResult = Spacing.createSpacing(0, 0, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  @Nullable
  private static ASTNode findFrom(ASTNode current, final IElementType expected, boolean forward) {
    while (current != null) {
      if (current.getElementType() == expected) return current;
      current = forward ? current.getTreeNext() : current.getTreePrev();
    }
    return null;
  }

  @Override
  public void visitCatchSection(PsiCatchSection section) {
    if (myRole2 == ChildRole.CATCH_BLOCK) {
      myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_CATCH_LBRACE, null);
    }
    else if (myRole2 == ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_CATCH_PARENTHESES);
    }
    else if (myRole1 == ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH || myRole2 == ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_CATCH_PARENTHESES);
    }
  }

  @Override
  public void visitResourceList(final PsiResourceList resourceList) {
    if (myType1 == JavaTokenType.LPARENTH && myType2 == JavaTokenType.RPARENTH) {
      createParenthSpace(mySettings.RESOURCE_LIST_RPAREN_ON_NEXT_LINE, false);
    }
    else if (myType1 == JavaTokenType.LPARENTH) {
      createParenthSpace(mySettings.RESOURCE_LIST_LPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_TRY_PARENTHESES);
    }
    else if (myType2 == JavaTokenType.RPARENTH) {
      createParenthSpace(mySettings.RESOURCE_LIST_RPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_TRY_PARENTHESES);
    }
    else if (myType1 == JavaTokenType.SEMICOLON) {
      createSpaceInCode(mySettings.SPACE_AFTER_SEMICOLON);
    }
    else if (myType2 == JavaTokenType.SEMICOLON) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SEMICOLON);
    }
  }

  @Override
  public void visitReferenceParameterList(PsiReferenceParameterList list) {
    if (myRole1 == ChildRole.LT_IN_TYPE_LIST && myRole2 == ChildRole.GT_IN_TYPE_LIST
        || myRole1 == ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST && myRole2 == ChildRole.COMMA) {
      createSpaceInCode(false);
    }
    else if (myRole1 == ChildRole.LT_IN_TYPE_LIST && myRole2 == ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST) {
      createSpaceInCode(myJavaSettings.SPACES_WITHIN_ANGLE_BRACKETS);
    }
    else if (myRole1 == ChildRole.COMMA && myRole2 == ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST) {
      createSpaceInCode(mySettings.SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS);
    }
    else if (myRole2 == ChildRole.GT_IN_TYPE_LIST) {
      createSpaceInCode(myJavaSettings.SPACES_WITHIN_ANGLE_BRACKETS);
    }
  }

  @Override
  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_CAST_PARENTHESES);
    }
    else if (myRole1 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_AFTER_TYPE_CAST);
    }
  }

  private void createSpaceProperty(boolean space, int keepBlankLines) {
    createSpaceProperty(space, mySettings.KEEP_LINE_BREAKS, keepBlankLines);
  }

  private void createSpaceProperty(boolean space, boolean keepLineBreaks, final int keepBlankLines) {
    final ASTNode prev = getPrevElementType(myChild2);
    if (prev != null && prev.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      if (!space && !canStickChildrenTogether(myChild1, myChild2)) {
        space = true;
      }
      myResult = Spacing.createSpacing(space ? 1 : 0, space ? 1 : 0, 0, keepLineBreaks, keepBlankLines);
    }
  }

  @Override
  public void visitReferenceList(PsiReferenceList list) {
    if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(true);
    }
    else if (myRole2 == ChildRole.COMMA) {
      createSpaceInCode(false);
    }
    else if (myRole1 == ChildRole.AMPERSAND_IN_BOUNDS_LIST || myRole2 == ChildRole.AMPERSAND_IN_BOUNDS_LIST) {
      createSpaceInCode(myJavaSettings.SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS);
    }
    else if (REF_LIST_KEYWORDS.contains(myType1) || REF_LIST_KEYWORDS.contains(myType2)) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
  }

  @Override
  public void visitConditionalExpression(PsiConditionalExpression expression) {
    if (myRole2 == ChildRole.QUEST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_QUEST);
    }
    else if (myRole1 == ChildRole.QUEST) {
      createSpaceInCode(mySettings.SPACE_AFTER_QUEST);
    }
    else if (myRole2 == ChildRole.COLON) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COLON);
    }
    else if (myRole1 == ChildRole.COLON) {
      createSpaceInCode(mySettings.SPACE_AFTER_COLON);
    }
  }

  @Override
  public void visitStatement(PsiStatement statement) {
    if (myRole2 == ChildRole.CLOSING_SEMICOLON) {
      createSpaceInCode(false);
    }

    if (statement instanceof JspClassLevelDeclarationStatement) {
      processClassBody();
    }
  }

  @Override
  public void visitReturnStatement(PsiReturnStatement statement) {
    if (myChild2.getElementType() == JavaTokenType.SEMICOLON) {
      createSpaceInCode(false);
    }
    else if (myRole1 == ChildRole.RETURN_KEYWORD) {
      createSpaceInCode(true);
    }
    else {
      super.visitReturnStatement(statement);
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    if (myRole2 == ChildRole.ARGUMENT_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    }
  }

  @Override
  public void visitTypeParameter(PsiTypeParameter classParameter) {
    createSpaceInCode(true);
  }

  @Override
  public void visitTypeElement(PsiTypeElement type) {
    if (myType1 == JavaElementType.ANNOTATION || myType2 == JavaElementType.ANNOTATION) {
      createSpaceInCode(true);
    }
    else if (myType2 == JavaTokenType.ELLIPSIS || myType2 == JavaTokenType.LBRACKET || myType2 == JavaTokenType.RBRACKET) {
      createSpaceInCode(false);
    }
    else if (type.getType() instanceof PsiDisjunctionType) {
      createSpaceInCode(mySettings.SPACE_AROUND_BITWISE_OPERATORS);
    }
    else {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitDeclarationStatement(PsiDeclarationStatement declarationStatement) {
    if (myRole2 == ChildRole.COMMA) {
      createSpaceProperty(false, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitTypeParameterList(PsiTypeParameterList list) {
    if (myRole1 == ChildRole.LT_IN_TYPE_LIST || myRole2 == ChildRole.GT_IN_TYPE_LIST) {
      createSpaceInCode(myJavaSettings.SPACES_WITHIN_ANGLE_BRACKETS);
    }
    else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_AFTER_COMMA);
    }
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    if (myRole1 == ChildRole.REFERENCE_PARAMETER_LIST && myRole2 == ChildRole.REFERENCE_NAME) {
      createSpaceInCode(myJavaSettings.SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT);
    }
    else if (myRole2 == ChildRole.REFERENCE_PARAMETER_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_TYPE_PARAMETER_LIST);
    }
    else if (myRole2 == ChildRole.DOT) {
      createSpaceInCode(false);
    }
    else if (myType1 == JavaElementType.ANNOTATION) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitAnnotation(PsiAnnotation annotation) {
    if (myRole2 == ChildRole.PARAMETER_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_ANOTATION_PARAMETER_LIST);
    }
    else if (myChild1.getElementType() == JavaTokenType.AT && myChild2.getElementType() == JavaElementType.JAVA_CODE_REFERENCE) {
      createSpaceInCode(false);
    }
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    if (myChild2.getElementType() == JavaElementType.CODE_BLOCK) {
      myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_METHOD_LBRACE, null);
    }
  }

  @Override
  public void visitAnnotationParameterList(PsiAnnotationParameterList list) {
    if (myRole1 == ChildRole.LPARENTH && myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(false);
    }
    // There is a possible case that annotation key-value pair is used in 'shorten' form (with implicit name 'values'). It's also
    // possible that target value is surrounded by curly braces. We want to define child role accordingly then.
    else if (myRole1 == ChildRole.LPARENTH && mySettings.SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE && myRole2 == ChildRole.ANNOTATION_VALUE) {
      createSpaceInCode(true);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_ANNOTATION_PARENTHESES);
    }
    else if (myRole2 == ChildRole.COMMA) {
      createSpaceInCode(false);
    }
    else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitNameValuePair(PsiNameValuePair pair) {
    if (myRole1 == ChildRole.OPERATION_SIGN || myRole2 == ChildRole.OPERATION_SIGN) {
      createSpaceInCode(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    }
  }

  @Override
  public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    visitArrayInitializer();
  }

  private void visitArrayInitializer() {
    if (myRole1 == ChildRole.LBRACE) {
      if (mySettings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE) {
        int spaces;
        if (myRole2 != ChildRole.RBRACE) {
          spaces = mySettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES ? 1 : 0;
        }
        else {
          spaces = mySettings.SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES ? 1 : 0;
        }
        myResult = Spacing.createDependentLFSpacing(spaces, spaces, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS,
                                    mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        boolean addSpace = (myRole2 != ChildRole.RBRACE)
                           ? mySettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES
                           : mySettings.SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES;
        createSpaceProperty(addSpace, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
    else if (myRole2 == ChildRole.LBRACE) {
      createSpaceInCode(mySettings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE);
    }
    else if (myRole2 == ChildRole.RBRACE) {
      if (mySettings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE) {
        int spaces = mySettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES ? 1 : 0;
        myResult = Spacing.createDependentLFSpacing(spaces, spaces, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS,
                                    mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
      else {
        createSpaceProperty(mySettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
    }
    else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_AFTER_COMMA);
    }
    else if (myRole2 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COMMA);
    }
  }

  @Override
  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    if (myRole2 == ChildRole.ARGUMENT_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    }
    else if (myRole2 == ChildRole.ANONYMOUS_CLASS) {
      myResult = getSpaceBeforeLBrace(myChild2, mySettings.SPACE_BEFORE_CLASS_LBRACE, null);
    }
    else if (myRole1 == ChildRole.MODIFIER_LIST && myRole2 == ChildRole.NAME) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitDocTag(PsiDocTag tag) {
    if (myType1 == JavaDocTokenType.DOC_TAG_NAME && myType2 == JavaDocElementType.DOC_TAG_VALUE_ELEMENT) {
      myResult = Spacing.createSpacing(1, 1, 0, false, 0);
    }
  }

  @Override
  public void visitAssertStatement(PsiAssertStatement statement) {
    if (myChild1.getElementType() == JavaTokenType.ASSERT_KEYWORD) {
      createSpaceInCode(true);
    }
    else if (myChild1.getElementType() == JavaTokenType.COLON) {
      createSpaceInCode(mySettings.SPACE_AFTER_COLON);
    }
    else if (myChild2.getElementType() == JavaTokenType.COLON) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COLON);
    }
  }

  @Override
  public void visitParameter(PsiParameter parameter) {
    if (myRole1 == ChildRole.TYPE || myRole1 == ChildRole.MODIFIER_LIST) {
      createSpaceInCode(true);
    }
  }

  @SuppressWarnings({"ConstantConditions"})
  public static Spacing getSpacing(Block node, CommonCodeStyleSettings settings, JavaCodeStyleSettings javaSettings) {
    JavaSpacePropertyProcessor spacePropertyProcessor = mySharedProcessorAllocator.get();
    try {
      if (spacePropertyProcessor == null) {
        spacePropertyProcessor = new JavaSpacePropertyProcessor();
        mySharedProcessorAllocator.set(spacePropertyProcessor);
      }
      spacePropertyProcessor.doInit(node, settings, javaSettings);
      return spacePropertyProcessor.getResult();
    }
    finally {
      spacePropertyProcessor.clear();
    }
  }

  private static boolean isWS(final ASTNode lastChild) {
    return lastChild != null && lastChild.getElementType() == TokenType.WHITE_SPACE;
  }

  private static final Map<Pair<IElementType, IElementType>, Boolean> myCanStickJavaTokensMatrix = ContainerUtil.newConcurrentMap();

  public static boolean canStickChildrenTogether(final ASTNode child1, final ASTNode child2) {
    if (child1 == null || child2 == null) return true;
    if (isWS(child1) || isWS(child2)) return true;

    ASTNode token1 = TreeUtil.findLastLeaf(child1);
    ASTNode token2 = TreeUtil.findFirstLeaf(child2);

    LOG.assertTrue(token1 != null);
    LOG.assertTrue(token2 != null);

    return !(token1.getElementType() instanceof IJavaElementType) ||
           !(token2.getElementType() instanceof IJavaElementType) ||
           canStickJavaTokens(token1, token2);
  }

  private static boolean canStickJavaTokens(ASTNode token1, ASTNode token2) {
    IElementType type1 = token1.getElementType();
    IElementType type2 = token2.getElementType();

    Pair<IElementType, IElementType> pair = Pair.create(type1, type2);
    Boolean res = myCanStickJavaTokensMatrix.get(pair);
    if (res == null) {
      Lexer lexer = JavaParserDefinition.createLexer(LanguageLevel.HIGHEST);

      TokenCheckResult res1 = checkToken(token1, lexer), res2 = checkToken(token2, lexer);
      if (res1 == TokenCheckResult.INCORRECT || res2 == TokenCheckResult.INCORRECT) return true;

      if (res1 == TokenCheckResult.RESTRICTED_KEYWORD || type1 == JavaTokenType.IDENTIFIER && res2 == TokenCheckResult.RESTRICTED_KEYWORD) {
        res = false;
      }
      else {
        lexer.start(token1.getText() + token2.getText());
        boolean canMerge = lexer.getTokenType() == type1;
        lexer.advance();
        canMerge &= lexer.getTokenType() == type2;
        res = canMerge;
      }

      myCanStickJavaTokensMatrix.put(pair, res);
    }
    return res.booleanValue();
  }

  private enum TokenCheckResult {OK, INCORRECT, RESTRICTED_KEYWORD}

  private static TokenCheckResult checkToken(ASTNode token, Lexer lexer) {
    lexer.start(token.getText());
    if (lexer.getTokenType() != token.getElementType()) {
      boolean kw = lexer.getTokenType() == JavaTokenType.IDENTIFIER && ElementType.KEYWORD_BIT_SET.contains(token.getElementType());
      return kw ? TokenCheckResult.RESTRICTED_KEYWORD : TokenCheckResult.INCORRECT;
    }
    lexer.advance();
    return lexer.getTokenType() == null ? TokenCheckResult.OK : TokenCheckResult.INCORRECT;
  }
}