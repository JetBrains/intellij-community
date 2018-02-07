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

import com.intellij.java.psi.formatter.java.AbstractJavaFormatterTest;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author peter
 */
@SkipSlowTestLocally
public class JavaCodeInsightSanityTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected void tearDown() throws Exception {
    // remove jdk if it was created during highlighting to avoid leaks
    try {
      JavaAwareProjectJdkTableImpl.removeInternalJdkInTests();
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  public void testRandomActivity() {
    MadTestingUtil.enableAllInspections(getProject(), getTestRootDisposable());
    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions = file ->
      Generator.anyOf(InvokeIntention.randomIntentions(file, new JavaIntentionPolicy()),
                      InvokeCompletion.completions(file, new JavaCompletionPolicy()),
                      Generator.constant(new StripTestDataMarkup(file)),
                      DeleteRange.psiRangeDeletions(file));
    ImperativeCommand.checkScenarios(actionsOnJavaFiles(fileActions));
  }

  public void testPreserveComments() {
    boolean oldSettings = AbstractJavaFormatterTest.getJavaSettings().ENABLE_JAVADOC_FORMATTING;
    try {
      AbstractJavaFormatterTest.getJavaSettings().ENABLE_JAVADOC_FORMATTING = false;
      MadTestingUtil.enableAllInspections(getProject(), getTestRootDisposable());
      Function<PsiFile, Generator<? extends MadTestingAction>> fileActions = file ->
        Generator.anyOf(InvokeIntention.randomIntentions(file, new JavaCommentingStrategy()),
                        InsertLineComment.insertComment(file, "//simple end comment\n"));
      ImperativeCommand.checkScenarios(actionsOnJavaFiles(fileActions));
    }
    finally {
      AbstractJavaFormatterTest.getJavaSettings().ENABLE_JAVADOC_FORMATTING = oldSettings;
    }
  }

  @NotNull
  private Supplier<ImperativeCommand> actionsOnJavaFiles(Function<PsiFile, Generator<? extends MadTestingAction>> fileActions) {
    return MadTestingUtil.commandsOnFileContents(myFixture, PathManager.getHomePath(), f -> f.getName().endsWith(".java"), fileActions);
  }

  public void testReparse() {
    ImperativeCommand.checkScenarios(actionsOnJavaFiles(MadTestingUtil::randomEditsWithReparseChecks));
  }
}
