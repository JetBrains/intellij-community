// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.*;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

@SkipSlowTestLocally
public class JavaFeatureSpecificSanityTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
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

  public void testSwitchExpressionSpecific() {
    MadTestingUtil.enableAllInspections(getProject());
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable()); // IDEA-228814

    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> {
        InvokeIntention anyIntentionInSwitchRange = new InvokeIntentionAtElement(
          file, new JavaIntentionPolicy(), PsiSwitchBlock.class, Function.identity());

        InvokeIntention replaceToEnhancedSwitchIntention = new InvokeIntentionAtElement(file, new IntentionPolicy() {
          @Override
          protected boolean shouldSkipIntention(@NotNull String actionText) {
            return !"Replace with 'switch' expression".equals(actionText) && !"Replace with enhanced 'switch' statement".equals(actionText);
          }
        }, PsiSwitchBlock.class, PsiSwitchBlock::getFirstChild);

        return Generator.sampledFrom(anyIntentionInSwitchRange, replaceToEnhancedSwitchIntention, new StripTestDataMarkup(file));
      };

    PropertyChecker
      .customized().withIterationCount(50)
      .checkScenarios(createChooser(fileActions, " switch"));
  }

  public void testPatternInstanceOfSpecific() {
    MadTestingUtil.enableAllInspections(getProject());
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable()); // IDEA-228814

    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> {
        InvokeIntention anyIntentionAroundInstanceOf = new InvokeIntentionAtElement(
          file, new JavaIntentionPolicy(), PsiInstanceOfExpression.class, stmt -> {
          PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(stmt, PsiCodeBlock.class);
          if (codeBlock != null) {
            return codeBlock;
          }
          return stmt.getParent();
        });

        InvokeIntention replaceToPattern = new InvokeIntentionAtElement(file, new IntentionPolicy() {
          @Override
          protected boolean shouldSkipIntention(@NotNull String actionText) {
            return !actionText.matches("Replace '.+' with pattern variable");
          }
        }, f -> {
          Collection<PsiElement> targets = new ArrayList<>();
          for (PsiTypeCastExpression cast : PsiTreeUtil.findChildrenOfType(f, PsiTypeCastExpression.class)) {
            PsiInstanceOfExpression candidate = InstanceOfUtils.findPatternCandidate(cast);
            if (candidate != null) {
              PsiElement element = PsiUtil.skipParenthesizedExprUp(cast.getParent());
              if (element instanceof PsiLocalVariable) {
                targets.add(((PsiLocalVariable)element).getNameIdentifier());
              }
            }
          }
          return targets;
        });

        return Generator.sampledFrom(anyIntentionAroundInstanceOf, replaceToPattern, new StripTestDataMarkup(file));
      };

    PropertyChecker
      .customized().withIterationCount(50)
      .checkScenarios(createChooser(fileActions, " instanceof"));
  }

  private Supplier<MadTestingAction> createChooser(Function<PsiFile, Generator<? extends MadTestingAction>> fileActions, String substring) {
    return MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(), f -> {
      try {
        return f.getName().endsWith(".java") && FileUtil.loadFile(f).contains(substring);
      }
      catch (IOException e) {
        return false;
      }
    }, fileActions);
  }
}