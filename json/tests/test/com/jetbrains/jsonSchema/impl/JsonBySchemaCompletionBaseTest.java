// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Irina.Chernushina on 2/20/2017.
 */
public abstract class JsonBySchemaCompletionBaseTest extends CompletionTestCase {
  protected void testBySchema(@Language("JSON") @NotNull final String schema, final @NotNull String text, final @NotNull String extension,
                              final @NotNull String... variants) throws Exception {
    final int position = EditorTestUtil.getCaretPosition(text);
    assertThat(position).isGreaterThan(0);
    final String completionText = text.replace("<caret>", "IntelliJIDEARulezzz");

    final PsiFile file = createFile(myModule, "tslint." + extension, completionText);
    final PsiElement element = file.findElementAt(position);
    assertThat(element).isNotNull();

    final PsiFile schemaFile = createFile(myModule, "testSchema.json", schema);
    final JsonSchemaObject schemaObject = JsonSchemaReader.readFromFile(myProject, schemaFile.getVirtualFile());
    assertThat(schemaObject).isNotNull();

    final List<LookupElement> foundVariants = JsonSchemaCompletionContributor.getCompletionVariants(schemaObject, element, element);
    Collections.sort(foundVariants, Comparator.comparing(LookupElement::getLookupString));
    myItems = foundVariants.toArray(LookupElement.EMPTY_ARRAY);
    assertStringItems(variants);
  }
}
