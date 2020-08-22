// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.PsiEnumConstantImpl;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.io.File;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author peter
 */
@SkipSlowTestLocally
public class JavaCodeInsightSanityTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
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

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14;
  }

  public void testRandomActivity() {
    enableInspections();
    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> Generator.sampledFrom(new InvokeIntention(file, new JavaPreviewIntentionPolicy()),
                                    new InvokeCompletion(file, new JavaCompletionPolicy()),
                                    new StripTestDataMarkup(file),
                                    new DeleteRange(file));
    PropertyChecker
      .checkScenarios(actionsOnJavaFiles(fileActions));
  }

  private void enableInspections() {
    MadTestingUtil.enableAllInspections(getProject());
  }

  public void testPreserveComments() {
    boolean oldSettings = getJavaSettings().ENABLE_JAVADOC_FORMATTING;
    try {
      getJavaSettings().ENABLE_JAVADOC_FORMATTING = false;
      enableInspections();
      Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
        file -> Generator.sampledFrom(new InvokeIntention(file, new JavaCommentingStrategy()),
                                      new InsertLineComment(file, "//simple end comment\n"));
      PropertyChecker
        .checkScenarios(actionsOnJavaFiles(fileActions));
    }
    finally {
      getJavaSettings().ENABLE_JAVADOC_FORMATTING = oldSettings;
    }
  }

  private JavaCodeStyleSettings getJavaSettings() {
    CodeStyleSettings rootSettings = CodeStyle.getSettings(getProject());
    return rootSettings.getCommonSettings(JavaLanguage.INSTANCE).getRootSettings().getCustomSettings(JavaCodeStyleSettings.class);
  }

  public void testRemoveRedundantCast() {
    enableInspections();
    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> Generator.sampledFrom(new InvokeIntentionAtElement(file, new JavaIntentionPolicy() {
                                      @Override
                                      protected boolean shouldSkipIntention(@NotNull String actionText) {
                                        return !actionText.equals(JavaAnalysisBundle.message("inspection.redundant.cast.remove.quickfix"));
                                      }
                                    }, PsiTypeCastExpression.class, Function.identity()),
                                    new InsertTypeCastCommand(file));
    PropertyChecker
      .checkScenarios(actionsOnJavaFiles(fileActions));
  }

  public void testParenthesesDontChangeIntention() {
    enableInspections();
    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> Generator.sampledFrom(new InvokeIntention(file, new JavaParenthesesPolicy()), new StripTestDataMarkup(file));
    PropertyChecker
      .checkScenarios(actionsOnJavaFiles(fileActions));
  }

  private @NotNull Supplier<MadTestingAction> actionsOnJavaFiles(Function<PsiFile, Generator<? extends MadTestingAction>> fileActions) {
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

  private static final class InsertTypeCastCommand extends ActionOnFile {
    private InsertTypeCastCommand(PsiFile file) {
      super(file);
    }

    @Override
    public void performCommand(@NotNull Environment env) {
      PsiDocumentManager.getInstance(getProject()).commitDocument(getDocument());

      int randomOffset = generatePsiOffset(env, null);
      PsiElement leaf = getFile().findElementAt(randomOffset);

      if (leaf != null) {
        List<PsiElement> elementsToWrap = new JavaParenthesesPolicy().getElementsToWrap(leaf);
        if (elementsToWrap.isEmpty()) return;
        PsiElement expr = env.generateValue(Generator.sampledFrom(elementsToWrap).noShrink(), null);
        if (!(expr instanceof PsiExpression)) return;
        PsiType type = ((PsiExpression)expr).getType();
        if (type == null || type instanceof PsiDisjunctionType || !PsiTypesUtil.isDenotableType(type, expr)) return; //accept cast in expression statement
        env.logMessage("Inserting cast '" +
                       StringUtil.escapeStringCharacters("(" + type.getCanonicalText() + ")") +
                       "' at " +
                       MadTestingUtil.getPositionDescription(expr.getTextOffset(), getDocument()));
        WriteCommandAction.runWriteCommandAction(getProject(), () -> AddTypeCastFix.addTypeCast(getProject(), (PsiExpression)expr, type));
      }
    }
  }
}
