// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.JavaIdeCodeInsightSettings;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.StaticImportCanBeUsedInspection;
import com.intellij.java.JavaBundle;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class StaticImportCanBeUsedInspectionTest extends LightJavaInspectionTestCase {

  @Override
  public void tearDown() throws Exception {
    try {
      cleanTable();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void cleanTable() {
    JavaProjectCodeInsightSettings.getSettings(getProject()).includedAutoStaticNames.clear();
    JavaIdeCodeInsightSettings.getInstance().includedAutoStaticNames.clear();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StaticImportCanBeUsedInspection();
  }

  public void testSimple() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testSimpleWithExclusion() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      addStaticAutoImportToProject("-java.util.Arrays.sort");
      doTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testRemoveAutoImportProject() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      addStaticAutoImportToProject("-java.util.Arrays.binarySearch");
      doTest();
      IntentionAction intention = myFixture.getAvailableIntention(
        JavaBundle.message("inspection.static.import.can.be.used.remove.from.auto.import.name", "java.util.Arrays.sort", 0));
      myFixture.launchAction(intention);
      checkAutoImportDoesntContain("java.util.Arrays");
    }
    finally {
      cleanTable();
    }
  }

  public void testRemoveAutoImportProjectAdditionalImports() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      addStaticAutoImportToProject("java.util.Arrays2");
      addStaticAutoImportToProject("java.util.A");
      addStaticAutoImportToProject("java.util");
      doNamedTest("RemoveAutoImportProject");
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

  public void testExcludeAutoImportProject() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      addStaticAutoImportToProject("-java.util.Arrays.binarySearch");
      doNamedTest("RemoveAutoImportProject");
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

  public void testRemoveAutoImportIde() {
    try {
      addStaticAutoImportToIde("java.util.Arrays");
      addStaticAutoImportToIde("-java.util.Arrays.binarySearch");
      doTest();
      IntentionAction intention = myFixture.getAvailableIntention(
        JavaBundle.message("inspection.static.import.can.be.used.remove.from.auto.import.name", "java.util.Arrays.sort", 1));
      myFixture.launchAction(intention);
      checkAutoImportDoesntContain("java.util.Arrays");
    }
    finally {
      cleanTable();
    }
  }

  public void testExcludeAutoImportIde() {
    try {
      addStaticAutoImportToIde("java.util.Arrays");
      addStaticAutoImportToIde("-java.util.Arrays.binarySearch");
      doNamedTest("RemoveAutoImportIde");
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

  public void testExcludeAutoImportIdeMethodAdded() {
    try {
      addStaticAutoImportToIde("java.util.Arrays.sort");
      doNamedTest("RemoveAutoImportIde");
      String message = JavaBundle.message("inspection.static.import.can.be.used.exclude.from.auto.import.name", "java.util.Arrays.sort", 1);
      assertNull(myFixture.getAvailableIntention(message));
    }
    finally {
      cleanTable();
    }
  }

  private void checkAutoImportDoesntContain(@NotNull String fqn) {
    assertFalse(JavaCodeStyleManager.getInstance(getProject()).isStaticAutoImportName(fqn));
  }

  private void checkAutoImportContains(@NotNull String fqn) {
    assertTrue(JavaCodeStyleManager.getInstance(getProject()).isStaticAutoImportName(fqn));
  }

  public void testSimpleWithOnDemand() {
    addStaticAutoImportToProject("java.util.Arrays");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    PackageEntry entry = new PackageEntry(true, "java.util.Arrays", false);
    PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    int onDemandLength = onDemand.getEntries().length;
    onDemand.addEntry(entry);
    try {
      doTest();
      cleanupTest();
    }
    finally {
      onDemand.removeEntryAt(onDemandLength);
      cleanTable();
    }
  }

  public void testSimpleMethod() {
    try {
      addStaticAutoImportToProject("java.util.Arrays.sort");
      doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testSimpleMethodOnDemand() {
    addStaticAutoImportToProject("java.util.Arrays.sort");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    PackageEntry entry = new PackageEntry(true, "java.util.Arrays", false);
    PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    int onDemandLength = onDemand.getEntries().length;
    onDemand.addEntry(entry);
    try {
      doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
      onDemand.removeEntryAt(onDemandLength);
    }
  }

  public void testWithConflicts() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      doTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testWithConflicts2() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      myFixture.addClass("""
                             package org;
                           
                             public final class Foo2 {
                                 public static void binarySearch(Object[] args, Object key) {}
                             }
                           """);
      doTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testWithConflictsWithField() {
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
      doTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testWithConflictsWithClass() {
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
      doTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testAlreadyImported() {
    try {
      addStaticAutoImportToProject("java.util.Arrays");
      doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
    }
  }

  private void cleanupTest() {
    IntentionAction intention = myFixture.getAvailableIntention(AnalysisBundle.message("cleanup.in.file"));
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/staticImportCanBeUsed/";
  }

  @SuppressWarnings("SameParameterValue")
  private void addStaticAutoImportToProject(@NotNull String name) {
    JavaProjectCodeInsightSettings.getSettings(getProject()).includedAutoStaticNames.add(name);
  }

  private static void addStaticAutoImportToIde(@NotNull String name) {
    JavaIdeCodeInsightSettings.getInstance().includedAutoStaticNames.add(name);
  }
}