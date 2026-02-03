// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public abstract class JsonBySchemaCompletionBaseTest extends BasePlatformTestCase {
  protected List<LookupElement> myItems;

  @Override
  protected void tearDown() throws Exception {
    myItems = null;
    super.tearDown();
  }

  protected void testBySchema(@Language("JSON") @NotNull String schema,
                              @NotNull String text,
                              @NotNull String testFileNameWithExtension,
                              String @NotNull ... variants) throws Exception {
    testBySchema(schema, text, testFileNameWithExtension, LookupElement::getLookupString, CompletionType.BASIC, variants);
  }

  protected void testBySchema(@Language("JSON") @NotNull String schema,
                              @NotNull String text,
                              @NotNull String testFileNameWithExtension,
                              @NotNull Function<@NotNull LookupElement,
                              @NotNull String> lookupElementRepresentation,
                              @NotNull CompletionType completionType,
                              String @NotNull ... variants) throws Exception {
    myItems = findAllPossibleCompletionVariantsLegacy(schema, text, testFileNameWithExtension, completionType);

    assertEqualRegardlessOfOrder(
      Arrays.stream(variants),
      myItems.stream().map(lookupElementRepresentation)
    );
  }

  /**
   * @return a list of lookups that does not represent the actual completion list in production
   * because collected by direct completion contributor call.
   * <p>
   * It is ok to use this method in addition to the {@code findAllPossibleCompletionVariantsLegacy} to check item presentations,
   * but not ok to check the effective completions list
   */
  protected List<LookupElement> findAllPossibleCompletionVariantsLegacy(@Language("JSON") @NotNull String schema,
                                                                      @NotNull String text,
                                                                      @NotNull String testFileNameWithExtension,
                                                                      @NotNull CompletionType completionType) throws Exception {
    return findVariants(
      configureSchema(schema),
      getElementAtCaretIn(text, testFileNameWithExtension),
      completionType
    );
  }

  /** Duplicates are semantic in [expected] and [actual] */
  private static <T extends Comparable<T>> void assertEqualRegardlessOfOrder(Stream<@NotNull T> expected, Stream<@NotNull T> actual) {
    assertEquals(expected.sorted().toList(), actual.sorted().toList());
  }

  @NotNull
  private static List<LookupElement> findVariants(JsonSchemaObject rootSchema, PsiElement position, @NotNull CompletionType completionType) {
    return JsonSchemaCompletionContributor.getCompletionVariants(rootSchema, position, position, completionType);
  }

  @NotNull
  private JsonSchemaObject configureSchema(@Language("JSON") @NotNull String schema) throws Exception {
    deleteFileIfExists("testSchema.json");

    JsonSchemaObject schemaObject = JsonSchemaReader.readFromFile(
      getProject(),
      myFixture.addFileToProject("testSchema.json", schema).getVirtualFile()
    );

    assertThat(schemaObject).isNotNull();
    return schemaObject;
  }

  @Nullable
  private PsiElement getElementAtCaretIn(@NotNull String text, @NotNull String testFileNameWithExtension) throws IOException {
    String completionText = text.replace("<caret>", CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED);
    deleteFileIfExists(testFileNameWithExtension);

    PsiElement elementAtCaret = myFixture.addFileToProject(testFileNameWithExtension, completionText)
      .findElementAt(EditorTestUtil.getCaretPosition(text));
    assertThat(elementAtCaret).isNotNull();
    return elementAtCaret;
  }

  private void deleteFileIfExists(@NotNull String filePath) throws IOException {
    VirtualFile fileInTemp = myFixture.findFileInTempDir(filePath);
    if (fileInTemp != null) {
      WriteAction.run(() -> fileInTemp.delete(null));
    }
  }
}
