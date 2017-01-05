/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

import static com.intellij.codeInsight.actions.TextRangeType.SELECTED_TEXT;
import static com.intellij.codeInsight.actions.TextRangeType.VCS_CHANGED_TEXT;

class FileInEditorProcessor {
  private static final Logger LOG = Logger.getInstance(FileInEditorProcessor.class);

  private final Editor myEditor;

  private boolean myNoChangesDetected = false;
  private final boolean myProcessChangesTextOnly;

  private final boolean myShouldOptimizeImports;
  private final boolean myShouldRearrangeCode;
  private final boolean myProcessSelectedText;

  private final Project myProject;

  private final PsiFile myFile;
  private AbstractLayoutCodeProcessor myProcessor;

  public FileInEditorProcessor(PsiFile file,
                               Editor editor,
                               LayoutCodeOptions runOptions)
  {
    myFile = file;
    myProject = file.getProject();
    myEditor = editor;

    myShouldOptimizeImports = runOptions.isOptimizeImports();
    myShouldRearrangeCode = runOptions.isRearrangeCode();
    myProcessSelectedText = myEditor != null && runOptions.getTextRangeType() == SELECTED_TEXT;
    myProcessChangesTextOnly = runOptions.getTextRangeType() == VCS_CHANGED_TEXT;
  }

  public void processCode() {
    if (myShouldOptimizeImports) {
      myProcessor = new OptimizeImportsProcessor(myProject, myFile);
    }

    if (myProcessChangesTextOnly && !FormatChangedTextUtil.hasChanges(myFile)) {
      myNoChangesDetected = true;
    }

    myProcessor = mixWithReformatProcessor(myProcessor);
    if (myShouldRearrangeCode) {
      myProcessor = mixWithRearrangeProcessor(myProcessor);
    }

    if (shouldNotify()) {
      myProcessor.setCollectInfo(true);
      myProcessor.setPostRunnable(() -> {
        String message = prepareMessage();
        if (!myEditor.isDisposed() && myEditor.getComponent().isShowing()) {
          HyperlinkListener hyperlinkListener = new HyperlinkAdapter() {
            @Override
            protected void hyperlinkActivated(HyperlinkEvent e) {
              AnAction action = ActionManager.getInstance().getAction("ShowReformatFileDialog");
              DataManager manager = DataManager.getInstance();
              if (manager != null) {
                DataContext context = manager.getDataContext(myEditor.getContentComponent());
                action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", context));
              }
            }
          };
          showHint(myEditor, message, hyperlinkListener);
        }
      });
    }

    myProcessor.run();
  }

  private AbstractLayoutCodeProcessor mixWithRearrangeProcessor(@NotNull AbstractLayoutCodeProcessor processor) {
    if (myProcessSelectedText) {
      processor = new RearrangeCodeProcessor(processor, myEditor.getSelectionModel());
    }
    else {
      processor = new RearrangeCodeProcessor(processor);
    }
    return processor;
  }

  @NotNull
  private AbstractLayoutCodeProcessor mixWithReformatProcessor(@Nullable AbstractLayoutCodeProcessor processor) {
    if (processor != null) {
      if (myProcessSelectedText) {
        processor = new ReformatCodeProcessor(processor, myEditor.getSelectionModel());
      }
      else {
        processor = new ReformatCodeProcessor(processor, myProcessChangesTextOnly);
      }
    }
    else {
      if (myProcessSelectedText) {
        processor = new ReformatCodeProcessor(myFile, myEditor.getSelectionModel());
      }
      else {
        processor = new ReformatCodeProcessor(myFile, myProcessChangesTextOnly);
      }
    }
    return processor;
  }

