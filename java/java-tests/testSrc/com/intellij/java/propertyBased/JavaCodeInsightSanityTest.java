// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.java.psi.formatter.java.AbstractJavaFormatterTest;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiEnumConstantImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.io.File;
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
    enableAlmostAllInspections();
    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> Generator.sampledFrom(new InvokeIntention(file, new JavaIntentionPolicy()),
                                    new InvokeCompletion(file, new JavaCompletionPolicy()),
                                    new StripTestDataMarkup(file),
                                    new DeleteRange(file));
    PropertyChecker
      .checkScenarios(actionsOnJavaFiles(fileActions));
  }

  private void enableAlmostAllInspections() {
    MadTestingUtil.enableAllInspections(getProject(), getTestRootDisposable(),
                                        "BoundedWildcard" // IDEA-194460
    );
  }

  public void testPreserveComments() {
    boolean oldSettings = AbstractJavaFormatterTest.getJavaSettings().ENABLE_JAVADOC_FORMATTING;
    try {
      AbstractJavaFormatterTest.getJavaSettings().ENABLE_JAVADOC_FORMATTING = false;
      enableAlmostAllInspections();
      Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
        file -> Generator.sampledFrom(new InvokeIntention(file, new JavaCommentingStrategy()),
                                      new InsertLineComment(file, "//simple end comment\n"));
      PropertyChecker
        .checkScenarios(actionsOnJavaFiles(fileActions));
    }
    finally {
      AbstractJavaFormatterTest.getJavaSettings().ENABLE_JAVADOC_FORMATTING = oldSettings;
    }
  }

  public void testParenthesesDontChangeIntention() {
    enableAlmostAllInspections();
    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> Generator.sampledFrom(new InvokeIntention(file, new JavaParenthesesPolicy()), new StripTestDataMarkup(file));
    PropertyChecker
      .checkScenarios(actionsOnJavaFiles(fileActions));
  }

  @NotNull
  private Supplier<MadTestingAction> actionsOnJavaFiles(Function<PsiFile, Generator<? extends MadTestingAction>> fileActions) {
    return MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(), f -> f.getName().endsWith(".java"), fileActions);
  }

  public void _testGenerator() {
    MadTestingUtil.testFileGenerator(new File(PathManager.getHomePath()), f -> f.getName().endsWith(".java"), 10000, System.out);
  }

  public void testReparse() {
    PropertyChecker
      .checkScenarios(actionsOnJavaFiles(MadTestingUtil::randomEditsWithReparseChecks));
  }

  public void testIncrementalHighlighterUpdate() {
    PropertyChecker
      .checkScenarios(actionsOnJavaFiles(CheckHighlighterConsistency.randomEditsWithHighlighterChecks));
  }

  public void testPsiAccessors() {
    PropertyChecker.checkScenarios(actionsOnJavaFiles(
      MadTestingUtil.randomEditsWithPsiAccessorChecks(
        method ->
          //method.getName().equals("getReferences") && method.getDeclaringClass().equals(PsiLiteralExpressionImpl.class) ||
          method.getName().equals("getOrCreateInitializingClass") && method.getDeclaringClass().equals(PsiEnumConstantImpl.class)
      )
    ));
  }
}
