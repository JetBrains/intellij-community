package com.intellij.json.formatter;

import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesHandler;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonEnterBetweenBracesHandler extends EnterBetweenBracesHandler {
  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffsetRef,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    if (!file.getLanguage().is(JsonLanguage.INSTANCE)) {
      return Result.Continue;
    }
    return super.preprocessEnter(file, editor, caretOffsetRef, caretAdvance, dataContext, originalHandler);
  }

  @Override
  protected boolean isBracePair(char c1, char c2) {
    return (c1 == '{' && c2 == '}') || (c1 == '[' && c2 == ']');
  }
}
