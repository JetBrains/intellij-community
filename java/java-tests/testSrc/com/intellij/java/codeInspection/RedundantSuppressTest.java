// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.miscGenerics.RawUseOfParameterizedTypeInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.JavaInspectionTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.dataflow.UnnecessaryLocalVariableInspection;
import com.siyeh.ig.inheritance.RefusedBequestInspection;
import com.siyeh.ig.internationalization.UnnecessaryUnicodeEscapeInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class RedundantSuppressTest extends JavaInspectionTestCase {
  private GlobalInspectionToolWrapper myWrapper;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    List<InspectionToolWrapper<?, ?>> myInspectionToolWrappers = Arrays.asList(new LocalInspectionToolWrapper(new JavaDocReferenceInspection()),
                                             new LocalInspectionToolWrapper(new PossibleHeapPollutionVarargsInspection()),
                                             new LocalInspectionToolWrapper(new UncheckedWarningLocalInspection()),
                                             new LocalInspectionToolWrapper(new I18nInspection()),
                                             new LocalInspectionToolWrapper(new RawUseOfParameterizedTypeInspection()),
                                             new LocalInspectionToolWrapper(new UnnecessaryLocalVariableInspection()),
                                             new LocalInspectionToolWrapper(new UnnecessaryUnicodeEscapeInspection()),
                                             new LocalInspectionToolWrapper(new RefusedBequestInspection()),
                                             new GlobalInspectionToolWrapper(new EmptyMethodInspection()),
                                             new GlobalInspectionToolWrapper(new HighlightVisitorBasedInspection().setRunAnnotators(true)),
                                             new GlobalInspectionToolWrapper(new UnusedDeclarationInspection()));

    myWrapper = new GlobalInspectionToolWrapper(new RedundantSuppressInspection() {
      @Override
      protected @NotNull List<InspectionToolWrapper<?, ?>> getInspectionTools(@NotNull PsiElement psiElement, @NotNull InspectionProfile profile) {
        return myInspectionToolWrappers;
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
    doTest("redundantSuppress/" + getTestName(true), myWrapper, false);
  }

  public void testDefaultFile() {
    doTest();
  }

  public void testAlternativeIds() {
    doTest();
  }

  public void testAnnotator() {
    doTest("redundantSuppress/" + getTestName(true), myWrapper, false);
  }

  public void testIgnoreUnused() {
    doTest();
  }

  public void testIgnoreWithAnnotation() { doTest(); }

  public void testSameSuppressIds() { doTest(); }

  public void testSuppressAll() {
    try {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = true;
      doTest();
    }
    finally {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = false;
    }
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
    doTest("redundantSuppress/defaultFile", myWrapper, true);
  }

  private void doTest() {
    doTest("redundantSuppress/" + getTestName(true), myWrapper, true);
  }
}
