// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AddOnDemandStaticImportToAutoImportActionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testAddedDefault() {
    doTest(() -> {
      myFixture.configureByText("a.java", """
        import static java.util.Objects.*;
        """);
      assertTrue(tryToAddToAutoImport("java.util.Objects"));
      assertTrue(tableContains("java.util.Objects"));
    });
  }


  public void testAlreadyAdded() {
    doTest(() -> {
      JavaProjectCodeInsightSettings codeInsightSettings = JavaProjectCodeInsightSettings.getSettings(getProject());
      codeInsightSettings.includedAutoStaticNames = List.of("java.util.Objects");
      myFixture.configureByText("a.java", """
        import static java.util.Objects.*;
        """);
      assertFalse(tryToAddToAutoImport("java.util.Objects"));
    });
  }

  private boolean tryToAddToAutoImport(@NotNull String name) {
    List<IntentionAction> actions =
      myFixture.filterAvailableIntentions(JavaBundle.message("intention.add.on.demand.static.import.to.auto.import.text", name));
    if (actions.size() != 1) return false;
    myFixture.launchAction(actions.get(0));
    return true;
  }


  private boolean tableContains(@NotNull String name) {
    JavaProjectCodeInsightSettings codeInsightSettings = JavaProjectCodeInsightSettings.getSettings(getProject());
    return codeInsightSettings.isStaticAutoImportClass(name);
  }

  private void doTest(@NotNull Runnable runnable) {
    JavaProjectCodeInsightSettings codeInsightSettings = JavaProjectCodeInsightSettings.getSettings(getProject());
    List<String> oldValues = codeInsightSettings.includedAutoStaticNames;
    try {
      runnable.run();
    }
    finally {
      codeInsightSettings.includedAutoStaticNames = oldValues;
    }
  }
}
