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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import junit.framework.ComparisonFailure;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class LightQuickFixTestCase extends LightDaemonAnalyzerTestCase {
  @NonNls protected static final String BEFORE_PREFIX = "before";
  @NonNls protected static final String AFTER_PREFIX = "after";

  private static QuickFixTestCase myWrapper;

  @Override
  protected void tearDown() throws Exception {
    myWrapper = null;
    super.tearDown();
  }

  protected boolean shouldBeAvailableAfterExecution() {
    return false;
  }

  private static void doTestFor(@NotNull String testName, @NotNull QuickFixTestCase quickFixTestCase) {
    final String relativePath = ObjectUtils.notNull(quickFixTestCase.getBasePath(), "") + "/" + BEFORE_PREFIX + testName;
    final String testFullPath = quickFixTestCase.getTestDataPath().replace(File.separatorChar, '/') + relativePath;
    final File testFile = new File(testFullPath);
    CommandProcessor.getInstance().executeCommand(quickFixTestCase.getProject(), () -> {
      try {
        String contents = StringUtil.convertLineSeparators(FileUtil.loadFile(testFile, CharsetToolkit.UTF8_CHARSET));
        quickFixTestCase.configureFromFileText(testFile.getName(), contents);
        quickFixTestCase.bringRealEditorBack();
        final ActionHint actionHint = quickFixTestCase.parseActionHintImpl(quickFixTestCase.getFile(), contents);

        quickFixTestCase.beforeActionStarted(testName, contents);

        try {
          myWrapper = quickFixTestCase;
          quickFixTestCase.doAction(actionHint, testFullPath, testName);
        }
        finally {
          myWrapper = null;
          quickFixTestCase.afterActionCompleted(testName, contents);
        }
      }
      catch (ComparisonFailure e) {
        throw e;
      }
      catch (Throwable e) {
        e.printStackTrace();
        Assert.fail(testName + " failed");
      }
    }, "", "");
  }

  protected void afterActionCompleted(final String testName, final String contents) {
  }

  protected void beforeActionStarted(final String testName, final String contents) {
  }

  public static void doAction(@NotNull ActionHint actionHint,
                              String testFullPath,
                              String testName,
                              QuickFixTestCase quickFix) throws Exception {
    IntentionAction action = actionHint.findAndCheck(quickFix.getAvailableActions(),
                                                     () -> getTestInfo(testFullPath, quickFix));
    if (action != null) {
      String text = action.getText();
      quickFix.invoke(action);
      UIUtil.dispatchAllInvocationEvents();
      UIUtil.dispatchAllInvocationEvents();
      if (!quickFix.shouldBeAvailableAfterExecution()) {
        final IntentionAction afterAction = quickFix.findActionWithText(text);
        if (afterAction != null) {
          fail("Action '" + text + "' is still available after its invocation in test " + testFullPath);
        }
      }

      String expectedFilePath = ObjectUtils.notNull(quickFix.getBasePath(), "") + "/" + AFTER_PREFIX + testName;
      quickFix.checkResultByFile("In file :" + expectedFilePath, expectedFilePath, false);

      String familyName = action.getFamilyName();
      if (StringUtil.isEmptyOrSpaces(familyName)) {
        fail("Action '" + text + "' provides empty family name which means that user would see action with empty presentable text in Inspection Results");
      }
    }
  }

  private static String getTestInfo(String testFullPath, QuickFixTestCase quickFix) {
    String infos = StreamEx.of(quickFix.doHighlighting())
      .filter(info -> info.getSeverity() != HighlightInfoType.SYMBOL_TYPE_SEVERITY)
      .map(info -> {
        String fixes = "";
        if (info.quickFixActionRanges != null) {
          fixes = StreamEx.of(info.quickFixActionRanges)
            .map(p -> p.getSecond()+" "+p.getFirst())
            .mapLastOrElse("|- "::concat, "\\- "::concat)
            .map(str -> "        " + str + "\n")
            .joining();
        }
        return info.getSeverity() +
               ": (" + info.getStartOffset() + "," + info.getEndOffset() + ") '" +
               info.getText() + "': " + info.getDescription() + "\n" + fixes;
      })
      .joining("       ");
    return "Test: " + testFullPath + "\n" +
           "Language level: " + PsiUtil.getLanguageLevel(quickFix.getProject()) + "\n" +
           (quickFix.getProject().equals(getProject()) ? ("SDK: " + ModuleRootManager.getInstance(getModule()).getSdk() + "\n") : "") +
           "Infos: " + infos;
  }

  protected void doAction(@NotNull ActionHint actionHint, final String testFullPath, final String testName)
    throws Exception {
    doAction(actionHint, testFullPath, testName, myWrapper);
  }

  protected void doAction(@NotNull String actionName) {
    final List<IntentionAction> available = getAvailableActions();
    final IntentionAction action = findActionWithText(available, actionName);
    assertNotNull("Action '" + actionName + "' not found among " + available, action);
    invoke(action);
  }

  protected static void invoke(@NotNull IntentionAction action) throws IncorrectOperationException {
    CodeInsightTestFixtureImpl.invokeIntention(action, getFile(), getEditor(), action.getText());
  }

  protected IntentionAction findActionAndCheck(@NotNull ActionHint hint, String testFullPath) {
    return hint.findAndCheck(getAvailableActions(), () -> "Test: "+testFullPath);
  }

  protected IntentionAction findActionWithText(@NotNull String text) {
    return findActionWithText(getAvailableActions(), text);
  }

  public static IntentionAction findActionWithText(@NotNull List<IntentionAction> actions, @NotNull String text) {
    for (IntentionAction action : actions) {
      if (text.equals(action.getText())) {
        return action;
      }
    }
    return null;
  }

  /**
   * @deprecated use {@link LightQuickFixParameterizedTestCase}
   * to get separate tests for all data files in testData directory.
   */
  protected void doAllTests() {
    doAllTests(createWrapper());
  }

  public static void doAllTests(QuickFixTestCase testCase) {
    final File[] files = getBeforeTestFiles(testCase);

    for (File file : files) {
      final String testName = file.getName().substring(BEFORE_PREFIX.length());
      doTestFor(testName, testCase);
    }
  }

  @NotNull
  public static File[] getBeforeTestFiles(@NotNull QuickFixTestCase testCase) {
    assertNotNull("getBasePath() should not return null!", testCase.getBasePath());

    final String testDirPath = testCase.getTestDataPath().replace(File.separatorChar, '/') + testCase.getBasePath();
    File testDir = new File(testDirPath);
    final File[] files = testDir.listFiles((dir, name) -> name.startsWith(BEFORE_PREFIX));

    if (files == null || files.length == 0) {
      fail("Test files not found in " + testDirPath);
    }
    return files;
  }

  protected void doSingleTest(@NotNull String fileSuffix) {
    doTestFor(fileSuffix, createWrapper());
  }

  protected void doSingleTest(String fileSuffix, String testDataPath) {
    doTestFor(fileSuffix, createWrapper(testDataPath));
  }

  protected ActionHint parseActionHintImpl(@NotNull PsiFile file, @NotNull String contents) {
    return ActionHint.parse(file, contents);
  }

  @NotNull
  protected QuickFixTestCase createWrapper() {
    return createWrapper(null);
  }

  @NotNull
  protected QuickFixTestCase createWrapper(final String testDataPath) {
    return new QuickFixTestCase() {
      public String myTestDataPath = testDataPath;

      @Override
      public String getBasePath() {
        return LightQuickFixTestCase.this.getBasePath();
      }

      @NotNull
      @Override
      public String getTestDataPath() {
        if (myTestDataPath == null) {
          myTestDataPath = LightQuickFixTestCase.this.getTestDataPath();
        }
        return myTestDataPath;
      }

      @NotNull
      @Override
      public ActionHint parseActionHintImpl(@NotNull PsiFile file, @NotNull String contents) {
        return LightQuickFixTestCase.this.parseActionHintImpl(file, contents);
      }

      @Override
      public void beforeActionStarted(@NotNull String testName, @NotNull String contents) {
        LightQuickFixTestCase.this.beforeActionStarted(testName, contents);
      }

      @Override
      public void afterActionCompleted(@NotNull String testName, @NotNull String contents) {
        LightQuickFixTestCase.this.afterActionCompleted(testName, contents);
      }

      @Override
      public void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) throws Exception {
        LightQuickFixTestCase.this.doAction(actionHint, testFullPath, testName);
      }

      @Override
      public void checkResultByFile(@NotNull String message, @NotNull String expectedFilePath, boolean ignoreTrailingSpaces) throws Exception {
        LightQuickFixTestCase.this.checkResultByFile(message, expectedFilePath, ignoreTrailingSpaces);
      }

      @Override
      public IntentionAction findActionWithText(@NotNull String text) {
        return LightQuickFixTestCase.this.findActionWithText(text);
      }

      @Override
      public boolean shouldBeAvailableAfterExecution() {
        return LightQuickFixTestCase.this.shouldBeAvailableAfterExecution();
      }

      @Override
      public void invoke(@NotNull IntentionAction action) {
        LightQuickFixTestCase.invoke(action);
      }

      @NotNull
      @Override
      public List<HighlightInfo> doHighlighting() {
        return LightQuickFixTestCase.this.doHighlighting();
      }

      @NotNull
      @Override
      public List<IntentionAction> getAvailableActions() {
        return LightQuickFixTestCase.this.getAvailableActions();
      }

      @Override
      public void configureFromFileText(@NotNull String name, @NotNull String contents) throws IOException {
        LightPlatformCodeInsightTestCase.configureFromFileText(name, contents, true);
      }

      @Override
      public PsiFile getFile() {
        return LightPlatformCodeInsightTestCase.getFile();
      }

      @Override
      public Project getProject() {
        return LightPlatformTestCase.getProject();
      }

      @Override
      public void bringRealEditorBack() {
        LightPlatformCodeInsightTestCase.bringRealEditorBack();
      }
    };
  }

  protected List<IntentionAction> getAvailableActions() {
    doHighlighting();
    return getAvailableActions(getEditor(), getFile());
  }

  @NotNull
  public static List<IntentionAction> getAvailableActions(@NotNull Editor editor, @NotNull PsiFile file) {
    return CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);
  }

  @NonNls protected String getBasePath() {return null;}
}
