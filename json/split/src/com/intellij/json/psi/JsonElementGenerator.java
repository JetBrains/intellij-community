// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi;

import com.intellij.json.JsonFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonElementGenerator {
  private final Project myProject;

  public JsonElementGenerator(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Create lightweight in-memory {@link JsonFile} filled with {@code content}.
   *
   * @param content content of the file to be created
   * @return created file
   */
  public @NotNull PsiFile createDummyFile(@NotNull String content) {
    final PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(myProject);
    return psiFileFactory.createFileFromText("dummy." + JsonFileType.INSTANCE.getDefaultExtension(), JsonFileType.INSTANCE, content);
  }

  /**
   * Create JSON value from supplied content.
   *
   * @param content properly escaped text of JSON value, e.g. Java literal {@code "\"new\\nline\""} if you want to create string literal
   * @param <T>     type of the JSON value desired
   * @return element created from given text
   *
   * @see #createStringLiteral(String)
   */
  public @NotNull <T extends JsonValue> T createValue(@NotNull String content) {
    final PsiFile file = createDummyFile("{\"foo\": " + content + "}");
    //noinspection unchecked,ConstantConditions
    return (T)((JsonObject)file.getFirstChild()).getPropertyList().get(0).getValue();
  }

  public @NotNull JsonObject createObject(@NotNull String content) {
    final PsiFile file = createDummyFile("{" + content + "}");
    return (JsonObject) file.getFirstChild();
  }

  public @NotNull JsonArray createEmptyArray() {
    final PsiFile file = createDummyFile("[]");
    return (JsonArray) file.getFirstChild();
  }

  public @NotNull JsonValue createArrayItemValue(@NotNull String content) {
    final PsiFile file = createDummyFile("[" + content + "]");
    JsonArray array = (JsonArray)file.getFirstChild();
    return array.getValueList().get(0);
  }

  /**
   * Create JSON string literal from supplied <em>unescaped</em> content.
   *
   * @param unescapedContent unescaped content of string literal, e.g. Java literal {@code "new\nline"} (compare with {@link #createValue(String)}).
   * @return JSON string literal created from given text
   */
  public @NotNull JsonStringLiteral createStringLiteral(@NotNull String unescapedContent) {
    return createValue('"' + StringUtil.escapeStringCharacters(unescapedContent) + '"');
  }

  public @NotNull JsonProperty createProperty(final @NotNull String name, final @NotNull String value) {
    final PsiFile file = createDummyFile("{\"" + name + "\": " + value + "}");
    return ((JsonObject) file.getFirstChild()).getPropertyList().get(0);
  }

  public @NotNull PsiElement createComma() {
    final JsonArray jsonArray1 = createValue("[1, 2]");
    return jsonArray1.getValueList().get(0).getNextSibling();
  }
}
