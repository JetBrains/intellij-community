package com.intellij.json.editor.lineMover;

import com.intellij.codeInsight.editorActions.moveUpDown.LineMover;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonLineMover extends LineMover {
   // Means that we should add comma after element of array/object because of movement
  private boolean myShouldAddComma = false;

  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    myShouldAddComma = false;

    if (!super.checkAvailable(editor, file, info, down)) {
      return false;
    }

    // TODO Implement proper selection movement
    if (editor.getSelectionModel().hasSelection()) {
      return false;
    }

    final Document document = editor.getDocument();
    final PsiElement targetElement = getFirstElementOnLine(file, document, info.toMove.startLine);
    if (targetElement == null) {
      info.prohibitMove();
      return false;
    }
    info.toMove.firstElement = info.toMove.lastElement = targetElement;
    final PsiElement destinationElement = getFirstElementOnLine(file, document, info.toMove2.startLine);
    info.toMove2.firstElement = info.toMove2.lastElement = destinationElement;

    final PsiElement elementBelow = down ? destinationElement : targetElement;
    if (elementBelow != null) {
      if (JsonPsiUtil.isArrayElement(elementBelow)) {
        if (PsiTreeUtil.getNextSiblingOfType(elementBelow, JsonValue.class) == null &&
            TreeUtil.findSibling(elementBelow.getNode(), JsonElementTypes.COMMA) == null) {
          myShouldAddComma = true;
        }
      }
      else {
        final JsonProperty property = PsiTreeUtil.getNonStrictParentOfType(elementBelow, JsonProperty.class);
        if (property != null && elementFitsLine(document, property, down? info.toMove2.startLine : info.toMove.startLine)) {
          if (PsiTreeUtil.getNextSiblingOfType(property, JsonProperty.class) == null &&
              TreeUtil.findSibling(property.getNode(), JsonElementTypes.COMMA) == null) {
            myShouldAddComma = true;
          }
        }
      }
    }
    info.indentSource = false;
    info.indentTarget = false;

    return true;
}

  @Override
  public void beforeMove(@NotNull Editor editor, @NotNull MoveInfo info, boolean down) {
    if (myShouldAddComma) {
      final Document document = editor.getDocument();
      final PsiElement elementBelow = down ? info.toMove2.firstElement : info.toMove.firstElement;
      document.insertString(elementBelow.getTextRange().getEndOffset(), ",");
      final PsiElement elementAbove = down ? info.toMove.firstElement : info.toMove2.firstElement;
      final int aboveLineEndOffset = document.getLineEndOffset(document.getLineNumber(elementAbove.getTextOffset()));
      final String aboveLineEnding = document.getText(new TextRange(aboveLineEndOffset - 1, aboveLineEndOffset));
      if (aboveLineEnding.equals(",")) {
        document.deleteString(aboveLineEndOffset - 1, aboveLineEndOffset);
      }
      final Project project = editor.getProject();
      assert project != null;
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
  }

  @Nullable
  private static PsiElement getFirstElementOnLine(@NotNull PsiFile psiFile, @NotNull Document document, int line) {

    PsiElement anchor = psiFile.findElementAt(getLineStartSafeOffset(document, line));
    while (anchor instanceof PsiWhiteSpace || anchor instanceof PsiComment) {
      anchor = anchor.getNextSibling();
    }
    if (anchor == null || !elementFitsLine(document, anchor, line)) {
      return null;
    }
    return anchor;
  }

  private static boolean elementFitsLine(@NotNull Document document, @NotNull PsiElement element, int line) {
    final TextRange range = element.getTextRange();
    final int startLine = document.getLineNumber(range.getStartOffset());
    final int endLine = document.getLineNumber(range.getEndOffset());
    return startLine == line && startLine == endLine;
  }
}
