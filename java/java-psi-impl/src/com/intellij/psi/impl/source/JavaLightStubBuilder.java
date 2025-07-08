// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.lang.impl.TokenSequence;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.TokenList;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class JavaLightStubBuilder extends LightStubBuilder {
  @Override
  protected @NotNull StubElement<?> createStubForFile(@NotNull PsiFile file, @NotNull LighterAST tree) {
    if (!(file instanceof PsiJavaFile)) {
      return super.createStubForFile(file, tree);
    }

    String refText = "";
    LighterASTNode pkg = LightTreeUtil.firstChildOfType(tree, tree.getRoot(), JavaElementType.PACKAGE_STATEMENT);
    if (pkg != null) {
      LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, pkg, JavaElementType.JAVA_CODE_REFERENCE);
      if (ref != null) {
        refText = JavaSourceUtil.getReferenceText(tree, ref);
      }
    }
    return new PsiJavaFileStubImpl((PsiJavaFile)file, null, false);
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
    IElementType parentType = parent.getElementType();
    IElementType nodeType = node.getElementType();

    if (checkByTypes(parentType, nodeType)) return true;

    if (nodeType == JavaElementType.CODE_BLOCK) return isCodeBlockWithoutStubs(node);

    return false;
  }

  private static boolean isCodeBlockWithoutStubs(@NotNull ASTNode node) {
    CodeBlockVisitor visitor = new CodeBlockVisitor();
    if (TreeUtil.isCollapsedChameleon(node)) {
      Lexer lexer = JavaParserDefinition.createLexer(PsiUtil.getLanguageLevel(node.getPsi()));
      TokenList tokens = TokenSequence.performLexing(node.getChars(), lexer);
      for (int i = 0; i < tokens.getTokenCount(); i++) {
        visitor.visit(tokens.getTokenType(i));
      }
    } else {
      ((TreeElement)node).acceptTree(visitor);
    }
    return visitor.result;
  }

  @Override
  protected boolean skipChildProcessingWhenBuildingStubs(@NotNull LighterAST tree, @NotNull LighterASTNode parent, @NotNull LighterASTNode node) {
    return checkByTypes(parent.getTokenType(), node.getTokenType()) || isCodeBlockWithoutStubs(node);
  }

  public static boolean isCodeBlockWithoutStubs(@NotNull LighterASTNode node) {
    if (node.getTokenType() == JavaElementType.CODE_BLOCK && node instanceof LighterLazyParseableNode) {
      CodeBlockVisitor visitor = new CodeBlockVisitor();
      ((LighterLazyParseableNode)node).accept(visitor);
      return visitor.result;
    }

    return false;
  }

  private static boolean checkByTypes(IElementType parentType, IElementType nodeType) {
    if (ElementType.IMPORT_STATEMENT_BASE_BIT_SET.contains(parentType)) {
      return true;
    }
    if (nodeType == JavaElementType.RECEIVER_PARAMETER) {
      return true;
    }
    if (nodeType == JavaElementType.PARAMETER && parentType != JavaElementType.PARAMETER_LIST) {
      return true;
    }
    if (nodeType == JavaElementType.PARAMETER_LIST && parentType == JavaElementType.LAMBDA_EXPRESSION) {
      return true;
    }
    if (nodeType == JavaDocElementType.DOC_COMMENT) {
      return true;
    }

    return false;
  }

  private static class CodeBlockVisitor extends RecursiveTreeElementWalkingVisitor implements LighterLazyParseableNode.Visitor {
    private static final TokenSet BLOCK_ELEMENTS = TokenSet.create(
      JavaElementType.CLASS, JavaElementType.ANONYMOUS_CLASS,
      JavaTokenType.ARROW, JavaTokenType.DOUBLE_COLON, JavaTokenType.AT);

    private boolean result = true;

    @Override
    protected void visitNode(TreeElement element) {
      if (BLOCK_ELEMENTS.contains(element.getElementType())) {
        result = false;
        stopWalking();
        return;
      }
      super.visitNode(element);
    }

    private IElementType preLast;
    private IElementType last;
    private boolean seenNew;
    private boolean seenLParen;
    private boolean seenModifier;

    @Override
    public boolean visit(IElementType type) {
      if (ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(type)) {
        return true;
      }

      // annotations, method refs & lambdas
      if (type == JavaTokenType.AT || type == JavaTokenType.ARROW || type == JavaTokenType.DOUBLE_COLON) {
        return (result = false);
      }
      // anonymous classes
      else if (type == JavaTokenType.NEW_KEYWORD) {
        seenNew = true;
      }
      else if (seenNew && type == JavaTokenType.SEMICOLON) {
        seenNew = false;
        seenLParen = false;
      }
      else if (seenNew && type == JavaTokenType.LBRACE && seenLParen) {
        return (result = false);
      }
      else if (seenNew && type == JavaTokenType.LPARENTH) {
        seenLParen = true;
      }
      else if (ElementType.MODIFIER_BIT_SET.contains(type)) {
        seenModifier = true;
      }
      // local classes
      else if (type == JavaTokenType.CLASS_KEYWORD && (last != JavaTokenType.DOT || preLast != JavaTokenType.IDENTIFIER || seenModifier) ||
               type == JavaTokenType.ENUM_KEYWORD ||
               type == JavaTokenType.INTERFACE_KEYWORD) {
        return (result = false);
      }
      // if record is inside lazy parseable element, tokens are not remapped and record token is still identifier
      // This token combination may be "record RecordName (" or "record RecordName<..."
      // Local records without < or ( won't be parsed
      else if (preLast == JavaTokenType.IDENTIFIER &&
               last == JavaTokenType.IDENTIFIER &&
               (type == JavaTokenType.LPARENTH || type == JavaTokenType.LT)) {
        return (result = false);
      }

      preLast = last;
      last = type;
      return true;
    }
  }
}
