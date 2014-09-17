package com.intellij.json.psi;

import com.intellij.json.JsonElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;

public class JsonPsiChangeUtils {
  public static void removeCommaSeparatedFromList(final ASTNode myNode, final ASTNode parent) {
    ASTNode from = myNode, to = myNode.getTreeNext();

    boolean seenComma = false;

    ASTNode toCandidate = to;
    while (toCandidate != null && toCandidate.getElementType() == TokenType.WHITE_SPACE) {
      toCandidate = toCandidate.getTreeNext();
    }

    if (toCandidate != null && toCandidate.getElementType() == JsonElementTypes.COMMA) {
      toCandidate = toCandidate.getTreeNext();
      to = toCandidate;
      seenComma = true;

      if (to != null && to.getElementType() == TokenType.WHITE_SPACE) {
        to = to.getTreeNext();
      }
    }

    if (!seenComma) {
      ASTNode treePrev = from.getTreePrev();

      while (treePrev != null && treePrev.getElementType() == TokenType.WHITE_SPACE) {
        from = treePrev;
        treePrev = treePrev.getTreePrev();
      }
      if (treePrev != null && treePrev.getElementType() == JsonElementTypes.COMMA) {
        from = treePrev;
      }
    }

    parent.removeRange(from, to);
  }
}
