package com.intellij.psi.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.FormattingModel;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiBasedFormattingModel implements FormattingModel {

  private final ASTNode myASTNode;
  private final FormattingDocumentModelImpl myDocumentModel;
  private final Block myRootBlock;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");
  private boolean myCanModifyAllWhiteSpaces = false;
  
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


  private String replaceWithPSI(final TextRange textRange, String whiteSpace) {
    final int offset = textRange.getEndOffset();
    ASTNode leafElement = findElementAt(offset);

    if (leafElement != null) {
      if (!myCanModifyAllWhiteSpaces) {
        if (leafElement.getElementType() == TokenType.WHITE_SPACE) return null;
        LOG.assertTrue(leafElement.getPsi().isValid());
        ASTNode prevNode = TreeUtil.prevLeaf(leafElement);

        if (prevNode != null) {
          IElementType type = prevNode.getElementType();
          if(type == TokenType.WHITE_SPACE) {
            final String text = prevNode.getText();

            final @NonNls String cdataStartMarker = "<![CDATA[";
            final int cdataPos = text.indexOf(cdataStartMarker);
            if (cdataPos != -1 && whiteSpace.indexOf(cdataStartMarker) == -1) {
              whiteSpace = mergeWsWithCdataMarker(whiteSpace, text, cdataPos);
              if (whiteSpace == null) return null;
            }

            prevNode = TreeUtil.prevLeaf(prevNode);
            type = prevNode != null ? prevNode.getElementType():null;
          }

          final @NonNls String cdataEndMarker = "]]>";
          if(type == XmlElementType.XML_CDATA_END && whiteSpace.indexOf(cdataEndMarker) == -1) {
            final ASTNode at = findElementAt(prevNode.getStartOffset());

            if (at != null && at.getPsi() instanceof PsiWhiteSpace) {
              final String s = at.getText();
              final int cdataEndPos = s.indexOf(cdataEndMarker);
              whiteSpace = mergeWsWithCdataMarker(whiteSpace, s, cdataEndPos);
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
    } else if (textRange.getEndOffset() == myASTNode.getTextLength()){
      FormatterUtil.replaceLastWhiteSpace(myASTNode, whiteSpace, textRange);
      return whiteSpace;
    } else {
      return null;
    }
  }

  private static String mergeWsWithCdataMarker(String whiteSpace, final String s, final int cdataPos) {
    final int firstCrInGeneratedWs = whiteSpace.indexOf('\n');
    final int secondCrInGeneratedWs = firstCrInGeneratedWs != -1 ? whiteSpace.indexOf('\n', firstCrInGeneratedWs + 1):-1;
    final int firstCrInPreviousWs = s.indexOf('\n');
    final int secondCrInPreviousWs = firstCrInPreviousWs != -1 ? s.indexOf('\n', firstCrInPreviousWs + 1):-1;

    boolean knowHowToModifyCData = false;

    if (secondCrInPreviousWs != -1 && secondCrInGeneratedWs != -1 && cdataPos > firstCrInPreviousWs && cdataPos < secondCrInPreviousWs ) {
      whiteSpace = whiteSpace.substring(0, secondCrInGeneratedWs) + s.substring(firstCrInPreviousWs + 1, secondCrInPreviousWs) + whiteSpace.substring(secondCrInGeneratedWs);
      knowHowToModifyCData = true;
    }
    if (!knowHowToModifyCData) whiteSpace = null;
    return whiteSpace;
  }

  protected ASTNode findElementAt(final int offset) {
    PsiFile containingFile = myASTNode.getPsi().getContainingFile();

    PsiElement psiElement = InjectedLanguageUtil.findInjectedElementAt(containingFile, offset);
    if (psiElement == null) psiElement = containingFile.findElementAt(offset);
    if (psiElement == null) return null;
    if (psiElement instanceof PsiFile) {
      psiElement = myASTNode.getPsi().findElementAt(offset);
    }
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
