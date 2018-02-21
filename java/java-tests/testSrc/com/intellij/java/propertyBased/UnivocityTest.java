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

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.propertyBased.*;
import com.intellij.util.SystemProperties;
import org.jetbrains.jetCheck.*;

import java.util.concurrent.atomic.AtomicLong;

@SkipSlowTestLocally
public class UnivocityTest extends AbstractApplyAndRevertTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject)).disableBackgroundCommit(getTestRootDisposable());
    MadTestingUtil.enableAllInspections(myProject, myProject);
  }

  public void testCompilabilityAfterIntentions() {
    initCompiler();
    PsiModificationTracker tracker = PsiManager.getInstance(myProject).getModificationTracker();

    AtomicLong rebuildStamp = new AtomicLong();

    Generator<MadTestingAction> genIntention = psiJavaFiles().flatMap(
      file -> Generator.frequency(5, InvokeIntention.randomIntentions(file, new JavaGreenIntentionPolicy()),
                                  1, Generator.constant(new InvalidateAllPsi(myProject)),
                                  10, Generator.constant(new FilePropertiesChanged(file))));
    PropertyChecker.forAll(ImperativeCommand.scenarios(() -> env -> {
      long startModCount = tracker.getModificationCount();
      if (rebuildStamp.getAndSet(startModCount) != startModCount) {
        checkCompiles(myCompilerTester.rebuild());
      }

      MadTestingUtil.changeAndRevert(myProject, () -> {
        env.executeCommands(genIntention);

        if (tracker.getModificationCount() != startModCount) {
          checkCompiles(myCompilerTester.make());
        }
      });
    })).withIterationCount(30).shouldHold(Scenario::ensureSuccessful);
  }

  public void testRandomActivity() {
    PropertyChecker.forAll(ImperativeCommand.scenarios(() -> env ->
      MadTestingUtil.changeAndRevert(myProject, () ->
        env.executeCommands(Generator.constant(env1 -> {
          PsiJavaFile file = env1.generateValue(psiJavaFiles(), "Working with %s");
          Generator<MadTestingAction> mutations = Generator.anyOf(DeleteRange.psiRangeDeletions(file),
                                                                  Generator.constant(new AddNullArgument(file)),
                                                                  Generator.constant(new DeleteForeachInitializers(file)),
                                                                  Generator.constant(new DeleteSecondArgument(file)),
                                                                  InvokeCompletion.completions(file, new JavaCompletionPolicy()),
                                                                  Generator.constant(new MakeAllMethodsVoid(file)));
          Generator<MadTestingAction> allActions = Generator.frequency(2, InvokeIntention.randomIntentions(file, new JavaIntentionPolicy()),
                                                                       1, Generator.constant(new RehighlightAllEditors(myProject)),
                                                                       1, mutations);

          env1.executeCommands(IntDistribution.uniform(1, 5), allActions.noShrink());
        }))))).withIterationCount(30).shouldHold(Scenario::ensureSuccessful);
  }

  @Override
  protected String getTestDataPath() {
    return SystemProperties.getUserHome() + "/IdeaProjects/univocity-parsers";
  }

}
