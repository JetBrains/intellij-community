package com.intellij.json.psi;

import com.intellij.json.JsonElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.TokenType;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  public static PsiFile createDummyFile(JsonLiteral element, String text) {
    Language language = element.getLanguage();
    ParserDefinition def = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    assert def != null;
    Project project = element.getProject();
    final PsiFile dummyFile = PsiFileFactory.getInstance(project).createFileFromText("dummy.json", language, text, false, true);
    assert dummyFile instanceof JsonFile;
    return dummyFile;
  }
}
