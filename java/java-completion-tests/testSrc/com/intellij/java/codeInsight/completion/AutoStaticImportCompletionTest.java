// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NeedsIndex.ForStandardLibrary
@TestApplication
@IndexingModeTemplate
public class AutoStaticImportCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<NormalCompletionFixture> completion =
    normalCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/autoStaticImport/");

  @AfterEach
  void tearDown() {
    clear();
  }

  private void clear() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    JavaProjectCodeInsightSettings.getSettings(myFixture.getProject()).includedAutoStaticNames.clear();
  }

  @Test
  public void testSimpleStaticAutoImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("java.util.Arrays");
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("sort") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  public void testExcludeAutoImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("java.util.Arrays");
        addStaticAutoImport("-java.util.Arrays.sort");
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        assertNotNull(elements);
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("sort") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
        assertNull(element);
      }
      finally {
        clear();
      }
    });
  }

  @Test
  public void testFullAutoImportEnum() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("java.time.temporal.ChronoField");
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        assertNotNull(elements);
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("values") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.time.temporal.ChronoField"));
        assertNotNull(element);
        completion.get().selectItem(element);
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  public void testAutoImportEnumWithExclusion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("java.time.temporal.ChronoField");
        addStaticAutoImport("-java.time.temporal.ChronoField.values");
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        assertNotNull(elements);
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("values") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.time.temporal.ChronoField"));
        assertNull(element);
        element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("NANO_OF_SECOND") &&
          e.getPsiElement() instanceof PsiField psiField &&
          psiField.getContainingClass().getQualifiedName().equals("java.time.temporal.ChronoField"));
        completion.get().selectItem(element);
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  public void testSimpleMethodStaticAutoImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("java.util.Arrays.sort");
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("sort") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  public void testSimpleMethodStaticAutoImportOnDemand() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      addStaticAutoImport("java.util.Arrays.sort");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      PackageEntry entry = new PackageEntry(true, "java.util.Arrays", false);
      PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
      int onDemandLength = onDemand.getEntries().length;
      onDemand.addEntry(entry);
      try {
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("sort") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
        onDemand.removeEntryAt(onDemandLength);
      }
    });
  }

  @Test
  public void testSimpleMethodStaticAutoImportNotFound() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("java.util.Arrays.binarySearch");
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("sort") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
          assertNull(element);
        }
      }
      finally {
        clear();
      }
    });
  }

  @Test
  public void testSimpleStaticAutoImportWithEnabledOnDemand() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      addStaticAutoImport("java.util.Arrays");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      PackageEntry entry = new PackageEntry(true, "java.util.Arrays", false);
      PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
      int onDemandLength = onDemand.getEntries().length;
      onDemand.addEntry(entry);
      try {
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("sort") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
        onDemand.removeEntryAt(onDemandLength);
      }
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSimpleStaticAutoImportWithConflict() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("java.util.Arrays");
        myFixture.addClass("""
                             package org.example;
                             public final class AAA {
                               public static <T> List<T> sort(T[] args){
                                 return null;
                               }
                             }
                             """);
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("sort") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  public void testSimpleStaticAutoImportWithAlreadyImported() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("java.util.Arrays");
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("sort") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  public void testNoAutoImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("sort") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
        assertNull(element);
      }
    });
  }

  @Test
  @NeedsIndex.Full
  public void testChainedAutoStaticImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
          addStaticAutoImport("java.util.stream.Collectors");
          completion.get().configureByTestName();
          completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().contains("toList")));
          completion.get().checkResult();
        });
      }
      finally {
        clear();
      }
    });
  }

  @Test
  @NeedsIndex.Full
  public void testChainedAutoStaticImportWithConflict() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
          addStaticAutoImport("java.util.stream.Collectors");
          myFixture.addClass("""
                               package org.example;
                               public final class AAA {
                                 public static <T> List<T> toList(){
                                   return null;
                                 }
                               }
                               """);
          completion.get().configureByTestName();
          completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().contains("toList")));
          completion.get().checkResult();
        });
      }
      finally {
        clear();
      }
    });
  }

  @Test
  public void testNoSimpleStaticAutoImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      LookupElement[] elements = myFixture.completeBasic();
      LookupElement element = ContainerUtil.find(elements, e ->
        e.getLookupString().equals("sort") &&
        e.getPsiElement() instanceof PsiMethod method &&
        method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
      assertNotNull(element);
      completion.get().selectItem(element);
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testWithPackageConflicts() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("java.util.Objects");
        myFixture.addClass("""
                             package requireNonNull;
                             public final class Dummy {}
                             """);
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("requireNonNull") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("java.util.Objects"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  @NeedsIndex.Full
  public void testWithPackageConflictsWithOnDemand() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      addStaticAutoImport("java.util.Objects");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      PackageEntry entry = new PackageEntry(true, "java.util.Objects", false);
      PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
      int onDemandLength = onDemand.getEntries().length;
      onDemand.addEntry(entry);
      try {
        myFixture.addClass("""
                             package requireNonNull;
                             public final class Dummy {}
                             """);
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("requireNonNull") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("java.util.Objects"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
        onDemand.removeEntryAt(onDemandLength);
      }
    });
  }

  @Test
  @NeedsIndex.Full
  public void testWithMemberConflicts() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("a.Objects");
        myFixture.addClass("""
                             package a;
                             public final class Objects {
                               public static <T> T requireNonNull(T obj) {}
                             }
                             """);
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("requireNonNull") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("a.Objects"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  @NeedsIndex.Full
  public void testDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("Foo.bar");
        myFixture.addClass("""
                             public final class Foo {
                               public static void bar() {}
                             }
                             """);
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("bar") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("Foo"));
          assertNotNull(element);
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  @NeedsIndex.Full
  public void testDefaultPackageFromNonDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("Foo.bar");
        myFixture.addClass("""
                             public final class Foo {
                               public static void bar() {}
                             }
                             """);
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("bar") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("Foo"));
          assertNull(element);
        }
      }
      finally {
        clear();
      }
    });
  }

  @Test
  @NeedsIndex.Full
  public void testShowPackage() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        addStaticAutoImport("org.example.Foo.bar");
        myFixture.addClass("""
                             package org.example;
                             public final class Foo {
                               public static void bar() {}
                             }
                             """);
        completion.get().configure();
        LookupElement[] elements = myFixture.getLookupElements();
        if (elements != null) {
          LookupElement element = ContainerUtil.find(elements, e ->
            e.getLookupString().equals("bar") &&
            e.getPsiElement() instanceof PsiMethod method &&
            method.getContainingClass().getQualifiedName().equals("org.example.Foo"));
          assertNotNull(element);
          LookupElementPresentation presentation = new LookupElementPresentation();
          element.renderElement(presentation);
          assertTrue(presentation.getTailText().contains("org.example"));
          completion.get().selectItem(element);
        }
        completion.get().checkResult();
      }
      finally {
        clear();
      }
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCallCompletionOutsideClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        myFixture.addClass("""
        public final class ClassWithStaticMethodsTwo {
        	static void foo() {}
        	static void foo2() {}
        	static void foo3() {}
        }""");
        addStaticAutoImport("ClassWithStaticMethodsTwo.foo");
        myFixture.configureByText("Foo.java", """
                             public final class Foo {
                               public static void bar() {}
                             }
                             <caret>""");
        LookupElement[] elements = myFixture.completeBasic();
        assertTrue(elements.length > 0);
      }
      finally {
        clear();
      }
    });
  }

  private void addStaticAutoImport(@NotNull String name) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    JavaProjectCodeInsightSettings.getSettings(myFixture.getProject()).includedAutoStaticNames.add(name);
  }
}