package com.intellij.json.editor.smartEnter;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.lang.SmartEnterProcessorWithFixers;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.json.JsonElementTypes.COLON;
import static com.intellij.json.JsonElementTypes.COMMA;

/**
 * This processor allows
 * <ul>
 * <li>Insert colon after key inside object property</li>
 * <li>Insert comma after array element or object property</li>
 * </ul>
 *
 * @author Mikhail Golubev
 */
public class JsonSmartEnterProcessor extends SmartEnterProcessorWithFixers {
  public static final Logger LOG = Logger.getInstance(JsonSmartEnterProcessor.class);

  private boolean myShouldAddNewline = false;

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

  private static boolean isFollowedByTerminal(@NotNull PsiElement element, IElementType type) {
    final PsiElement nextLeaf = PsiTreeUtil.nextVisibleLeaf(element);
    return nextLeaf != null && nextLeaf.getNode().getElementType() == type;
  }

  private static class JsonArrayElementFixer extends SmartEnterProcessorWithFixers.Fixer<JsonSmartEnterProcessor> {
    @Override
    public void apply(@NotNull Editor editor, @NotNull JsonSmartEnterProcessor processor, @NotNull PsiElement element)
      throws IncorrectOperationException {
      if (element instanceof JsonValue && element.getParent() instanceof JsonArray) {
        final JsonValue arrayElement = (JsonValue)element;
        if (terminatedOnCurrentLine(editor, arrayElement) && !isFollowedByTerminal(element, COMMA)) {
          editor.getDocument().insertString(arrayElement.getTextRange().getEndOffset(), ",");
          processor.myShouldAddNewline = true;
        }
      }
    }
  }

  private class JsonObjectPropertyFixer extends SmartEnterProcessorWithFixers.Fixer<JsonSmartEnterProcessor> {
    @Override
    public void apply(@NotNull Editor editor, @NotNull JsonSmartEnterProcessor processor, @NotNull PsiElement element)
      throws IncorrectOperationException {
      if (element instanceof JsonProperty) {
        final JsonValue propertyValue = ((JsonProperty)element).getValue();
        if (propertyValue != null) {
          if (terminatedOnCurrentLine(editor, propertyValue) && !isFollowedByTerminal(propertyValue, COMMA)) {
            editor.getDocument().insertString(propertyValue.getTextRange().getEndOffset(), ",");
            processor.myShouldAddNewline = true;
          }
        }
        else {
          final JsonValue propertyKey = ((JsonProperty)element).getNameElement();
          final int keyEndOffset = propertyKey.getTextRange().getEndOffset();
          //processor.myFirstErrorOffset = keyEndOffset;
          if (terminatedOnCurrentLine(editor, propertyKey) && !isFollowedByTerminal(propertyKey, COLON)) {
            processor.myFirstErrorOffset = keyEndOffset + 2;
            editor.getDocument().insertString(keyEndOffset, ": ");
          }
        }
      }
    }
  }

  private class JsonEnterProcessor extends SmartEnterProcessorWithFixers.FixEnterProcessor {
    @Override
    public boolean doEnter(PsiElement atCaret, PsiFile file, @NotNull Editor editor, boolean modified) {
      if (myShouldAddNewline) {
        try {
          plainEnter(editor);
        }
        finally {
          myShouldAddNewline = false;
        }
      }
      return true;
    }
  }
}
