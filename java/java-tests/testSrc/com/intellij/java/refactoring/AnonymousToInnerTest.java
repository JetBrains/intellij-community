// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;
import com.intellij.refactoring.anonymousToInner.VariableInfo;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertFalse;


@TestDataPath("$CONTENT_ROOT/testData")
@TestApplication
public class AnonymousToInnerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private static final String TEST_ROOT = "/refactoring/anonymousToInner/";

  @Test
  public void testGenericTypeParameters() {  // IDEADEV-29446
    doTest("MyIterator", true);
  }

  @Test
  public void testGenericTypeParametersNonStatic() {
    doTest("MyIterator", false);
  }

  @Test
  public void testGenericTypeParametersNonStaticNoGenericsNeeded() {
    doTest("MyIterator", false);
  }

  @Test
  public void testGenericParametersWithGenericBounds() {
    doTest("Inner", false);
  }

  @Test
  public void testInsideInterface() {  // IDEADEV-29446
    doTest("MyRunnable", true);
  }
  
  @Test
  public void testCollapseDiamonds() {  // IDEADEV-29446
    doTest("MyPredicate", true);
  }
  
  @Test
  public void testRedundantTypeParameter() {
    doTest("MyConsumer", false);
  }
  
  @Test
  public void testRequiredTypeParameter() {
    doTest("MyConsumer", true);
  }

  @Test
  public void testDiamondType() {
    doTest("StringArrayList" ,true);
  }
  
  @Test
  public void testCaptureInFieldInitializer() {
    doTest("Inner" ,true);
  }
  
  @Test
  public void testLocalClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(TEST_ROOT + myFixture.getTestName(true) + ".java");
    myFixture.runInCommand(() -> {
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
      handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(TEST_ROOT + myFixture.getTestName(true) + "_after.java");
    });
  }

  @Test
  public void testLocalClassNoTP() {
    doTest("InnerClass", true);
  }
  
  @Test
  public void testLocalClassNoRename() {
    doTest("Hello", true);
  }
  
  @Test
  public void testLocalClassVarargCtor() {
    doTest("InnerClass", true);
  }
  
  @Test
  public void testLocalClassCaptureInCtorOnly() {
    doTest("InnerClass", true);
  }
  
  @Test
  public void testLocalRecord() {
    doTest("MyRecord", false);
  }

  @Test
  public void testLocalEnum() {
    doTest("MyEnum", false);
  }

  @Test
  public void testCanBeStatic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(TEST_ROOT + myFixture.getTestName(true) + ".java");
    myFixture.runInCommand(() -> {
      AnonymousToInnerHandler handler = new AnonymousToInnerHandler(){
        @Override
        protected boolean showRefactoringDialog() {
          myNewClassName = "MyPredicate";
          myMakeStatic = !needsThis();
          return true;
        }
      };
      handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      assertFalse(handler.needsThis());
      myFixture.checkResultByFile(TEST_ROOT + myFixture.getTestName(true) + "_after.java");
    });
  }

  @Test
  public void testNameConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(TEST_ROOT + myFixture.getTestName(true) + ".java");
    myFixture.runInCommand(() -> {
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
      handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(TEST_ROOT + myFixture.getTestName(true) + "_after.java");
    });
  }
  
  @Test
  public void testTypeParameterNotMentioned() {
    doTest("MyClass", true);
  }

  @Test
  public void testAddThisQualifier() {
    doTest("MyClass", true);
  }

  @Test
  public void testAddThisQualifierWithImplicitThis() {
    doTest("MyClass", true);
  }

  @Test
  public void testOuterThisQualifier() {
    doTest("MyClass", true);
  }

  @Test
  public void testChainedConstructor() {
    doTest("Inner", true);
  }

  private void doTest(final String newClassName, final boolean makeStatic) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(TEST_ROOT + myFixture.getTestName(true) + ".java");
    myFixture.runInCommand(() -> {
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


      handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(TEST_ROOT + myFixture.getTestName(true) + "_after.java");
    });
  }
}
