// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.*;
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
public class JavaSwitchExpressionSanityTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_13;
  }

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

  public void testIntentionsAroundSwitch() {
    MadTestingUtil.enableAllInspections(getProject(), getTestRootDisposable(), "BoundedWildcard");  // IDEA-194460

    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> {

        InvokeIntentionAroundSwitch anyIntentionInSwitchRange = new InvokeIntentionAroundSwitch(file, new JavaIntentionPolicy() {
          @Override
          protected boolean shouldSkipByFamilyName(@NotNull String familyName) {
            return super.shouldSkipByFamilyName(familyName);
          }
        });

        InvokeIntentionAroundSwitch replaceToEnhancedSwitchIntention = new InvokeIntentionAroundSwitch(file, new IntentionPolicy() {
          @Override
          protected boolean shouldSkipIntention(@NotNull String actionText) {
            return !"Replace with 'switch' expression".equals(actionText) && !"Replace with enhanced 'switch' statement".equals(actionText);
          }
        }) {
          @Override
          protected PsiElement getRangeElement(PsiSwitchBlock stmt) {
            return stmt.getFirstChild();
          }
        };

        return Generator.sampledFrom(anyIntentionInSwitchRange, replaceToEnhancedSwitchIntention, new StripTestDataMarkup(file));
      };

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

  private static class InvokeIntentionAroundSwitch extends InvokeIntention {
    InvokeIntentionAroundSwitch(PsiFile file, final IntentionPolicy policy) {
      super(file, policy);
    }

    @Override
    protected int generateDocOffset(@NotNull Environment env, @Nullable String logMessage) {
      Collection<PsiSwitchBlock> children = PsiTreeUtil.findChildrenOfType(getFile(), PsiSwitchBlock.class);
      if (children.isEmpty()) {
        return super.generateDocOffset(env, logMessage);
      }

      List<Generator<Integer>> generators =
        ContainerUtil.map(children, stmt ->
        Generator.integers(getRangeElement(stmt).getTextRange().getStartOffset(),
                           getRangeElement(stmt).getTextRange().getEndOffset()).noShrink());
      return env.generateValue(Generator.anyOf(generators), logMessage);
    }

    protected PsiElement getRangeElement(PsiSwitchBlock stmt) {
      return stmt;
    }
  }
}