  @NotNull
  private String prepareMessage() {
    StringBuilder builder = new StringBuilder("<html>");
    LayoutCodeInfoCollector notifications = myProcessor.getInfoCollector();
    LOG.assertTrue(notifications != null);

    if (notifications.isEmpty() && !myNoChangesDetected) {
      if (myProcessChangesTextOnly) {
        builder.append("No lines changed: changes since last revision are already properly formatted").append("<br>");
      }
      else {
        builder.append("No lines changed: content is already properly formatted").append("<br>");
      }
    }
    else {
      if (notifications.hasReformatOrRearrangeNotification()) {
        String reformatInfo = notifications.getReformatCodeNotification();
        String rearrangeInfo = notifications.getRearrangeCodeNotification();

        builder.append(joinWithCommaAndCapitalize(reformatInfo, rearrangeInfo));

        if (myProcessChangesTextOnly) {
          builder.append(" in changes since last revision");
        }

        builder.append("<br>");
      }
      else if (myNoChangesDetected) {
        builder.append("No lines changed: no changes since last revision").append("<br>");
      }

      String optimizeImportsNotification = notifications.getOptimizeImportsNotification();
      if (optimizeImportsNotification != null) {
        builder.append(StringUtil.capitalize(optimizeImportsNotification)).append("<br>");
      }
    }

    String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ShowReformatFileDialog"));
    String color = ColorUtil.toHex(JBColor.gray);

    builder.append("<span style='color:#").append(color).append("'>")
           .append("<a href=''>Show</a> reformat dialog: ").append(shortcutText).append("</span>")
           .append("</html>");

    return builder.toString();
  }

  @NotNull
  private static String joinWithCommaAndCapitalize(String reformatNotification, String rearrangeNotification) {
    String firstNotificationLine = reformatNotification != null ? reformatNotification : rearrangeNotification;
    if (reformatNotification != null && rearrangeNotification != null) {
      firstNotificationLine += ", " + rearrangeNotification;
    }
    firstNotificationLine = StringUtil.capitalize(firstNotificationLine);
    return firstNotificationLine;
  }

  private static boolean isCaretVisible(Editor editor) {
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    Caret currentCaret = editor.getCaretModel().getCurrentCaret();
    Point caretPoint = editor.visualPositionToXY(currentCaret.getVisualPosition());
    return visibleArea.contains(caretPoint);
  }

  public static void showHint(@NotNull Editor editor, @NotNull String info, @Nullable HyperlinkListener hyperlinkListener) {
    JComponent component = HintUtil.createInformationLabel(info, hyperlinkListener, null, null);
    LightweightHint hint = new LightweightHint(component);

    int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
    if (isCaretVisible(editor)) {
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER, flags, 0, false);
    }
    else {
      showHintWithoutScroll(editor, hint, flags);
    }
  }

  private static void showHintWithoutScroll(Editor editor, LightweightHint hint, int flags) {
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    
    short constraint;
    int y;
    
    if (isCaretAboveTop(editor, visibleArea)) {
      y = visibleArea.y;
      constraint = HintManager.UNDER;
    }
    else {
      y = visibleArea.y + visibleArea.height;
      constraint = HintManager.ABOVE;
    }
    
    Point hintPoint = new Point(visibleArea.x + (visibleArea.width / 2), y);
    
    JComponent component = HintManagerImpl.getExternalComponent(editor);
    Point convertedPoint = SwingUtilities.convertPoint(editor.getContentComponent(), hintPoint, component);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, convertedPoint, flags, 0, false, constraint);
  }

  private static boolean isCaretAboveTop(Editor editor, Rectangle area) {
    Caret caret = editor.getCaretModel().getCurrentCaret();
    VisualPosition caretVisualPosition = caret.getVisualPosition();
    int caretY = editor.visualPositionToXY(caretVisualPosition).y;
    return caretY < area.y;
  }

  private boolean shouldNotify() {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      return false;
    }
    EditorSettingsExternalizable.OptionSet editorOptions = EditorSettingsExternalizable.getInstance().getOptions();
    return editorOptions.SHOW_NOTIFICATION_AFTER_REFORMAT_CODE_ACTION && myEditor != null && !myProcessSelectedText;
  }
}
