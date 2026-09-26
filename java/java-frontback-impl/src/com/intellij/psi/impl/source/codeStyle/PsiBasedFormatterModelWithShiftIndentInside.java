// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiBasedFormatterModelWithShiftIndentInside extends PsiBasedFormattingModel {
  private static final Logger LOG =
    Logger.getInstance(PsiBasedFormatterModelWithShiftIndentInside.class);

  private final Project myProject;

  public PsiBasedFormatterModelWithShiftIndentInside(final PsiFile file,
                                                     final @NotNull Block rootBlock,
                                                     final FormattingDocumentModelImpl documentModel) {
    super(file, rootBlock, documentModel);
    myProject = file.getProject();
  }

  @Override
  public TextRange shiftIndentInsideRange(ASTNode node, TextRange textRange, int shift) {
    return shiftIndentInsideWithPsi(node, textRange, shift);
  }


  private static TextRange shiftIndentInsideWithPsi(ASTNode node, final TextRange textRange, final int shift) {
    if (node != null && node.getTextRange().equals(textRange) && ShiftIndentInsideHelper.mayShiftIndentInside(node)) {
      PsiFile file = node.getPsi().getContainingFile();
      return new ShiftIndentInsideHelper(file).shiftIndentInside(node, shift).getTextRange();
    } else {
      return textRange;
    }

  }

  @Override
  protected String replaceWithPsiInLeaf(final TextRange textRange, String whiteSpace, ASTNode leafElement) {
     if (!myCanModifyAllWhiteSpaces) {
       if (leafElement.getElementType() == TokenType.WHITE_SPACE) return null;
       ASTNode prevNode = TreeUtil.prevLeaf(leafElement);

       if (prevNode != null) {
         IElementType type = prevNode.getElementType();
         if(type == TokenType.WHITE_SPACE) {
           final String text = prevNode.getText();

           final @NonNls String cdataStartMarker = "<![CDATA[";
           final int cdataPos = text.indexOf(cdataStartMarker);
           if (cdataPos != -1 && !whiteSpace.contains(cdataStartMarker)) {
             whiteSpace = DocumentBasedFormattingModel.mergeWsWithCdataMarker(whiteSpace, text, cdataPos);
             if (whiteSpace == null) return null;
           }

           prevNode = TreeUtil.prevLeaf(prevNode);
           type = prevNode != null ? prevNode.getElementType():null;
         }

         final @NonNls String cdataEndMarker = "]]>";
         if(type == XmlTokenType.XML_CDATA_END && !whiteSpace.contains(cdataEndMarker)) {
           final ASTNode at = findElementAt(prevNode.getStartOffset());

           if (at != null && at.getPsi() instanceof PsiWhiteSpace) {
             final String s = at.getText();
             final int cdataEndPos = s.indexOf(cdataEndMarker);
             whiteSpace = DocumentBasedFormattingModel.mergeWsWithCdataMarker(whiteSpace, s, cdataEndPos);
             leafElement = at;
           } else {
             whiteSpace = null;
           }
           if (whiteSpace == null) return null;
         }
       }
     }
     FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, TokenType.WHITE_SPACE, textRange);
     return whiteSpace;
   }
}
