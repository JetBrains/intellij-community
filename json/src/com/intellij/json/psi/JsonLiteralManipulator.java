package com.intellij.json.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class JsonLiteralManipulator extends AbstractElementManipulator<JsonLiteral> {

  @Override
  public JsonLiteral handleContentChange(@NotNull JsonLiteral element, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    String text = "{\"\":\"" + newContent + "\"}";

    final PsiFile dummy = JsonPsiChangeUtils.createDummyFile(element, text);
    JsonProperty property = PsiTreeUtil.findChildOfType(dummy, JsonProperty.class);
    assert property != null;

    JsonValue value = property.getValue();
    assert value instanceof JsonLiteral;

    return (JsonLiteral)element.replace(value);
  }
}
