// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class JsonSchemaQuickFixTestBase extends JsonSchemaHighlightingTestBase {

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData/jsonSchema/highlighting";
  }

  protected void doTest(@Language("JSON") @NotNull String schema, @NotNull String text, String fixName, String afterFix) throws Exception {
    configureInitially(schema, text, "json");
    myFixture.doHighlighting();
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    IntentionAction action = ContainerUtil.find(intentions, o -> fixName.equals(o.getFamilyName()));
    action.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    String fileText = myFixture.getFile().getText();
    int caretIndex = afterFix.indexOf("<caret>");
    if (caretIndex >= 0) {
      int caretOffset = myFixture.getEditor().getCaretModel().getOffset();
      fileText = fileText.substring(0, caretOffset - 1) + "<caret>" + fileText.substring(caretOffset - 1);
    }
    assertEquals(afterFix, fileText);
  }
/*
  @Override
  @NotNull
  protected PsiFile configureInitially(@NotNull String schema,
                                       @NotNull String text,
                                       @NotNull String schemaExt) {
    myFixture.enableInspections(getInspectionProfile());
    registerProvider(schema, schemaExt);
    return myFixture.addFileToProject("json_schema_test_r/" + getTestFileName(), text);
  }*/
}
