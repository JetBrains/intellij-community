/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Irina.Chernushina on 2/20/2017.
 */
public abstract class JsonBySchemaCompletionBaseTest extends CompletionTestCase {
  protected void testBySchema(@Language("JSON") @NotNull final String schema, final @NotNull String text, final @NotNull String extension,
                              final @NotNull String... variants) throws Exception {
    final int position = EditorTestUtil.getCaretPosition(text);
    Assert.assertTrue(position > 0);
    final String completionText = text.replace("<caret>", "IntelliJIDEARulezzz");

    final PsiFile file = createFile(myModule, "tslint." + extension, completionText);
    final PsiElement element = file.findElementAt(position);
    Assert.assertNotNull(element);

    final PsiFile schemaFile = createFile(myModule, "testSchema.json", schema);
    final JsonSchemaObject schemaObject = JsonSchemaReader.readFromFile(myProject, schemaFile.getVirtualFile());
    Assert.assertNotNull(schemaObject);

    final List<LookupElement> foundVariants = JsonSchemaCompletionContributor.getCompletionVariants(schemaObject, element, element);
    Collections.sort(foundVariants, Comparator.comparing(LookupElement::getLookupString));
    myItems = foundVariants.toArray(LookupElement.EMPTY_ARRAY);
    assertStringItems(variants);
  }
}
