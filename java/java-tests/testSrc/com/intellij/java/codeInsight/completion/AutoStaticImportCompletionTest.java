// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiMethod;
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
      JavaProjectCodeInsightSettings.getSettings(getProject()).includedAutoStaticNames.clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
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
    addStaticAutoImport("java.util.Arrays");
    configure();
    type("r");
    LookupElement[] elements = myFixture.getLookupElements();
    LookupElement element = ContainerUtil.find(elements, e ->
      e.getLookupString().equals("sort") &&
      e.getPsiElement() instanceof PsiMethod method &&
      method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
    assertNotNull(element);
    selectItem(element);
    checkResult();
  }

  @NeedsIndex.Full
  public void testSimpleStaticAutoImportWithConflict() {
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
    type("r");
    LookupElement[] elements = myFixture.getLookupElements();
    LookupElement element = ContainerUtil.find(elements, e ->
      e.getLookupString().equals("sort") &&
      e.getPsiElement() instanceof PsiMethod method &&
      method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
    assertNotNull(element);
    selectItem(element);
    checkResult();
  }

  public void testSimpleStaticAutoImportWithAlreadyImported() {
    addStaticAutoImport("java.util.Arrays");
    configure();
    type("r");
    LookupElement[] elements = myFixture.getLookupElements();
    LookupElement element = ContainerUtil.find(elements, e ->
      e.getLookupString().equals("sort") &&
      e.getPsiElement() instanceof PsiMethod method &&
      method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
    assertNotNull(element);
    selectItem(element);
    checkResult();
  }

  public void testNoAutoImport() {
    configure();
    type("r");
    LookupElement[] elements = myFixture.getLookupElements();
    LookupElement element = ContainerUtil.find(elements, e ->
      e.getLookupString().equals("sort") &&
      e.getPsiElement() instanceof PsiMethod method &&
      method.getContainingClass().getQualifiedName().equals("java.util.Arrays"));
    assertNull(element);
  }

  @NeedsIndex.Full
  public void testChainedAutoStaticImport() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> {
      addStaticAutoImport("java.util.stream.Collectors");
      configureByTestName();
      selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().contains("toList")));
      checkResult();
    });
  }

  @NeedsIndex.Full
  public void testChainedAutoStaticImportWithConflict() {
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
    addStaticAutoImport("java.util.Objects");
    myFixture.addClass("""
                         package requireNonNull;
                         public final class Dummy {}
                         """);
    configure();
    type("requireN");
    LookupElement[] elements = myFixture.getLookupElements();
    LookupElement element = ContainerUtil.find(elements, e ->
      e.getLookupString().equals("requireNonNull") &&
      e.getPsiElement() instanceof PsiMethod method &&
      method.getContainingClass().getQualifiedName().equals("java.util.Objects"));
    assertNotNull(element);
    selectItem(element);
    checkResult();
  }

  @NeedsIndex.Full
  public void testWithMemberConflicts() {
    addStaticAutoImport("a.Objects");
    myFixture.addClass("""
                         package a;
                         public final class Objects {
                           public static <T> T requireNonNull(T obj) {}
                         }
                         """);
    configure();
    type("requireN");
    LookupElement[] elements = myFixture.getLookupElements();
    LookupElement element = ContainerUtil.find(elements, e ->
      e.getLookupString().equals("requireNonNull") &&
      e.getPsiElement() instanceof PsiMethod method &&
      method.getContainingClass().getQualifiedName().equals("a.Objects"));
    assertNotNull(element);
    selectItem(element);
    checkResult();
  }

  private void addStaticAutoImport(@NotNull String name) {
    JavaProjectCodeInsightSettings.getSettings(getProject()).includedAutoStaticNames.add(name);
  }
}