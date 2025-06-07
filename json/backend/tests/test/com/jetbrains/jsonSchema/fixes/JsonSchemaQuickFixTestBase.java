// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.StubTextInconsistencyException;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public abstract class JsonSchemaQuickFixTestBase extends JsonSchemaHighlightingTestBase {

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData/jsonSchema/highlighting";
  }

  protected void doTest(@Language("JSON") @NotNull String schema, @NotNull String text, String fixName, String afterFix) {
    PsiFile psiFile = configureInitially(schema, text, "json");
    myFixture.checkHighlighting();
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    IntentionAction action = ContainerUtil.find(intentions, o -> fixName.equals(o.getText()));
    if (action == null) {
      String intentionsList = intentions.stream().map(i -> i.getText()).distinct().sorted().collect(Collectors.joining("\n"));
      throw new IllegalStateException("No available intention found with name \"" + fixName + "\". Available intentions:\n" + intentionsList);
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, myFixture.getEditor(), action, action.getText());
      }
      catch (StubTextInconsistencyException e) {
        PsiTestUtil.compareStubTexts(e);
      }
    });
    UIUtil.dispatchAllInvocationEvents();
    myFixture.checkResult(afterFix);
  }
}
