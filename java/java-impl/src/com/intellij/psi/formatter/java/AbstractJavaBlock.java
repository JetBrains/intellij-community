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
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.formatting.alignment.AlignmentInColumnsConfig;
import com.intellij.formatting.alignment.AlignmentInColumnsHelper;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.formatter.common.JavaBlockUtil;
import com.intellij.psi.formatter.java.wrap.JavaWrapManager;
import com.intellij.psi.formatter.java.wrap.ReservedWrapsProvider;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.ClassElement;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Arrays.asList;

public abstract class AbstractJavaBlock extends AbstractBlock implements JavaBlock, ReservedWrapsProvider {

  /**
   * Holds types of the elements for which <code>'align in column'</code> rule may be preserved.
   *
   * @see CodeStyleSettings#ALIGN_MULTILINE_SUBSEQUENT_DECLARATIONS
   */
  protected static final Set<IElementType> ALIGN_IN_COLUMNS_ELEMENT_TYPES = Collections.unmodifiableSet(new HashSet<IElementType>(asList(
    JavaElementType.FIELD, JavaElementType.DECLARATION_STATEMENT, JavaElementType.LOCAL_VARIABLE
  )));

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.java.AbstractJavaBlock");

  /**
   * Shared thread-safe config object to use during <code>'align in column'</code> processing.
   *
   * @see CodeStyleSettings#ALIGN_MULTILINE_SUBSEQUENT_DECLARATIONS
   */
  private static final AlignmentInColumnsConfig ALIGNMENT_IN_COLUMNS_CONFIG = new AlignmentInColumnsConfig(
    JavaTokenType.IDENTIFIER, StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET, ElementType.COMMENT_BIT_SET, JavaTokenType.EQ,
    JavaElementType.FIELD, JavaElementType.LOCAL_VARIABLE
  );

  /**
   * Enumerates types of variable declaration sub-elements that should be aligned in columns.
   */
  private static final Set<IElementType> VAR_DECLARATION_ELEMENT_TYPES_TO_ALIGN = new HashSet<IElementType>(asList(
    JavaElementType.MODIFIER_LIST, JavaElementType.TYPE, JavaTokenType.IDENTIFIER, JavaTokenType.EQ
  ));

  protected final CodeStyleSettings mySettings;
  private final Indent myIndent;
  protected Indent myChildIndent;
  protected Alignment myChildAlignment;
  protected boolean myUseChildAttributes = false;
  protected final AlignmentStrategy myAlignmentStrategy;
  private boolean myIsAfterClassKeyword = false;
  private Wrap myAnnotationWrap = null;

  protected Alignment myReservedAlignment;
  protected Alignment myReservedAlignment2;

  private final JavaWrapManager myWrapManager;
  private final AlignmentInColumnsHelper myAlignmentInColumnsHelper;

