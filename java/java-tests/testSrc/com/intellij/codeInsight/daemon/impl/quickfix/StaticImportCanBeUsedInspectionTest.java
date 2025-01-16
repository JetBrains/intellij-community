// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.StaticImportCanBeUsedInspection;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class StaticImportCanBeUsedInspectionTest extends LightJavaInspectionTestCase {

  @Override
  public void tearDown() throws Exception {
    try {
      JavaProjectCodeInsightSettings.getSettings(getProject()).includedAutoStaticNames.clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StaticImportCanBeUsedInspection();
  }

  public void testSimple() {
    addStaticAutoImport("java.util.Arrays");
    doTest();
    cleanupTest();
  }

  public void testWithConflicts() {
    addStaticAutoImport("java.util.Arrays");
    doTest();
  }

  public void testWithConflicts2() {
    addStaticAutoImport("java.util.Arrays");
    myFixture.addClass("""
                           package org;
                         
                           public final class Foo2 {
                               public static void binarySearch(Object[] args, Object key) {}
                           }
                         """);
    doTest();
  }

  public void testWithConflictsWithField() {
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

  public void testWithConflictsWithClass() {
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

  public void testAlreadyImported() {
    addStaticAutoImport("java.util.Arrays");
    doTest();
    cleanupTest();
  }

  //todo test for inner class and for field

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
}
