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
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import slowCheck.CheckerSettings;
import slowCheck.Generator;
import slowCheck.IntDistribution;
import slowCheck.PropertyChecker;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@SkipSlowTestLocally
public class ApplyRandomIntentionsTest extends AbstractApplyAndRevertTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject)).disableBackgroundCommit(getTestRootDisposable());
  }

  public void testIntentionsInDifferentFiles() throws Throwable {
    initCompiler();
    PsiModificationTracker tracker = PsiManager.getInstance(myProject).getModificationTracker();

    AtomicLong rebuildStamp = new AtomicLong();

    CheckerSettings settings = CheckerSettings.DEFAULT_SETTINGS.withIterationCount(30);
    Generator<InvokeIntention> genIntention = psiJavaFiles().flatMap(
      file -> Generator.from(data -> InvokeIntention.generate(file, data)));
    PropertyChecker.forAll(settings, Generator.listsOf(genIntention.noShrink()), list -> {
      long startModCount = tracker.getModificationCount();
      if (rebuildStamp.getAndSet(startModCount) != startModCount) {
        checkCompiles(myCompilerTester.rebuild());
      }

      changeAndRevert(() -> {
        MadTestingAction.runActions(list, myProject);
        
        if (tracker.getModificationCount() != startModCount) {
          checkCompiles(myCompilerTester.make());
        }
      });
      return true;
    });
  }

  public void testIntentionsAndModificationsInDifferentFiles() throws Throwable {
    CheckerSettings settings = CheckerSettings.DEFAULT_SETTINGS.withIterationCount(50);
    Generator<List<MadTestingAction>> genActionGroup = psiJavaFiles().flatMap(
      file -> {
        Generator<MadTestingAction> mutation = Generator.anyOf(DeleteRange.deletePsiRange(file),
                                                               Generator.constant(new AddNullArgument(file)),
                                                               Generator.constant(new DeleteForeachInitializers(file)),
                                                               Generator.constant(new DeleteSecondArgument(file)),
                                                               Generator.constant(new MakeAllMethodsVoid(file)));
        Generator<MadTestingAction> allActions = Generator.frequency(2, Generator.from(data -> InvokeIntention.generate(file, data)),
                                                                     1, Generator.constant(new RehighlightAllEditors(myProject)),
                                                                     1, mutation);
        return Generator.listsOf(IntDistribution.uniform(0, 5), allActions.noShrink());
      });

    PropertyChecker.forAll(settings, Generator.listsOf(genActionGroup).map(ContainerUtil::flatten), list -> {
      changeAndRevert(() -> {
        //System.out.println(list);
        MadTestingAction.runActions(list, myProject);
      });
      return true;
    });
  }

  @Override
  protected String getTestDataPath() {
    return SystemProperties.getUserHome() + "/IdeaProjects/univocity-parsers";
  }

}
