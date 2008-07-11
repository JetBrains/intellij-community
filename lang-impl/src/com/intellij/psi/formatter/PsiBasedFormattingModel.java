package com.intellij.psi.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.FormattingModel;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiBasedFormattingModel implements FormattingModel {

  private final ASTNode myASTNode;
  private final FormattingDocumentModelImpl myDocumentModel;
  private final Block myRootBlock;
  protected boolean myCanModifyAllWhiteSpaces = false;
  
  public PsiBasedFormattingModel(final PsiFile file,
                                 final Block rootBlock,
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
    assert !PsiDocumentManager.getInstance(containingFile.getProject()).isUncommited(myDocumentModel.getDocument());
    PsiElement psiElement = InjectedLanguageUtil.findInjectedElementNoCommitWithOffset(containingFile, offset);
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
