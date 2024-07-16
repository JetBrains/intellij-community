// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.surroundWith;

import com.intellij.json.JsonBundle;
import com.intellij.json.psi.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This surrounder ported from JavaScript allows to wrap single JSON value or several consecutive JSON properties
 * in object literal.
 * <p/>
 * Examples:
 * <ol>
 * <li>{@code [42]} converts to {@code [{"property": 42}]}</li>
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
public final class JsonWithObjectLiteralSurrounder extends JsonSurrounderBase {
  @Override
  public String getTemplateDescription() {
    return JsonBundle.message("surround.with.object.literal.desc");
  }

  @Override
  public boolean isApplicable(PsiElement @NotNull [] elements) {
    return !JsonPsiUtil.isPropertyKey(elements[0]) && (elements[0] instanceof JsonProperty || elements.length == 1);
  }

  @Override
  public @Nullable TextRange surroundElements(@NotNull Project project,
                                              @NotNull Editor editor,
                                              PsiElement @NotNull [] elements) {

    if (!isApplicable(elements)) {
      return null;
    }

    final JsonElementGenerator generator = new JsonElementGenerator(project);

    final PsiElement firstElement = elements[0];
    final JsonElement newNameElement;
    if (firstElement instanceof JsonValue) {
      assert elements.length == 1 : "Only single JSON value can be wrapped in object literal";
      JsonObject replacement = generator.createValue(createReplacementText(firstElement.getText()));
      replacement = (JsonObject)firstElement.replace(replacement);
      newNameElement = replacement.getPropertyList().get(0).getNameElement();
    }
    else {
      assert firstElement instanceof JsonProperty;
      final String propertiesText = getTextAndRemoveMisc(firstElement, elements[elements.length - 1]);
      final JsonObject tempJsonObject = generator.createValue(createReplacementText("{\n" + propertiesText) + "\n}");
      JsonProperty replacement = tempJsonObject.getPropertyList().get(0);
      replacement = (JsonProperty)firstElement.replace(replacement);
      newNameElement = replacement.getNameElement();
    }
    final TextRange rangeWithQuotes = newNameElement.getTextRange();
    return new TextRange(rangeWithQuotes.getStartOffset() + 1, rangeWithQuotes.getEndOffset() - 1);
  }

  @Override
  protected @NotNull String createReplacementText(@NotNull String textInRange) {
    return "{\n\"property\": " + textInRange + "\n}";
  }
}
