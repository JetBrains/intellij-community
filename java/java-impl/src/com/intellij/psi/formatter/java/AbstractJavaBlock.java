/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.formatter.java.wrap.JavaWrapManager;
import com.intellij.psi.formatter.java.wrap.ReservedWrapsProvider;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.ShiftIndentInsideHelper;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.java.ClassElement;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractJavaBlock extends AbstractBlock implements JavaBlock, ReservedWrapsProvider {

  /**
   * Holds types of the elements for which <code>'align in column'</code> rule may be preserved.
   *
   * @see CodeStyleSettings#ALIGN_GROUP_FIELD_DECLARATIONS
   */
  protected static final Set<IElementType> ALIGN_IN_COLUMNS_ELEMENT_TYPES = Collections.unmodifiableSet(new HashSet<IElementType>(
    Arrays.asList(JavaElementType.FIELD)));

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.java.AbstractJavaBlock");

  /**
   * Shared thread-safe config object to use during <code>'align in column'</code> processing.
   *
   * @see CodeStyleSettings#ALIGN_GROUP_FIELD_DECLARATIONS
   */
  private static final AlignmentInColumnsConfig ALIGNMENT_IN_COLUMNS_CONFIG = new AlignmentInColumnsConfig(
    TokenSet.create(JavaTokenType.IDENTIFIER), JavaJspElementType.WHITE_SPACE_BIT_SET, ElementType.JAVA_COMMENT_BIT_SET,
    TokenSet.create(JavaTokenType.EQ), TokenSet.create(JavaElementType.FIELD));

  /**
   * Enumerates types of variable declaration sub-elements that should be aligned in columns.
   */
  private static final Set<IElementType> VAR_DECLARATION_ELEMENT_TYPES_TO_ALIGN = new HashSet<IElementType>(Arrays.asList(
    JavaElementType.MODIFIER_LIST, JavaElementType.TYPE, JavaTokenType.IDENTIFIER, JavaTokenType.EQ
  ));

  @NotNull protected final CommonCodeStyleSettings mySettings;
  protected final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final Indent myIndent;
  protected Indent myChildIndent;
  protected Alignment myChildAlignment;
  protected boolean myUseChildAttributes = false;
  @NotNull protected final AlignmentStrategy myAlignmentStrategy;
  private boolean myIsAfterClassKeyword = false;
  private Wrap myAnnotationWrap = null;

  protected Alignment myReservedAlignment;
  protected Alignment myReservedAlignment2;

  private final JavaWrapManager myWrapManager;
  private final AlignmentInColumnsHelper myAlignmentInColumnsHelper;

  protected AbstractJavaBlock(@NotNull final ASTNode node,
                              final Wrap wrap,
                              final Alignment alignment,
                              final Indent indent,
                              @NotNull final CommonCodeStyleSettings settings) {
    this(node, wrap, indent, settings, JavaWrapManager.INSTANCE, AlignmentStrategy.wrap(alignment), AlignmentInColumnsHelper.INSTANCE);
  }

  protected AbstractJavaBlock(@NotNull final ASTNode node,
                              final Wrap wrap,
                              @NotNull final AlignmentStrategy alignmentStrategy,
                              final Indent indent,
                              @NotNull final CommonCodeStyleSettings settings) {
    this(node, wrap, indent, settings, JavaWrapManager.INSTANCE, alignmentStrategy, AlignmentInColumnsHelper.INSTANCE);
  }

  protected AbstractJavaBlock(@NotNull final ASTNode node,
                              final Wrap wrap,
                              final Indent indent,
                              @NotNull final CommonCodeStyleSettings settings,
                              final JavaWrapManager wrapManager,
                              @NotNull final AlignmentStrategy alignmentStrategy,
                              AlignmentInColumnsHelper alignmentInColumnsHelper) {
    super(node, wrap, createBlockAlignment(alignmentStrategy, node));
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myIndent = indent;
    myWrapManager = wrapManager;
    myAlignmentStrategy = alignmentStrategy;
    myAlignmentInColumnsHelper = alignmentInColumnsHelper;
  }

  @Nullable
  private static Alignment createBlockAlignment(@NotNull AlignmentStrategy strategy, @NotNull ASTNode node) {
    // There is a possible case that 'implements' section is incomplete (e.g. ends with comma). We may want to align lbrace
    // to the first implemented interface reference then.
    if (node.getElementType() == JavaElementType.IMPLEMENTS_LIST) {
      return null;
    }
    return strategy.getAlignment(node.getElementType());
  }
  
  @NotNull
  public static Block createJavaBlock(@NotNull final ASTNode child,
                                      @NotNull final CommonCodeStyleSettings settings,
                                      @Nullable final Indent indent,
                                      @Nullable Wrap wrap,
                                      Alignment alignment) {
    return createJavaBlock(child, settings, indent, wrap, AlignmentStrategy.wrap(alignment));
  }

  @NotNull
  public static Block createJavaBlock(@NotNull final ASTNode child,
                                      @NotNull final CommonCodeStyleSettings settings,
                                      final Indent indent,
                                      @Nullable Wrap wrap,
                                      @NotNull AlignmentStrategy alignmentStrategy) {
    return createJavaBlock(child, settings, indent, wrap, alignmentStrategy, -1);
  }

  @NotNull
  public static Block createJavaBlock(@NotNull final ASTNode child,
                                      @NotNull final CommonCodeStyleSettings settings,
                                      @Nullable final Indent indent,
                                      Wrap wrap,
                                      @NotNull AlignmentStrategy alignmentStrategy,
                                      int startOffset) {
    Indent actualIndent = indent == null ? getDefaultSubtreeIndent(child, getJavaIndentOptions(settings)) : indent;
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
    if (child instanceof PsiComment && child instanceof PsiLanguageInjectionHost && InjectedLanguageUtil
      .hasInjections((PsiLanguageInjectionHost)child)) {
      return new CommentWithInjectionBlock(child, wrap, alignment, indent, settings);
    }
    if (child instanceof LeafElement) {
      final LeafBlock block = new LeafBlock(child, wrap, alignment, actualIndent);
      block.setStartOffset(startOffset);
      return block;
    }
    else if (isLikeExtendsList(elementType)) {
      return new ExtendsListBlock(child, wrap, alignmentStrategy, settings);
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

  @NotNull
  public static Block createJavaBlock(@NotNull ASTNode child, @NotNull CommonCodeStyleSettings settings) {
    return createJavaBlock(child, settings, getDefaultSubtreeIndent(child, getJavaIndentOptions(settings)),
                           null, AlignmentStrategy.getNullStrategy());
  }

  @NotNull
  private static CommonCodeStyleSettings.IndentOptions getJavaIndentOptions(CommonCodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions();
    assert indentOptions != null : "Java indent options are not initialized";
    return indentOptions;
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


  @Nullable
  private static Indent getDefaultSubtreeIndent(@NotNull ASTNode child, @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    final ASTNode parent = child.getTreeParent();
    final IElementType childNodeType = child.getElementType();
    if (childNodeType == JavaElementType.ANNOTATION) {
      if (parent.getPsi() instanceof PsiArrayInitializerMemberValue) {
        return Indent.getNormalIndent();
      }
      return Indent.getNoneIndent();
    }

    final ASTNode prevElement = FormatterUtil.getPreviousNonWhitespaceSibling(child);
    if (prevElement != null && prevElement.getElementType() == JavaElementType.MODIFIER_LIST) {
      return Indent.getNoneIndent();
    }

    if (childNodeType == JavaDocElementType.DOC_TAG) return Indent.getNoneIndent();
    if (childNodeType == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) return Indent.getSpaceIndent(1);
    if (child.getPsi() instanceof PsiFile) return Indent.getNoneIndent();
    if (parent != null) {
      final Indent defaultChildIndent = getChildIndent(parent, indentOptions);
      if (defaultChildIndent != null) return defaultChildIndent;
    }
    if (child.getTreeParent() instanceof PsiLambdaExpression && child instanceof PsiCodeBlock) {
      return Indent.getNoneIndent();
    }

    return null;
  }

  @Nullable
  private static Indent getChildIndent(@NotNull ASTNode parent, @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    final IElementType parentType = parent.getElementType();
    if (parentType == JavaElementType.MODIFIER_LIST) return Indent.getNoneIndent();
    if (parentType == JspElementType.JSP_CODE_BLOCK) return Indent.getNormalIndent();
    if (parentType == JspElementType.JSP_CLASS_LEVEL_DECLARATION_STATEMENT) return Indent.getNormalIndent();
    if (parentType == TokenType.DUMMY_HOLDER) return Indent.getNoneIndent();
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
    if (parentType == JavaElementType.FIELD) return Indent.getContinuationWithoutFirstIndent(indentOptions.USE_RELATIVE_INDENTS);
    if (parentType == JavaElementType.EXPRESSION_STATEMENT) return Indent.getNoneIndent();
    if (SourceTreeToPsiMap.treeElementToPsi(parent) instanceof PsiFile) {
      return Indent.getNoneIndent();
    }
    return null;
  }

  protected static boolean isRBrace(@NotNull final ASTNode child) {
    return child.getElementType() == JavaTokenType.RBRACE;
  }

  @Nullable
  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    return JavaSpacePropertyProcessor.getSpacing(getTreeNode(child2), mySettings);
  }

  @Override
  public ASTNode getFirstTreeNode() {
    return myNode;
  }

  @Override
  public Indent getIndent() {
    return myIndent;
  }

  protected static boolean isStatement(final ASTNode child, @Nullable final ASTNode parentNode) {
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
    IElementType nodeType = myNode.getElementType();
    if (nodeType == JavaElementType.POLYADIC_EXPRESSION) nodeType = JavaElementType.BINARY_EXPRESSION;
    if (nodeType == JavaElementType.ASSIGNMENT_EXPRESSION) {
      if (myNode.getTreeParent() != null
          && myNode.getTreeParent().getElementType() == JavaElementType.ASSIGNMENT_EXPRESSION
          && myAlignment != null) {
        return myAlignment;
      }
      return createAlignment(mySettings.ALIGN_MULTILINE_ASSIGNMENT, null);
    }
    if (nodeType == JavaElementType.PARENTH_EXPRESSION) {
      return createAlignment(mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION, null);
    }
    if (nodeType == JavaElementType.CONDITIONAL_EXPRESSION) {
      return createAlignment(mySettings.ALIGN_MULTILINE_TERNARY_OPERATION, null);
    }
    if (nodeType == JavaElementType.FOR_STATEMENT) {
      return createAlignment(mySettings.ALIGN_MULTILINE_FOR, null);
    }
    if (nodeType == JavaElementType.EXTENDS_LIST || nodeType == JavaElementType.IMPLEMENTS_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_EXTENDS_LIST, null);
    }
    if (nodeType == JavaElementType.THROWS_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_THROWS_LIST, null);
    }
    if (nodeType == JavaElementType.PARAMETER_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_PARAMETERS, null);
    }
    if (nodeType == JavaElementType.RESOURCE_LIST) {
      return createAlignment(mySettings.ALIGN_MULTILINE_RESOURCES, null);
    }
    if (nodeType == JavaElementType.BINARY_EXPRESSION) {
      Alignment defaultAlignment = null;
      if (shouldInheritAlignment()) {
        defaultAlignment = myAlignment;
      }
      return createAlignment(mySettings.ALIGN_MULTILINE_BINARY_OPERATION, defaultAlignment);
    }
    if (nodeType == JavaElementType.CLASS || nodeType == JavaElementType.METHOD) {
      return Alignment.createAlignment();
    }
    return null;
  }

  @Nullable
  protected Alignment createChildAlignment2(@Nullable Alignment base) {
    final IElementType nodeType = myNode.getElementType();
    if (nodeType == JavaElementType.CONDITIONAL_EXPRESSION) {
      return base == null ? createAlignment(mySettings.ALIGN_MULTILINE_TERNARY_OPERATION, null) : createAlignment(base, mySettings.ALIGN_MULTILINE_TERNARY_OPERATION, null);
    }
    return null;
  }

  @Nullable
  protected Alignment chooseAlignment(@Nullable Alignment alignment, @Nullable Alignment alignment2, @NotNull ASTNode child) {
    if (preferSlaveAlignment(child)) {
      return alignment2;
    }
    return alignment;
  }

  private boolean preferSlaveAlignment(@NotNull final ASTNode child) {
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
    if (myNode instanceof PsiPolyadicExpression) {
      final ASTNode treeParent = myNode.getTreeParent();
      if (treeParent instanceof PsiPolyadicExpression) {
        return JavaFormatterUtil.areSamePriorityBinaryExpressions(myNode, treeParent);
      }
    }
    return false;
  }


  @Nullable
  protected ASTNode processChild(@NotNull final List<Block> result,
                                 @NotNull ASTNode child,
                                 Alignment defaultAlignment,
                                 final Wrap defaultWrap,
                                 final Indent childIndent) {
    return processChild(result, child, AlignmentStrategy.wrap(defaultAlignment), defaultWrap, childIndent, -1);
  }

  @Nullable
  protected ASTNode processChild(@NotNull final List<Block> result,
                                 @NotNull ASTNode child,
                                 @NotNull AlignmentStrategy alignmentStrategy,
                                 @Nullable final Wrap defaultWrap,
                                 final Indent childIndent) {
    return processChild(result, child, alignmentStrategy, defaultWrap, childIndent, -1);
  }

  @Nullable
  protected ASTNode processChild(@NotNull final List<Block> result,
                                 @NotNull ASTNode child,
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
      IElementType nodeType = myNode.getElementType();
      if (nodeType == JavaElementType.POLYADIC_EXPRESSION) nodeType = JavaElementType.BINARY_EXPRESSION;

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
        final Wrap wrap;
        Wrap reservedWrap = getReservedWrap(JavaElementType.MODIFIER_LIST);
        // There is a possible case that particular annotated method definition is too long. We may wrap either after annotation
        // or after opening lbrace then. Our strategy is to wrap after annotation whenever possible.
        if (reservedWrap == null) {
          wrap = Wrap.createWrap(getWrapType(mySettings.METHOD_PARAMETERS_WRAP), false);
        }
        else {
          wrap = Wrap.createChildWrap(reservedWrap, getWrapType(mySettings.METHOD_PARAMETERS_WRAP), false);
        }
        child = processParenthesisBlock(result, child,
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  mySettings.ALIGN_MULTILINE_PARAMETERS);
      }
      else if (childType == JavaTokenType.LPARENTH && nodeType == JavaElementType.RESOURCE_LIST) {
        final Wrap reservedWrap = getReservedWrap(JavaElementType.MODIFIER_LIST);
        final Wrap wrap = reservedWrap != null
                          ? Wrap.createChildWrap(reservedWrap, getWrapType(mySettings.RESOURCE_LIST_WRAP), false)
                          : Wrap.createWrap(getWrapType(mySettings.RESOURCE_LIST_WRAP), false);
        child = processParenthesisBlock(result, child, WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                        mySettings.ALIGN_MULTILINE_RESOURCES);
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
               || childType == JavaElementType.DECLARATION_STATEMENT && myNode.getElementType() == JavaElementType.METHOD)
      {
        result.add(new SimpleJavaBlock(child, defaultWrap, alignmentStrategy, childIndent, mySettings));
      }
      else {
        AlignmentStrategy alignmentStrategyToUse = AlignmentStrategy.wrap(arrangeChildAlignment(child, alignmentStrategy));
        if (myAlignmentStrategy.getAlignment(nodeType, childType) != null
            && (nodeType == JavaElementType.IMPLEMENTS_LIST || nodeType == JavaElementType.CLASS))
        {
          alignmentStrategyToUse = myAlignmentStrategy;
        }
        final Block block = createJavaBlock(
          child, mySettings, childIndent, arrangeChildWrap(child, defaultWrap), alignmentStrategyToUse, childOffset
        );

        if (childType == JavaElementType.MODIFIER_LIST && containsAnnotations(child)) {
          myAnnotationWrap = Wrap.createWrap(getWrapType(getAnnotationWrapType(child)), true);
        }

        if (block instanceof AbstractJavaBlock) {
          final AbstractJavaBlock javaBlock = (AbstractJavaBlock)block;
          if (nodeType == JavaElementType.METHOD_CALL_EXPRESSION && childType == JavaElementType.REFERENCE_EXPRESSION
              || nodeType == JavaElementType.REFERENCE_EXPRESSION && childType == JavaElementType.METHOD_CALL_EXPRESSION)
          {
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
          else if (childType == JavaElementType.PARAMETER_LIST && nodeType == JavaElementType.METHOD) {
            // We prefer wrapping after method annotation to wrapping method parameter list, hence, deliver target wrap object
            // to child block if necessary.
            if (!result.isEmpty()) {
              Block firstChildBlock = result.get(0);
              if (firstChildBlock instanceof AbstractJavaBlock) {
                AbstractJavaBlock childJavaBlock = (AbstractJavaBlock)firstChildBlock;
                if (firstChildIsAnnotation(childJavaBlock.getNode())) {
                  javaBlock.setReservedWrap(childJavaBlock.getReservedWrap(JavaElementType.MODIFIER_LIST), JavaElementType.MODIFIER_LIST);
                }
              }
            }
          }
        }

        result.add(block);
      }
    }


    return child;
  }

  private ASTNode processField(@NotNull final List<Block> result,
                               ASTNode child,
                               @NotNull final AlignmentStrategy alignmentStrategy,
                               final Wrap defaultWrap,
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
          localResult.add(createJavaBlock(child,
                                          getSettings(),
                                          Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS),
                                          arrangeChildWrap(child, defaultWrap),
                                          alignmentStrategy)
          );
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

  /**
   * Serves for processing composite field definitions as a single formatting block.
   * <p/>
   * <code>'Composite field definition'</code> looks like {@code 'int i1, i2 = 2'}. It produces two nodes of type
   * {@link JavaElementType#FIELD} - {@code 'int i1'} and {@code 'i2 = 2'}. This method returns the second node if the first one
   * is given (the given node is returned for <code>'single'</code> fields).
   * 
   * @param child     child field node to check
   * @return          last child field node at the field group identified by the given node if any; given child otherwise
   */
  @NotNull
  private static ASTNode findLastFieldInGroup(@NotNull final ASTNode child) {
    PsiElement psi = child.getPsi();
    if (psi == null) {
      return child;
    }
    final PsiTypeElement typeElement = ((PsiVariable)psi).getTypeElement();
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
        if (compoundFieldPart(currentNode)) {
          currentResult = currentNode;
        }
        else {
          return currentResult;
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
  private ASTNode processTernaryOperationRange(@NotNull final List<Block> result,
                                               @NotNull final ASTNode child,
                                               final Wrap defaultWrap,
                                               final Indent childIndent) {
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
    return current.getTreePrev();
  }

  private boolean isTernaryOperationSign(@NotNull final ASTNode child) {
    if (myNode.getElementType() != JavaElementType.CONDITIONAL_EXPRESSION) return false;
    final int role = ((CompositeElement)child.getTreeParent()).getChildRole(child);
    return role == ChildRole.OPERATION_SIGN || role == ChildRole.COLON;
  }

  @NotNull
  private Block createMethodCallExpressionBlock(@NotNull final ASTNode node, final Wrap blockWrap, final Alignment alignment) {
    final ArrayList<ASTNode> nodes = new ArrayList<ASTNode>();
    collectNodes(nodes, node);
    return new ChainMethodCallsBlockBuilder(alignment, blockWrap).build(nodes);
  }

  @NotNull
  private Block createSyntheticBlock(@NotNull final List<ASTNode> subNodes, final Wrap wrap, @Nullable final Alignment alignment) {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    final ASTNode firstNode = subNodes.get(0);
    if (firstNode.getElementType() == JavaTokenType.DOT) {
      subBlocks.add(createJavaBlock(firstNode, getSettings(), Indent.getNoneIndent(), null, AlignmentStrategy.getNullStrategy()));
      subNodes.remove(0);
      if (!subNodes.isEmpty()) {
        subBlocks.add(createSyntheticBlock(subNodes, wrap, null));
      }
      return new SyntheticCodeBlock(subBlocks, alignment, mySettings,
                                    Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS), wrap);
    }
    return new SyntheticCodeBlock(createJavaBlocks(subNodes), alignment, mySettings,
                                  Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS), null);
  }

  @NotNull
  private List<Block> createJavaBlocks(@NotNull final List<ASTNode> subNodes) {
    final ArrayList<Block> result = new ArrayList<Block>();
    for (ASTNode node : subNodes) {
      result.add(createJavaBlock(node, getSettings(), Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS), null,
                                 AlignmentStrategy.getNullStrategy()));
    }
    return result;
  }

  private static void collectNodes(@NotNull List<ASTNode> nodes, @NotNull ASTNode node) {
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

  private static boolean firstChildIsAnnotation(@NotNull final ASTNode child) {
    ASTNode current = child.getFirstChildNode();
    while (current != null && current.getElementType() == TokenType.WHITE_SPACE) {
      current = current.getTreeNext();
    }
    return current != null && current.getElementType() == JavaElementType.ANNOTATION;
  }
  
  private static boolean lastChildIsAnnotation(@NotNull final ASTNode child) {
    ASTNode current = child.getLastChildNode();
    while (current != null && current.getElementType() == TokenType.WHITE_SPACE) {
      current = current.getTreePrev();
    }
    return current != null && current.getElementType() == JavaElementType.ANNOTATION;
  }

  private static boolean containsAnnotations(@NotNull final ASTNode child) {
    PsiElement psi = child.getPsi();
    return psi instanceof PsiModifierList && ((PsiModifierList)psi).getAnnotations().length > 0;
  }

  private int getAnnotationWrapType(@NotNull ASTNode child) {
    final IElementType nodeType = myNode.getElementType();
    if (nodeType == JavaElementType.METHOD) {
      return mySettings.METHOD_ANNOTATION_WRAP;
    }
    if (nodeType == JavaElementType.CLASS) {
      // There is a possible case that current document state is invalid from language syntax point of view, e.g. the user starts
      // typing field definition and re-formatting is triggered by 'auto insert javadoc' processing. Example:
      //     class Test {
      //         @NotNull Object
      //     }
      // Here '@NotNull' has a 'class' node as a parent but we want to use field annotation setting value. Hence, we check if subsequent
      // parsed info is valid.
      for (ASTNode node = child.getTreeNext(); node != null; node = node.getTreeNext()) {
        if (TokenType.WHITE_SPACE == node.getElementType() || node instanceof PsiTypeElement) {
          continue;
        }
        if (node instanceof PsiErrorElement) {
          return mySettings.FIELD_ANNOTATION_WRAP;
        }
      }
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
    return CommonCodeStyleSettings.DO_NOT_WRAP;
  }

  @Nullable
  private Alignment arrangeChildAlignment(@NotNull final ASTNode child, @NotNull final AlignmentStrategy alignmentStrategy) {
    int role = getChildRole(child);
    final IElementType nodeType = myNode.getElementType();
    Alignment defaultAlignment = alignmentStrategy.getAlignment(child.getElementType());

    if (nodeType == JavaElementType.FOR_STATEMENT) {
      if (role == ChildRole.FOR_INITIALIZATION || role == ChildRole.CONDITION || role == ChildRole.FOR_UPDATE) {
        return defaultAlignment;
      }
      return null;
    }
    else if (nodeType == JavaElementType.EXTENDS_LIST || nodeType == JavaElementType.IMPLEMENTS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST || role == ChildRole.IMPLEMENTS_KEYWORD) {
        return defaultAlignment;
      }
      return null;
    }
    else if (nodeType == JavaElementType.THROWS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST) {
        return defaultAlignment;
      }
      return null;
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
      if (role == ChildRole.THROWS_LIST && mySettings.ALIGN_THROWS_KEYWORD) return defaultAlignment;
      return null;
    }

    else if (nodeType == JavaElementType.ASSIGNMENT_EXPRESSION) {
      if (role == ChildRole.LOPERAND) return defaultAlignment;
      if (role == ChildRole.ROPERAND && child.getElementType() == JavaElementType.ASSIGNMENT_EXPRESSION) {
        return defaultAlignment;
      }
      return null;
    }

    else if (child.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
      ASTNode previous = child.getTreePrev();
      // There is a special case - comment block that is located at the very start of the line. We don't reformat such a blocks,
      // hence, no alignment should be applied to them in order to avoid subsequent blocks aligned with the same alignment to
      // be located at the left editor edge as well.
      CharSequence prevChars;
      if (previous != null && previous.getElementType() == TokenType.WHITE_SPACE && (prevChars = previous.getChars()).length() > 0
          && prevChars.charAt(prevChars.length() - 1) == '\n') {
        return null;
      }
      return defaultAlignment;
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

  private static int getChildRole(@NotNull ASTNode child) {
    return ((CompositeElement)child.getTreeParent()).getChildRole(child);
  }

  /**
   * Encapsulates alignment retrieval logic for variable declaration use-case assuming that given node is a child node
   * of basic variable declaration node.
   *
   * @param child   variable declaration child node which alignment is to be defined
   * @return        alignment to use for the given node
   * @see CodeStyleSettings#ALIGN_GROUP_FIELD_DECLARATIONS
   */
  @Nullable
  private Alignment getVariableDeclarationSubElementAlignment(@NotNull ASTNode child) {
    // The whole idea of variable declarations alignment is that complete declaration blocks which children are to be aligned hold
    // reference to the same AlignmentStrategy object, hence, reuse the same Alignment objects. So, there is no point in checking
    // if it's necessary to align sub-blocks if shared strategy is not defined.
    if (!mySettings.ALIGN_GROUP_FIELD_DECLARATIONS) {
      return null;
    }

    IElementType childType = child.getElementType();

    // We don't want to align subsequent identifiers in single-line declarations like 'int i1, i2, i3'. I.e. only 'i1'
    // should be aligned then.
    ASTNode previousNode = FormatterUtil.getPreviousNonWhitespaceSibling(child);
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
  
  @Nullable
  private static Alignment createAlignment(final boolean alignOption, @Nullable final Alignment defaultAlignment) {
    return alignOption ? createAlignmentOrDefault(null, defaultAlignment) : defaultAlignment;
  }

  @Nullable
  private static Alignment createAlignment(Alignment base, final boolean alignOption, @Nullable final Alignment defaultAlignment) {
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

  @NotNull
  private static WrapType getWrapType(final int wrap) {
    switch (wrap) {
      case CommonCodeStyleSettings.WRAP_ALWAYS:
        return WrapType.ALWAYS;
      case CommonCodeStyleSettings.WRAP_AS_NEEDED:
        return WrapType.NORMAL;
      case CommonCodeStyleSettings.DO_NOT_WRAP:
        return WrapType.NONE;
      default:
        return WrapType.CHOP_DOWN_IF_LONG;
    }
  }

  @NotNull
  private ASTNode processParenthesisBlock(@NotNull List<Block> result,
                                          @NotNull ASTNode child,
                                          @NotNull WrappingStrategy wrappingStrategy,
                                          final boolean doAlign) {

    myUseChildAttributes = true;

    final IElementType from = JavaTokenType.LPARENTH;
    final IElementType to = JavaTokenType.RPARENTH;

    return processParenthesisBlock(from, to, result, child, wrappingStrategy, doAlign);
  }


  @NotNull
  private ASTNode processParenthesisBlock(@NotNull IElementType from,
                                          @Nullable final IElementType to,
                                          @NotNull final List<Block> result,
                                          @NotNull ASTNode child,
                                          @NotNull final WrappingStrategy wrappingStrategy,
                                          final boolean doAlign) {
    final Indent externalIndent = Indent.getNoneIndent();
    final Indent internalIndent = Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
    final Indent internalIndentEnforcedToChildren = Indent.getIndent(Indent.Type.CONTINUATION, myIndentSettings.USE_RELATIVE_INDENTS, true);
    AlignmentStrategy alignmentStrategy = AlignmentStrategy.wrap(createAlignment(doAlign, null), JavaTokenType.COMMA);
    setChildIndent(internalIndent);
    setChildAlignment(alignmentStrategy.getAlignment(null));
    boolean methodParametersBlock = true;
    ASTNode lBracketParent = child.getTreeParent();
    if (lBracketParent != null) {
      ASTNode methodCandidate = lBracketParent.getTreeParent();
      methodParametersBlock = methodCandidate != null && (methodCandidate.getElementType() == JavaElementType.METHOD
                                                          || methodCandidate.getElementType() == JavaElementType.METHOD_CALL_EXPRESSION);
    }
    Alignment bracketAlignment = methodParametersBlock && mySettings.ALIGN_MULTILINE_METHOD_BRACKETS ? Alignment.createAlignment() : null;
    AlignmentStrategy anonymousClassStrategy = doAlign ? alignmentStrategy
                                                       : AlignmentStrategy.wrap(Alignment.createAlignment(),
                                                                                false,
                                                                                JavaTokenType.NEW_KEYWORD,
                                                                                JavaElementType.NEW_EXPRESSION,
                                                                                JavaTokenType.RBRACE);
    setChildIndent(internalIndent);
    setChildAlignment(alignmentStrategy.getAlignment(null));

    boolean isAfterIncomplete = false;

    ASTNode prev = child;
    boolean afterAnonymousClass = false;
    while (child != null) {
      isAfterIncomplete = isAfterIncomplete || child.getElementType() == TokenType.ERROR_ELEMENT ||
                          child.getElementType() == JavaElementType.EMPTY_EXPRESSION;
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        if (child.getElementType() == from) {
          result.add(createJavaBlock(child, mySettings, externalIndent, null, bracketAlignment));
        }
        else if (child.getElementType() == to) {
          result.add(createJavaBlock(child, mySettings,
                                     isAfterIncomplete && !afterAnonymousClass ? internalIndent : externalIndent,
                                     null,
                                     isAfterIncomplete ? alignmentStrategy.getAlignment(null) : bracketAlignment));
          return child;
        }
        else {
          final IElementType elementType = child.getElementType();
          final boolean enforceIndent = shouldEnforceIndentToChildren(child);
          Indent indentToUse = enforceIndent ? internalIndentEnforcedToChildren : internalIndent;
          AlignmentStrategy alignmentStrategyToUse = canUseAnonymousClassAlignment(child) ? anonymousClassStrategy : alignmentStrategy;
          processChild(result, child, alignmentStrategyToUse.getAlignment(elementType), wrappingStrategy.getWrap(elementType), indentToUse);
          if (to == null) {//process only one statement
            return child;
          }
        }
        isAfterIncomplete = false;
        if (child.getElementType() != JavaTokenType.COMMA) {
          afterAnonymousClass = isAnonymousClass(child);
        }
      }
      prev = child;
      child = child.getTreeNext();
    }

    return prev;
  }

  private static boolean canUseAnonymousClassAlignment(@NotNull ASTNode child) {
    // The general idea is to handle situations like below:
    //     test(new Runnable() {       
    //              public void run() {
    //              }                  
    //          }, new Runnable() {    
    //              public void run() {
    //              }                  
    //          }
    //     );
    // I.e. we want to align subsequent anonymous class argument to the previous one if it's not preceded by another argument 
    // at the same line, e.g.:
    //      test("this is a long argument", new Runnable() {       
    //              public void run() {
    //              }                  
    //          }, new Runnable() {    
    //              public void run() {
    //              }                  
    //          }
    //     );
    if (!isAnonymousClass(child)) {
      return false;
    }

    for (ASTNode node = child.getTreePrev(); node != null; node = node.getTreePrev()) {
      if (node.getElementType() == TokenType.WHITE_SPACE) {
        if (StringUtil.countNewLines(node.getChars()) > 0) {
          return false;
        }
      }
      else if (node.getElementType() == JavaTokenType.LPARENTH) {
        // First method call argument.
        return true;
      }
      else if (node.getElementType() != JavaTokenType.COMMA && !isAnonymousClass(node)) {
        return false;
      }
    }
    return true;
  }
  
  private boolean shouldEnforceIndentToChildren(@NotNull ASTNode node) {
    if (myNode.getElementType() != JavaElementType.EXPRESSION_LIST) {
      return false;
    }
    ASTNode parent = myNode.getTreeParent();
    if (parent == null || parent.getElementType() != JavaElementType.METHOD_CALL_EXPRESSION) {
      return false;
    }

    PsiExpressionList methodParamsList = (PsiExpressionList)myNode.getPsi();
    return JavaFormatterUtil.hasMultilineArguments(methodParamsList)
           && JavaFormatterUtil.isMultilineExceptArguments(methodParamsList);
  }

  private static boolean isAnonymousClass(@Nullable ASTNode node) {
    if (node == null || node.getElementType() != JavaElementType.NEW_EXPRESSION) {
      return false;
    }
    ASTNode lastChild = node.getLastChildNode();
    return lastChild != null && lastChild.getElementType() == JavaElementType.ANONYMOUS_CLASS;
  }
  
  @Nullable
  private ASTNode processEnumBlock(@NotNull List<Block> result,
                                   @Nullable ASTNode child,
                                   ASTNode last)
  {
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

  @Nullable
  private static Alignment createAlignmentOrDefault(@Nullable Alignment base, @Nullable final Alignment defaultAlignment) {
    if (defaultAlignment == null) {
      return base == null ? Alignment.createAlignment() : Alignment.createChildAlignment(base);
    }
    return defaultAlignment;
  }

  private int getBraceStyle() {
    final PsiElement psiNode = SourceTreeToPsiMap.treeElementToPsi(myNode);
    if (psiNode instanceof PsiClass) {
      return mySettings.CLASS_BRACE_STYLE;
    }
    if (psiNode instanceof PsiMethod
             || psiNode instanceof PsiCodeBlock && psiNode.getParent() != null && psiNode.getParent() instanceof PsiMethod) {
      return mySettings.METHOD_BRACE_STYLE;
    }
    return mySettings.BRACE_STYLE;
  }

  protected Indent getCodeBlockInternalIndent(final int baseChildrenIndent) {
    return getCodeBlockInternalIndent(baseChildrenIndent, false);
  }
  
  protected Indent getCodeBlockInternalIndent(final int baseChildrenIndent, boolean enforceParentIndent) {
    if (isTopLevelClass() && mySettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS) {
      return Indent.getNoneIndent();
    }

    final int braceStyle = getBraceStyle();
    return braceStyle == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ?
           createNormalIndent(baseChildrenIndent - 1, enforceParentIndent)
           : createNormalIndent(baseChildrenIndent, enforceParentIndent);
  }

  protected static Indent createNormalIndent(final int baseChildrenIndent) {
    return createNormalIndent(baseChildrenIndent, false);
  }
  
  protected static Indent createNormalIndent(final int baseChildrenIndent, boolean enforceIndentToChildren) {
    if (baseChildrenIndent == 1) {
      return Indent.getIndent(Indent.Type.NORMAL, false, enforceIndentToChildren);
    }
    else if (baseChildrenIndent <= 0) {
      return Indent.getNoneIndent();
    }
    else {
      LOG.assertTrue(false);
      return Indent.getIndent(Indent.Type.NORMAL, false, enforceIndentToChildren);
    }
  }

  private boolean isTopLevelClass() {
    return myNode.getElementType() == JavaElementType.CLASS &&
           SourceTreeToPsiMap.treeElementToPsi(myNode.getTreeParent()) instanceof PsiFile;
  }

  protected Indent getCodeBlockExternalIndent() {
    final int braceStyle = getBraceStyle();
    if (braceStyle == CommonCodeStyleSettings.END_OF_LINE || braceStyle == CommonCodeStyleSettings.NEXT_LINE ||
        braceStyle == CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED) {
      return Indent.getNoneIndent();
    }
    return Indent.getNormalIndent();
  }

  protected Indent getCodeBlockChildExternalIndent(final int newChildIndex) {
    final int braceStyle = getBraceStyle();
    if (!isAfterCodeBlock(newChildIndex)) {
      return Indent.getNormalIndent();
    }
    if (braceStyle == CommonCodeStyleSettings.NEXT_LINE ||
        braceStyle == CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED ||
        braceStyle == CommonCodeStyleSettings.END_OF_LINE) {
      return Indent.getNoneIndent();
    }
    return Indent.getNormalIndent();
  }

  private boolean isAfterCodeBlock(final int newChildIndex) {
    if (newChildIndex == 0) return false;
    Block blockBefore = getSubBlocks().get(newChildIndex - 1);
    return blockBefore instanceof CodeBlockBlock;
  }

  /**
   * <b>Note:</b> this method is considered to be a legacy heritage and is assumed to be removed as soon as formatting processing
   * is refactored
   *
   * @param elementType   target element type
   * @return              <code>null</code> all the time
   */
  @Nullable
  @Override
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
    if (child2 instanceof LeafBlock) {
      return ((LeafBlock)child2).getTreeNode();
    }
    return null;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myUseChildAttributes) {
      return new ChildAttributes(myChildIndent, myChildAlignment);
    }
    if (isAfter(newChildIndex, new IElementType[]{JavaDocElementType.DOC_COMMENT})) {
      return new ChildAttributes(Indent.getNoneIndent(), myChildAlignment);
    }
    return super.getChildAttributes(newChildIndex);
  }

  @Override
  @Nullable
  protected Indent getChildIndent() {
    return getChildIndent(myNode, myIndentSettings);
  }

  @NotNull
  public CommonCodeStyleSettings getSettings() {
    return mySettings;
  }

  protected boolean isAfter(final int newChildIndex, @NotNull final IElementType[] elementTypes) {
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

  @Override
  public boolean isLeaf() {
    return ShiftIndentInsideHelper.mayShiftIndentInside(myNode);
  }

  @Nullable
  protected ASTNode composeCodeBlock(@NotNull final List<Block> result,
                                     ASTNode child,
                                     final Indent indent,
                                     final int childrenIndent,
                                     @Nullable final Wrap childWrap) {
    final ArrayList<Block> localResult = new ArrayList<Block>();
    processChild(localResult, child, AlignmentStrategy.getNullStrategy(), null, Indent.getNoneIndent());
    child = child.getTreeNext();

    AlignmentStrategy varDeclarationAlignmentStrategy
      = AlignmentStrategy.createAlignmentPerTypeStrategy(VAR_DECLARATION_ELEMENT_TYPES_TO_ALIGN, JavaElementType.FIELD, true);

    while (child != null) {
      // We consider that subsequent fields shouldn't be aligned if they are separated by blank line(s).
      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        if (!ElementType.JAVA_COMMENT_BIT_SET.contains(child.getElementType()) && !shouldUseVarDeclarationAlignment(child)) {
          // Reset var declaration alignment.
          varDeclarationAlignmentStrategy = AlignmentStrategy.createAlignmentPerTypeStrategy(
            VAR_DECLARATION_ELEMENT_TYPES_TO_ALIGN, JavaElementType.FIELD, true
          );
        }
        final boolean rBrace = isRBrace(child);
        Indent childIndent = rBrace ? Indent.getNoneIndent() : getCodeBlockInternalIndent(childrenIndent, false);
        if (!rBrace && child.getElementType() == JavaElementType.CODE_BLOCK 
            && (getBraceStyle() == CommonCodeStyleSettings.NEXT_LINE_SHIFTED 
                || getBraceStyle() == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2)) 
        {
          childIndent = Indent.getNormalIndent();
        }
        AlignmentStrategy alignmentStrategyToUse = ALIGN_IN_COLUMNS_ELEMENT_TYPES.contains(child.getElementType())
                                                      ? varDeclarationAlignmentStrategy : AlignmentStrategy.getNullStrategy();
        child = processChild(localResult, child, alignmentStrategyToUse, childWrap, childIndent);
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
  protected boolean shouldUseVarDeclarationAlignment(@NotNull ASTNode node) {
    return mySettings.ALIGN_GROUP_FIELD_DECLARATIONS && ALIGN_IN_COLUMNS_ELEMENT_TYPES.contains(node.getElementType())
           && (!myAlignmentInColumnsHelper.useDifferentVarDeclarationAlignment(
                  node, ALIGNMENT_IN_COLUMNS_CONFIG, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS)
           || compoundFieldPart(node));
  }

  /**
   * Allows to answer if given node corresponds to part of composite field definition. Example:
   * <p/>
   * <pre>
   *   int i1, i2 = 2;
   * </pre> 
   * <p/>
   * Parsing such a code produces two fields - {@code 'int i1'} and {@code 'i2 = 2'}. This method returns <code>true</code>
   * for the second one.
   *  
   * @param node    node to check
   * @return        <code>true</code> if given node is a non-first part of composite field definition; <code>false</code> otherwise
   */
  protected static boolean compoundFieldPart(@NotNull ASTNode node) {
    if (node.getElementType() != JavaElementType.FIELD) {
      return false;
    }
    ASTNode firstChild = node.getFirstChildNode();
    if (firstChild == null || firstChild.getElementType() != JavaTokenType.IDENTIFIER) {
      return false;
    }

    ASTNode prev = node.getTreePrev();
    return prev == null || !JavaJspElementType.WHITE_SPACE_BIT_SET.contains(prev.getElementType())
           || StringUtil.countNewLines(prev.getChars()) <= 1;
  }

  @NotNull
  public SyntheticCodeBlock createCodeBlockBlock(final List<Block> localResult, final Indent indent, final int childrenIndent) {
    final SyntheticCodeBlock result = new SyntheticCodeBlock(localResult, null, getSettings(), indent, null);
    result.setChildAttributes(new ChildAttributes(getCodeBlockInternalIndent(childrenIndent), null));
    return result;
  }

  private class ChainMethodCallsBlockBuilder {
    private Wrap blockWrap;
    private Alignment blockAlignment;

    private Wrap myWrap;
    private Alignment myChainedCallsAlignment;

    public ChainMethodCallsBlockBuilder(Alignment alignment, Wrap wrap) {
      blockWrap = wrap;
      blockAlignment = alignment;
    }

    public Block build(List<ASTNode> nodes)  {
      myWrap = getNewWrap();
      myChainedCallsAlignment = getNewAlignment();

      List<Block> blocks = buildBlocksFrom(nodes);
      Indent indent = Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);

      return new SyntheticCodeBlock(blocks, blockAlignment, mySettings, indent, blockWrap);
    }

    private List<Block> buildBlocksFrom(List<ASTNode> nodes) {
      List<ChainedCallChunk> methodCall = splitMethodCallOnChunksByDots(nodes);
      Wrap wrapToUse = null;
      Alignment alignmentToUse = null;

      List<Block> blocks = new ArrayList<Block>();

      for (ChainedCallChunk currentCallChunk : methodCall) {
        if (isMethodCall(currentCallChunk)) {
          wrapToUse = myWrap;
          alignmentToUse = shouldAlignMethod(currentCallChunk, methodCall) ? myChainedCallsAlignment : null;
        }
        else if (wrapToUse != null) {
          wrapToUse = null;
          alignmentToUse = null;

          myChainedCallsAlignment = getNewAlignment();
          myWrap = getNewWrap();
        }

        blocks.add(createSyntheticBlock(currentCallChunk.nodes, wrapToUse, alignmentToUse));
      }

      return blocks;
    }

    private boolean shouldAlignMethod(ChainedCallChunk currentMethodChunk, List<ChainedCallChunk> methodCall) {
      return mySettings.ALIGN_MULTILINE_CHAINED_METHODS
             && !currentMethodChunk.isEmpty()
             && !chunkIsFirstInChainMethodCall(currentMethodChunk, methodCall);
    }

    private boolean chunkIsFirstInChainMethodCall(@NotNull ChainedCallChunk callChunk, @NotNull List<ChainedCallChunk> methodCall) {
      return !methodCall.isEmpty() && callChunk == methodCall.get(0);
    }

    @NotNull
    private List<ChainedCallChunk> splitMethodCallOnChunksByDots(@NotNull List<ASTNode> nodes) {
      List<ChainedCallChunk> result = new ArrayList<ChainedCallChunk>();

      List<ASTNode> current = new ArrayList<ASTNode>();
      for (ASTNode node : nodes) {
        if (node.getElementType() == JavaTokenType.DOT) {
          result.add(new ChainedCallChunk(current));
          current = new ArrayList<ASTNode>();
        }
        current.add(node);
      }

      result.add(new ChainedCallChunk(current));
      return result;
    }

    private Alignment getNewAlignment() {
      return createAlignment(mySettings.ALIGN_MULTILINE_CHAINED_METHODS, null);
    }

    private Wrap getNewWrap() {
      return Wrap.createWrap(getWrapType(mySettings.METHOD_CALL_CHAIN_WRAP), false);
    }

    private boolean isMethodCall(@NotNull ChainedCallChunk callChunk) {
      List<ASTNode> nodes = callChunk.nodes;
      return !nodes.isEmpty() && nodes.get(nodes.size() - 1).getElementType() == JavaElementType.EXPRESSION_LIST;
    }
  }

  private static class ChainedCallChunk {
    @NotNull final List<ASTNode> nodes;

    ChainedCallChunk(@NotNull List<ASTNode> nodes) {
      this.nodes = nodes;
    }

    boolean isEmpty() {
      return nodes.isEmpty();
    }
  }
}