  protected AbstractJavaBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent,
                              final CodeStyleSettings settings)
  {
    this(node, wrap, indent, settings, JavaWrapManager.INSTANCE, AlignmentStrategy.wrap(alignment), AlignmentInColumnsHelper.INSTANCE);
  }

  protected AbstractJavaBlock(final ASTNode node, final Wrap wrap, final AlignmentStrategy alignmentStrategy, final Indent indent,
                              final CodeStyleSettings settings)
  {
    this(node, wrap, indent, settings, JavaWrapManager.INSTANCE, alignmentStrategy, AlignmentInColumnsHelper.INSTANCE);
  }

  protected AbstractJavaBlock(final ASTNode node, final Wrap wrap, final Indent indent,
                              final CodeStyleSettings settings, final JavaWrapManager wrapManager,
                              @NotNull final AlignmentStrategy alignmentStrategy, AlignmentInColumnsHelper alignmentInColumnsHelper)
  {
    super(node, wrap, alignmentStrategy.getAlignment(node.getElementType()));
    mySettings = settings;
    myIndent = indent;
    myWrapManager = wrapManager;
    myAlignmentStrategy = alignmentStrategy;
    myAlignmentInColumnsHelper = alignmentInColumnsHelper;
  }

  public static Block createJavaBlock(final ASTNode child,
                                      final CodeStyleSettings settings,
                                      final Indent indent,
                                      Wrap wrap,
                                      Alignment alignment) {
    return createJavaBlock(child, settings, indent, wrap, AlignmentStrategy.wrap(alignment));
  }

  public static Block createJavaBlock(final ASTNode child,
                                      final CodeStyleSettings settings,
                                      final Indent indent,
                                      Wrap wrap,
                                      @NotNull AlignmentStrategy alignmentStrategy) {
    return createJavaBlock(child, settings, indent, wrap, alignmentStrategy, -1);
  }

  public static Block createJavaBlock(final ASTNode child,
                                      final CodeStyleSettings settings,
                                      final Indent indent,
                                      Wrap wrap,
                                      AlignmentStrategy alignmentStrategy,
                                      int startOffset
                                     ) {
    Indent actualIndent = indent == null ? getDefaultSubtreeIndent(child) : indent;
    final IElementType elementType = child.getElementType();
    Alignment alignment = alignmentStrategy.getAlignment(elementType);

    if (child.getPsi() instanceof PsiWhiteSpace) {
      String text = child.getText();
      int start = CharArrayUtil.shiftForward(text, 0, " \t\n");
      int end = CharArrayUtil.shiftBackward(text, text.length() - 1, " \t\n") + 1;
      LOG.assertTrue(start < end);
      return new PartialWhitespaceBlock(child, new TextRange(start + child.getStartOffset(), end + child.getStartOffset()),
                                        wrap, alignment, actualIndent, settings);
    }
    if (child.getPsi() instanceof PsiClass) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings);
    }
    if (isBlockType(elementType)) {
      return new BlockContainingJavaBlock(child, wrap, alignment, actualIndent, settings);
    }
    if (isStatement(child, child.getTreeParent())) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings);
    }
    if (child instanceof LeafElement) {
      final LeafBlock block = new LeafBlock(child, wrap, alignment, actualIndent);
      block.setStartOffset(startOffset);
      return block;
    }
    else if (isLikeExtendsList(elementType)) {
      return new ExtendsListBlock(child, wrap, alignment, settings);
    }
    else if (elementType == JavaElementType.CODE_BLOCK) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings);
    }
    else if (elementType == JavaElementType.LABELED_STATEMENT) {
      return new LabeledJavaBlock(child, wrap, alignment, actualIndent, settings);
    }
    else if (elementType == JavaDocElementType.DOC_COMMENT) {
      return new DocCommentBlock(child, wrap, alignment, actualIndent, settings);
    }
    else {
      final SimpleJavaBlock simpleJavaBlock = new SimpleJavaBlock(child, wrap, alignmentStrategy, actualIndent, settings);
      simpleJavaBlock.setStartOffset(startOffset);
      return simpleJavaBlock;
    }
  }

  private static boolean isLikeExtendsList(final IElementType elementType) {
    return elementType == JavaElementType.EXTENDS_LIST
           || elementType == JavaElementType.IMPLEMENTS_LIST
           || elementType == JavaElementType.THROWS_LIST;
  }

  private static boolean isBlockType(final IElementType elementType) {
    return elementType == JavaElementType.SWITCH_STATEMENT
           || elementType == JavaElementType.FOR_STATEMENT
           || elementType == JavaElementType.WHILE_STATEMENT
           || elementType == JavaElementType.DO_WHILE_STATEMENT
           || elementType == JavaElementType.TRY_STATEMENT
           || elementType == JavaElementType.CATCH_SECTION
           || elementType == JavaElementType.IF_STATEMENT
           || elementType == JavaElementType.METHOD
           || elementType == JavaElementType.ARRAY_INITIALIZER_EXPRESSION
           || elementType == JavaElementType.ANNOTATION_ARRAY_INITIALIZER
           || elementType == JavaElementType.CLASS_INITIALIZER
           || elementType == JavaElementType.SYNCHRONIZED_STATEMENT
           || elementType == JavaElementType.FOREACH_STATEMENT;
  }

  public static Block createJavaBlock(final ASTNode child, final CodeStyleSettings settings) {
    return createJavaBlock(child, settings, getDefaultSubtreeIndent(child), null, AlignmentStrategy.getNullStrategy());
  }

  @Nullable
  private static Indent getDefaultSubtreeIndent(final ASTNode child) {
    final ASTNode parent = child.getTreeParent();
    final IElementType childNodeType = child.getElementType();
    if (childNodeType == JavaElementType.ANNOTATION) {
      if (parent.getPsi() instanceof PsiArrayInitializerMemberValue) {
        return Indent.getNormalIndent();
      } else {
        return Indent.getNoneIndent();
      }
    }

    final ASTNode prevElement = FormattingAstUtil.getPrevNonWhiteSpaceNode(child);
    if (prevElement != null && prevElement.getElementType() == JavaElementType.MODIFIER_LIST) {
      return Indent.getNoneIndent();
    }

    if (childNodeType == JavaDocElementType.DOC_TAG) return Indent.getNoneIndent();
    if (childNodeType == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) return Indent.getSpaceIndent(1);
    if (child.getPsi() instanceof PsiFile) return Indent.getNoneIndent();
    if (parent != null) {
      final Indent defaultChildIndent = getChildIndent(parent);
      if (defaultChildIndent != null) return defaultChildIndent;
    }

    return null;
  }

  @Nullable
  private static Indent getChildIndent(final ASTNode parent) {
    final IElementType parentType = parent.getElementType();
    if (parentType == JavaElementType.MODIFIER_LIST) return Indent.getNoneIndent();
    if (parentType == JspElementType.JSP_CODE_BLOCK) return Indent.getNormalIndent();
    if (parentType == JspElementType.JSP_CLASS_LEVEL_DECLARATION_STATEMENT) return Indent.getNormalIndent();
    if (parentType == ElementType.DUMMY_HOLDER) return Indent.getNoneIndent();
    if (parentType == JavaElementType.CLASS) return Indent.getNoneIndent();
    if (parentType == JavaElementType.IF_STATEMENT) return Indent.getNoneIndent();
    if (parentType == JavaElementType.TRY_STATEMENT) return Indent.getNoneIndent();
    if (parentType == JavaElementType.CATCH_SECTION) return Indent.getNoneIndent();
    if (parentType == JavaElementType.FOR_STATEMENT) return Indent.getNoneIndent();
    if (parentType == JavaElementType.FOREACH_STATEMENT) return Indent.getNoneIndent();
    if (parentType == JavaElementType.BLOCK_STATEMENT) return Indent.getNoneIndent();
    if (parentType == JavaElementType.DO_WHILE_STATEMENT) return Indent.getNoneIndent();
    if (parentType == JavaElementType.WHILE_STATEMENT) return Indent.getNoneIndent();
    if (parentType == JavaElementType.SWITCH_STATEMENT) return Indent.getNoneIndent();
    if (parentType == JavaElementType.METHOD) return Indent.getNoneIndent();
    if (parentType == JavaDocElementType.DOC_COMMENT) return Indent.getNoneIndent();
    if (parentType == JavaDocElementType.DOC_TAG) return Indent.getNoneIndent();
    if (parentType == JavaDocElementType.DOC_INLINE_TAG) return Indent.getNoneIndent();
    if (parentType == JavaElementType.IMPORT_LIST) return Indent.getNoneIndent();
    if (parentType == JavaElementType.FIELD) return Indent.getContinuationWithoutFirstIndent();
    if (parentType == JavaElementType.EXPRESSION_STATEMENT) return Indent.getNoneIndent();
    if (SourceTreeToPsiMap.treeElementToPsi(parent) instanceof PsiFile) {
      return Indent.getNoneIndent();
    }
    else {
      return null;
    }
  }

  protected static boolean isRBrace(final ASTNode child) {
    return child.getElementType() == ElementType.RBRACE;
  }

  public Spacing getSpacing(Block child1, Block child2) {
    return JavaSpacePropertyProcessor.getSpacing(getTreeNode(child2), mySettings);
  }

  public ASTNode getFirstTreeNode() {
    return myNode;
  }

  public Indent getIndent() {
    return myIndent;
  }

  protected static boolean isStatement(final ASTNode child, final ASTNode parentNode) {
    if (parentNode != null) {
      final IElementType parentType = parentNode.getElementType();
      if (parentType == JavaElementType.CODE_BLOCK) return false;
      final int role = ((CompositeElement)parentNode).getChildRole(child);
      if (parentType == JavaElementType.IF_STATEMENT) return role == ChildRole.THEN_BRANCH || role == ChildRole.ELSE_BRANCH;
      if (parentType == JavaElementType.FOR_STATEMENT) return role == ChildRole.LOOP_BODY;
      if (parentType == JavaElementType.WHILE_STATEMENT) return role == ChildRole.LOOP_BODY;
      if (parentType == JavaElementType.DO_WHILE_STATEMENT) return role == ChildRole.LOOP_BODY;
      if (parentType == JavaElementType.FOREACH_STATEMENT) return role == ChildRole.LOOP_BODY;
    }
    return false;
  }

  @Nullable
  protected Wrap createChildWrap() {
    return myWrapManager.createChildBlockWrap(this, getSettings(), this);
  }

  @Nullable
  protected Alignment createChildAlignment() {
    final IElementType nodeType = myNode.getElementType();
    if (nodeType == JavaElementType.ASSIGNMENT_EXPRESSION) {
      if (myNode.getTreeParent() != null
          && myNode.getTreeParent().getElementType() == JavaElementType.ASSIGNMENT_EXPRESSION
          && myAlignment != null) {
        return myAlignment;
      }
      else {
        return createAlignment(mySettings.ALIGN_MULTILINE_ASSIGNMENT, null);
      }
    }
    else if (nodeType == JavaElementType.PARENTH_EXPRESSION) {
      return createAlignment(mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION, null);
    }
    else if (nodeType == JavaElementType.CONDITIONAL_EXPRESSION) {
      return createAlignment(mySettings.ALIGN_MULTILINE_TERNARY_OPERATION, null);
    }
    else if (nodeType == JavaElementType.FOR_STATEMENT) {
      return createAlignment(mySettings.ALIGN_MULTILINE_FOR, null);
    }
    else if (nodeType == JavaElementType.EXTENDS_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_EXTENDS_LIST, null);
    }
    else if (nodeType == JavaElementType.IMPLEMENTS_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_EXTENDS_LIST, null);
    }
    else if (nodeType == JavaElementType.THROWS_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_THROWS_LIST, null);
    }
    else if (nodeType == JavaElementType.PARAMETER_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_PARAMETERS, null);
    }
    else if (nodeType == JavaElementType.BINARY_EXPRESSION) {
      Alignment defaultAlignment = null;
      if (shouldInheritAlignment()) {
        defaultAlignment = myAlignment;
      }
      return createAlignment(mySettings.ALIGN_MULTILINE_BINARY_OPERATION, defaultAlignment);
    }
    else if (nodeType == JavaElementType.CLASS) {
      return Alignment.createAlignment();
    }
    else if (nodeType == JavaElementType.METHOD) {
      return Alignment.createAlignment();
    }

    else if (nodeType == JavaElementType.MODIFIER_LIST) {
      return myAlignment;
    }

    else {
      return null;
    }
  }

  @Nullable
  protected Alignment createChildAlignment2(Alignment base) {
    final IElementType nodeType = myNode.getElementType();
    if (nodeType == JavaElementType.CONDITIONAL_EXPRESSION) {
      return base == null ? createAlignment(mySettings.ALIGN_MULTILINE_TERNARY_OPERATION, null) : createAlignment(base, mySettings.ALIGN_MULTILINE_TERNARY_OPERATION, null);
    }

    else {
      return null;
    }
  }

  protected Alignment chooseAlignment(Alignment alignment, Alignment alignment2, ASTNode child) {
    if (preferesSlaveAlignment(child)) {
      return alignment2;
    }
    else {
      return alignment;
    }
  }

  private boolean preferesSlaveAlignment(final ASTNode child) {
    final IElementType nodeType = myNode.getElementType();

    if (nodeType == JavaElementType.CONDITIONAL_EXPRESSION) {
      IElementType childType = child.getElementType();
      return childType == JavaTokenType.QUEST || childType ==JavaTokenType.COLON;
    }
    else {
      return false;
    }
  }

  private boolean shouldInheritAlignment() {
    if (myNode.getElementType() == JavaElementType.BINARY_EXPRESSION) {
      final ASTNode treeParent = myNode.getTreeParent();
      if (treeParent != null && treeParent.getElementType() == JavaElementType.BINARY_EXPRESSION) {
        return FormattingAstUtil.binaryExpressionHasTheSamePriority(myNode, treeParent);
      }
    }
    return false;
  }


  @Nullable
  protected ASTNode processChild(final List<Block> result,
                                 ASTNode child,
                                 Alignment defaultAlignment,
                                 final Wrap defaultWrap,
                                 final Indent childIndent) {
    return processChild(result, child, AlignmentStrategy.wrap(defaultAlignment), defaultWrap, childIndent, -1);
  }

  @Nullable
  protected ASTNode processChild(final List<Block> result,
                                 ASTNode child,
                                 AlignmentStrategy alignmentStrategy,
                                 final Wrap defaultWrap,
                                 final Indent childIndent) {
    return processChild(result, child, alignmentStrategy, defaultWrap, childIndent, -1);
  }

  @Nullable
  protected ASTNode processChild(final List<Block> result,
                                 ASTNode child,
                                 @NotNull AlignmentStrategy alignmentStrategy,
                                 final Wrap defaultWrap,
                                 final Indent childIndent,
                                 int childOffset) {
    final IElementType childType = child.getElementType();
    if (childType == JavaTokenType.CLASS_KEYWORD || childType == JavaTokenType.INTERFACE_KEYWORD) {
      myIsAfterClassKeyword = true;
    }
    if (childType == JavaElementType.METHOD_CALL_EXPRESSION) {
      result.add(createMethodCallExpressionBlock(child,
                                                 arrangeChildWrap(child, defaultWrap),
                                                 arrangeChildAlignment(child, alignmentStrategy)));
    }
    else {
      final IElementType nodeType = myNode.getElementType();

      if (childType == JavaTokenType.LBRACE && nodeType == JavaElementType.ARRAY_INITIALIZER_EXPRESSION) {
        final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.ARRAY_INITIALIZER_WRAP), false);
        child = processParenthesisBlock(JavaTokenType.LBRACE, JavaTokenType.RBRACE,
                                  result,
                                  child,
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  mySettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);
      }
      else if (childType == JavaTokenType.LBRACE && nodeType == JavaElementType.ANNOTATION_ARRAY_INITIALIZER) {
        final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.ARRAY_INITIALIZER_WRAP), false);
        child = processParenthesisBlock(JavaTokenType.LBRACE, JavaTokenType.RBRACE,
                                  result,
                                  child,
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  mySettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);
      }
      else if (childType == JavaTokenType.LPARENTH && nodeType == JavaElementType.EXPRESSION_LIST) {
        final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.CALL_PARAMETERS_WRAP), false);
        if (mySettings.PREFER_PARAMETERS_WRAP) {
          wrap.ignoreParentWraps();
        }
        child = processParenthesisBlock(result,
                                  child,
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
      }

      else if (childType == JavaTokenType.LPARENTH && nodeType == JavaElementType.PARAMETER_LIST) {
        final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.METHOD_PARAMETERS_WRAP), false);
        child = processParenthesisBlock(result, child,
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  mySettings.ALIGN_MULTILINE_PARAMETERS);
      }
      else if (childType == JavaTokenType.LPARENTH && nodeType == JavaElementType.ANNOTATION_PARAMETER_LIST) {
        final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.CALL_PARAMETERS_WRAP), false);
        child = processParenthesisBlock(result, child,
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
      }
      else if (childType == JavaTokenType.LPARENTH && nodeType == JavaElementType.PARENTH_EXPRESSION) {
        child = processParenthesisBlock(result, child,
                                  WrappingStrategy.DO_NOT_WRAP,
                                  mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
      }
      else if (childType == JavaElementType.ENUM_CONSTANT && myNode instanceof ClassElement) {
        child = processEnumBlock(result, child, ((ClassElement)myNode).findEnumConstantListDelimiterPlace());
      }
      else if (mySettings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE && isTernaryOperationSign(child)) {
        child = processTernaryOperationRange(result, child, defaultWrap, childIndent);
      }
      else if (childType == JavaElementType.FIELD) {
        child = processField(result, child, alignmentStrategy, defaultWrap, childIndent);
      }
      else if (childType == JavaElementType.LOCAL_VARIABLE
               || (childType == JavaElementType.DECLARATION_STATEMENT && myNode.getElementType() == JavaElementType.METHOD))
      {
        result.add(new SimpleJavaBlock(child, defaultWrap, alignmentStrategy, childIndent, mySettings));
      }
      else {
        final Block block =
          createJavaBlock(child, mySettings, childIndent, arrangeChildWrap(child, defaultWrap),
                          AlignmentStrategy.wrap(arrangeChildAlignment(child, alignmentStrategy)), childOffset);

        if (childType == JavaElementType.MODIFIER_LIST && containsAnnotations(child)) {
          myAnnotationWrap = Wrap.createWrap(getWrapType(getAnnotationWrapType()), true);
        }

        if (block instanceof AbstractJavaBlock) {
          final AbstractJavaBlock javaBlock = (AbstractJavaBlock)block;
          if (nodeType == JavaElementType.METHOD_CALL_EXPRESSION && childType == JavaElementType.REFERENCE_EXPRESSION) {
            javaBlock.setReservedWrap(getReservedWrap(nodeType), nodeType);
            javaBlock.setReservedWrap(getReservedWrap(childType), childType);
          }
          else if (nodeType == JavaElementType.REFERENCE_EXPRESSION &&
                   childType == JavaElementType.METHOD_CALL_EXPRESSION) {
            javaBlock.setReservedWrap(getReservedWrap(nodeType), nodeType);
            javaBlock.setReservedWrap(getReservedWrap(childType), childType);
          }
          else if (nodeType == JavaElementType.BINARY_EXPRESSION) {
            javaBlock.setReservedWrap(defaultWrap, nodeType);
          }
          else if (childType == JavaElementType.MODIFIER_LIST) {
            javaBlock.setReservedWrap(myAnnotationWrap, JavaElementType.MODIFIER_LIST);
            if (!lastChildIsAnnotation(child)) {
              myAnnotationWrap = null;
            }
          }
        }

        result.add(block);
      }
    }


    return child;
  }

  private ASTNode processField(final List<Block> result, ASTNode child, final AlignmentStrategy alignmentStrategy, final Wrap defaultWrap,
                               final Indent childIndent) {
    ASTNode lastFieldInGroup = findLastFieldInGroup(child);
    if (lastFieldInGroup == child) {
      result.add(createJavaBlock(child, getSettings(), childIndent, arrangeChildWrap(child, defaultWrap), alignmentStrategy));
      return child;
    }
    else {
      final ArrayList<Block> localResult = new ArrayList<Block>();
      while (child != null) {
        if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
          localResult.add(createJavaBlock(child, getSettings(), Indent.getContinuationWithoutFirstIndent(),
                                          arrangeChildWrap(child, defaultWrap), alignmentStrategy));
        }
        if (child == lastFieldInGroup) break;

        child = child.getTreeNext();

      }
      if (!localResult.isEmpty()) {
        result.add(new SyntheticCodeBlock(localResult, null, getSettings(), childIndent, null));
      }
      return lastFieldInGroup;
    }
  }

  @NotNull private static ASTNode findLastFieldInGroup(final ASTNode child) {
    final PsiTypeElement typeElement = ((PsiVariable)child.getPsi()).getTypeElement();
    if (typeElement == null) return child;

    ASTNode lastChildNode = child.getLastChildNode();
    if (lastChildNode == null) return child;

    if (lastChildNode.getElementType() == JavaTokenType.SEMICOLON) return child;

    ASTNode currentResult = child;
    ASTNode currentNode = child.getTreeNext();

    while (currentNode != null) {
      if (currentNode.getElementType() == TokenType.WHITE_SPACE
          || currentNode.getElementType() == JavaTokenType.COMMA
          || StdTokenSets.COMMENT_BIT_SET.contains(currentNode.getElementType())) {
      }
      else if (currentNode.getElementType() == JavaElementType.FIELD) {
        if (((PsiVariable)currentNode.getPsi()).getTypeElement() != typeElement) {
          return currentResult;
        }
        else {
          currentResult = currentNode;
        }
      }
      else {
        return currentResult;
      }

      currentNode = currentNode.getTreeNext();
    }
    return currentResult;
  }

  @Nullable
  private ASTNode processTernaryOperationRange(final List<Block> result,
                                               final ASTNode child, final Wrap defaultWrap, final Indent childIndent) {
    final ArrayList<Block> localResult = new ArrayList<Block>();
    final Wrap wrap = arrangeChildWrap(child, defaultWrap);
    final Alignment alignment = myReservedAlignment;
    final Alignment alignment2 = myReservedAlignment2;
    localResult.add(new LeafBlock(child, wrap, chooseAlignment(alignment,  alignment2, child), childIndent));

    ASTNode current = child.getTreeNext();
    while (current != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(current) && current.getTextLength() > 0) {
        if (isTernaryOperationSign(current)) break;
        current = processChild(localResult, current, chooseAlignment(alignment,  alignment2, current), defaultWrap, childIndent);
      }
      if (current != null) {
        current = current.getTreeNext();
      }
    }

    result.add(new SyntheticCodeBlock(localResult,  chooseAlignment(alignment,  alignment2, child), getSettings(), null, wrap));

    if (current == null) {
      return null;
    }
    else {
      return current.getTreePrev();
    }
  }

  private boolean isTernaryOperationSign(final ASTNode child) {
    if (myNode.getElementType() != JavaElementType.CONDITIONAL_EXPRESSION) return false;
    final int role = ((CompositeElement)child.getTreeParent()).getChildRole(child);
    return role == ChildRole.OPERATION_SIGN || role == ChildRole.COLON;
  }

  @SuppressWarnings({"ConstantConditions"})
  private Block createMethodCallExpressionBlock(final ASTNode node, final Wrap blockWrap, final Alignment alignment) {
    final ArrayList<ASTNode> nodes = new ArrayList<ASTNode>();
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    collectNodes(nodes, node);

    final Wrap wrap = Wrap.createWrap(getWrapType(mySettings.METHOD_CALL_CHAIN_WRAP), false);

    // We use this alignment object to align chained method calls to the first method invocation place if necessary (see IDEA-30369)
    Alignment chainedCallsAlignment = createAlignment(mySettings.ALIGN_MULTILINE_CHAINED_METHODS, null);

    // We want to align chained method calls only if method target is explicitly specified, i.e. we don't want to align methods
    // chain like 'recursive().recursive().recursive()' but want to align calls like 'foo.recursive().recursive().recursive()'
    boolean callPointDefined = false;

    while (!nodes.isEmpty()) {
      ArrayList<ASTNode> subNodes = readToNextDot(nodes);
      Alignment alignmentToUseForSubBlock = null;

      // Just create a no-aligned sub-block if we don't need to bother with it's alignment (either due to end-user
      // setup or sub-block state).
      if (chainedCallsAlignment == null || subNodes.isEmpty()) {
        subBlocks.add(createSynthBlock(subNodes, wrap, alignmentToUseForSubBlock));
        continue;
      }

      IElementType lastNodeType = subNodes.get(subNodes.size() - 1).getElementType();
      boolean currentSubBlockIsMethodCall = lastNodeType == JavaElementType.EXPRESSION_LIST;

      // Update information about chained method alignment point if necessary. I.e. we want to align only continuous method calls
      // like 'foo.bar().bar().bar()' but not 'foo.bar().foo.bar()'
      if (callPointDefined && !currentSubBlockIsMethodCall) {
        chainedCallsAlignment = createAlignment(mySettings.ALIGN_MULTILINE_CHAINED_METHODS, null);
      }
      callPointDefined |= !currentSubBlockIsMethodCall;

      // We want to align method call only if call target is defined for the first chained method and current block is a method call.
      if (callPointDefined && currentSubBlockIsMethodCall) {
        alignmentToUseForSubBlock = chainedCallsAlignment;
      }
      subBlocks.add(createSynthBlock(subNodes, wrap, alignmentToUseForSubBlock));
    }

    return new SyntheticCodeBlock(subBlocks, alignment, mySettings, Indent.getContinuationWithoutFirstIndent(),
                                  blockWrap);
  }

  private Block createSynthBlock(final ArrayList<ASTNode> subNodes, final Wrap wrap, final Alignment alignment) {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    final ASTNode firstNode = subNodes.get(0);
    if (firstNode.getElementType() == JavaTokenType.DOT) {
      subBlocks.add(createJavaBlock(firstNode, getSettings(), Indent.getNoneIndent(), null, AlignmentStrategy.getNullStrategy()));
      subNodes.remove(0);
      if (!subNodes.isEmpty()) {
        subBlocks.add(createSynthBlock(subNodes, wrap, null));
      }
      return new SyntheticCodeBlock(subBlocks, alignment, mySettings, Indent.getContinuationIndent(), wrap);
    }
    else {
      return new SyntheticCodeBlock(createJavaBlocks(subNodes), alignment, mySettings,
                                    Indent.getContinuationWithoutFirstIndent(), null);
    }
  }

  private List<Block> createJavaBlocks(final ArrayList<ASTNode> subNodes) {
    final ArrayList<Block> result = new ArrayList<Block>();
    for (ASTNode node : subNodes) {
      result.add(createJavaBlock(node, getSettings(), Indent.getContinuationWithoutFirstIndent(), null,
                                 AlignmentStrategy.getNullStrategy()));
    }
    return result;
  }

  private static ArrayList<ASTNode> readToNextDot(final ArrayList<ASTNode> nodes) {
    final ArrayList<ASTNode> result = new ArrayList<ASTNode>();
    result.add(nodes.remove(0));
    for (Iterator<ASTNode> iterator = nodes.iterator(); iterator.hasNext();) {
      ASTNode node = iterator.next();
      if (node.getElementType() == JavaTokenType.DOT) return result;
      result.add(node);
      iterator.remove();
    }
    return result;
  }

  private static void collectNodes(List<ASTNode> nodes, ASTNode node) {
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        if (child.getElementType() == JavaElementType.METHOD_CALL_EXPRESSION || child.getElementType() ==
                                                                                JavaElementType
                                                                                  .REFERENCE_EXPRESSION) {
          collectNodes(nodes, child);
        }
        else {
          nodes.add(child);
        }
      }
      child = child.getTreeNext();
    }

  }

  private static boolean lastChildIsAnnotation(final ASTNode child) {
    ASTNode current = child.getLastChildNode();
    while (current != null && current.getElementType() == TokenType.WHITE_SPACE) {
      current = current.getTreePrev();
    }
    return current != null && current.getElementType() == JavaElementType.ANNOTATION;
  }

  private static boolean containsAnnotations(final ASTNode child) {
    return ((PsiModifierList)child.getPsi()).getAnnotations().length > 0;
  }

  private int getAnnotationWrapType() {
    final IElementType nodeType = myNode.getElementType();
    if (nodeType == JavaElementType.METHOD) {
      return mySettings.METHOD_ANNOTATION_WRAP;
    }
    if (nodeType == JavaElementType.CLASS) {
      return mySettings.CLASS_ANNOTATION_WRAP;
    }
    if (nodeType == JavaElementType.FIELD) {
      return mySettings.FIELD_ANNOTATION_WRAP;
    }
    if (nodeType == JavaElementType.PARAMETER) {
      return mySettings.PARAMETER_ANNOTATION_WRAP;
    }
    if (nodeType == JavaElementType.LOCAL_VARIABLE) {
      return mySettings.VARIABLE_ANNOTATION_WRAP;
    }
    return CodeStyleSettings.DO_NOT_WRAP;
  }

  @Nullable
  private Alignment arrangeChildAlignment(final ASTNode child, final AlignmentStrategy alignmentStrategy) {
    int role = getChildRole(child);
    final IElementType nodeType = myNode.getElementType();
    Alignment defaultAlignment = alignmentStrategy.getAlignment(child.getElementType());

    if (nodeType == JavaElementType.FOR_STATEMENT) {
      if (role == ChildRole.FOR_INITIALIZATION || role == ChildRole.CONDITION || role == ChildRole.FOR_UPDATE) {
        return defaultAlignment;
      }
      else {
        return null;
      }
    }
    else if (nodeType == JavaElementType.EXTENDS_LIST || nodeType == JavaElementType.IMPLEMENTS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST || role == ChildRole.IMPLEMENTS_KEYWORD) {
        return defaultAlignment;
      }
      else {
        return null;
      }
    }
    else if (nodeType == JavaElementType.THROWS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST) {
        return defaultAlignment;
      }
      else {
        return null;
      }
    }
    else if (nodeType == JavaElementType.CLASS) {
      if (role == ChildRole.CLASS_OR_INTERFACE_KEYWORD) return defaultAlignment;
      if (myIsAfterClassKeyword) return null;
      if (role == ChildRole.MODIFIER_LIST) return defaultAlignment;
      return null;
    }
    else if (ALIGN_IN_COLUMNS_ELEMENT_TYPES.contains(nodeType)) {
      return getVariableDeclarationSubElementAlignment(child);
    }
    else if (nodeType == JavaElementType.METHOD) {
      if (role == ChildRole.MODIFIER_LIST) return defaultAlignment;
      if (role == ChildRole.TYPE_PARAMETER_LIST) return defaultAlignment;
      if (role == ChildRole.TYPE) return defaultAlignment;
      if (role == ChildRole.NAME) return defaultAlignment;
      return null;
    }

    else if (nodeType == JavaElementType.ASSIGNMENT_EXPRESSION) {
      if (role == ChildRole.LOPERAND) return defaultAlignment;
      if (role == ChildRole.ROPERAND && child.getElementType() == JavaElementType.ASSIGNMENT_EXPRESSION) {
        return defaultAlignment;
      }
      else {
        return null;
      }
    }

    else if (child.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
      ASTNode previous = child.getTreePrev();
      // There is a special case - comment block that is located at the very start of the line. We don't reformat such a blocks,
      // hence, no alignment should be applied to them in order to avoid subsequent blocks aligned with the same alignment to
      // be located at the left editor edge as well.
      if (previous != null && previous.getElementType() == JavaTokenType.WHITE_SPACE && previous.getChars().length() > 0
          && previous.getChars().charAt(previous.getChars().length() - 1) == '\n') {
        return null;
      } else {
        return defaultAlignment;
      }
    }

    else if (nodeType == JavaElementType.MODIFIER_LIST) {
      // There is a possible case that modifier list contains from more than one elements, e.g. 'private final'. It's also possible
      // that the list is aligned. We want to apply alignment rule only to the first element then.
      ASTNode previous = child.getTreePrev();
      if (previous == null || previous.getTreeParent() != myNode) {
        return defaultAlignment;
      }
      return null;
    }

    else {
      return defaultAlignment;
    }
  }

  private static int getChildRole(ASTNode child) {
    return ((CompositeElement)child.getTreeParent()).getChildRole(child);
  }

  /**
   * Encapsulates alignment retrieval logic for variable declaration use-case assuming that given node is a child node
   * of basic variable declaration node.
   *
   * @param child   variable declaration child node which alignment is to be defined
   * @return        alignment to use for the given node
   * @see CodeStyleSettings#ALIGN_MULTILINE_SUBSEQUENT_DECLARATIONS
   */
  @Nullable
  private Alignment getVariableDeclarationSubElementAlignment(ASTNode child) {
    // The whole idea of variable declarations alignment is that complete declaration blocks which children are to be aligned hold
    // reference to the same AlignmentStrategy object, hence, reuse the same Alignment objects. So, there is no point in checking
    // if it's necessary to align sub-blocks if shared strategy is not defined.
    if (myAlignmentStrategy == null || !mySettings.ALIGN_MULTILINE_SUBSEQUENT_DECLARATIONS) {
      return null;
    }

    IElementType childType = child.getElementType();

    // We don't want to align subsequent identifiers in single-line declarations like 'int i1, i2, i3'. I.e. only 'i1'
    // should be aligned then.
    ASTNode previousNode = FormattingAstUtil.getPrevNonWhiteSpaceNode(child);
    if (childType == JavaTokenType.IDENTIFIER && (previousNode == null || previousNode.getElementType() == JavaTokenType.COMMA)) {
      return null;
    }

    return myAlignmentStrategy.getAlignment(childType);
  }

  /*
  private boolean isAfterClassKeyword(final ASTNode child) {
    ASTNode treePrev = child.getTreePrev();
    while (treePrev != null) {
      if (treePrev.getElementType() == ElementType.CLASS_KEYWORD ||
          treePrev.getElementType() == ElementType.INTERFACE_KEYWORD) {
        return true;
      }
      treePrev = treePrev.getTreePrev();
    }
    return false;
  }

  */
  private static Alignment createAlignment(final boolean alignOption, final Alignment defaultAlignment) {
    return alignOption ? createAlignmentOrDefault(null, defaultAlignment) : defaultAlignment;
  }

  private static Alignment createAlignment(Alignment base, final boolean alignOption, final Alignment defaultAlignment) {
    return alignOption ? createAlignmentOrDefault(base, defaultAlignment) : defaultAlignment;
  }

  @Nullable
  protected Wrap arrangeChildWrap(final ASTNode child, Wrap defaultWrap) {
    if (myAnnotationWrap != null) {
      try {
        return myAnnotationWrap;
      }
      finally {
        myAnnotationWrap = null;
      }
    }
    return myWrapManager.arrangeChildWrap(child, myNode, getSettings(), defaultWrap, this);
  }

  private static WrapType getWrapType(final int wrap) {
    switch (wrap) {
      case CodeStyleSettings.WRAP_ALWAYS:
        return WrapType.ALWAYS;
      case CodeStyleSettings.WRAP_AS_NEEDED:
        return WrapType.NORMAL;
      case CodeStyleSettings.DO_NOT_WRAP:
        return WrapType.NONE;
      default:
        return WrapType.CHOP_DOWN_IF_LONG;
    }
  }

  private ASTNode processParenthesisBlock(List<Block> result, ASTNode child,WrappingStrategy wrappingStrategy, final boolean doAlign) {

    myUseChildAttributes = true;

    final IElementType from = JavaTokenType.LPARENTH;
    final IElementType to = JavaTokenType.RPARENTH;

    return processParenthesisBlock(from, to, result, child, wrappingStrategy, doAlign);
  }

  

  private ASTNode processParenthesisBlock(final IElementType from,
                                    final IElementType to, final List<Block> result, ASTNode child,
                                    final WrappingStrategy wrappingStrategy, final boolean doAlign
  ) {
    final Indent externalIndent = Indent.getNoneIndent();
    final Indent internalIndent = Indent.getContinuationIndent();
    AlignmentStrategy alignmentStrategy = AlignmentStrategy.wrap(createAlignment(doAlign, null), ElementType.COMMA);
    setChildIndent(internalIndent);
    setChildAlignment(alignmentStrategy.getAlignment(null));
    Alignment bracketAlignment = mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION ? Alignment.createAlignment() : null;

    boolean isAfterIncomplete = false;

    ASTNode prev = child;
    while (child != null) {
      isAfterIncomplete = isAfterIncomplete || child.getElementType() == TokenType.ERROR_ELEMENT ||
                          child.getElementType() == JavaElementType.EMPTY_EXPRESSION;
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        if (child.getElementType() == from) {
          result.add(createJavaBlock(child, mySettings, externalIndent, null, bracketAlignment));
        }
        else if (child.getElementType() == to) {
          result.add(createJavaBlock(child, mySettings,
                                     isAfterIncomplete ? internalIndent : externalIndent,
                                     null,
                                     isAfterIncomplete ? alignmentStrategy.getAlignment(null) : bracketAlignment));
          return child;
        }
        else {
          final IElementType elementType = child.getElementType();
          processChild(result, child, alignmentStrategy.getAlignment(elementType), wrappingStrategy.getWrap(elementType), internalIndent);
          if (to == null) {//process only one statement
            return child;
          }
        }
        isAfterIncomplete = false;
      }
      prev = child;
      child = child.getTreeNext();
    }

    return prev;
  }

  @Nullable
  private ASTNode processEnumBlock(List<Block> result,
                                   ASTNode child,
                                   ASTNode last) {

    final WrappingStrategy wrappingStrategy = WrappingStrategy.createDoNotWrapCommaStrategy(Wrap
      .createWrap(getWrapType(mySettings.ENUM_CONSTANTS_WRAP), true));
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        result.add(createJavaBlock(child, mySettings, Indent.getNormalIndent(),
                                   wrappingStrategy.getWrap(child.getElementType()), AlignmentStrategy.getNullStrategy()));
        if (child == last) return child;
      }
      child = child.getTreeNext();
    }
    return null;
  }

  private void setChildAlignment(final Alignment alignment) {
    myChildAlignment = alignment;
  }

  private void setChildIndent(final Indent internalIndent) {
    myChildIndent = internalIndent;
  }

  private static Alignment createAlignmentOrDefault(Alignment base, final Alignment defaultAlignment) {
    if (defaultAlignment == null) {
      return base == null ? Alignment.createAlignment() : Alignment.createChildAlignment(base);
    }
    else {
      return defaultAlignment;
    }
  }

  private int getBraceStyle() {
    final PsiElement psiNode = SourceTreeToPsiMap.treeElementToPsi(myNode);
    if (psiNode instanceof PsiClass) {
      return mySettings.CLASS_BRACE_STYLE;
    }
    else if (psiNode instanceof PsiMethod) {
      return mySettings.METHOD_BRACE_STYLE;
    }

    else if (psiNode instanceof PsiCodeBlock && psiNode.getParent() != null && psiNode.getParent() instanceof PsiMethod) {
      return mySettings.METHOD_BRACE_STYLE;
    }

    else {
      return mySettings.BRACE_STYLE;
    }

  }

  protected Indent getCodeBlockInternalIndent(final int baseChildrenIndent) {
    if (isTopLevelClass() && mySettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS) {
      return Indent.getNoneIndent();
    }

    final int braceStyle = getBraceStyle();
    return braceStyle == CodeStyleSettings.NEXT_LINE_SHIFTED ?
           createNormalIndent(baseChildrenIndent - 1)
           : createNormalIndent(baseChildrenIndent);
  }

  protected static Indent createNormalIndent(final int baseChildrenIndent) {
    if (baseChildrenIndent == 1) {
      return Indent.getNormalIndent();
    }
    else if (baseChildrenIndent <= 0) {
      return Indent.getNoneIndent();
    }
    else {
      LOG.assertTrue(false);
      return Indent.getNormalIndent();
    }
  }

  private boolean isTopLevelClass() {
    return myNode.getElementType() == JavaElementType.CLASS &&
           SourceTreeToPsiMap.treeElementToPsi(myNode.getTreeParent()) instanceof PsiFile;
  }

  protected Indent getCodeBlockExternalIndent() {
    final int braceStyle = getBraceStyle();
    if (braceStyle == CodeStyleSettings.END_OF_LINE || braceStyle == CodeStyleSettings.NEXT_LINE ||
        braceStyle == CodeStyleSettings.NEXT_LINE_IF_WRAPPED) {
      return Indent.getNoneIndent();
    }
    else {
      return Indent.getNormalIndent();
    }
  }

  protected Indent getCodeBlockChildExternalIndent(final int newChildIndex) {
    final int braceStyle = getBraceStyle();
    if (!isAfterCodeBlock(newChildIndex)) {
      return Indent.getNormalIndent();
    }
    else if (braceStyle == CodeStyleSettings.NEXT_LINE ||
        braceStyle == CodeStyleSettings.NEXT_LINE_IF_WRAPPED ||
        braceStyle == CodeStyleSettings.END_OF_LINE) {
      return Indent.getNoneIndent();
    }
    else {
      return Indent.getNormalIndent();
    }
  }

  private boolean isAfterCodeBlock(final int newChildIndex) {
    if (newChildIndex == 0) return false;
    Block blockBefore = getSubBlocks().get(newChildIndex - 1);
    if (blockBefore instanceof CodeBlockBlock) {
      return true;
    }
    return false;
  }

  /**
   * <b>Note:</b> this method is considered to be a legacy heritage and is assumed to be removed as soon as formatting processing
   * is refactored
   *
   * @param elementType   target element type
   * @return              <code>null</code> all the time
   */
  public Wrap getReservedWrap(IElementType elementType) {
    return null;
  }

  /**
   * Defines contract for associating operation type and particular wrap instance. I.e. given wrap object <b>may</b> be returned
   * from subsequent {@link #getReservedWrap(IElementType)} call if given operation type is used as an argument there.
   * <p/>
   * Default implementation ({@link AbstractJavaBlock#setReservedWrap(Wrap, IElementType)}) does nothing.
   * <p/>
   * <b>Note:</b> this method is considered to be a legacy heritage and is assumed to be removed as soon as formatting processing
   * is refactored
   *
   * @param reservedWrap    reserved wrap instance
   * @param operationType   target operation type to associate with the given wrap instance
   */
  protected void setReservedWrap(final Wrap reservedWrap, final IElementType operationType) {
  }

  @Nullable
  protected static ASTNode getTreeNode(final Block child2) {
    if (child2 instanceof JavaBlock) {
      return ((JavaBlock)child2).getFirstTreeNode();
    }
    else if (child2 instanceof LeafBlock) {
      return ((LeafBlock)child2).getTreeNode();
    }
    else {
      return null;
    }
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myUseChildAttributes) {
      return new ChildAttributes(myChildIndent, myChildAlignment);
    }
    else if (isAfter(newChildIndex, new IElementType[]{JavaDocElementType.DOC_COMMENT})) {
      return new ChildAttributes(Indent.getNoneIndent(), myChildAlignment);
    }
    else {
      return super.getChildAttributes(newChildIndex);
    }
  }

  @Nullable
  protected Indent getChildIndent() {
    return getChildIndent(myNode);
  }

  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  protected boolean isAfter(final int newChildIndex, final IElementType[] elementTypes) {
    if (newChildIndex == 0) return false;
    final Block previousBlock = getSubBlocks().get(newChildIndex - 1);
    if (!(previousBlock instanceof AbstractBlock)) return false;
    final IElementType previousElementType = ((AbstractBlock)previousBlock).getNode().getElementType();
    for (IElementType elementType : elementTypes) {
      if (previousElementType == elementType) return true;
    }
    return false;
  }

  @Nullable
  protected Alignment getUsedAlignment(final int newChildIndex) {
    final List<Block> subBlocks = getSubBlocks();
    for (int i = 0; i < newChildIndex; i++) {
      if (i >= subBlocks.size()) return null;
      final Block block = subBlocks.get(i);
      final Alignment alignment = block.getAlignment();
      if (alignment != null) return alignment;
    }
    return null;
  }

  public boolean isLeaf() {
    return JavaBlockUtil.mayShiftIndentInside(myNode);
  }

  @Nullable
  protected ASTNode composeCodeBlock(final ArrayList<Block> result, ASTNode child, final Indent indent, final int childrenIndent) {
    final ArrayList<Block> localResult = new ArrayList<Block>();
    processChild(localResult, child, AlignmentStrategy.getNullStrategy(), null, Indent.getNoneIndent());
    child = child.getTreeNext();

    AlignmentStrategy varDeclarationAlignmentStrategy
      = AlignmentStrategy.createAlignmentPerTypeStrategy(VAR_DECLARATION_ELEMENT_TYPES_TO_ALIGN, true);

    while (child != null) {
      // We consider that subsequent fields shouldn't be aligned if they are separated by blank line(s).
      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        if (!shouldUseVarDeclarationAlignment(child)) {
          // Reset var declaration alignment.
          varDeclarationAlignmentStrategy = AlignmentStrategy.createAlignmentPerTypeStrategy(VAR_DECLARATION_ELEMENT_TYPES_TO_ALIGN, true);
        }
        final boolean rBrace = isRBrace(child);
        final Indent childIndent = rBrace ? Indent.getNoneIndent() : getCodeBlockInternalIndent(childrenIndent);
        AlignmentStrategy alignmentStrategyToUse = ALIGN_IN_COLUMNS_ELEMENT_TYPES.contains(child.getElementType())
                                                      ? varDeclarationAlignmentStrategy : AlignmentStrategy.getNullStrategy();
        child = processChild(localResult, child, alignmentStrategyToUse, null, childIndent);
        if (rBrace) {
          result.add(createCodeBlockBlock(localResult, indent, childrenIndent));
          return child;
        }
      }
      
      if (child != null) {
        child = child.getTreeNext();
      }
    }
    result.add(createCodeBlockBlock(localResult, indent, childrenIndent));
    return null;
  }

  /**
   * Allows to answer if special 'variable declaration alignment' strategy should be used for the given node.
   * I.e. given node is supposed to be parent node of sub-nodes that should be aligned 'by-columns'.
   * <p/>
   * The main idea of that strategy is to provide alignment in columns like the one below:
   * <pre>
   *     public int    i   = 1;
   *     public double ddd = 2;
   * </pre>
   *
   * @param node    node
   * @return
   */
  protected boolean shouldUseVarDeclarationAlignment(ASTNode node) {
    return mySettings.ALIGN_MULTILINE_SUBSEQUENT_DECLARATIONS && ALIGN_IN_COLUMNS_ELEMENT_TYPES.contains(node.getElementType())
           && !myAlignmentInColumnsHelper.useDifferentVarDeclarationAlignment(node, ALIGNMENT_IN_COLUMNS_CONFIG);
  }

  private SyntheticCodeBlock createCodeBlockBlock(final ArrayList<Block> localResult, final Indent indent, final int childrenIndent) {
    final SyntheticCodeBlock result = new SyntheticCodeBlock(localResult, null, getSettings(), indent, null);
    result.setChildAttributes(new ChildAttributes(getCodeBlockInternalIndent(childrenIndent), null));
    return result;
  }
}
