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
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.JavaInspectionTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

public class RedundantSuppressTest extends JavaInspectionTestCase {
  private GlobalInspectionToolWrapper myWrapper;
  private final List<InspectionToolWrapper<?, ?>> myWrappers = new ArrayList<>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myWrapper = new GlobalInspectionToolWrapper(new RedundantSuppressInspection() {
      @Override
      protected @NotNull @Unmodifiable List<InspectionToolWrapper<?, ?>> getInspectionTools(@NotNull PsiElement psiElement, @NotNull InspectionProfile profile) {
        return myWrappers;
      }
    });
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  @Override
  protected void tearDown() throws Exception {
    myWrapper = null;
    super.tearDown();
  }

  public void testModuleInfo() {
    myWrappers.add(new LocalInspectionToolWrapper(new JavaDocReferenceInspection()));
    doTest("redundantSuppress/" + getTestName(true), myWrapper, false);
  }

  public void testDefaultFile() {
    myWrappers.add(new LocalInspectionToolWrapper(new I18nInspection()));
    doTest();
  }

  public void testAlternativeIds() { doTest(); }

  public void testAnnotator() {
    myWrappers.add(new GlobalInspectionToolWrapper(new HighlightVisitorBasedInspection().setRunAnnotators(true)));
    doTest("redundantSuppress/" + getTestName(true), myWrapper, false);
  }

  public void testIgnoreUnused() { doTest(); }
  public void testIgnoreWithAnnotation() { doTest(); }

  public void testSameSuppressIds() {
    myWrappers.add(new LocalInspectionToolWrapper(new UncheckedWarningLocalInspection()));
    doTest(); 
  }

  public void testSuppressAll() {
    ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = true;
    doTest();
  }

  public void testInjections() {
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(myFixture.getTestRootDisposable());

    doTest();
  }

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
    }, getTestRootDisposable());
    myWrappers.add(new LocalInspectionToolWrapper(new I18nInspection()));
    doTest();
  }

  private void doTest() {
    doTest("redundantSuppress/" + getTestName(true), myWrapper, true);
  }
}
