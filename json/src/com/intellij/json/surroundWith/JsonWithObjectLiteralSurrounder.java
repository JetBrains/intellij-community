package com.intellij.json.surroundWith;

import com.intellij.json.JsonBundle;
import com.intellij.json.psi.*;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This surrounder ported from JavaScript allows to wrap single JSON value or several consecutive JSON properties
 * in object literal.
 * <p/>
 * Examples:
 * <ol>
 * <li>{@code [42]} converts to <code>[{"property": 42}]</code></li>
 * <li><pre>
 * {
 *    "foo": 42,
 *    "bar": false
 * }
 * </pre> converts to <pre>
 * {
 *    "property": {
 *      "foo": 42,
 *      "bar": false
 *    }
 * }
 * </pre></li>
 * </ol>
 *
 * @author Mikhail Golubev
 */
public class JsonWithObjectLiteralSurrounder implements Surrounder {
  @Override
  public String getTemplateDescription() {
    return JsonBundle.message("surround.with.object.literal.desc");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    return !JsonPsiUtil.isPropertyKey(elements[0]);
  }

  @Nullable
  @Override
  public TextRange surroundElements(@NotNull Project project,
                                    @NotNull Editor editor,
                                    @NotNull PsiElement[] elements) throws IncorrectOperationException {

    if (!isApplicable(elements)) {
      return null;
    }

    final JsonElementGenerator generator = new JsonElementGenerator(project);

    final PsiElement firstElement = elements[0];
    final JsonElement newNameElement;
    if (firstElement instanceof JsonValue) {
      assert elements.length == 1 : "Only single JSON value can be wrapped in object literal";
      JsonObject replacement = generator.createValue("{\n\"property\": " + firstElement.getText() + "\n}");
      replacement = (JsonObject)firstElement.replace(replacement);
      newNameElement = replacement.getPropertyList().get(0).getNameElement();
    }
    else {
      assert firstElement instanceof JsonProperty;
      final JsonProperty firstProperty = (JsonProperty)elements[0];
      final JsonProperty lastProperty = (JsonProperty)elements[elements.length - 1];
      final TextRange replacedRange = new TextRange(firstProperty.getTextOffset(), lastProperty.getTextRange().getEndOffset());
      final String propertiesText = replacedRange.substring(firstProperty.getContainingFile().getText());
      if (firstProperty != lastProperty) {
        final PsiElement parent = firstProperty.getParent();
        parent.deleteChildRange(firstProperty.getNextSibling(), lastProperty);
      }
      final JsonObject tempJsonObject = generator.createValue("{\"property\": {\n" + propertiesText + "\n}}");
      JsonProperty replacement = tempJsonObject.getPropertyList().get(0);
      replacement = (JsonProperty)firstProperty.replace(replacement);
      newNameElement = replacement.getNameElement();
    }
    final TextRange rangeWithQuotes = newNameElement.getTextRange();
    return new TextRange(rangeWithQuotes.getStartOffset() + 1, rangeWithQuotes.getEndOffset() - 1);
  }
}
