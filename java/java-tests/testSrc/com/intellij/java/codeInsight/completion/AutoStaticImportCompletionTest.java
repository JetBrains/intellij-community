// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

@NeedsIndex.ForStandardLibrary
public class AutoStaticImportCompletionTest extends NormalCompletionTestCase {

  @Override
  public void tearDown() throws Exception {
    try {
      clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void clear() {
    JavaProjectCodeInsightSettings.getSettings(getProject()).includedAutoStaticNames.clear();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Override
  public final String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/autoStaticImport/";
  }

  public void testSimpleStaticAutoImport() {
    try {
      addStaticAutoImport("java.util.Arrays");
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("sort") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
    }
  }

  public void testExcludeAutoImport() {
    try {
      addStaticAutoImport("java.util.Arrays");
      addStaticAutoImport("-java.util.Arrays.sort");
      configure();
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
  }

  public void testFullAutoImportEnum() {
    try {
      addStaticAutoImport("java.time.temporal.ChronoField");
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      assertNotNull(elements);
      LookupElement element = ContainerUtil.find(elements, e ->
        e.getLookupString().equals("values") &&
        e.getPsiElement() instanceof PsiMethod method &&
        method.getContainingClass().getQualifiedName().equals("java.time.temporal.ChronoField"));
      assertNotNull(element);
      selectItem(element);
      checkResult();
    }
    finally {
      clear();
    }
  }

  public void testAutoImportEnumWithExclusion() {
    try {
      addStaticAutoImport("java.time.temporal.ChronoField");
      addStaticAutoImport("-java.time.temporal.ChronoField.values");
      configure();
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
      selectItem(element);
      checkResult();
    }
    finally {
      clear();
    }
  }

  public void testSimpleMethodStaticAutoImport() {
    try {
      addStaticAutoImport("java.util.Arrays.sort");
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("sort") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
    }
  }

  public void testSimpleMethodStaticAutoImportOnDemand() {
    addStaticAutoImport("java.util.Arrays.sort");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    PackageEntry entry = new PackageEntry(true, "java.util.Arrays", false);
    PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    int onDemandLength = onDemand.getEntries().length;
    onDemand.addEntry(entry);
    try {
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("sort") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
      onDemand.removeEntryAt(onDemandLength);
    }
  }

  public void testSimpleMethodStaticAutoImportNotFound() {
    try {
      addStaticAutoImport("java.util.Arrays.binarySearch");
      configure();
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
  }

  public void testSimpleStaticAutoImportWithEnabledOnDemand() {
    addStaticAutoImport("java.util.Arrays");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    PackageEntry entry = new PackageEntry(true, "java.util.Arrays", false);
    PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    int onDemandLength = onDemand.getEntries().length;
    onDemand.addEntry(entry);
    try {
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("sort") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
      onDemand.removeEntryAt(onDemandLength);
    }
  }

  @NeedsIndex.Full
  public void testSimpleStaticAutoImportWithConflict() {
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
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("sort") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
    }
  }

  public void testSimpleStaticAutoImportWithAlreadyImported() {
    try {
      addStaticAutoImport("java.util.Arrays");
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("sort") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
    }
  }

  public void testNoAutoImport() {
    configure();
    LookupElement[] elements = myFixture.getLookupElements();
    if (elements != null) {
      LookupElement element = ContainerUtil.find(elements, e ->
        e.getLookupString().equals("sort") &&
        e.getPsiElement() instanceof PsiMethod method &&
        method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
      assertNull(element);
    }
  }

  @NeedsIndex.Full
  public void testChainedAutoStaticImport() {
    try {
      IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> {
        addStaticAutoImport("java.util.stream.Collectors");
        configureByTestName();
        selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().contains("toList")));
        checkResult();
      });
    }
    finally {
      clear();
    }
  }

  @NeedsIndex.Full
  public void testChainedAutoStaticImportWithConflict() {
    try {
      IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> {
        addStaticAutoImport("java.util.stream.Collectors");
        myFixture.addClass("""
                             package org.example;
                             public final class AAA {
                               public static <T> List<T> toList(){
                                 return null;
                               }
                             }
                             """);
        configureByTestName();
        selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().contains("toList")));
        checkResult();
      });
    }
    finally {
      clear();
    }
  }

  public void testNoSimpleStaticAutoImport() {
    configure();
    LookupElement[] elements = myFixture.completeBasic();
    LookupElement element = ContainerUtil.find(elements, e ->
      e.getLookupString().equals("sort") &&
      e.getPsiElement() instanceof PsiMethod method &&
      method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
    assertNotNull(element);
    selectItem(element);
    checkResult();
  }

  @NeedsIndex.Full
  public void testWithPackageConflicts() {
    try {
      addStaticAutoImport("java.util.Objects");
      myFixture.addClass("""
                           package requireNonNull;
                           public final class Dummy {}
                           """);
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("requireNonNull") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Objects"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
    }
  }

  @NeedsIndex.Full
  public void testWithPackageConflictsWithOnDemand() {
    addStaticAutoImport("java.util.Objects");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    PackageEntry entry = new PackageEntry(true, "java.util.Objects", false);
    PackageEntryTable onDemand = javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    int onDemandLength = onDemand.getEntries().length;
    onDemand.addEntry(entry);
    try {
      myFixture.addClass("""
                           package requireNonNull;
                           public final class Dummy {}
                           """);
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("requireNonNull") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("java.util.Objects"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
      onDemand.removeEntryAt(onDemandLength);
    }
  }

  @NeedsIndex.Full
  public void testWithMemberConflicts() {
    try {
      addStaticAutoImport("a.Objects");
      myFixture.addClass("""
                           package a;
                           public final class Objects {
                             public static <T> T requireNonNull(T obj) {}
                           }
                           """);
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("requireNonNull") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("a.Objects"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
    }
  }

  @NeedsIndex.Full
  public void testDefaultPackage() {
    try {
      addStaticAutoImport("Foo.bar");
      myFixture.addClass("""
                           public final class Foo {
                             public static void bar() {}
                           }
                           """);
      configure();
      LookupElement[] elements = myFixture.getLookupElements();
      if (elements != null) {
        LookupElement element = ContainerUtil.find(elements, e ->
          e.getLookupString().equals("bar") &&
          e.getPsiElement() instanceof PsiMethod method &&
          method.getContainingClass().getQualifiedName().equals("Foo"));
        assertNotNull(element);
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
    }
  }

  @NeedsIndex.Full
  public void testDefaultPackageFromNonDefaultPackage() {
    try {
      addStaticAutoImport("Foo.bar");
      myFixture.addClass("""
                           public final class Foo {
                             public static void bar() {}
                           }
                           """);
      configure();
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
  }

  @NeedsIndex.Full
  public void testShowPackage() {
    try {
      addStaticAutoImport("org.example.Foo.bar");
      myFixture.addClass("""
                           package org.example;
                           public final class Foo {
                             public static void bar() {}
                           }
                           """);
      configure();
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
        selectItem(element);
      }
      checkResult();
    }
    finally {
      clear();
    }
  }

  private void addStaticAutoImport(@NotNull String name) {
    JavaProjectCodeInsightSettings.getSettings(getProject()).includedAutoStaticNames.add(name);
  }
}