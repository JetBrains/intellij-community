// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class AddOnDemandStaticImportToAutoImportActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testAddedDefault() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(() -> {
      myFixture.configureByText("a.java", """
        import static java.util.Objects.*<caret>;
        """);
      assertTrue(tryToAddToAutoImport("java.util.Objects"));
      assertTrue(tableContains("java.util.Objects"));
    });
  }

  @Test
  public void testAddFromCallOnDemand() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(() -> {
      myFixture.configureByText("a.java", """
        import static java.util.Objects.*;
        
        public class Favorite {
            public static void a() {
                require<caret>NonNull("a");
            }
        }
        """);
      assertTrue(tryToAddToAutoImport("java.util.Objects"));
      assertTrue(tableContains("java.util.Objects"));
    });
  }

  @Test
  public void testAddFromCall() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(() -> {
      myFixture.configureByText("a.java", """
        import static java.util.Objects.requireNonNull;
        
        public class Favorite {
            public static void a() {
                requireNon<caret>Null("a");
            }
        }
        """);
      assertTrue(tryToAddToAutoImport("java.util.Objects.requireNonNull"));
      assertTrue(tableContains("java.util.Objects.requireNonNull"));
    });
  }

  @Test
  public void testAlreadyAdded() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(() -> {
      JavaProjectCodeInsightSettings codeInsightSettings = JavaProjectCodeInsightSettings.getSettings(myFixture.getProject());
      codeInsightSettings.includedAutoStaticNames = List.of("java.util.Objects");
      myFixture.configureByText("a.java", """
        import static java.util.Objects.*<caret>;
        """);
      assertFalse(tryToAddToAutoImport("java.util.Objects"));
    });
  }

  private boolean tryToAddToAutoImport(@NotNull String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<IntentionAction> actions =
      myFixture.filterAvailableIntentions(JavaBundle.message("intention.add.on.demand.static.import.to.auto.import.text", name));
    if (actions.size() != 1) return false;
    myFixture.launchAction(actions.get(0));
    return true;
  }

  private boolean tableContains(@NotNull String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaProjectCodeInsightSettings codeInsightSettings = JavaProjectCodeInsightSettings.getSettings(myFixture.getProject());
    return codeInsightSettings.isStaticAutoImportName(name);
  }

  private void doTest(@NotNull Runnable runnable) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaProjectCodeInsightSettings codeInsightSettings = JavaProjectCodeInsightSettings.getSettings(myFixture.getProject());
    List<String> oldValues = codeInsightSettings.includedAutoStaticNames;
    try {
      runnable.run();
    }
    finally {
      codeInsightSettings.includedAutoStaticNames = oldValues;
    }
  }
}
