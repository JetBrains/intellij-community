package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class LightQuickFixTestCase extends LightDaemonAnalyzerTestCase {
  protected boolean shouldBeAvailableAfterExecution() {
    return false;
  }

  protected void doTestFor(final String testName) throws Exception {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final String relativePath = getBasePath() + "/before" + testName;
            final String testFullPath = getTestDataPath().replace(File.separatorChar, '/') + relativePath;
            final File ioFile = new File(testFullPath);
            try {
              String contents = StringUtil.convertLineSeparators(new String(FileUtil.loadFileText(ioFile)), "\n");
              configureFromFileText(ioFile.getName(), contents);
              final Pair<String, Boolean> pair = parseActionHint(getFile(), contents);
              final String text = pair.getFirst();
              final boolean actionShouldBeAvailable = pair.getSecond().booleanValue();

              beforeActionStarted(testName, contents);

              try {
                doAction(text, actionShouldBeAvailable, testFullPath, testName);
              }
              finally {
                afterActionCompleted(testName, contents);
              }
            }
            catch (Exception e) {
              e.printStackTrace();
              fail();
            }
          }
        });
      }
    }, "", "");
  }

  protected void afterActionCompleted(final String testName, final String contents) {
  }

  protected void beforeActionStarted(final String testName, final String contents) {
  }

  public static Pair<String, Boolean> parseActionHint(final PsiFile file, String contents) throws IOException {
    String comment = file instanceof XmlFile ? "<!--" : "//";
    // "quick fix action text to perform" "should be available"
    Pattern pattern = Pattern.compile("^" + comment + " \"([^\"]*)\" \"(\\S*)\".*", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(contents);
    assertTrue(matcher.matches());
    final String text = matcher.group(1);
    final Boolean actionShouldBeAvailable = Boolean.valueOf(matcher.group(2));
    return Pair.create(text, actionShouldBeAvailable);
  }

  protected void doAction(final String text, final boolean actionShouldBeAvailable, final String testFullPath, final String testName)
    throws Exception {
    IntentionAction action = findActionWithText(text);
    if (action == null) {
      if (actionShouldBeAvailable) {
        fail("Action with text '" + text + "' is not available in test " + testFullPath);
      }
    }
    else {
      if (!actionShouldBeAvailable) {
        fail("Action '" + text + "' is available in test " + testFullPath);
      }
      final PsiFile htmlFile = getFile().getViewProvider().getPsi(StdLanguages.HTML);
      if (htmlFile != null) {
        System.out.println(DebugUtil.psiToString(htmlFile, false));
      }
      action.invoke(getProject(), getEditor(), getFile());
      if (!shouldBeAvailableAfterExecution()) {
        final IntentionAction afterAction = findActionWithText(text);
        if (afterAction != null) {
          fail("Action '" + text + "' is still available after it's invocation in test " + testFullPath);
        }
      }
      final String expectedFilePath = getBasePath() + "/after" + testName;
      checkResultByFile("In file :" + expectedFilePath, expectedFilePath, false);
    }
  }

  protected IntentionAction findActionWithText(final String text) {
    final List<IntentionAction> actions = getAvailableActions();
    return findActionWithText(actions, text);
  }

  public static IntentionAction findActionWithText(final List<IntentionAction> actions, final String text) {
    for (IntentionAction action : actions) {
      if (text.equals(action.getText())) {
        return action;
      }
    }
    return null;
  }

  protected void doAllTests() throws Exception {
    final String testDirPath = getTestDataPath().replace(File.separatorChar, '/') + getBasePath();
    File testDir = new File(testDirPath);
    final File[] files = testDir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, @NonNls String name) {
        return name.startsWith("before");
      }
    });
    for (File file : files) {
      final String testName = file.getName().substring("before".length());
      doTestFor(testName);
      //noinspection UseOfSystemOutOrSystemErr
      System.out.print(testName + " ");
    }
    assertTrue("Test files not found in "+testDirPath,files.length != 0);
  }

  protected List<IntentionAction> getAvailableActions() {
    final Collection<HighlightInfo> infos = doHighlighting();
    return getAvailableActions(infos, getEditor(), getFile());
  }

  public static List<IntentionAction> getAvailableActions(final Collection<HighlightInfo> infos, final Editor editor, final PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final List<IntentionAction> availableActions = new ArrayList<IntentionAction>();
    if (infos != null) {
      for (HighlightInfo info :infos) {
        final int startOffset = info.fixStartOffset;
        final int endOffset = info.fixEndOffset;
        if (startOffset <= offset && offset <= endOffset
            && info.quickFixActionRanges != null
        ) {
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
            IntentionAction action = pair.first.getAction();
            TextRange range = pair.second;
            if (range.getStartOffset() <= offset && offset <= range.getEndOffset() &&
                action.isAvailable(getProject(), editor, file)) {
              availableActions.add(action);
              if (pair.first.getOptions() != null) {
                for (IntentionAction intentionAction : pair.first.getOptions()) {
                  if (intentionAction.isAvailable(getProject(), editor, file)) {
                    availableActions.add(intentionAction);
                  }
                }
              }
            }
          }
        }
      }
    }
    return availableActions;
  }

  @NonNls protected abstract String getBasePath();
}
