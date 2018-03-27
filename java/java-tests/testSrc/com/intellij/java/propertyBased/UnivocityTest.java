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
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.propertyBased.*;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.IntDistribution;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.List;
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
    PropertyChecker.forAll(Generator.listsOf(genIntention.noShrink())).withIterationCount(30).shouldHold(list -> {
      long startModCount = tracker.getModificationCount();
      if (rebuildStamp.getAndSet(startModCount) != startModCount) {
        checkCompiles(myCompilerTester.rebuild());
      }

      MadTestingUtil.changeAndRevert(myProject, () -> {
        MadTestingAction.runActions(list);
        
        if (tracker.getModificationCount() != startModCount) {
          checkCompiles(myCompilerTester.make());
        }
      });
      return true;
    });
  }

  public void testRandomActivity() {
    Generator<List<MadTestingAction>> genActionGroup = psiJavaFiles().flatMap(
      file -> {
        Generator<MadTestingAction> mutation = Generator.anyOf(DeleteRange.psiRangeDeletions(file),
                                                               Generator.constant(new AddNullArgument(file)),
                                                               Generator.constant(new DeleteForeachInitializers(file)),
                                                               Generator.constant(new DeleteSecondArgument(file)),
                                                               InvokeCompletion.completions(file, new JavaCompletionPolicy()),
                                                               Generator.constant(new MakeAllMethodsVoid(file)));
        Generator<MadTestingAction> allActions = Generator.frequency(2, InvokeIntention.randomIntentions(file, new JavaIntentionPolicy()),
                                                                     1, Generator.constant(new RehighlightAllEditors(myProject)),
                                                                     1, mutation);
        return Generator.listsOf(IntDistribution.uniform(0, 5), allActions.noShrink());
      });

    PropertyChecker.forAll(Generator.listsOf(genActionGroup).map(ContainerUtil::flatten)).withIterationCount(50).shouldHold(list -> {
      MadTestingUtil.changeAndRevert(myProject, () -> {
        //System.out.println(list);
        MadTestingAction.runActions(list);
      });
      return true;
    });
  }

  @Override
  protected String getTestDataPath() {
    return SystemProperties.getUserHome() + "/IdeaProjects/univocity-parsers";
  }

}
