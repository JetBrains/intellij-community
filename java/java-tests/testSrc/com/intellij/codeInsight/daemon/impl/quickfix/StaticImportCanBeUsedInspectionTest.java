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
      addStaticAutoImport("java.util.Arrays");
      doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testSimpleWithExclusion() {
    try {
      addStaticAutoImport("java.util.Arrays");
      addStaticAutoImport("-java.util.Arrays.sort");
      doTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testRemoveAutoImportProject() {
    try {
      addStaticAutoImport("java.util.Arrays");
      addStaticAutoImport("-java.util.Arrays.binarySearch");
      doTest();
      IntentionAction intention = myFixture.getAvailableIntention(
        JavaBundle.message("inspection.static.import.can.be.used.remove.from.auto.import.name", "java.util.Arrays.sort"));
      myFixture.launchAction(intention);
      checkAutoImportDoesntContain("java.util.Arrays");
    }
    finally {
      cleanTable();
    }
  }

  public void testExcludeAutoImportProject() {
    try {
      addStaticAutoImport("java.util.Arrays");
      addStaticAutoImport("-java.util.Arrays.binarySearch");
      doNamedTest("RemoveAutoImportProject");
      IntentionAction intention = myFixture.getAvailableIntention(
        JavaBundle.message("inspection.static.import.can.be.used.exclude.from.auto.import.name", "java.util.Arrays.sort"));
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
        JavaBundle.message("inspection.static.import.can.be.used.remove.from.auto.import.name", "java.util.Arrays.sort"));
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
        JavaBundle.message("inspection.static.import.can.be.used.exclude.from.auto.import.name", "java.util.Arrays.sort"));
      myFixture.launchAction(intention);
      checkAutoImportContains("java.util.Arrays");
      checkAutoImportDoesntContain("java.util.Arrays.sort");
      checkAutoImportDoesntContain("java.util.Arrays.binarySearch");
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
    addStaticAutoImport("java.util.Arrays");
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
      addStaticAutoImport("java.util.Arrays.sort");
      doTest();
      cleanupTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testSimpleMethodOnDemand() {
    addStaticAutoImport("java.util.Arrays.sort");
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
      addStaticAutoImport("java.util.Arrays");
      doTest();
    }
    finally {
      cleanTable();
    }
  }

  public void testWithConflicts2() {
    try {
      addStaticAutoImport("java.util.Arrays");
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
      addStaticAutoImport("org.Foo2");
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
      addStaticAutoImport("org.Foo2");
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
      addStaticAutoImport("java.util.Arrays");
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
  private void addStaticAutoImport(@NotNull String name) {
    JavaProjectCodeInsightSettings.getSettings(getProject()).includedAutoStaticNames.add(name);
  }

  private static void addStaticAutoImportToIde(@NotNull String name) {
    JavaIdeCodeInsightSettings.getInstance().includedAutoStaticNames.add(name);
  }
}
