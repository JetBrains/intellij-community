package com.intellij.json.editor.lineMover;

import com.intellij.codeInsight.editorActions.moveUpDown.LineMover;
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonLineMover extends LineMover {

  private boolean myShouldAddComma = false;

  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    myShouldAddComma = false;

    if (!(file instanceof JsonFile) || !super.checkAvailable(editor, file, info, down)) {
      return false;
    }

    Pair<PsiElement, PsiElement> movedElementRange = getElementRange(editor, file, info.toMove);
    if (!isValidElementRange(movedElementRange)) {
      return false;
    }

    // Tweak range to move if it's necessary
    movedElementRange = expandCommentsInRange(movedElementRange);
    info.toMove = new LineRange(movedElementRange.getFirst(), movedElementRange.getSecond());

    // Adjust destination range to prevent illegal offsets
    final int lineCount = editor.getDocument().getLineCount();
    if (down) {
      info.toMove2 = new LineRange(info.toMove.endLine, Math.min(info.toMove.endLine + 1, lineCount));
    }
    else {
      info.toMove2 = new LineRange(Math.max(info.toMove.startLine - 1, 0), info.toMove.startLine);
    }

    if (movedElementRange.getFirst() instanceof PsiComment && movedElementRange.getSecond() instanceof PsiComment) {
      return true;
    }

    // Check whether additional comma is needed
    final Pair<PsiElement, PsiElement> destElementRange = getElementRange(editor, file, info.toMove2);
    if (isValidElementRange(destElementRange) && movedElementRange.getFirst().getParent() == destElementRange.getSecond().getParent()) {
      final PsiElement commonParent = movedElementRange.getFirst().getParent();
      final PsiElement lowerRightElement = down ? destElementRange.getSecond() : movedElementRange.getSecond();
      // Destination rightmost element is not closing brace or bracket
      if (lowerRightElement instanceof JsonElement) {
        if (commonParent instanceof JsonArray && notFollowedByNextElementOrComma(lowerRightElement, JsonValue.class) ||
            commonParent instanceof JsonObject && notFollowedByNextElementOrComma(lowerRightElement, JsonProperty.class)) {
          myShouldAddComma = true;
        }
      }
    }
    return true;
  }

  @NotNull
  private Pair<PsiElement, PsiElement> expandCommentsInRange(@NotNull Pair<PsiElement, PsiElement> range) {
    final PsiElement upper = JsonPsiUtil.findFurthestSiblingOfSameType(range.getFirst(), false);
    final PsiElement lower = JsonPsiUtil.findFurthestSiblingOfSameType(range.getSecond(), true);
    return Pair.create(upper, lower);
  }

  @Override
  public void beforeMove(@NotNull Editor editor, @NotNull MoveInfo info, boolean down) {
    if (myShouldAddComma) {
      final Document document = editor.getDocument();
      final int lineBelow = down ? info.toMove2.endLine - 1 : info.toMove.endLine - 1;
      document.insertString(document.getLineEndOffset(lineBelow), ",");

      final int lineAbove = down ? info.toMove.endLine - 1 : info.toMove2.endLine - 1;
      final int lineAboveEndOffset = document.getLineEndOffset(lineAbove);
      final String aboveLineEnding = document.getText(new TextRange(lineAboveEndOffset - 1, lineAboveEndOffset));
      if (aboveLineEnding.equals(",")) {
        document.deleteString(lineAboveEndOffset - 1, lineAboveEndOffset);
      }
      final Project project = editor.getProject();
      assert project != null;
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
  }

  private boolean notFollowedByNextElementOrComma(@NotNull PsiElement anchor, @NotNull Class<? extends PsiElement> nextElementType) {
    return PsiTreeUtil.getNextSiblingOfType(anchor, nextElementType) == null &&
           TreeUtil.findSibling(anchor.getNode(), JsonElementTypes.COMMA) == null;
  }

  private boolean isValidElementRange(@Nullable Pair<PsiElement, PsiElement> elementRange) {
    if (elementRange == null) {
      return false;
    }
    return elementRange.getFirst().getParent() == elementRange.getSecond().getParent();
  }
}
