// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the JSON schema completion variants at the caret.
 * The helpers call the completion contributor directly, so the variants do not represent the actual completion list in production.
 * Call them off the EDT.
 */
public final class JsonSchemaCompletionTestUtil {
  private JsonSchemaCompletionTestUtil() {
  }

  /**
   * Checks that the basic completion variants at the caret have the lookup strings {@code variants}, in any order.
   *
   * @return the variants
   */
  public static @NotNull List<LookupElement> testBySchema(@NotNull CodeInsightTestFixture fixture,
                                                          @Language("JSON") @NotNull String schema,
                                                          @NotNull String text,
                                                          @NotNull String testFileNameWithExtension,
                                                          String @NotNull ... variants) throws Exception {
    return testBySchema(fixture, schema, text, testFileNameWithExtension, LookupElement::getLookupString, CompletionType.BASIC, variants);
  }

  /**
   * Checks that the variants at the caret give {@code variants} in any order.
   * Duplicates are semantic.
   *
   * @param lookupElementRepresentation runs in a read action
   * @return the variants
   */
  public static @NotNull List<LookupElement> testBySchema(@NotNull CodeInsightTestFixture fixture,
                                                          @Language("JSON") @NotNull String schema,
                                                          @NotNull String text,
                                                          @NotNull String testFileNameWithExtension,
                                                          @NotNull Function<@NotNull LookupElement,
                                                          @NotNull String> lookupElementRepresentation,
                                                          @NotNull CompletionType completionType,
                                                          String @NotNull ... variants) throws Exception {
    List<LookupElement> items = findAllPossibleCompletionVariantsLegacy(fixture, schema, text, testFileNameWithExtension, completionType);

    List<String> actual = ReadAction.computeBlocking(() -> items.stream().map(lookupElementRepresentation).sorted().toList());
    assertEquals(Arrays.stream(variants).sorted().toList(), actual);
    return items;
  }

  private static @NotNull List<LookupElement> findAllPossibleCompletionVariantsLegacy(@NotNull CodeInsightTestFixture fixture,
                                                                                     @Language("JSON") @NotNull String schema,
                                                                                     @NotNull String text,
                                                                                     @NotNull String testFileNameWithExtension,
                                                                                     @NotNull CompletionType completionType)
    throws Exception {
    JsonSchemaObject rootSchema = configureSchema(fixture, schema);
    PsiFile file = addFileWithoutCaret(fixture, text, testFileNameWithExtension);
    return ReadAction.computeBlocking(() -> {
      PsiElement position = file.findElementAt(EditorTestUtil.getCaretPosition(text));
      assertThat(position).isNotNull();
      return JsonSchemaCompletionContributor.getCompletionVariants(rootSchema, position, position, completionType);
    });
  }

  private static @NotNull JsonSchemaObject configureSchema(@NotNull CodeInsightTestFixture fixture,
                                                           @Language("JSON") @NotNull String schema) throws Exception {
    deleteFileIfExists(fixture, "testSchema.json");

    VirtualFile schemaFile = fixture.addFileToProject("testSchema.json", schema).getVirtualFile();
    JsonSchemaObject schemaObject = ReadAction.computeBlocking(() -> JsonSchemaReader.readFromFile(fixture.getProject(), schemaFile));

    assertThat(schemaObject).isNotNull();
    return schemaObject;
  }

  private static @NotNull PsiFile addFileWithoutCaret(@NotNull CodeInsightTestFixture fixture,
                                                      @NotNull String text,
                                                      @NotNull String testFileNameWithExtension) throws IOException {
    String completionText = text.replace("<caret>", CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED);
    deleteFileIfExists(fixture, testFileNameWithExtension);
    return fixture.addFileToProject(testFileNameWithExtension, completionText);
  }

  private static void deleteFileIfExists(@NotNull CodeInsightTestFixture fixture, @NotNull String filePath) throws IOException {
    VirtualFile fileInTemp = fixture.getTempDirFixture().getFile(filePath);
    if (fileInTemp != null) {
      WriteAction.runAndWait(() -> fileInTemp.delete(null));
    }
  }
}
