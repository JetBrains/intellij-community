package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author yole
 */
public class AnonymousToInnerTest extends LightCodeInsightTestCase {
  private static final String TEST_ROOT = "/refactoring/anonymousToInner/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  public void testGenericTypeParameters() throws Exception {  // IDEADEV-29446
    doTest("MyIterator", true);
  }

  private void doTest(final String newClassName, final boolean makeStatic) throws Exception {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    AnonymousToInnerHandler handler = new AnonymousToInnerHandler() {
      @Override
      protected boolean showRefactoringDialog() {
        myNewClassName = newClassName;
        myMakeStatic = makeStatic;
        return true;
      }
    };


    handler.invoke(getProject(), myEditor, myFile, null);
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }
}
