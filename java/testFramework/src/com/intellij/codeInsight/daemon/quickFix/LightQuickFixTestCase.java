// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.modcommand.ActionContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public abstract class LightQuickFixTestCase extends LightDaemonAnalyzerTestCase {
  protected static final @NonNls String BEFORE_PREFIX = "before";
  protected static final @NonNls String AFTER_PREFIX = "after";
  protected static final @NonNls String PREVIEW_PREFIX = "preview";

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
        String contents = StringUtil.convertLineSeparators(FileUtil.loadFile(testFile, StandardCharsets.UTF_8));
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
      catch (RuntimeException | Error e) {
        throw e;
      }
      catch (Throwable e) {
        throw new AssertionError(testName + " failed", e);
      }
    }, "", "");
  }

  protected void afterActionCompleted(final String testName, final String contents) {
  }

  protected void beforeActionStarted(final String testName, final String contents) {
  }

  public void doAction(@NotNull ActionHint actionHint,
                       @NotNull String testFullPath,
                       @NotNull String testName,
                       @NotNull QuickFixTestCase quickFix) throws Exception {
    IntentionAction action = actionHint.findAndCheck(quickFix.getAvailableActions(),
                                                     ActionContext.from(getEditor(), getFile()),
                                                     () -> getTestInfo(testFullPath, quickFix));
    if (action != null) {
      String text = action.getText();
      PsiElement element = PsiUtilBase.getElementAtCaret(getEditor());
      if (actionHint.shouldCheckPreview()) {
        String previewFilePath = ObjectUtils.notNull(quickFix.getBasePath(), "") + "/" + PREVIEW_PREFIX + testName;
        quickFix.checkPreviewAndInvoke(action, previewFilePath);
      }
      else {
        quickFix.invoke(action);
      }
      UIUtil.dispatchAllInvocationEvents();
      UIUtil.dispatchAllInvocationEvents();
      if (!quickFix.shouldBeAvailableAfterExecution()) {
        final IntentionAction afterAction = quickFix.findActionWithText(text);
        if (afterAction != null && Comparing.equal(element, PsiUtilBase.getElementAtCaret(getEditor()))) {
          fail("Action '" + text + "' is still available after its invocation in test " + testFullPath);
        }
      }

      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      String expectedFilePath = ObjectUtils.notNull(quickFix.getBasePath(), "") + "/" + AFTER_PREFIX + testName;
      quickFix.checkResultByFile("In file :" + expectedFilePath, expectedFilePath, false);

      String familyName = action.getFamilyName();
      if (StringUtil.isEmptyOrSpaces(familyName)) {
        fail("Action '" + text + "' provides empty family name which means that user would see action with empty presentable text in Inspection Results");
      }
    }
  }

  private String getTestInfo(@NotNull String testFullPath, @NotNull QuickFixTestCase quickFix) {
    String infos = getCurrentHighlightingInfo(quickFix.doHighlighting());
    return "Test: " + testFullPath + "\n" +
           "Language level: " + PsiUtil.getLanguageLevel(quickFix.getProject()) + "\n" +
           (quickFix.getProject().equals(getProject()) ? "SDK: " + ModuleRootManager.getInstance(getModule()).getSdk() + "\n" : "") +
           "Infos: " + infos;
  }

  static String getCurrentHighlightingInfo(@NotNull List<? extends HighlightInfo> infos) {
    return StreamEx.of(infos)
      .filter(info -> info.getSeverity() != HighlightInfoType.SYMBOL_TYPE_SEVERITY)
      .map(info -> {
        List<String> s = new ArrayList<>();
        info.findRegisteredQuickFix((descriptor, range) -> {
          s.add(range + " " + descriptor);
          return null;
        });
        String fixes = StreamEx.of(s)
          .mapLastOrElse("|- "::concat, "\\- "::concat)
          .map(str -> "        " + str + "\n")
          .joining();
        return info.getSeverity() +
               ": (" + info.getStartOffset() + "," + info.getEndOffset() + ") '" +
               info.getText() + "': " + info.getDescription() + "\n" + fixes;
      })
      .joining("       ");
  }

  protected void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName)
    throws Exception {
    doAction(actionHint, testFullPath, testName, myWrapper);
  }

  protected void doAction(@NotNull String actionName) {
    final List<IntentionAction> available = getAvailableActions();
    final IntentionAction action = findActionWithText(available, actionName);
    assertNotNull("Action '" + actionName + "' not found among " + available, action);
    invoke(action);
  }

  protected void invoke(@NotNull IntentionAction action) throws IncorrectOperationException {
    CodeInsightTestFixtureImpl.invokeIntention(action, getFile(), getEditor());
  }

  protected IntentionAction findActionAndCheck(@NotNull ActionHint hint, String testFullPath) {
    return hint.findAndCheck(getAvailableActions(), () -> "Test: "+testFullPath);
  }

  protected IntentionAction findActionWithText(@NotNull String text) {
    return findActionWithText(getAvailableActions(), text);
  }

  public static IntentionAction findActionWithText(@NotNull List<? extends IntentionAction> actions, @NotNull String text) {
    for (IntentionAction action : actions) {
      if (text.equals(action.getText())) {
        return action;
      }
    }
    return null;
  }

  public static void doAllTests(QuickFixTestCase testCase) {
    final File[] files = getBeforeTestFiles(testCase);

    for (File file : files) {
      final String testName = file.getName().substring(BEFORE_PREFIX.length());
      doTestFor(testName, testCase);
    }
  }

  public static File @NotNull [] getBeforeTestFiles(@NotNull QuickFixTestCase testCase) {
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

  protected void doSingleTest(@NotNull String fileSuffix, String testDataPath) {
    doTestFor(fileSuffix, createWrapper(testDataPath));
  }

  protected ActionHint parseActionHintImpl(@NotNull PsiFile psiFile, @NotNull String contents) {
    return ActionHint.parse(psiFile, contents);
  }

  protected @NotNull QuickFixTestCase createWrapper() {
    return createWrapper(null);
  }

  protected @NotNull QuickFixTestCase createWrapper(final String testDataPath) {
    return new QuickFixTestCase() {
      String myTestDataPath = testDataPath;

      @Override
      public String getBasePath() {
        return LightQuickFixTestCase.this.getBasePath();
      }

      @Override
      public @NotNull String getTestDataPath() {
        if (myTestDataPath == null) {
          myTestDataPath = LightQuickFixTestCase.this.getTestDataPath();
        }
        return myTestDataPath;
      }

      @Override
      public @NotNull ActionHint parseActionHintImpl(@NotNull PsiFile file, @NotNull String contents) {
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
      public void checkResultByFile(@NotNull String message, @NotNull String expectedFilePath, boolean ignoreTrailingSpaces) {
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
        LightQuickFixTestCase.this.invoke(action);
      }

      @Override
      public void checkPreviewAndInvoke(@NotNull IntentionAction action, @NotNull String previewFilePath) {
        // Run in background thread to catch accidental write-actions during preview generation
        String previewContent;
        try {
          previewContent = ReadAction.nonBlocking(
              () -> IntentionPreviewPopupUpdateProcessor.getPreviewContent(getProject(), action, getFile(), getEditor()))
            .submit(AppExecutorUtil.getAppExecutorService()).get();
        }
        catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
        LightQuickFixTestCase.this.invoke(action);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        Path path = Path.of(getTestDataPath(), previewFilePath);
        if (Files.exists(path)) {
          assertSameLinesWithFile(path.toString(), previewContent);
        } else {
          if (previewContent.isEmpty()) {
            fail("No preview was generated for '" + action.getText() + "'");
          }
          assertEquals(getFile().getText(), previewContent);
        }
      }

      @Override
      public @NotNull @Unmodifiable List<HighlightInfo> doHighlighting() {
        return LightQuickFixTestCase.this.doHighlighting();
      }

      @Override
      public @NotNull @Unmodifiable List<IntentionAction> getAvailableActions() {
        return LightQuickFixTestCase.this.getAvailableActions();
      }

      @Override
      public void configureFromFileText(@NotNull String name, @NotNull String contents) {
        LightQuickFixTestCase.this.configureFromFileText(name, contents, true);
      }

      @Override
      public PsiFile getFile() {
        return LightQuickFixTestCase.this.getFile();
      }

      @Override
      public Project getProject() {
        return LightQuickFixTestCase.this.getProject();
      }

      @Override
      public void bringRealEditorBack() {
        LightQuickFixTestCase.this.bringRealEditorBack();
      }
    };
  }

  protected @Unmodifiable List<IntentionAction> getAvailableActions() {
    doHighlighting();
    return getAvailableActions(getEditor(), getFile());
  }

  public static @NotNull @Unmodifiable List<IntentionAction> getAvailableActions(@NotNull Editor editor, @NotNull PsiFile file) {
    return CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);
  }

  protected @NonNls String getBasePath() {return null;}
}
