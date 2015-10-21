package com.intellij.codeInspection;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.InspectionTestCase;
import com.siyeh.ig.migration.RawUseOfParameterizedTypeInspection;
import org.jetbrains.annotations.NotNull;

public class RedundantSuppressTest extends InspectionTestCase {
  private GlobalInspectionToolWrapper myWrapper;
  private InspectionToolWrapper[] myInspectionToolWrappers;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myInspectionToolWrappers = new InspectionToolWrapper[]{
      new LocalInspectionToolWrapper(new I18nInspection()),
      new LocalInspectionToolWrapper(new RawUseOfParameterizedTypeInspection()),
      new GlobalInspectionToolWrapper(new EmptyMethodInspection()),
      new GlobalInspectionToolWrapper(new UnusedDeclarationInspection())};

    myWrapper = new GlobalInspectionToolWrapper(new RedundantSuppressInspection() {
      @Override
      protected InspectionToolWrapper[] getInspectionTools(PsiElement psiElement, @NotNull InspectionManager manager) {
        return myInspectionToolWrappers;
      }
    });
  }

  public void testDefaultFile() throws Exception {
    doTest();
  }

  public void testAlternativeIds() throws Exception {
    doTest();
  }

  public void testIgnoreUnused() throws Exception {
    doTest();
  }

  public void testSuppressAll() throws Exception {
    try {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = true;
      doTest();
    }
    finally {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = false;
    }
  }

  public void testInjections() throws Exception {
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(myTestRootDisposable);
    
    doTest();
  }

  private void doTest() throws Exception {
    doTest("redundantSuppress/" + getTestName(true), myWrapper,"java 1.5",true);
  }
}
