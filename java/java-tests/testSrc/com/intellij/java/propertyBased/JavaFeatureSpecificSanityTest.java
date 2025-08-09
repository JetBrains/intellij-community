// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.propertyBased;

import com.intellij.lang.java.JavaLanguage;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@SkipSlowTestLocally
public class JavaFeatureSpecificSanityTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
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
    checkScenarios(JavaFeatureSpecificSanityTest::getSwitchGenerator, Pattern.compile(" switch"));
  }

  public void testPatternInstanceOfSpecific() {
    checkScenarios(JavaFeatureSpecificSanityTest::getPatternInstanceOfGenerator, Pattern.compile(" instanceof"));
  }

  public void testPatternIfCanBeSwitchSpecific() {
    checkScenarios(JavaFeatureSpecificSanityTest::getIfCanBeSwitchGenerator, Pattern.compile(" if \\(\\w+ instanceof .+") );
  }

  private void checkScenarios(@NotNull Function<PsiFile, Generator<? extends MadTestingAction>> fileActions,
                              @NotNull Pattern pattern) {
    enableInspections();

    PropertyChecker
      .customized().withIterationCount(30)
      .checkScenarios(createChooser(fileActions, pattern));
  }

  private void enableInspections() {
    MadTestingUtil.enableAllInspections(getProject(), JavaLanguage.INSTANCE, "GrazieInspection");
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable()); // https://youtrack.jetbrains.com/issue/IDEA-228814
  }

  @Contract(pure = true)
  private @NotNull Supplier<MadTestingAction> createChooser(@NotNull Function<PsiFile, Generator<? extends MadTestingAction>> fileActions,
                                                            @NotNull Pattern pattern) {
    return MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(), f -> {
      try {
        return f.getName().endsWith(".java") && pattern.matcher(FileUtil.loadFile(f)).find();
      }
      catch (IOException e) {
        return false;
      }
    }, fileActions);
  }

  @Contract(pure = true)
  private static @NotNull Generator<ActionOnFile> getSwitchGenerator(@NotNull PsiFile file) {
    InvokeIntention anyIntentionInSwitchRange = new InvokeIntentionAtElement(
      file, new JavaIntentionPolicy(), PsiSwitchBlock.class, Function.identity());

    InvokeIntention replaceToEnhancedSwitchIntention = new InvokeIntentionAtElement(file, new IntentionPolicy() {
      @Override
      protected boolean shouldSkipIntention(@NotNull String actionText) {
        return !"Replace with 'switch' expression".equals(actionText) && !"Replace with enhanced 'switch' statement".equals(actionText);
      }
    }, PsiSwitchBlock.class, PsiSwitchBlock::getFirstChild);

    return Generator.sampledFrom(anyIntentionInSwitchRange, replaceToEnhancedSwitchIntention, new StripTestDataMarkup(file));
  }

  @Contract(pure = true)
  private static @NotNull Generator<ActionOnFile> getPatternInstanceOfGenerator(@NotNull PsiFile file) {
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
  }

  @Contract(pure = true)
  private static @NotNull Generator<IfCanBeSwitchActionOnFile> getIfCanBeSwitchGenerator(@NotNull PsiFile file) {
    return Generator.sampledFrom(new IfCanBeSwitchActionOnFile(file));
  }
}