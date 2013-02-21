/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.lang.impl.ASTNodeBuilder;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

public class JavaLightStubBuilder extends LightStubBuilder {
  private static final TokenSet BLOCK_ELEMENTS = TokenSet.create(
    JavaElementType.ANNOTATION, JavaElementType.CLASS, JavaElementType.ANONYMOUS_CLASS);
  private static final TokenSet BLOCK_TOKENS = TokenSet.orSet(
    TokenSet.create(JavaTokenType.AT), ElementType.CLASS_KEYWORD_BIT_SET);

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
        refText = SourceUtil.getTextSkipWhiteSpaceAndComments(tree, ref);
      }
    }
    return new PsiJavaFileStubImpl((PsiJavaFile)file, StringRef.fromString(refText), false);
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
    IElementType parentType = parent.getElementType();
    IElementType nodeType = node.getElementType();

    if (nodeType == JavaElementType.PARAMETER && parentType != JavaElementType.PARAMETER_LIST) {
      return true;
    }
    if (nodeType == JavaElementType.PARAMETER_LIST && parentType == JavaElementType.LAMBDA_EXPRESSION) {
      return true;
    }

    if (nodeType == JavaElementType.CODE_BLOCK && node instanceof TreeElement) {
      final Ref<Boolean> skip = Ref.create(true);

      ((TreeElement)node).acceptTree(new RecursiveTreeElementWalkingVisitor() {
        @Override
        protected void visitNode(TreeElement element) {
          if (BLOCK_ELEMENTS.contains(element.getElementType())) {
            skip.set(false);
            stopWalking();
            return;
          }
          super.visitNode(element);
        }
      });

      return skip.get();
    }

    return false;
  }

  @Override
  protected boolean skipChildProcessingWhenBuildingStubs(@NotNull LighterAST tree, @NotNull LighterASTNode parent, @NotNull LighterASTNode node) {
    IElementType parentType = parent.getTokenType();
    IElementType nodeType = node.getTokenType();

    if (nodeType == JavaElementType.PARAMETER && parentType != JavaElementType.PARAMETER_LIST) {
      return true;
    }
    if (nodeType == JavaElementType.PARAMETER_LIST && parentType == JavaElementType.LAMBDA_EXPRESSION) {
      return true;
    }

    if (nodeType == JavaElementType.CODE_BLOCK && node instanceof ASTNodeBuilder.ASTUnparsedNodeMarker) {
      ASTNodeBuilder.ASTUnparsedNodeMarker nodeMarker = (ASTNodeBuilder.ASTUnparsedNodeMarker)node;
      ASTNodeBuilder nodeBuilder = nodeMarker.getBuilder();
      boolean seenNew = false, skip = true;

      for (int i = nodeMarker.getStartLexemeIndex(), endIndex = nodeMarker.getEndLexemeIndex(); i < endIndex; ++i) {
        IElementType type = nodeBuilder.getElementType(i);
        if (type == JavaTokenType.NEW_KEYWORD) {
          seenNew = true;
        }
        else if (seenNew && type == JavaTokenType.LBRACE) {  // anonymous class
          skip = false;
          break;
        }
        else if (seenNew && type == JavaTokenType.SEMICOLON) {
          seenNew = false;
        }
        else if (BLOCK_TOKENS.contains(type)) {  // annotated local variables, type annotations, local classes
          skip = false;
          break;
        }
      }

      return skip;
    }

    return false;
  }
}
