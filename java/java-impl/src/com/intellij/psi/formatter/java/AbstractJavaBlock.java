/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.psi.formatter.java.JavaFormatterUtil.getWrapType;
import static com.intellij.psi.formatter.java.MultipleFieldDeclarationHelper.findLastFieldInGroup;

public abstract class AbstractJavaBlock extends AbstractBlock implements JavaBlock, ReservedWrapsProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.java.AbstractJavaBlock");

  @NotNull protected final CommonCodeStyleSettings mySettings;
  @NotNull protected final JavaCodeStyleSettings myJavaSettings;
  protected final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final Indent myIndent;
  protected Indent myChildIndent;
  protected Alignment myChildAlignment;
  protected boolean myUseChildAttributes;
  @NotNull protected final AlignmentStrategy myAlignmentStrategy;
  private boolean myIsAfterClassKeyword;

  protected Alignment myReservedAlignment;
  protected Alignment myReservedAlignment2;

  private final JavaWrapManager myWrapManager;
  private Map<IElementType, Wrap> myPreferredWraps;
  private AbstractJavaBlock myParentBlock;

  private BlockFactory myBlockFactory = new BlockFactory() {
    @Override
    public Block createBlock(ASTNode node, Indent indent, Alignment alignment, Wrap wrap) {
      return new SimpleJavaBlock(node, wrap, AlignmentStrategy.wrap(alignment), indent, mySettings, myJavaSettings);
    }

    @Override
    public CommonCodeStyleSettings getSettings() {
      return mySettings;
    }

    @Override
    public JavaCodeStyleSettings getJavaSettings() {
      return myJavaSettings;
    }
  };

  protected AbstractJavaBlock(@NotNull final ASTNode node,
                              final Wrap wrap,
                              final Alignment alignment,
                              final Indent indent,
                              @NotNull final CommonCodeStyleSettings settings,
                              @NotNull JavaCodeStyleSettings javaSettings)
  {
    this(node, wrap, indent, settings, javaSettings, JavaWrapManager.INSTANCE, AlignmentStrategy.wrap(alignment));
  }

  protected AbstractJavaBlock(@NotNull final ASTNode node,
                              final Wrap wrap,
                              @NotNull final AlignmentStrategy alignmentStrategy,
                              final Indent indent,
                              @NotNull final CommonCodeStyleSettings settings,
                              @NotNull JavaCodeStyleSettings javaSettings)
  {
    this(node, wrap, indent, settings, javaSettings, JavaWrapManager.INSTANCE, alignmentStrategy);
  }

  private AbstractJavaBlock(@NotNull ASTNode ignored,
                            @NotNull CommonCodeStyleSettings commonSettings,
                            @NotNull JavaCodeStyleSettings javaSettings) {
    super(ignored, null, null);
    mySettings = commonSettings;
    myJavaSettings = javaSettings;
    myIndentSettings = commonSettings.getIndentOptions();
    myIndent = null;
    myWrapManager = JavaWrapManager.INSTANCE;
    myAlignmentStrategy = AlignmentStrategy.getNullStrategy();
  }

  protected AbstractJavaBlock(@NotNull final ASTNode node,
                              final Wrap wrap,
                              final Indent indent,
                              @NotNull final CommonCodeStyleSettings settings,
                              @NotNull JavaCodeStyleSettings javaSettings,
                              final JavaWrapManager wrapManager,
                              @NotNull final AlignmentStrategy alignmentStrategy) {
    super(node, wrap, createBlockAlignment(alignmentStrategy, node));
    mySettings = settings;
    myJavaSettings = javaSettings;
    myIndentSettings = settings.getIndentOptions();
    myIndent = indent;
    myWrapManager = wrapManager;
    myAlignmentStrategy = alignmentStrategy;
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
  public Block createJavaBlock(@NotNull ASTNode child,
                               @NotNull CommonCodeStyleSettings settings,
                               @NotNull JavaCodeStyleSettings javaSettings,
                               @Nullable Indent indent,
                               @Nullable Wrap wrap,
                               Alignment alignment) {
    return createJavaBlock(child, settings, javaSettings,indent, wrap, AlignmentStrategy.wrap(alignment));
  }

  @NotNull
  public Block createJavaBlock(@NotNull ASTNode child,
                               @NotNull CommonCodeStyleSettings settings,
                               @NotNull JavaCodeStyleSettings javaSettings,
                               final Indent indent,
                               @Nullable Wrap wrap,
                               @NotNull AlignmentStrategy alignmentStrategy) {
    return createJavaBlock(child, settings, javaSettings, indent, wrap, alignmentStrategy, -1);
  }

  @NotNull
  private Block createJavaBlock(@NotNull ASTNode child,
                                @NotNull CommonCodeStyleSettings settings,
                                @NotNull JavaCodeStyleSettings javaSettings,
                                @Nullable Indent indent,
                                Wrap wrap,
                                @NotNull AlignmentStrategy alignmentStrategy,
                                int startOffset) {
    Indent actualIndent = indent == null ? getDefaultSubtreeIndent(child, getJavaIndentOptions(settings)) : indent;
    IElementType elementType = child.getElementType();
    Alignment alignment = alignmentStrategy.getAlignment(elementType);
    PsiElement childPsi = child.getPsi();

    if (childPsi instanceof PsiWhiteSpace) {
      String text = child.getText();
      int start = CharArrayUtil.shiftForward(text, 0, " \t\n");
      int end = CharArrayUtil.shiftBackward(text, text.length() - 1, " \t\n") + 1;
      LOG.assertTrue(start < end);
      TextRange range = new TextRange(start + child.getStartOffset(), end + child.getStartOffset());
      return new PartialWhitespaceBlock(child, range, wrap, alignment, actualIndent, settings, javaSettings);
    }

    if (childPsi instanceof PsiClass || childPsi instanceof PsiJavaModule) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings, javaSettings);
    }
    if (child.getElementType() == JavaElementType.METHOD) {
      return new BlockContainingJavaBlock(child, actualIndent, alignmentStrategy, mySettings, myJavaSettings);
    }
    if (isBlockType(elementType)) {
      return new BlockContainingJavaBlock(child, wrap, alignment, actualIndent, settings, javaSettings);
    }
    if (isStatement(child, child.getTreeParent())) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings, javaSettings);
    }
    if (!isBuildIndentsOnly() &&
        child instanceof PsiComment &&
        child instanceof PsiLanguageInjectionHost &&
        InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)child)) {
      return new CommentWithInjectionBlock(child, wrap, alignment, indent, settings, javaSettings);
    }
    if (child instanceof LeafElement || childPsi instanceof PsiJavaModuleReferenceElement) {
      final LeafBlock block = new LeafBlock(child, wrap, alignment, actualIndent);
      block.setStartOffset(startOffset);
      return block;
    }
    if (isLikeExtendsList(elementType)) {
      return new ExtendsListBlock(child, wrap, alignmentStrategy, settings, javaSettings);
    }
    if (elementType == JavaElementType.CODE_BLOCK) {
      return new CodeBlockBlock(child, wrap, alignment, actualIndent, settings, javaSettings);
    }
    if (elementType == JavaElementType.LABELED_STATEMENT) {
      return new LabeledJavaBlock(child, wrap, alignment, actualIndent, settings, javaSettings);
    }
    if (elementType == JavaDocElementType.DOC_COMMENT) {
      return new DocCommentBlock(child, wrap, alignment, actualIndent, settings, javaSettings);
    }

    final SimpleJavaBlock simpleJavaBlock = new SimpleJavaBlock(child, wrap, alignmentStrategy, actualIndent, settings, javaSettings);
    simpleJavaBlock.setStartOffset(startOffset);
    return simpleJavaBlock;
  }

  @NotNull
  public static Block newJavaBlock(@NotNull ASTNode child,
                                   @NotNull CommonCodeStyleSettings settings,
                                   @NotNull JavaCodeStyleSettings javaSettings) {
    final Indent indent = getDefaultSubtreeIndent(child, getJavaIndentOptions(settings));
    return newJavaBlock(child, settings, javaSettings, indent, null, AlignmentStrategy.getNullStrategy());
  }

  @NotNull
  public static Block newJavaBlock(@NotNull ASTNode child,
                                   @NotNull CommonCodeStyleSettings settings,
                                   @NotNull JavaCodeStyleSettings javaSettings,
                                   @Nullable Indent indent,
                                   @Nullable Wrap wrap,
                                   @NotNull AlignmentStrategy strategy) {
    return new AbstractJavaBlock(child, settings, javaSettings) {
      @Override
      protected List<Block> buildChildren() {
        return null;
      }
    }.createJavaBlock(child, settings, javaSettings, indent, wrap, strategy);
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
    if (prevElement != null && prevElement.getElementType() == JavaElementType.MODIFIER_LIST && !isMethodParameterAnnotation(prevElement)) {
      return Indent.getNoneIndent();
    }

    if (childNodeType == JavaDocElementType.DOC_TAG) return Indent.getNoneIndent();
    if (childNodeType == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) return Indent.getSpaceIndent(1);
    if (child.getPsi() instanceof PsiFile) return Indent.getNoneIndent();
    if (parent != null) {
      final Indent defaultChildIndent = getChildIndent(parent, indentOptions);
      if (defaultChildIndent != null) return defaultChildIndent;
      if (parent.getPsi() instanceof PsiLambdaExpression && child instanceof PsiCodeBlock) {
        return Indent.getNoneIndent();
      }
    }

    return null;
  }

  private static boolean isMethodParameterAnnotation(@NotNull ASTNode element) {
    ASTNode parent = element.getTreeParent();
    if (parent != null && parent.getElementType() == JavaElementType.PARAMETER) {
      ASTNode lastChild = element.getLastChildNode();
      return lastChild != null && lastChild.getElementType() == JavaElementType.ANNOTATION;
    }
    return false;
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
    return JavaSpacePropertyProcessor.getSpacing(getTreeNode(child2), mySettings, myJavaSettings);
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
    //when detecting indent we do not care about wraps
    return isBuildIndentsOnly() ? null : myWrapManager.createChildBlockWrap(this, getSettings(), this);
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
    return null;
  }

  @Nullable
  protected Alignment chooseAlignment(@Nullable Alignment alignment, @Nullable Alignment alignment2, @NotNull ASTNode child) {
    if (isTernaryOperatorToken(child)) {
      return alignment2;
    }
    return alignment;
  }

  private boolean isTernaryOperatorToken(@NotNull final ASTNode child) {
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
      Alignment alignment = shouldAlignChild(child) ? alignmentStrategy.getAlignment(childType) : null;
      result.add(createMethodCallExpressionBlock(child, arrangeChildWrap(child, defaultWrap), alignment, childIndent));
    }
    else {
      IElementType nodeType = myNode.getElementType();
      if (nodeType == JavaElementType.POLYADIC_EXPRESSION) nodeType = JavaElementType.BINARY_EXPRESSION;

      if (childType == JavaTokenType.LBRACE && nodeType == JavaElementType.ARRAY_INITIALIZER_EXPRESSION) {
        ArrayInitializerBlocksBuilder builder = new ArrayInitializerBlocksBuilder(myNode, myBlockFactory);
        List<Block> newlyCreated = builder.buildBlocks();

        child = myNode.getLastChildNode();
        result.addAll(newlyCreated);
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
        if (mySettings.PREFER_PARAMETERS_WRAP && !isInsideMethodCall(myNode.getPsi())) {
            wrap.ignoreParentWraps();
        }
        child = processParenthesisBlock(result, child,
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
      }
      else if (childType == JavaTokenType.LPARENTH && nodeType == JavaElementType.PARAMETER_LIST) {
        ASTNode parent = myNode.getTreeParent();
        boolean isLambdaParameterList = parent != null && parent.getElementType() == JavaElementType.LAMBDA_EXPRESSION;
        Wrap wrapToUse = isLambdaParameterList ? null : getMethodParametersWrap();
        WrappingStrategy wrapStrategy = WrappingStrategy.createDoNotWrapCommaStrategy(wrapToUse);
        child = processParenthesisBlock(result, child, wrapStrategy, mySettings.ALIGN_MULTILINE_PARAMETERS);
      }
      else if (childType == JavaTokenType.LPARENTH && nodeType == JavaElementType.RESOURCE_LIST) {
        Wrap wrap = Wrap.createWrap(getWrapType(mySettings.RESOURCE_LIST_WRAP), false);
        child = processParenthesisBlock(result, child,
                                  WrappingStrategy.createDoNotWrapCommaStrategy(wrap),
                                  mySettings.ALIGN_MULTILINE_RESOURCES);
      }
      else if (childType == JavaTokenType.LPARENTH && nodeType == JavaElementType.ANNOTATION_PARAMETER_LIST) {
        AnnotationInitializerBlocksBuilder builder = new AnnotationInitializerBlocksBuilder(myNode, myBlockFactory);
        List<Block> newlyCreated = builder.buildBlocks();

        child = myNode.getLastChildNode();
        result.addAll(newlyCreated);
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
               || childType == JavaElementType.DECLARATION_STATEMENT
                  && (nodeType == JavaElementType.METHOD || nodeType == JavaElementType.CODE_BLOCK))
      {
        result.add(new SimpleJavaBlock(child, defaultWrap, alignmentStrategy, childIndent, mySettings, myJavaSettings));
      }
      else if (childType == JavaElementType.METHOD) {
        Wrap wrap = arrangeChildWrap(child, defaultWrap);
        Block block = createJavaBlock(child, mySettings, myJavaSettings, childIndent, wrap, alignmentStrategy);
        result.add(block);
      }
      else {
        Alignment alignment = alignmentStrategy.getAlignment(childType);
        AlignmentStrategy alignmentStrategyToUse = shouldAlignChild(child)
                                                   ? AlignmentStrategy.wrap(alignment)
                                                   : AlignmentStrategy.getNullStrategy();

        if (myAlignmentStrategy.getAlignment(nodeType, childType) != null &&
            (nodeType == JavaElementType.IMPLEMENTS_LIST || nodeType == JavaElementType.CLASS)) {
          alignmentStrategyToUse = myAlignmentStrategy;
        }

        Wrap wrap = arrangeChildWrap(child, defaultWrap);

        Block block = createJavaBlock(child, mySettings, myJavaSettings, childIndent, wrap, alignmentStrategyToUse, childOffset);

        if (block instanceof AbstractJavaBlock) {
          final AbstractJavaBlock javaBlock = (AbstractJavaBlock)block;
          if (nodeType == JavaElementType.METHOD_CALL_EXPRESSION && childType == JavaElementType.REFERENCE_EXPRESSION ||
              nodeType == JavaElementType.REFERENCE_EXPRESSION && childType == JavaElementType.METHOD_CALL_EXPRESSION) {
            javaBlock.setReservedWrap(getReservedWrap(nodeType), nodeType);
            javaBlock.setReservedWrap(getReservedWrap(childType), childType);
          }
          else if (nodeType == JavaElementType.BINARY_EXPRESSION) {
            javaBlock.setReservedWrap(defaultWrap, nodeType);
          }
        }

        result.add(block);
      }
    }

    return child;
  }

  private static boolean isInsideMethodCall(@NotNull PsiElement element) {
    PsiElement e = element.getParent();
    int parentsVisited = 0;
    while (e != null && !(e instanceof PsiStatement) && parentsVisited < 5) {
      if (e instanceof PsiExpressionList) {
        return true;
      }
      e = e.getParent();
      parentsVisited++;
    }
    return false;
  }

  @NotNull
  private Wrap getMethodParametersWrap() {
    Wrap preferredWrap = getModifierListWrap();
    if (preferredWrap == null) {
      return Wrap.createWrap(getWrapType(mySettings.METHOD_PARAMETERS_WRAP), false);
    } else {
      return Wrap.createChildWrap(preferredWrap, getWrapType(mySettings.METHOD_PARAMETERS_WRAP), false);
    }
  }

  @Nullable
  private Wrap getModifierListWrap() {
    AbstractJavaBlock parentBlock = getParentBlock();
    if (parentBlock != null) {
      return parentBlock.getReservedWrap(JavaElementType.MODIFIER_LIST);
    }
    return null;
  }

  private ASTNode processField(@NotNull final List<Block> result,
                               ASTNode child,
                               @NotNull final AlignmentStrategy alignmentStrategy,
                               final Wrap defaultWrap,
                               final Indent childIndent) {
    ASTNode lastFieldInGroup = findLastFieldInGroup(child);
    if (lastFieldInGroup == child) {
      result.add(createJavaBlock(child, getSettings(), myJavaSettings, childIndent, arrangeChildWrap(child, defaultWrap), alignmentStrategy));
      return child;
    }
    else {
      final ArrayList<Block> localResult = new ArrayList<>();
      while (child != null) {
        if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
          localResult.add(createJavaBlock(
              child, getSettings(), myJavaSettings,
              Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS),
              arrangeChildWrap(child, defaultWrap),
              alignmentStrategy
            )
          );
        }
        if (child == lastFieldInGroup) break;

        child = child.getTreeNext();
      }
      if (!localResult.isEmpty()) {
        result.add(new SyntheticCodeBlock(localResult, null, getSettings(), myJavaSettings, childIndent, null));
      }
      return lastFieldInGroup;
    }
  }

  @Nullable
  private ASTNode processTernaryOperationRange(@NotNull final List<Block> result,
                                               @NotNull final ASTNode child,
                                               final Wrap defaultWrap,
                                               final Indent childIndent) {
    final ArrayList<Block> localResult = new ArrayList<>();
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

    result.add(new SyntheticCodeBlock(localResult,  chooseAlignment(alignment,  alignment2, child), getSettings(), myJavaSettings, null, wrap));

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
  private Block createMethodCallExpressionBlock(@NotNull ASTNode node, Wrap blockWrap, Alignment alignment, Indent indent) {
    final ArrayList<ASTNode> nodes = new ArrayList<>();
    collectNodes(nodes, node);
    return new ChainMethodCallsBlockBuilder(alignment, blockWrap, indent, mySettings, myJavaSettings).build(nodes);
  }

  private static void collectNodes(@NotNull List<ASTNode> nodes, @NotNull ASTNode node) {
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        IElementType type = child.getElementType();
        if (type == JavaElementType.METHOD_CALL_EXPRESSION ||
            type == JavaElementType.REFERENCE_EXPRESSION) {
          collectNodes(nodes, child);
        }
        else {
          nodes.add(child);
        }
      }
      child = child.getTreeNext();
    }
  }

  private boolean shouldAlignChild(@NotNull final ASTNode child) {
    int role = getChildRole(child);
    final IElementType nodeType = myNode.getElementType();

    if (nodeType == JavaElementType.FOR_STATEMENT) {
      if (role == ChildRole.FOR_INITIALIZATION || role == ChildRole.CONDITION || role == ChildRole.FOR_UPDATE) {
        return true;
      }
      return false;
    }
    else if (nodeType == JavaElementType.EXTENDS_LIST || nodeType == JavaElementType.IMPLEMENTS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST || role == ChildRole.IMPLEMENTS_KEYWORD) {
        return true;
      }
      return false;
    }
    else if (nodeType == JavaElementType.THROWS_LIST) {
      if (role == ChildRole.REFERENCE_IN_LIST) {
        return true;
      }
      return false;
    }
    else if (nodeType == JavaElementType.CLASS) {
      if (role == ChildRole.CLASS_OR_INTERFACE_KEYWORD) return true;
      if (myIsAfterClassKeyword) return false;
      if (role == ChildRole.MODIFIER_LIST) return true;
      return false;
    }
    else if (JavaElementType.FIELD == nodeType) {
      return shouldAlignFieldInColumns(child);
    }
    else if (nodeType == JavaElementType.METHOD) {
      if (role == ChildRole.MODIFIER_LIST) return true;
      if (role == ChildRole.TYPE_PARAMETER_LIST) return true;
      if (role == ChildRole.TYPE) return true;
      if (role == ChildRole.NAME) return true;
      if (role == ChildRole.THROWS_LIST && mySettings.ALIGN_THROWS_KEYWORD) return true;
      if (role == ChildRole.METHOD_BODY) return !getNode().textContains('\n');
      return false;
    }

    else if (nodeType == JavaElementType.ASSIGNMENT_EXPRESSION) {
      if (role == ChildRole.LOPERAND) return true;
      if (role == ChildRole.ROPERAND && child.getElementType() == JavaElementType.ASSIGNMENT_EXPRESSION) {
        return true;
      }
      return false;
    }

    else if (child.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
      ASTNode previous = child.getTreePrev();
      // There is a special case - comment block that is located at the very start of the line. We don't reformat such a blocks,
      // hence, no alignment should be applied to them in order to avoid subsequent blocks aligned with the same alignment to
      // be located at the left editor edge as well.
      CharSequence prevChars;
      if (previous != null && previous.getElementType() == TokenType.WHITE_SPACE && (prevChars = previous.getChars()).length() > 0
          && prevChars.charAt(prevChars.length() - 1) == '\n') {
        return false;
      }
      return true;
    }

    else if (nodeType == JavaElementType.MODIFIER_LIST) {
      // There is a possible case that modifier list contains from more than one elements, e.g. 'private final'. It's also possible
      // that the list is aligned. We want to apply alignment rule only to the first element then.
      ASTNode previous = child.getTreePrev();
      if (previous == null || previous.getTreeParent() != myNode) {
        return true;
      }
      return false;
    }

    else {
      return true;
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
  private boolean shouldAlignFieldInColumns(@NotNull ASTNode child) {
    // The whole idea of variable declarations alignment is that complete declaration blocks which children are to be aligned hold
    // reference to the same AlignmentStrategy object, hence, reuse the same Alignment objects. So, there is no point in checking
    // if it's necessary to align sub-blocks if shared strategy is not defined.
    if (!mySettings.ALIGN_GROUP_FIELD_DECLARATIONS) {
      return false;
    }

    IElementType childType = child.getElementType();

    // We don't want to align subsequent identifiers in single-line declarations like 'int i1, i2, i3'. I.e. only 'i1'
    // should be aligned then.
    ASTNode previousNode = FormatterUtil.getPreviousNonWhitespaceSibling(child);
    if (childType == JavaTokenType.IDENTIFIER && (previousNode == null || previousNode.getElementType() == JavaTokenType.COMMA)) {
      return false;
    }

    return true;
  }

  @Nullable
  public static Alignment createAlignment(final boolean alignOption, @Nullable final Alignment defaultAlignment) {
    return alignOption ? createAlignmentOrDefault(null, defaultAlignment) : defaultAlignment;
  }

  @Nullable
  public static Alignment createAlignment(Alignment base, final boolean alignOption, @Nullable final Alignment defaultAlignment) {
    return alignOption ? createAlignmentOrDefault(base, defaultAlignment) : defaultAlignment;
  }

  @Nullable
  protected Wrap arrangeChildWrap(final ASTNode child, Wrap defaultWrap) {
    //when detecting indent we do not care about wraps
    return isBuildIndentsOnly() ? null : myWrapManager.arrangeChildWrap(child, myNode, mySettings, myJavaSettings, defaultWrap, this);
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
                                          final boolean doAlign)
  {
    Indent externalIndent = Indent.getNoneIndent();
    Indent internalIndent = Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);

    if (isInsideMethodCallParenthesis(child)) {
      internalIndent = Indent.getSmartIndent(Indent.Type.CONTINUATION);
    }

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
          result.add(createJavaBlock(child, mySettings, myJavaSettings, externalIndent, null, bracketAlignment));
        }
        else if (child.getElementType() == to) {
          Block block = createJavaBlock(child, mySettings, myJavaSettings,
                                        isAfterIncomplete && !afterAnonymousClass ? internalIndent : externalIndent,
                                        null,
                                        isAfterIncomplete ? alignmentStrategy.getAlignment(null) : bracketAlignment);
          result.add(block);
          return child;
        }
        else {
          final IElementType elementType = child.getElementType();
          AlignmentStrategy alignmentStrategyToUse = canUseAnonymousClassAlignment(child) ? anonymousClassStrategy : alignmentStrategy;
          processChild(result, child, alignmentStrategyToUse.getAlignment(elementType), wrappingStrategy.getWrap(elementType), internalIndent);
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

  private static boolean isInsideMethodCallParenthesis(ASTNode child) {
    ASTNode currentPredecessor = child.getTreeParent();
    if (currentPredecessor != null) {
      currentPredecessor = currentPredecessor.getTreeParent();
      return currentPredecessor != null && currentPredecessor.getElementType() == JavaElementType.METHOD_CALL_EXPRESSION;
    }
    return false;
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
        result.add(createJavaBlock(child, mySettings, myJavaSettings, Indent.getNormalIndent(),
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
    final int shift = braceStyle == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ? 1 : 0;
    return createNormalIndent(baseChildrenIndent - shift, enforceParentIndent);
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
    return myPreferredWraps != null ? myPreferredWraps.get(elementType) : null;
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
  public void setReservedWrap(final Wrap reservedWrap, final IElementType operationType) {
    if (myPreferredWraps == null) {
      myPreferredWraps = ContainerUtil.newHashMap();
    }
    myPreferredWraps.put(operationType, reservedWrap);
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
    final ArrayList<Block> localResult = new ArrayList<>();
    processChild(localResult, child, AlignmentStrategy.getNullStrategy(), null, Indent.getNoneIndent());
    child = child.getTreeNext();

    ChildAlignmentStrategyProvider alignmentStrategyProvider = getStrategyProvider();

    while (child != null) {
      if (FormatterUtil.containsWhiteSpacesOnly(child)) {
        child = child.getTreeNext();
        continue;
      }

      Indent childIndent = getIndentForCodeBlock(child, childrenIndent);
      AlignmentStrategy alignmentStrategyToUse = alignmentStrategyProvider.getNextChildStrategy(child);

      final boolean isRBrace = isRBrace(child);
      child = processChild(localResult, child, alignmentStrategyToUse, childWrap, childIndent);

      if (isRBrace) {
        result.add(createCodeBlockBlock(localResult, indent, childrenIndent));
        return child;
      }

      if (child != null) {
        child = child.getTreeNext();
      }
    }
    result.add(createCodeBlockBlock(localResult, indent, childrenIndent));
    return null;
  }

  protected ChildAlignmentStrategyProvider getStrategyProvider() {
    if (myNode.getElementType() == JavaElementType.CLASS) {
      return new SubsequentClassMemberAlignment(mySettings);
    }

    ASTNode parent = myNode.getTreeParent();
    IElementType parentType = parent != null ? parent.getElementType() : null;
    if (mySettings.ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS
        && (parentType == JavaElementType.METHOD || myNode instanceof PsiCodeBlock)) {
      return new SubsequentVariablesAligner();
    }

    return ChildAlignmentStrategyProvider.NULL_STRATEGY_PROVIDER;
  }

  private Indent getIndentForCodeBlock(ASTNode child, int childrenIndent) {
    if (child.getElementType() == JavaElementType.CODE_BLOCK
        && (getBraceStyle() == CommonCodeStyleSettings.NEXT_LINE_SHIFTED
            || getBraceStyle() == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2))
    {
      return Indent.getNormalIndent();
    }

    return isRBrace(child) ? Indent.getNoneIndent() : getCodeBlockInternalIndent(childrenIndent, false);
  }

  public AbstractJavaBlock getParentBlock() {
    return myParentBlock;
  }

  public void setParentBlock(@NotNull AbstractJavaBlock parentBlock) {
    myParentBlock = parentBlock;
  }

  @NotNull
  public SyntheticCodeBlock createCodeBlockBlock(final List<Block> localResult, final Indent indent, final int childrenIndent) {
    final SyntheticCodeBlock result = new SyntheticCodeBlock(localResult, null, getSettings(), myJavaSettings, indent, null);
    result.setChildAttributes(new ChildAttributes(getCodeBlockInternalIndent(childrenIndent), null));
    return result;
  }
}