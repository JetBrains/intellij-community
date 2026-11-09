// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.JavaIdeCodeInsightSettings;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.StaticImportCanBeUsedInspection;
import com.intellij.java.JavaBundle;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class StaticImportCanBeUsedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new StaticImportCanBeUsedInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/staticImportCanBeUsed/");
  }

  @AfterEach
  void tearDown() {
    cleanTable();
  }

  private void cleanTable() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    JavaProjectCodeInsightSettings.getSettings(myFixture.getProject()).includedAutoStaticNames.clear();
    JavaIdeCodeInsightSettings.getInstance().includedAutoStaticNames.clear();
  }

  @Test
  public void testSimple() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      inspectionFixture.get().doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testSimpleWithExclusion() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      addStaticAutoImportToProject("-java.util.Arrays.sort");
      inspectionFixture.get().doTest();
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testRemoveAutoImportProject() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      addStaticAutoImportToProject("-java.util.Arrays.binarySearch");
      inspectionFixture.get().doTest();
      IntentionAction intention = myFixture.getAvailableIntention(
        JavaBundle.message("inspection.static.import.can.be.used.remove.from.auto.import.name", "java.util.Arrays.sort", 0));
      myFixture.launchAction(intention);
      checkAutoImportDoesntContain("java.util.Arrays");
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testRemoveAutoImportProjectAdditionalImports() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      addStaticAutoImportToProject("java.util.Arrays2");
      addStaticAutoImportToProject("java.util.A");
      addStaticAutoImportToProject("java.util");
      inspectionFixture.get().doNamedTest("RemoveAutoImportProject");
      IntentionAction intention = myFixture.getAvailableIntention(
        JavaBundle.message("inspection.static.import.can.be.used.remove.from.auto.import.name", "java.util.Arrays.sort", 0));
      myFixture.launchAction(intention);
      checkAutoImportDoesntContain("java.util.Arrays.sort");
      checkAutoImportContains("java.util.Arrays2");
      checkAutoImportContains("java.util.A");
      checkAutoImportContains("java.util");
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testExcludeAutoImportProject() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      addStaticAutoImportToProject("-java.util.Arrays.binarySearch");
      inspectionFixture.get().doNamedTest("RemoveAutoImportProject");
      IntentionAction intention = myFixture.getAvailableIntention(
        JavaBundle.message("inspection.static.import.can.be.used.exclude.from.auto.import.name", "java.util.Arrays.sort", 0));
      myFixture.launchAction(intention);
      checkAutoImportContains("java.util.Arrays");
      checkAutoImportDoesntContain("java.util.Arrays.sort");
      checkAutoImportDoesntContain("java.util.Arrays.binarySearch");
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testRemoveAutoImportIde() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      addStaticAutoImportToIde("java.util.Arrays");
      addStaticAutoImportToIde("-java.util.Arrays.binarySearch");
      inspectionFixture.get().doTest();
      IntentionAction intention = myFixture.getAvailableIntention(
        JavaBundle.message("inspection.static.import.can.be.used.remove.from.auto.import.name", "java.util.Arrays.sort", 1));
      myFixture.launchAction(intention);
      checkAutoImportDoesntContain("java.util.Arrays");
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testExcludeAutoImportIde() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      addStaticAutoImportToIde("java.util.Arrays");
      addStaticAutoImportToIde("-java.util.Arrays.binarySearch");
      inspectionFixture.get().doNamedTest("RemoveAutoImportIde");
      IntentionAction intention = myFixture.getAvailableIntention(
        JavaBundle.message("inspection.static.import.can.be.used.exclude.from.auto.import.name", "java.util.Arrays.sort", 1));
      myFixture.launchAction(intention);
      checkAutoImportContains("java.util.Arrays");
      checkAutoImportDoesntContain("java.util.Arrays.sort");
      checkAutoImportDoesntContain("java.util.Arrays.binarySearch");
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testExcludeAutoImportIdeMethodAdded() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      addStaticAutoImportToIde("java.util.Arrays.sort");
      inspectionFixture.get().doNamedTest("RemoveAutoImportIde");
      String message = JavaBundle.message("inspection.static.import.can.be.used.exclude.from.auto.import.name", "java.util.Arrays.sort", 1);
      assertNull(myFixture.getAvailableIntention(message));
    }
    finally {
      cleanTable();
    }
  }

  private void checkAutoImportDoesntContain(@NotNull String fqn) {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    assertFalse(JavaCodeStyleManager.getInstance(myFixture.getProject()).isStaticAutoImportName(fqn));
  }

  private void checkAutoImportContains(@NotNull String fqn) {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    assertTrue(JavaCodeStyleManager.getInstance(myFixture.getProject()).isStaticAutoImportName(fqn));
  }

  @Test
  public void testSimpleWithOnDemand() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    addStaticAutoImportToProject("java.util.Arrays");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    PackageEntry entry = new PackageEntry(true, "java.util.Arrays", false);
    PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    int onDemandLength = onDemand.getEntries().length;
    onDemand.addEntry(entry);
    try {
      inspectionFixture.get().doTest();
      cleanupTest();
    }
    finally {
      onDemand.removeEntryAt(onDemandLength);
      cleanTable();
    }
  }

  @Test
  public void testSimpleMethod() {
    try {
      addStaticAutoImportToProject("java.util.Arrays.sort");
      inspectionFixture.get().doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testSimpleMethodOnDemand() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    addStaticAutoImportToProject("java.util.Arrays.sort");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    PackageEntry entry = new PackageEntry(true, "java.util.Arrays", false);
    PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    int onDemandLength = onDemand.getEntries().length;
    onDemand.addEntry(entry);
    try {
      inspectionFixture.get().doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
      onDemand.removeEntryAt(onDemandLength);
    }
  }

  @Test
  public void testWithConflicts() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      inspectionFixture.get().doTest();
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testWithConflicts2() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      myFixture.addClass("""
                             package org;
                           
                             public final class Foo2 {
                                 public static void binarySearch(Object[] args, Object key) {}
                             }
                           """);
      inspectionFixture.get().doTest();
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testWithConflictsWithField() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      addStaticAutoImportToProject("org.Foo2");
      myFixture.addClass("""
                             package org;
                           
                             public final class Foo2 {
                                 public static final String PI = "3.14"
                                 public static void sort(String[] args) {}
                             }
                           """);
      myFixture.addClass("""
                             package org;
                           
                             public final class Foo3 {
                                 public static final String PI = "3.14"
                             }
                           """);
      inspectionFixture.get().doTest();
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testWithConflictsWithClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      addStaticAutoImportToProject("org.Foo2");
      myFixture.addClass("""
                             package org;
                           
                             public final class Foo2 {
                                 public static class Calculus{}
                                 public static void sort(String[] args) {}
                             }
                           """);
      myFixture.addClass("""
                             package org;
                           
                             public final class Foo3 {
                                 public static class Calculus{}
                             }
                           """);
      inspectionFixture.get().doTest();
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testAlreadyImported() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      inspectionFixture.get().doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
    }
  }

  @Test
  public void testDoubleTheSameMethod() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    try {
      myFixture.addClass("""
                           package staticImportCanBeUsed;
                           import java.util.Locale;
                           public interface Example {
                             default String localize() {
                               return localize(Locale.getDefault());
                           	 }
                             String localize(Locale locale);
                           }""");

      myFixture.addClass("""
                           package staticImportCanBeUsed;
                           import java.util.Locale;
                           public class StaticMethod {
                             public static String localize(Locale locale, String translationKey) {
                               return translationKey;
                             }
                           }""");
      addStaticAutoImportToProject("staticImportCanBeUsed.StaticMethod.localize");
      inspectionFixture.get().doTest();
    }
    finally {
      cleanTable();
    }
  }

  private void cleanupTest() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IntentionAction intention = myFixture.getAvailableIntention(AnalysisBundle.message("cleanup.in.file"));
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(inspectionFixture.get().getTestName(false) + "_after.java");
  }

  @SuppressWarnings("SameParameterValue")
  private void addStaticAutoImportToProject(@NotNull String name) {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    JavaProjectCodeInsightSettings.getSettings(myFixture.getProject()).includedAutoStaticNames.add(name);
  }

  private static void addStaticAutoImportToIde(@NotNull String name) {
    JavaIdeCodeInsightSettings.getInstance().includedAutoStaticNames.add(name);
  }
}