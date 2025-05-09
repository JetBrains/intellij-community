// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;
import com.intellij.refactoring.anonymousToInner.VariableInfo;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;


@TestDataPath("$CONTENT_ROOT/testData")
public class AnonymousToInnerTest extends LightJavaCodeInsightTestCase {
  private static final String TEST_ROOT = "/refactoring/anonymousToInner/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testGenericTypeParameters() {  // IDEADEV-29446
    doTest("MyIterator", true);
  }

  public void testGenericTypeParametersNonStatic() {
    doTest("MyIterator", false);
  }

  public void testGenericTypeParametersNonStaticNoGenericsNeeded() {
    doTest("MyIterator", false);
  }

  public void testInsideInterface() {  // IDEADEV-29446
    doTest("MyRunnable", true);
  }
  
  public void testCollapseDiamonds() {  // IDEADEV-29446
    doTest("MyPredicate", true);
  }
  
  public void testRedundantTypeParameter() {
    doTest("MyConsumer", false);
  }
  
  public void testRequiredTypeParameter() {
    doTest("MyConsumer", true);
  }

  public void testDiamondType() {
    doTest("StringArrayList" ,true);
  }
  
  public void testCaptureInFieldInitializer() {
    doTest("Inner" ,true);
  }
  
  public void testLocalClass() {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    AnonymousToInnerHandler handler = new AnonymousToInnerHandler(){
      @Override
      protected boolean showRefactoringDialog() {
        myNewClassName = "MyObject";
        myMakeStatic = !needsThis();
        VariableInfo info = myVariableInfos[0];
        info.fieldName = "myX";
        info.parameterName = "x";
        myVariableInfos = new VariableInfo[] {info};
        return true;
      }
    };
    handler.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }

  public void testLocalClassNoTP() {
    doTest("InnerClass", true);
  }
  
  public void testLocalClassNoRename() {
    doTest("Hello", true);
  }
  
  public void testLocalClassVarargCtor() {
    doTest("InnerClass", true);
  }
  
  public void testLocalClassCaptureInCtorOnly() {
    doTest("InnerClass", true);
  }
  
  public void testLocalRecord() {
    doTest("MyRecord", false);
  }

  public void testLocalEnum() {
    doTest("MyEnum", false);
  }

  public void testCanBeStatic() {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    AnonymousToInnerHandler handler = new AnonymousToInnerHandler(){
      @Override
      protected boolean showRefactoringDialog() {
        myNewClassName = "MyPredicate";
        myMakeStatic = !needsThis();
        return true;
      }
    };
    handler.invoke(getProject(), getEditor(), getFile(), null);
    assertFalse(handler.needsThis());
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }

  public void testNameConflict() {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    AnonymousToInnerHandler handler = new AnonymousToInnerHandler(){
      @Override
      protected boolean showRefactoringDialog() {
        myNewClassName = "MyObject";
        myMakeStatic = !needsThis();
        VariableInfo info = myVariableInfos[0];
        info.fieldName = "myFast";
        info.parameterName = "fast";
        myVariableInfos = new VariableInfo[] {info};
        return true;
      }
    };
    handler.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }
  
  public void testTypeParameterNotMentioned() {
    doTest("MyClass", true);
  }
  

  private void doTest(final String newClassName, final boolean makeStatic) {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    AnonymousToInnerHandler handler = new AnonymousToInnerHandler() {
      @Override
      protected boolean showRefactoringDialog() {
        myNewClassName = newClassName;
        myMakeStatic = makeStatic;
        for (VariableInfo info : myVariableInfos) {
          info.parameterName = info.fieldName = info.variable.getName();
        }
        return true;
      }
    };


    handler.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }
}
