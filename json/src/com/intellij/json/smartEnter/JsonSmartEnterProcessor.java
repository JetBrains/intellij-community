package com.intellij.json.smartEnter;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.lang.SmartEnterProcessorWithFixers;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * This processor allows
 * <ul>
 *   <li>Insert colon after key inside object property</li>
 *   <li>Insert comma after array element or object property</li>
 * </ul>
 *
 * @author Mikhail Golubev
 */
public class JsonSmartEnterProcessor extends SmartEnterProcessorWithFixers {
  public static final Logger LOG = Logger.getInstance(JsonSmartEnterProcessor.class);

  public JsonSmartEnterProcessor() {
    addFixers(new JsonObjectPropertyFixer(), new JsonArrayElementFixer());
    addEnterProcessors(new JsonEnterProcessor());
  }

  @Override
  protected void collectAdditionalElements(@NotNull PsiElement element, @NotNull List<PsiElement> result) {
    // include all parents as well
    PsiElement parent = element.getParent();
    while (parent != null && !(parent instanceof JsonFile)) {
      result.add(parent);
      parent = parent.getParent();
    }
  }

  private static boolean terminatedOnCurrentLine(@NotNull Editor editor, @NotNull PsiElement element) {
    final Document document = editor.getDocument();
    final int caretOffset = editor.getCaretModel().getCurrentCaret().getOffset();
    final int elementEndOffset = element.getTextRange().getEndOffset();
    if (document.getLineNumber(elementEndOffset) != document.getLineNumber(caretOffset)) {
      return false;
    }
    // Skip empty PsiError elements if comma is missing
    PsiElement nextLeaf = PsiTreeUtil.nextLeaf(element, true);
    return nextLeaf == null || (nextLeaf instanceof PsiWhiteSpace && nextLeaf.getText().contains("\n"));
  }

  private static boolean isFollowedByComma(@NotNull PsiElement element) {
    final PsiElement nextLeaf = PsiTreeUtil.nextVisibleLeaf(element);
    return nextLeaf != null && nextLeaf.getNode().getElementType() == JsonElementTypes.COMMA;
  }

  private static class JsonArrayElementFixer extends SmartEnterProcessorWithFixers.Fixer<JsonSmartEnterProcessor>{
    @Override
    public void apply(@NotNull Editor editor, @NotNull JsonSmartEnterProcessor processor, @NotNull PsiElement element)
      throws IncorrectOperationException {
      if (element instanceof JsonValue && element.getParent() instanceof JsonArray) {
        final JsonValue arrayElement = (JsonValue)element;
        if (terminatedOnCurrentLine(editor, arrayElement) && !isFollowedByComma(element)) {
          editor.getDocument().insertString(arrayElement.getTextRange().getEndOffset(), ",");
        }
      }
    }
  }

  private static class JsonObjectPropertyFixer extends SmartEnterProcessorWithFixers.Fixer<JsonSmartEnterProcessor> {
    @Override
    public void apply(@NotNull Editor editor, @NotNull JsonSmartEnterProcessor processor, @NotNull PsiElement element)
      throws IncorrectOperationException {
      if (element instanceof JsonProperty) {
        final JsonValue jsonValue = ((JsonProperty)element).getValue();
        if (jsonValue != null && terminatedOnCurrentLine(editor, jsonValue) && !isFollowedByComma(jsonValue)) {
          editor.getDocument().insertString(jsonValue.getTextRange().getEndOffset(), ",");
        }
      }
    }
  }

  private static class JsonEnterProcessor extends SmartEnterProcessorWithFixers.FixEnterProcessor {
    @Override
    public boolean doEnter(PsiElement atCaret, PsiFile file, @NotNull Editor editor, boolean modified) {
      plainEnter(editor);
      return true;
    }
  }
}
