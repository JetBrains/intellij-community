// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Irina.Chernushina on 2/20/2017.
 */
public abstract class JsonBySchemaCompletionBaseTest extends BasePlatformTestCase {
  protected List<LookupElement> myItems;

  @Override
  protected void tearDown() throws Exception {
    myItems = null;
    super.tearDown();
  }

  protected void testBySchema(@Language("JSON") @NotNull String schema,
                              @NotNull String text,
                              @NotNull String extension,
                              String @NotNull ... variants) throws Exception {
    int position = EditorTestUtil.getCaretPosition(text);
    assertThat(position).isGreaterThan(0);
    String completionText = text.replace("<caret>", "IntelliJIDEARulezzz");

    VirtualFile fileInTemp = myFixture.findFileInTempDir("tslint." + extension);
    if (fileInTemp != null) {
      WriteAction.run(() -> fileInTemp.delete(null));
    }

    PsiFile file = myFixture.addFileToProject("tslint." + extension, completionText);
    PsiElement element = file.findElementAt(position);
    assertThat(element).isNotNull();

    VirtualFile schemaInTemp = myFixture.findFileInTempDir("testSchema.json");
    if (schemaInTemp != null) {
      WriteAction.run(() -> schemaInTemp.delete(null));
    }

    PsiFile schemaFile = myFixture.addFileToProject("testSchema.json", schema);
    JsonSchemaObject schemaObject = JsonSchemaReader.readFromFile(getProject(), schemaFile.getVirtualFile());
    assertThat(schemaObject).isNotNull();

    List<LookupElement> foundVariants = JsonSchemaCompletionContributor.getCompletionVariants(schemaObject, element, element);
    Collections.sort(foundVariants, Comparator.comparing(LookupElement::getLookupString));
    List<String> actual = ContainerUtil.map(foundVariants, LookupElement::getLookupString);
    assertOrderedEquals(actual, variants);
    myItems = foundVariants;
  }
}
