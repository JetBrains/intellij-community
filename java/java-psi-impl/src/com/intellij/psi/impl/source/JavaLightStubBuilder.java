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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class JavaLightStubBuilder extends LightStubBuilder {
  @NotNull
  @Override
  protected StubElement createStubForFile(@NotNull PsiFile file, @NotNull LighterAST tree) {
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
    return new PsiJavaFileStubImpl((PsiJavaFile)file, refText, null, false);
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
    IElementType parentType = parent.getElementType();
    IElementType nodeType = node.getElementType();

    if (checkByTypes(parentType, nodeType)) return true;

    if (nodeType == JavaElementType.CODE_BLOCK) {
      CodeBlockVisitor visitor = new CodeBlockVisitor();
      ((TreeElement)node).acceptTree(visitor);
      return visitor.result;
    }

    return false;
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

    @Override
    @SuppressWarnings("IfStatementWithIdenticalBranches")
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
      }
      else if (seenNew && type == JavaTokenType.LBRACE && last != JavaTokenType.RBRACKET) {
        return (result = false);
      }
      // local classes
      else if (type == JavaTokenType.CLASS_KEYWORD && (last != JavaTokenType.DOT || preLast != JavaTokenType.IDENTIFIER)  
               || type == JavaTokenType.ENUM_KEYWORD 
               || type == JavaTokenType.INTERFACE_KEYWORD) {
        return (result = false);
      }

      preLast = last;
      last = type;
      return true;
    }
  }
}
