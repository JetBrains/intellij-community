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
import com.intellij.util.SystemProperties;
import slowCheck.CheckerSettings;
import slowCheck.Generator;
import slowCheck.PropertyChecker;

/**
 * @author peter
 */
@SkipSlowTestLocally
public class CompletionConsistencyTest extends AbstractApplyAndRevertTestCase {

  public void testCompletionConsistency() {
    CheckerSettings settings = CheckerSettings.DEFAULT_SETTINGS;
    PropertyChecker.forAll(settings.withIterationCount(20), psiJavaFiles(), file -> {
      System.out.println("for file: " + file.getVirtualFile().getPresentableUrl());
      PropertyChecker.forAll(settings.withIterationCount(10), Generator.listsOf(InvokeCompletion.completions(file)), list -> {
        changeAndRevert(myProject, () -> MadTestingAction.runActions(list, myProject));
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
