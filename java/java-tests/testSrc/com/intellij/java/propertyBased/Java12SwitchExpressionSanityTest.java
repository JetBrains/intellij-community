// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.InvokeIntention;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.testFramework.propertyBased.StripTestDataMarkup;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@SkipSlowTestLocally
public class Java12SwitchExpressionSanityTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected void tearDown() throws Exception {
    // remove jdk if it was created during highlighting to avoid leaks
    try {
      JavaAwareProjectJdkTableImpl.removeInternalJdkInTests();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_12;
  }

  public void testIntentionsAroundSwitch() {
    MadTestingUtil.enableAllInspections(getProject(), getTestRootDisposable(),
                                        "BoundedWildcard" // IDEA-194460
    );

    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> Generator.sampledFrom(new InvokeIntention(file, new JavaIntentionPolicy()) {
        @Override
        protected int generateDocOffset(@NotNull Environment env, @Nullable String logMessage) {
          Collection<PsiSwitchBlock> children = PsiTreeUtil.findChildrenOfType(getFile(), PsiSwitchBlock.class);
          if (children.isEmpty()) {
            return super.generateDocOffset(env, logMessage);
          }

          List<Generator<Integer>> generators = 
            ContainerUtil.map(children, stmt ->
            Generator.integers(stmt.getTextRange().getStartOffset(),
                               stmt.getTextRange().getEndOffset()).noShrink());
          return env.generateValue(Generator.anyOf(generators), logMessage);
        }
      }, new StripTestDataMarkup(file));

    Supplier<MadTestingAction> fileChooser = MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(), f -> {
      try {
        return f.getName().endsWith(".java") && FileUtil.loadFile(f).contains(" switch");
      }
      catch (IOException e) {
        return false;
      }
    }, fileActions);
    PropertyChecker.checkScenarios(fileChooser);
  }

}
