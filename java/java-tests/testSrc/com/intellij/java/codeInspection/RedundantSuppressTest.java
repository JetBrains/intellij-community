// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.LanguageInspectionSuppressors;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class RedundantSuppressTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture);

  private GlobalInspectionToolWrapper myWrapper;
  private final List<InspectionToolWrapper<?, ?>> myWrappers = new ArrayList<>();

  @BeforeEach
  public void setUp() {
    myWrapper = new GlobalInspectionToolWrapper(new RedundantSuppressInspection() {
      @Override
      protected @NotNull @Unmodifiable List<InspectionToolWrapper<?, ?>> getInspectionTools(@NotNull PsiElement psiElement, @NotNull InspectionProfile profile) {
        return myWrappers;
      }
    });
  }

  @Test
  public void testModuleInfo() {
    myWrappers.add(new LocalInspectionToolWrapper(new JavaDocReferenceInspection()));
    javaInspection.get().doTest("redundantSuppress/" + javaInspection.get().getTestName(true), myWrapper, false);
  }

  @Test
  public void testDefaultFile() {
    myWrappers.add(new LocalInspectionToolWrapper(new I18nInspection()));
    doTest();
  }

  @Test
  public void testAlternativeIds() { doTest(); }

  @Test
  public void testAnnotator() {
    myWrappers.add(new GlobalInspectionToolWrapper(new HighlightVisitorBasedInspection().setRunAnnotators(true)));
    javaInspection.get().doTest("redundantSuppress/" + javaInspection.get().getTestName(true), myWrapper, false);
  }

  @Test
  public void testIgnoreUnused() { doTest(); }
  @Test
  public void testIgnoreWithAnnotation() { doTest(); }

  @Test
  public void testSameSuppressIds() {
    myWrappers.add(new LocalInspectionToolWrapper(new UncheckedWarningLocalInspection()));
    doTest(); 
  }

  @Test
  public void testSuppressAll() {
    ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = true;
    doTest();
  }

  @Test
  public void testInjections() {
    MyTestInjector testInjector = new MyTestInjector(PsiManager.getInstance(javaInspection.get().getFixture().getProject()));
    testInjector.injectAll(javaInspection.get().getFixture().getTestRootDisposable());

    doTest();
  }

  @Test
  public void testAdditionalEmptySuppressor() {
    LanguageInspectionSuppressors.INSTANCE.addExplicitExtension(Language.findLanguageByID("UAST"), new InspectionSuppressor() {
      @Override
      public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        return false;
      }

      @Override
      public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
      }
    }, javaInspection.get().getFixture().getTestRootDisposable());
    myWrappers.add(new LocalInspectionToolWrapper(new I18nInspection()));
    doTest();
  }

  private void doTest() {
    javaInspection.get().doTest("redundantSuppress/" + javaInspection.get().getTestName(true), myWrapper, true);
  }
}
