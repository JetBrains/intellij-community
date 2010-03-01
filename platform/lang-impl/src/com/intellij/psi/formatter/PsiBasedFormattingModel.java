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

package com.intellij.psi.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.FormattingModel;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiBasedFormattingModel implements FormattingModel {

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");

  private final ASTNode myASTNode;
  private final FormattingDocumentModelImpl myDocumentModel;
  @NotNull private final Block myRootBlock;
  protected boolean myCanModifyAllWhiteSpaces = false;
  
  public PsiBasedFormattingModel(final PsiFile file,
                                 @NotNull final Block rootBlock,
                                 final FormattingDocumentModelImpl documentModel) {
    myASTNode = SourceTreeToPsiMap.psiElementToTree(file);
    myDocumentModel = documentModel;
    myRootBlock = rootBlock;

  }

  public TextRange replaceWhiteSpace(TextRange textRange,
                                String whiteSpace) {
    final String wsReplaced = replaceWithPSI(textRange, whiteSpace);
    
    if (wsReplaced != null){
      return new TextRange(textRange.getStartOffset(), textRange.getStartOffset() + wsReplaced.length());
    } else {
      return textRange;
    }
  }

  public TextRange shiftIndentInsideRange(TextRange textRange, int shift) {
    return textRange; // TODO: Remove this method from here...
  }

  public void commitChanges() {
  }


  @Nullable
  private String replaceWithPSI(final TextRange textRange, String whiteSpace) {
    final int offset = textRange.getEndOffset();
    ASTNode leafElement = findElementAt(offset);

    if (leafElement != null) {
      if (leafElement.getPsi() instanceof PsiFile) {
        return null;
      } else {
        if (!leafElement.getPsi().isValid()) {
          String message = "Invalid element found in '\n" +
                           myASTNode.getText() +
                           "\n' at " +
                           offset +
                           "(" +
                           myASTNode.getText().substring(offset, Math.min(offset + 10, myASTNode.getTextLength()));
          LOG.error(message);
        }
        return replaceWithPsiInLeaf(textRange, whiteSpace, leafElement);
      }
    } else if (textRange.getEndOffset() == myASTNode.getTextLength()){
      FormatterUtil.replaceLastWhiteSpace(myASTNode, whiteSpace, textRange);
      return whiteSpace;
    } else {
      return null;
    }
  }

  @Nullable
  protected String replaceWithPsiInLeaf(final TextRange textRange, String whiteSpace, ASTNode leafElement) {
    if (!myCanModifyAllWhiteSpaces) {
      if (leafElement.getElementType() == TokenType.WHITE_SPACE) return null;
    }

    FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, TokenType.WHITE_SPACE, textRange);
    return whiteSpace;
  }

  @Nullable
  protected ASTNode findElementAt(final int offset) {
    PsiFile containingFile = myASTNode.getPsi().getContainingFile();
    Project project = containingFile.getProject();
    assert !PsiDocumentManager.getInstance(project).isUncommited(myDocumentModel.getDocument());
    // TODO:default project can not be used for injections, because latter might wants (unavailable) indices
    PsiElement psiElement = project.isDefault() ? null :InjectedLanguageUtil.findInjectedElementNoCommitWithOffset(containingFile, offset);
    if (psiElement == null) psiElement = containingFile.findElementAt(offset);
    if (psiElement == null) return null;
    return psiElement.getNode();
  }

  @NotNull
  public FormattingDocumentModel getDocumentModel() {
    return myDocumentModel;
  }

  @NotNull
  public Block getRootBlock() {
    return myRootBlock;
  }

  public void canModifyAllWhiteSpaces() {
    myCanModifyAllWhiteSpaces = true;
  }
}
