package com.intellij.json.editor.selection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.json.JsonElementTypes.SINGLE_QUOTED_STRING;

/**
 * @author Mikhail Golubev
 */
public class JsonStringLiteralSelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(PsiElement e) {
    if (!(e.getParent() instanceof JsonStringLiteral)) {
      return false;
    }
    return !InjectedLanguageManager.getInstance(e.getProject()).isInjectedFragment(e.getContainingFile());
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    final IElementType type = e.getNode().getElementType();
    final StringLiteralLexer lexer = new StringLiteralLexer(type == SINGLE_QUOTED_STRING ? '\'' : '"', type, false, "/", false, false);
    final List<TextRange> result = new ArrayList<>();
    SelectWordUtil.addWordHonoringEscapeSequences(editorText, e.getTextRange(), cursorOffset, lexer, result);

    final PsiElement parent = e.getParent();
    result.add(ElementManipulators.getValueTextRange(parent).shiftRight(parent.getTextOffset()));
    return result;
  }
}
