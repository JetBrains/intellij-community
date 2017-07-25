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
package com.intellij.java.propertyBased;

import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.propertyBased.InvokeCompletion;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.util.SystemProperties;
import jetCheck.Generator;
import jetCheck.PropertyChecker;

/**
 * @author peter
 */
@SkipSlowTestLocally
public class CompletionConsistencyTest extends AbstractApplyAndRevertTestCase {

  public void testCompletionConsistency() {
    PropertyChecker.forAll(psiJavaFiles()).withIterationCount(20).shouldHold(file -> {
      System.out.println("for file: " + file.getVirtualFile().getPresentableUrl());
      PropertyChecker.forAll(Generator.listsOf(InvokeCompletion.completions(file, new JavaCompletionPolicy()))).withIterationCount(10).shouldHold(list -> {
        MadTestingUtil.changeAndRevert(myProject, () -> MadTestingAction.runActions(list));
        return true;
      });
      return true;
    });
  }

  @Override
  protected String getTestDataPath() {
    return SystemProperties.getUserHome() + "/IdeaProjects/univocity-parsers";
  }
}
