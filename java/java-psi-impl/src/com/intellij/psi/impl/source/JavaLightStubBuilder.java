/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.ASTNodeBuilder;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.SourceUtil;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.io.StringRef;


public class JavaLightStubBuilder extends LightStubBuilder {
  @Override
  protected StubElement createStubForFile(final PsiFile file, final LighterAST tree) {
    if (!(file instanceof PsiJavaFile)) return super.createStubForFile(file, tree);

    String refText = "";

    final LighterASTNode pkg = LightTreeUtil.firstChildOfType(tree, tree.getRoot(), JavaElementType.PACKAGE_STATEMENT);
    if (pkg != null) {
      final LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, pkg, JavaElementType.JAVA_CODE_REFERENCE);
      if (ref != null) {
        refText = SourceUtil.getTextSkipWhiteSpaceAndComments(tree, ref);
      }
    }

    return new PsiJavaFileStubImpl((PsiJavaFile)file, StringRef.fromString(refText), false);
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(final IElementType nodeType, final IElementType childType) {
    return childType == JavaElementType.PARAMETER && nodeType != JavaElementType.PARAMETER_LIST;
  }

  static int totalMethods;
  static int nonexpandedMethods;

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(LightStubBuilder builder,
                                                       LighterAST tree,
                                                       LighterASTNode parent,
                                                       LighterASTNode child) {
    if (child.getTokenType() == JavaElementType.CODE_BLOCK) {
      if (child instanceof ASTNodeBuilder.ASTUnparsedNodeMarker) {
        ++totalMethods;
        ASTNodeBuilder.ASTUnparsedNodeMarker nodeMarker = (ASTNodeBuilder.ASTUnparsedNodeMarker)child;
        final ASTNodeBuilder nodeBuilder = nodeMarker.getBuilder();
        final int endLexemIndex = nodeMarker.getEndLexemIndex();
        boolean seenNew = false;

        for(int i = nodeMarker.getStartLexemIndex(); i < endLexemIndex; ++i) {
          final IElementType type = nodeBuilder.getElementType(i);
          if (type == JavaTokenType.NEW_KEYWORD) {
            seenNew = true;
          } else if (seenNew && type == JavaTokenType.LBRACE) {
            return false;
          } else if (seenNew && type == JavaTokenType.SEMICOLON) {
            seenNew = false;
          } else if (type == JavaTokenType.AT || // local vars can be annotated and we have them in stubs!
                     type == JavaTokenType.CLASS_KEYWORD || // local classes
                     type == JavaTokenType.INTERFACE_KEYWORD ||
                     type == JavaTokenType.ENUM_KEYWORD
                    ) {
            return false;
          }
        }

        ++nonexpandedMethods;
        return true;
      }
    }
    return false;
  }
}