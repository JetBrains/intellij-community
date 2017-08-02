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

import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.*;
import org.jetbrains.annotations.NotNull;
import jetCheck.Generator;
import jetCheck.PropertyChecker;

import java.util.function.Function;

/**
 * @author peter
 */
@SkipSlowTestLocally
public class JavaCodeInsightSanityTest extends LightCodeInsightFixtureTestCase {

  public void testRandomActivity() {
    MadTestingUtil.enableAllInspections(getProject(), getTestRootDisposable());
    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions = file ->
      Generator.anyOf(InvokeIntention.randomIntentions(file, new JavaIntentionPolicy()),
                      InvokeCompletion.completions(file, new JavaCompletionPolicy()),
                      Generator.constant(new StripTestDataMarkup(file)),
                      DeleteRange.psiRangeDeletions(file));
    PropertyChecker.forAll(actionsOnJavaFiles(fileActions)).shouldHold(FileWithActions::runActions);
  }

  @NotNull
  private Generator<FileWithActions> actionsOnJavaFiles(Function<PsiFile, Generator<? extends MadTestingAction>> fileActions) {
    return MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(), f -> f.getName().endsWith(".java"), fileActions);
  }

  public void testReparse() {
    PropertyChecker.forAll(actionsOnJavaFiles(MadTestingUtil::randomEditsWithReparseChecks)).shouldHold(FileWithActions::runActions);
  }
}
