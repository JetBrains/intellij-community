/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/testData")
public class AnonymousToInnerTest extends LightCodeInsightTestCase {
  private static final String TEST_ROOT = "/refactoring/anonymousToInner/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testGenericTypeParameters() {  // IDEADEV-29446
    doTest("MyIterator", true);
  }

  public void testInsideInterface() {  // IDEADEV-29446
    doTest("MyRunnable", true);
  }
  
  public void testCollapseDiamonds() {  // IDEADEV-29446
    doTest("MyPredicate", true);
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
    handler.invoke(getProject(), myEditor, myFile, null);
    assertFalse(handler.needsThis());
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }
  

  private void doTest(final String newClassName, final boolean makeStatic) {
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
