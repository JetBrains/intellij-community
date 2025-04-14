// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.JavaBinaryOperations;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.tree.ParentProviderElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.JavaTokenType.JAVA_TOKEN_TYPE_SET;
import static com.intellij.psi.JavaTokenType.RPARENTH;
import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_KEYWORD_BIT_SET;
import static com.intellij.psi.impl.source.BasicJavaDocElementType.BASIC_DOC_COMMENT;
import static com.intellij.psi.impl.source.BasicJavaDocElementType.BASIC_DOC_SNIPPET_ATTRIBUTE_VALUE;
import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public final class BasicJavaAstTreeUtil {
  private BasicJavaAstTreeUtil() { }

  @Contract("null ,_ -> false")
  public static boolean is(@Nullable ASTNode element, @Nullable IElementType iElementType) {
    boolean notNull = element != null && iElementType != null;
    if (!notNull) {
      return false;
    }
    IElementType sourceElementType = element.getElementType();
    return is(sourceElementType, iElementType);
  }

  public static boolean is(@NotNull IElementType source, @NotNull IElementType target) {
    if (source == target) {
      return true;
    }
    if (source instanceof ParentProviderElementType) {
      Set<IElementType> parents = ((ParentProviderElementType)source).getParents();
      return ContainerUtil.exists(parents, parent -> parent != null && is(parent, target));
    }
    return false;
  }

  @Contract("null ,_ -> false")
  public static boolean is(@Nullable ASTNode element, @NotNull Set<IElementType> iElementTypes) {
    boolean isNotNull = element != null;
    if (!isNotNull) {
      return false;
    }
    return is(element.getElementType(), iElementTypes);
  }

  //needs for FrontBackJavaTokenSet
  public static boolean is(@Nullable ASTNode element, @NotNull TokenSet tokenSet) {
    boolean isNotNull = element != null;
    if (!isNotNull) {
      return false;
    }
    return ParentProviderElementType.containsWithSourceParent(element.getElementType(), tokenSet);
  }

  public static boolean is(@Nullable ASTNode element, @NotNull ParentAwareTokenSet tokenSet) {
    boolean isNotNull = element != null;
    if (!isNotNull) {
      return false;
    }
    return is(element.getElementType(), tokenSet);
  }

  private static boolean is(@NotNull IElementType source, @NotNull ParentAwareTokenSet tokenSet) {
    //not call iteratively!
    if (tokenSet.contains(source)) {
      return true;
    }
    return false;
  }

  private static boolean is(@NotNull IElementType source, @NotNull Set<IElementType> tokenSet) {
    if (tokenSet.contains(source)) {
      return true;
    }
    if (source instanceof ParentProviderElementType) {
      Set<IElementType> parents = ((ParentProviderElementType)source).getParents();
      return ContainerUtil.exists(parents, parent -> parent != null && is(parent, tokenSet));
    }
    return false;
  }

  public static boolean is(@Nullable ASTNode element, IElementType... iElementTypes) {
    return is(element, ParentAwareTokenSet.create(iElementTypes));
  }

  public static List<ASTNode> getChildren(@Nullable ASTNode element) {
    ArrayList<ASTNode> results = new ArrayList<>();
    if (element == null) {
      return results;
    }
    for (ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      results.add(child);
    }
    return results;
  }

  public static @Nullable ASTNode getReferenceNameElement(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    IElementType type = element.getElementType();
    if (is(type, BASIC_IMPORT_STATIC_REFERENCE)) {
      return findChildByType(element, JavaTokenType.IDENTIFIER);
    }
    else if (is(type, BASIC_JAVA_CODE_REFERENCE)) {
      return TreeUtil.findChildBackward(element, JavaTokenType.IDENTIFIER);
    }
    else if (is(type, BASIC_METHOD_REF_EXPRESSION)) {
      final ASTNode lastChild = element.getLastChildNode();
      return is(lastChild, JavaTokenType.IDENTIFIER) || is(lastChild, JavaTokenType.NEW_KEYWORD) ? lastChild : null;
    }
    else if (is(type, BASIC_REFERENCE_EXPRESSION)) {
      ASTNode lastChild = element.getLastChildNode();
      return lastChild == null || is(lastChild,
                                     ContainerUtil.newHashSet(JavaTokenType.IDENTIFIER, JavaTokenType.THIS_KEYWORD,
                                                              JavaTokenType.SUPER_KEYWORD)) ?
             lastChild : findChildByType(element, JavaTokenType.IDENTIFIER);
    }
    return null;
  }


  public static @Nullable ASTNode findChildByType(@Nullable ASTNode astNode, IElementType... targets) {
    return findChildByType(astNode, ParentAwareTokenSet.create(targets));
  }

  public static @Nullable ASTNode findChildByType(@Nullable ASTNode astNode, Collection<IElementType> targets) {
    return findChildByType(astNode, ParentAwareTokenSet.create(targets));
  }

  public static @Nullable ASTNode findChildByType(@Nullable ASTNode astNode, ParentAwareTokenSet targets) {
    if (astNode == null) {
      return null;
    }
    for (ASTNode child = astNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      IElementType elementType = child.getElementType();
      if (is(elementType, targets)) return child;
    }
    return null;
  }

  public static @Nullable ASTNode getElseElement(@NotNull ASTNode element) {
    if (!is(element, BASIC_IF_STATEMENT)) {
      return null;
    }
    return findChildByType(element, JavaTokenType.ELSE_KEYWORD);
  }

  public static @Nullable ASTNode getThenBranch(@NotNull ASTNode element) {
    if (!is(element, BASIC_IF_STATEMENT)) {
      return null;
    }
    for (ASTNode child : getChildren(element)) {
      if (is(child, STATEMENT_SET)) {
        return child;
      }
    }
    return null;
  }

  public static ASTNode getElseBranch(@Nullable ASTNode element) {
    if (!is(element, BASIC_IF_STATEMENT)) {
      return null;
    }
    ASTNode elseKeyword = getElseElement(element);
    if (elseKeyword == null) return null;
    for (ASTNode child = elseKeyword.getTreeNext(); child != null; child = child.getTreeNext()) {
      if (is(child, STATEMENT_SET)) return child;
    }
    return null;
  }

  public static @Nullable ASTNode getLParenth(@Nullable ASTNode element) {
    return findChildByType(element, JavaTokenType.LPARENTH);
  }

  public static @Nullable ASTNode getRParenth(@Nullable ASTNode element) {
    return findChildByType(element, RPARENTH);
  }

  public static @Nullable ASTNode getFinallyBlock(@Nullable ASTNode element) {
    if (!is(element, BASIC_TRY_STATEMENT)) {
      return null;
    }
    ASTNode finallyKeyword = findChildByType(element, JavaTokenType.FINALLY_KEYWORD);
    if (finallyKeyword == null) return null;
    for (ASTNode child = finallyKeyword.getTreeNext(); child != null; child = child.getTreeNext()) {
      if (is(child, BASIC_CODE_BLOCK)) {
        return child;
      }
    }
    return null;
  }

  public static @Nullable ASTNode getNameIdentifier(@Nullable ASTNode element) {
    return findChildByType(element, JavaTokenType.IDENTIFIER);
  }

  public static @Nullable ASTNode getInitializer(@Nullable ASTNode element) {
    return findChildByType(element, EXPRESSION_SET);
  }


  public static @Nullable ASTNode getDocComment(@Nullable ASTNode element) {
    return findChildByType(element, BASIC_DOC_COMMENT);
  }

  public static @Nullable ASTNode getTypeElement(@Nullable ASTNode element) {
    return findChildByType(element, BASIC_TYPE);
  }

  public static @Nullable ASTNode getModifierList(@Nullable ASTNode element) {
    return findChildByType(element, BASIC_MODIFIER_LIST);
  }

  public static @Nullable ASTNode getLBrace(@Nullable ASTNode element) {
    return findChildByType(element, JavaTokenType.LBRACE);
  }

  public static @Nullable ASTNode getRBrace(@Nullable ASTNode element) {
    return findChildByType(element, JavaTokenType.RBRACE);
  }

  public static boolean isJavaToken(@Nullable ASTNode element) {
    if (element == null) {
      return false;
    }
    return toPsi(element) instanceof PsiJavaToken ||
           is(element, JavaTokenType.IDENTIFIER) ||
           isKeyword(element) ||
           JAVA_TOKEN_TYPE_SET.contains(element.getElementType());
  }

  public static boolean isKeyword(@Nullable ASTNode element) {
    return is(element, BASIC_KEYWORD_BIT_SET);
  }

  public static boolean isWhiteSpace(@Nullable ASTNode element) {
    return is(element, TokenType.WHITE_SPACE);
  }

  public static @Nullable ASTNode skipSiblingsBackward(@Nullable ASTNode element,
                                                       IElementType... elementClasses) {
    if (element != null) {
      for (ASTNode e = element.getTreePrev(); e != null; e = e.getTreePrev()) {
        if (!is(e, elementClasses)) {
          return e;
        }
      }
    }
    return null;
  }

  public static @Nullable ASTNode skipSiblingsForward(@Nullable ASTNode element,
                                                      IElementType... elementClasses) {
    if (element != null) {
      for (ASTNode e = element.getTreeNext(); e != null; e = e.getTreeNext()) {
        if (!is(e, elementClasses)) {
          return e;
        }
      }
    }
    return null;
  }

  public static @Nullable PsiElement toPsi(@Nullable ASTNode astNode) {
    return SourceTreeToPsiMap.treeElementToPsi(astNode);
  }

  public static @Nullable ASTNode toNode(@Nullable PsiElement psiElement) {
    if (psiElement == null) {
      return null;
    }
    return psiElement.getNode();
  }

  public static @Nullable ASTNode getParentOfType(@Nullable ASTNode e, @NotNull ParentAwareTokenSet set) {
    if (e == null) {
      return null;
    }
    return findParent(e, set);
  }

  private static @Nullable ASTNode findParent(@NotNull ASTNode element, @NotNull IElementType type) {
    for (ASTNode parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()) {
      if (is(parent.getElementType(), type)) return parent;
    }
    return null;
  }

  private static @Nullable ASTNode findParent(@NotNull ASTNode element, @NotNull ParentAwareTokenSet type) {
    for (ASTNode parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()) {
      if (is(parent.getElementType(), type)) return parent;
    }
    return null;
  }


  public static @Nullable ASTNode getParentOfType(@Nullable ASTNode e, @NotNull IElementType elementType) {
    if (e == null) {
      return null;
    }
    return findParent(e, elementType);
  }

  public static @Nullable ASTNode getMethodExpression(@Nullable ASTNode element) {
    if (!is(element, BASIC_METHOD_CALL_EXPRESSION)) {
      return null;
    }
    return element.getFirstChildNode();
  }

  public static @Nullable ASTNode getExpressionList(@Nullable ASTNode element) {
    return findChildByType(element, BASIC_EXPRESSION_LIST);
  }

  public static @Nullable ASTNode getTypeParameterList(@Nullable ASTNode element) {
    return findChildByType(element, BASIC_TYPE_PARAMETER_LIST);
  }

  public static boolean isComment(@Nullable ASTNode element) {
    return is(element, BasicElementTypes.BASIC_JAVA_COMMENT_BIT_SET);
  }

  public static boolean isDocToken(@Nullable ASTNode element) {
    return
      element != null &&
      element.getElementType().getClass().equals(IJavaDocElementType.class) &&
      !is(element, BASIC_DOC_SNIPPET_ATTRIBUTE_VALUE);
  }

  public static @Nullable String getTagName(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    ASTNode docTagName = findChildByType(element, JavaDocTokenType.DOC_TAG_NAME);
    if (docTagName == null) {
      return null;
    }
    String text = docTagName.getText();
    if (text.isEmpty()) {
      return null;
    }
    return text.substring(1);
  }

  public static @Nullable ASTNode getROperand(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    if (!is(element, BASIC_BINARY_EXPRESSION)) {
      return null;
    }
    return findChildByType(element, EXPRESSION_SET);
  }

  public static @Nullable ASTNode getBlock(@Nullable ASTNode statement) {
    if (statement == null) return null;
    return findChildByType(statement, STATEMENT_SET);
  }

  public static @Nullable ASTNode getAnonymousClass(@Nullable ASTNode expression) {
    if (expression == null) return null;
    return findChildByType(expression, BASIC_ANONYMOUS_CLASS);
  }


  @Contract("null, _, _, _ -> null")
  public static @Nullable PsiElement getParentOfType(@Nullable PsiElement element,
                                                     @NotNull IElementType target,
                                                     boolean strict,
                                                     @Nullable ParentAwareTokenSet stopAt) {
    if (element == null) return null;
    if (strict) {
      if (element instanceof PsiFile) return null;
      element = element.getParent();
    }

    while (element != null && !is(element.getNode(), target)) {
      if (stopAt != null && is(element.getNode(), stopAt)) return null;
      if (element instanceof PsiFile) return null;
      element = element.getParent();
    }

    return element;
  }

  public static @Nullable ASTNode findElementInRange(@NotNull PsiFile file, int startOffset, int endOffset, @NotNull IElementType elementType) {
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, JavaLanguage.INSTANCE);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, JavaLanguage.INSTANCE);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.getViewProvider().findElementAt(startOffset, JavaLanguage.INSTANCE);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.getViewProvider().findElementAt(endOffset - 1, JavaLanguage.INSTANCE);
    }
    if (element2 == null || element1 == null) return null;
    final PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    if (commonParent == null) {
      return null;
    }
    ASTNode node = commonParent.getNode();
    final ASTNode element = is(node, elementType) ? node : getParentOfType(node, elementType);
    if (element == null || element.getTextRange().getStartOffset() != startOffset || element.getTextRange().getEndOffset() != endOffset) {
      return null;
    }
    return element;
  }

  public static @Nullable ASTNode getForInitialization(@Nullable ASTNode forStatement) {

    if (!is(forStatement, BASIC_FOR_STATEMENT)) {
      return null;
    }
    ASTNode initialization = findChildByType(forStatement, STATEMENT_SET);
    if (initialization == null) {
      return null;
    }
    // should be inside parens
    ASTNode paren = getLParenth(forStatement);
    if (paren == null) {
      return null;
    }
    for (ASTNode child = paren; child != null; child = child.getTreeNext()) {
      if (child == initialization) return initialization;
      if (is(child, RPARENTH)) return null;
    }
    return null;
  }

  @Contract(pure = true)
  public static @Nullable ASTNode findElementOfClassAtOffset(@NotNull PsiFile file,
                                                             int offset,
                                                             @NotNull IElementType elementType,
                                                             boolean strictStart) {
    PsiElement result = null;
    for (PsiElement root : file.getViewProvider().getAllFiles()) {
      PsiElement elementAt = root.findElementAt(offset);
      if (elementAt != null) {
        PsiElement parent = getParentOfType(elementAt, elementType, strictStart, null);
        if (parent != null) {
          TextRange range = parent.getTextRange();
          if (!strictStart || range.getStartOffset() == offset) {
            if (result == null || result.getTextRange().getEndOffset() > range.getEndOffset()) {
              result = parent;
            }
          }
        }
      }
    }
    return result == null ? null : result.getNode();
  }

  public static @Nullable ASTNode getParentOfType(@Nullable ASTNode e, @NotNull ParentAwareTokenSet types, boolean strict) {
    if (!strict && is(e, types)) {
      return e;
    }
    return getParentOfType(e, types);
  }

  public static @Nullable PsiElement getParentOfType(@Nullable PsiElement e, @NotNull Set<IElementType> types, boolean strict) {
    if (!strict && e != null && is(e.getNode(), types)) {
      return e;
    }
    if (e == null) {
      return null;
    }
    return toPsi(getParentOfType(e.getNode(), ParentAwareTokenSet.create(types)));
  }

  public static @Nullable ASTNode getCatchBlock(@Nullable ASTNode element) {
    if (!is(element, BASIC_CATCH_SECTION)) {
      return null;
    }
    return findChildByType(element, BASIC_CODE_BLOCK);
  }

  public static @Nullable ASTNode getParameter(@Nullable ASTNode element) {
    return findChildByType(element, BASIC_PARAMETER);
  }

  public static @Nullable ASTNode getWhileKeyword(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    return findChildByType(element, JavaTokenType.WHILE_KEYWORD);
  }

  public static @Nullable ASTNode getWhileCondition(@Nullable ASTNode element) {
    if (!is(element, BASIC_DO_WHILE_STATEMENT) &&
        !is(element, BASIC_WHILE_STATEMENT)) {
      return null;
    }
    return findChildByType(element, EXPRESSION_SET);
  }

  public static @Nullable ASTNode getDoWhileBody(@Nullable ASTNode element) {
    if (!is(element, BASIC_DO_WHILE_STATEMENT)) {
      return null;
    }
    return findChildByType(element, STATEMENT_SET);
  }

  public static @Nullable ASTNode getForUpdate(@Nullable ASTNode statement) {
    if (!is(statement, BASIC_FOR_STATEMENT)) {
      return null;
    }
    ASTNode semicolon = findChildByType(statement, JavaTokenType.SEMICOLON);
    if (semicolon == null) {
      return null;
    }
    for (ASTNode child = semicolon; child != null; child = child.getTreeNext()) {
      if (is(child, STATEMENT_SET)) {
        return child;
      }
      if (is(child, RPARENTH)) break;
    }
    return null;
  }

  public static @Nullable ASTNode getForCondition(@Nullable ASTNode element) {
    if (!is(element, BASIC_FOR_STATEMENT)) {
      return null;
    }
    return findChildByType(element, EXPRESSION_SET);
  }

  public static @Nullable ASTNode getIfCondition(@Nullable ASTNode statement) {
    if (!is(statement, BASIC_IF_STATEMENT)) {
      return null;
    }
    return findChildByType(statement, EXPRESSION_SET);
  }

  public static boolean hasModifierProperty(@Nullable ASTNode element,
                                            @NotNull IElementType property) {
    if (element == null) {
      return false;
    }
    ASTNode modifierList = findChildByType(element, BASIC_MODIFIER_LIST);
    if (modifierList == null) {
      return false;
    }
    ASTNode firstChild = modifierList.getFirstChildNode();
    if (firstChild == null) {
      return false;
    }
    for (ASTNode child = firstChild; child != null; child = child.getTreeNext()) {
      if (is(child, property)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isInterfaceEnumClassOrRecord(@Nullable ASTNode element,
                                                     @NotNull IElementType elementType) {
    if (!is(element, CLASS_SET)) {
      return false;
    }
    if (element == null) {
      return false;
    }
    ASTNode firstChild = element.getFirstChildNode();
    if (firstChild == null) {
      return false;
    }
    for (ASTNode child = firstChild; child != null; child = child.getTreeNext()) {
      if (is(child, elementType)) return true;
    }
    return false;
  }


  public static ASTNode @Nullable [] getParameterListParameters(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    return getChildren(element).stream().filter(ch -> is(ch, BASIC_PARAMETER)).toArray(ASTNode[]::new);
  }

  public static ASTNode @Nullable [] getAnnotationParameterListAttributes(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    return getChildren(element).stream().filter(ch -> is(ch, BASIC_NAME_VALUE_PAIR)).toArray(ASTNode[]::new);
  }

  public static @Nullable ASTNode getFirstBodyElement(@Nullable ASTNode block) {
    if (block == null) {
      return null;
    }
    final ASTNode lBrace = getLBrace(block);
    if (lBrace == null) return null;
    final ASTNode nextSibling = lBrace.getTreeNext();
    return nextSibling == getRBrace(block) ? null : nextSibling;
  }

  public static @Nullable ASTNode getCodeBlock(@Nullable ASTNode element) {
    if (element == null) return null;
    return findChildByType(element, BASIC_CODE_BLOCK);
  }

  public static @Nullable ASTNode getForBody(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    ASTNode rparenth = getRParenth(element);
    if (rparenth == null) {
      return null;
    }
    for (ASTNode child = rparenth; child != null; child = child.getTreeNext()) {
      if (is(child, STATEMENT_SET)) {
        return child;
      }
    }
    return null;
  }


  public static ASTNode[] getCatchBlocks(@Nullable ASTNode element) {
    ASTNode tryBlock = getCodeBlock(element);
    if (tryBlock != null) {
      final List<ASTNode> catchSections = ContainerUtil.filter(getChildren(element), ch -> is(ch, BASIC_CATCH_SECTION));
      return catchSections.stream()
        .map(t -> getCatchBlock(t))
        .filter(t -> t != null)
        .toArray(ASTNode[]::new);
    }
    return ASTNode.EMPTY_ARRAY;
  }

  public static @Nullable ASTNode getConditionalExpressionThenExpression(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    ASTNode quest = findChildByType(element, JavaTokenType.QUEST);
    if (quest == null) {
      return null;
    }
    ASTNode child = quest.getTreeNext();
    while (true) {
      if (child == null) return null;
      if (EXPRESSION_SET.contains(child.getElementType())) break;
      child = child.getTreeNext();
    }
    return child;
  }

  public static @Nullable IElementType getAssignmentOperationTokenType(@Nullable ASTNode expr) {
    if (expr == null) {
      return null;
    }
    ASTNode ASTNode = findChildByType(expr, JavaBinaryOperations.ASSIGNMENT_OPS.getTypes());
    if (ASTNode == null) {
      return null;
    }
    return ASTNode.getElementType();
  }

  public static @Nullable ASTNode getPatternVariable(@Nullable ASTNode ASTNode) {
    if (ASTNode == null) {
      return null;
    }
    return findChildByType(ASTNode, BASIC_DECONSTRUCTION_PATTERN_VARIABLE, BASIC_PATTERN_VARIABLE);
  }

  public static @Nullable ASTNode getExpression(@Nullable ASTNode ASTNode) {
    if (ASTNode == null) {
      return null;
    }
    return findChildByType(ASTNode, EXPRESSION_SET);
  }

  public static @Nullable ASTNode getRecordComponentContainingClass(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    ASTNode parent = element.getTreeParent();
    if (parent == null) {
      return null;
    }
    ASTNode grandParent = parent.getTreeParent();
    if (is(grandParent, CLASS_SET)) {
      return grandParent;
    }
    return null;
  }

  public static @Nullable ASTNode getRecordHeader(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    return findChildByType(element, BASIC_RECORD_HEADER);
  }

  public static @Nullable ASTNode getRuleBody(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }

    return findChildByType(element, ParentAwareTokenSet.orSet(
      ParentAwareTokenSet.create(BASIC_BLOCK_STATEMENT, BASIC_THROW_STATEMENT), EXPRESSION_SET));
  }

  public static @Nullable ASTNode getCaseLabelElementList(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    return findChildByType(element, BASIC_CASE_LABEL_ELEMENT_LIST);
  }

  public static @Nullable ASTNode getReturnValue(@Nullable ASTNode element) {
    if (element == null) {
      return null;
    }
    return findChildByType(element, EXPRESSION_SET);
  }

  public static @Nullable ASTNode getForEachIteratedValue(@Nullable ASTNode statement) {
    if (!is(statement, BASIC_FOREACH_STATEMENT)) {
      return null;
    }
    return findChildByType(statement, EXPRESSION_SET);
  }

  public static @Nullable ASTNode getForEachIterationParameter(@Nullable ASTNode statement) {
    if (statement == null) {
      return null;
    }
    if (!is(statement, BASIC_FOREACH_STATEMENT)) {
      return null;
    }
    return findChildByType(statement, BASIC_PARAMETER);
  }

  public static @Nullable ASTNode getForeachBody(@Nullable ASTNode statement) {
    if (statement == null) {
      return null;
    }
    if (!is(statement, BASIC_FOREACH_STATEMENT)) {
      return null;
    }
    return findChildByType(statement, STATEMENT_SET);
  }

  public static @Nullable ASTNode getWhileBody(@Nullable ASTNode statement) {
    if (statement == null) {
      return null;
    }
    if (!is(statement, BASIC_WHILE_STATEMENT)) {
      return null;
    }
    return findChildByType(statement, STATEMENT_SET);
  }

  public static boolean hasErrorElements(@Nullable ASTNode node) {
    return !SyntaxTraverser.astTraverser(node).traverse()
      .filter(t -> is(t, TokenType.ERROR_ELEMENT)).isEmpty();
  }

  public static int getTextOffset(@NotNull ASTNode node) {
    if (is(node, BASIC_LOCAL_VARIABLE, BASIC_PATTERN_VARIABLE, BASIC_RECORD_COMPONENT,
           BASIC_RECEIVER_PARAMETER, BASIC_MODULE,
           BASIC_IMPORT_STATIC_REFERENCE, BASIC_JAVA_CODE_REFERENCE)) {
      ASTNode identifier = findChildByType(node, JavaTokenType.IDENTIFIER);
      if (identifier != null) {
        return identifier.getStartOffset();
      }
    }
    if (is(node, BASIC_REFERENCE_EXPRESSION)) {
      ASTNode identifier = findChildByType(node, JavaTokenType.IDENTIFIER, JavaTokenType.THIS_KEYWORD, JavaTokenType.SUPER_KEYWORD);
      if (identifier != null) {
        return identifier.getStartOffset();
      }
    }
    ASTNode doc = findChildByType(node, JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
    if (doc != null) {
      return doc.getStartOffset();
    }
    return node.getStartOffset();
  }
}