package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.javadoc.JavadocHelper;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Serves as a facade for javadoc smart completion.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 5/31/11 1:22 PM
 */
public class JavadocFixer {

  private final JavadocHelper myHelper = new JavadocHelper();
  
  /**
   * Checks if caret of the given editor is located inside javadoc and tries to perform smart completion there in case of the positive
   * answer.
   * 
   * @param editor   target editor
   * @param psiFile  PSI file for the document exposed via the given editor
   * @return  <code>true</code> if smart completion was performed; <code>false</code> otherwise
   */
  public boolean process(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    // Check parameter description completion.
    final CaretModel caretModel = editor.getCaretModel();
    final Pair<JavadocHelper.JavadocParameterInfo,List<JavadocHelper.JavadocParameterInfo>> pair =
      myHelper.parse(psiFile, editor, caretModel.getOffset());

    if (pair.first == null) {
      return false;
    }

    final JavadocHelper.JavadocParameterInfo next = findNext(pair.second, pair.first);
    if (next == null) {
      return false;
    }

    if (next.parameterDescriptionStartPosition != null) {
      myHelper.navigate(next.parameterDescriptionStartPosition, editor, psiFile.getProject());
    }
    else {
      final LogicalPosition position = myHelper.calculateDescriptionStartPosition(psiFile, pair.second, next);
      myHelper.navigate(position, editor, psiFile.getProject());
    }
    return true;
  }

  @Nullable
  private static JavadocHelper.JavadocParameterInfo findNext(@NotNull Collection<JavadocHelper.JavadocParameterInfo> data,
                                                             @NotNull JavadocHelper.JavadocParameterInfo anchor)
  {
    boolean returnNow = false;
    for (JavadocHelper.JavadocParameterInfo info : data) {
      if (returnNow) {
        return info;
      }
      returnNow = info == anchor;
    }
    return null;
  }
}
