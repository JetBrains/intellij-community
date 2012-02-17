package com.intellij.codeInspection;

import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.InspectionTestCase;

public class RedundantSuppressTest extends InspectionTestCase {
  private GlobalInspectionToolWrapper myWrapper;
  private InspectionTool[] myInspectionTools;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionToolRegistrar.getInstance().ensureInitialized();
    myInspectionTools = new InspectionTool[]{new LocalInspectionToolWrapper(new I18nInspection()),
      new GlobalInspectionToolWrapper(new EmptyMethodInspection())};

    myWrapper = new GlobalInspectionToolWrapper(new RedundantSuppressInspection() {
      @Override
      protected InspectionTool[] getInspectionTools(PsiElement psiElement, InspectionManager manager) {
        return myInspectionTools;
      }
    });
  }

  public void testDefaultFile() throws Exception {
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

  private void doTest() throws Exception {
    doTest("redundantSuppress/" + getTestName(true), myWrapper,"java 1.5",true);
  }
}
