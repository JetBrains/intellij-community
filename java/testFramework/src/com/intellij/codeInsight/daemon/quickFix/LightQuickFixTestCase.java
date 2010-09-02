/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.RegExp;
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
  @NonNls private static final String BEFORE_PREFIX = "before";
  private static QuickFixTestCase myWrapper;

  protected boolean shouldBeAvailableAfterExecution() {
    return false;
  }

  protected Pair<String, Boolean> parseActionHintImpl(final PsiFile file, String contents) {
    return parseActionHint(file, contents);
  }

  private static void doTestFor(final String testName, final QuickFixTestCase quickFixTestCase) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final String relativePath = quickFixTestCase.getBasePath() + "/" + BEFORE_PREFIX + testName;
            final String testFullPath = quickFixTestCase.getTestDataPath().replace(File.separatorChar, '/') + relativePath;
            final File ioFile = new File(testFullPath);
            try {
              String contents = StringUtil.convertLineSeparators(new String(FileUtil.loadFileText(ioFile, CharsetToolkit.UTF8)));
              quickFixTestCase.configureFromFileText(ioFile.getName(), contents);
              quickFixTestCase.bringRealEditorBack();
              final Pair<String, Boolean> pair = quickFixTestCase.parseActionHintImpl(quickFixTestCase.getFile(), contents);
              final String text = pair.getFirst();
              final boolean actionShouldBeAvailable = pair.getSecond().booleanValue();

              quickFixTestCase.beforeActionStarted(testName, contents);

              try {
                myWrapper = quickFixTestCase;
                quickFixTestCase.doAction(text, actionShouldBeAvailable, testFullPath, testName);
              }
              finally {
                myWrapper = null;
                quickFixTestCase.afterActionCompleted(testName, contents);
              }
            }
            catch (Throwable e) {
              e.printStackTrace();
              fail(testName);
            }
          }
        });
      }
    }, "", "");
    System.out.print(testName + " ");
  }

  protected void afterActionCompleted(final String testName, final String contents) {
  }

  protected void beforeActionStarted(final String testName, final String contents) {
  }

  public static Pair<String, Boolean> parseActionHint(final PsiFile file, String contents) {
    return parseActionHint(file, contents, " \"(.*)\" \"(true|false)\".*");
  }

  public static Pair<String, Boolean> parseActionHint(final PsiFile file, String contents, @NonNls @RegExp String actionPattern) {
    PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(file);

    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(hostFile.getLanguage());
    String comment = commenter.getLineCommentPrefix();
    if (comment == null) {
      comment = commenter.getBlockCommentPrefix();
    }
    
    // "quick fix action text to perform" "should be available"
    Pattern pattern = Pattern.compile("^" + comment.replace("*", "\\*") + actionPattern, Pattern.DOTALL);
    Matcher matcher = pattern.matcher(contents);
    assertTrue("No comment found in "+file.getVirtualFile(), matcher.matches());
    final String text = matcher.group(1);
    final Boolean actionShouldBeAvailable = Boolean.valueOf(matcher.group(2));
    return Pair.create(text, actionShouldBeAvailable);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void doAction(final String text, final boolean actionShouldBeAvailable, final String testFullPath, final String testName,
                              QuickFixTestCase quickFix)
    throws Exception {
    IntentionAction action = quickFix.findActionWithText(text);
    if (action == null) {
      if (actionShouldBeAvailable) {
        List<IntentionAction> actions = quickFix.getAvailableActions();
        List<String> texts = new ArrayList<String>();
        for (IntentionAction intentionAction : actions) {
          texts.add(intentionAction.getText());
        }
        Collection<HighlightInfo> infos = quickFix.doHighlighting();
        fail("Action with text '" + text + "' is not available in test " + testFullPath+"\nAvailable actions: "+texts+"\n"+actions+"\nErrors:"+infos);
      }
    }
    else {
      if (!actionShouldBeAvailable) {
        fail("Action '" + text + "' is available in test " + testFullPath);
      }
      quickFix.invoke(action);
      UIUtil.dispatchAllInvocationEvents();
      UIUtil.dispatchAllInvocationEvents();
      if (!quickFix.shouldBeAvailableAfterExecution()) {
        final IntentionAction afterAction = quickFix.findActionWithText(text);
        if (afterAction != null) {
          fail("Action '" + text + "' is still available after its invocation in test " + testFullPath);
        }
      }
      final String expectedFilePath = quickFix.getBasePath() + "/after" + testName;
      quickFix.checkResultByFile("In file :" + expectedFilePath, expectedFilePath, false);
    }
  }

  protected void doAction(final String text, final boolean actionShouldBeAvailable, final String testFullPath, final String testName)
    throws Exception {
    doAction(text, actionShouldBeAvailable, testFullPath, testName, myWrapper);
  }

  protected static void invoke(IntentionAction action) throws IncorrectOperationException {
    ShowIntentionActionsHandler.chooseActionAndInvoke(getFile(), getEditor(), action, action.getText());
  }

  protected IntentionAction findActionWithText(final String text) {
    return findActionWithText(getAvailableActions(), text);
  }

  public static IntentionAction findActionWithText(final List<IntentionAction> actions, final String text) {
    for (IntentionAction action : actions) {
      if (text.equals(action.getText())) {
        return action;
      }
    }
    return null;
  }

  public static void doAllTests(QuickFixTestCase testCase) throws Exception {
    assertNotNull("getBasePath() should not return null!", testCase.getBasePath());

    final String testDirPath = testCase.getTestDataPath().replace(File.separatorChar, '/') + testCase.getBasePath();
    File testDir = new File(testDirPath);
    final File[] files = testDir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, @NonNls String name) {
        return name.startsWith(BEFORE_PREFIX);
      }
    });

    if (files == null) {
      fail("Test files not found in " + testDirPath);
    }

    for (File file : files) {
      final String testName = file.getName().substring(BEFORE_PREFIX.length());
      doTestFor(testName, testCase);
    }
    assertTrue("Test files not found in "+testDirPath,files.length != 0);
  }

  protected void doAllTests() throws Exception {
    doAllTests(new QuickFixTestCase() {
      public String getBasePath() {
        return LightQuickFixTestCase.this.getBasePath();
      }

      public String getTestDataPath() {
        return LightQuickFixTestCase.this.getTestDataPath();
      }

      public Pair<String, Boolean> parseActionHintImpl(PsiFile file, String contents) {
        return LightQuickFixTestCase.this.parseActionHintImpl(file, contents);
      }

      public void beforeActionStarted(String testName, String contents) {
        LightQuickFixTestCase.this.beforeActionStarted(testName, contents);
      }

      public void afterActionCompleted(String testName, String contents) {
        LightQuickFixTestCase.this.afterActionCompleted(testName, contents);
      }

      public void doAction(String text, boolean actionShouldBeAvailable, String testFullPath, String testName) throws Exception {
        LightQuickFixTestCase.this.doAction(text, actionShouldBeAvailable, testFullPath, testName);
      }

      public void checkResultByFile(String s, String expectedFilePath, boolean b) throws Exception {
        LightQuickFixTestCase.this.checkResultByFile(s, expectedFilePath, b);
      }

      public IntentionAction findActionWithText(String text) {
        return LightQuickFixTestCase.this.findActionWithText(text);
      }

      public boolean shouldBeAvailableAfterExecution() {
        return LightQuickFixTestCase.this.shouldBeAvailableAfterExecution();
      }

      public void invoke(IntentionAction action) {
        LightQuickFixTestCase.this.invoke(action);
      }

      public List<HighlightInfo> doHighlighting() {
        return LightQuickFixTestCase.this.doHighlighting();
      }

      public List<IntentionAction> getAvailableActions() {
        return LightQuickFixTestCase.this.getAvailableActions();
      }

      public void configureFromFileText(String name, String contents) throws IOException {
        LightCodeInsightTestCase.configureFromFileText(name, contents);
      }

      public PsiFile getFile() {
        return LightCodeInsightTestCase.getFile();
      }

      public void bringRealEditorBack() {
        LightCodeInsightTestCase.bringRealEditorBack();
      }
    });
  }

  protected List<IntentionAction> getAvailableActions() {
    doHighlighting();
    return getAvailableActions(getEditor(), getFile());
  }

  public static List<IntentionAction> getAvailableActions(final Editor editor, final PsiFile file) {
    return CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);
  }

  @NonNls protected String getBasePath() {return null;}
}
