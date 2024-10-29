// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.formatting.service.CoreFormattingService;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.lang.LangBundle;
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
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
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
import java.util.Objects;

import static com.intellij.codeInsight.actions.TextRangeType.SELECTED_TEXT;
import static com.intellij.codeInsight.actions.TextRangeType.VCS_CHANGED_TEXT;

public final class FileInEditorProcessor {
  private static final Logger LOG = Logger.getInstance(FileInEditorProcessor.class);

  private final @NotNull Editor myEditor;

  private boolean myNoChangesDetected;
  private final boolean myProcessChangesTextOnly;

  private final boolean myProcessSelectedText;
  private final LayoutCodeOptions myOptions;

  private final @NotNull Project myProject;

  private final @NotNull PsiFile myFile;
  private AbstractLayoutCodeProcessor myProcessor;

  public FileInEditorProcessor(@NotNull PsiFile file,
                               @NotNull Editor editor,
                               @NotNull LayoutCodeOptions runOptions) {
    myFile = file;
    myProject = file.getProject();
    myEditor = editor;

    myOptions = runOptions;
    myProcessSelectedText = runOptions.getTextRangeType() == SELECTED_TEXT;
    myProcessChangesTextOnly = runOptions.getTextRangeType() == VCS_CHANGED_TEXT;
  }

  public void processCode() {
    if (!CodeStyle.isFormattingEnabled(myFile)) {
      if (!isInHeadlessMode() && !myEditor.isDisposed() && myEditor.getComponent().isShowing()) {
        showHint(myEditor, new DisabledFormattingMessageBuilder());
      }
      return;
    }

    if (myOptions.isOptimizeImports() && myOptions.getTextRangeType() == VCS_CHANGED_TEXT) {
      myProcessor = new OptimizeImportsProcessor(myProject, myFile);
    }

    if (myProcessChangesTextOnly && !VcsFacade.getInstance().hasChanges(myFile)) {
      myNoChangesDetected = true;
    }

    myProcessor = mixWithReformatProcessor(myProcessor);
    if (myOptions.isRearrangeCode()) {
      myProcessor = mixWithRearrangeProcessor(myProcessor);
    }

    if (myOptions.isCodeCleanup()) {
      myProcessor = mixWithCleanupProcessor(myProcessor);
    }

    if (shouldNotify()) {
      myProcessor.setCollectInfo(true);
      myProcessor.setPostRunnable(() -> {
        if (myEditor.isDisposed() || !myEditor.getComponent().isShowing()) {
          return;
        }
        if ((!myProcessSelectedText || Objects.requireNonNull(myProcessor.getInfoCollector()).getSecondFormatNotification() != null)
            && !isExternalFormatterInUse()) {
          showHint(myEditor, new FormattedMessageBuilder());
        }
      });
    }

    myProcessor.run();

    if (myOptions.getTextRangeType() == TextRangeType.WHOLE_FILE) {
      CodeStyleSettingsManager.getInstance(myProject).notifyCodeStyleSettingsChanged();
      if (myOptions.isOptimizeImports()) {
        CodeStyleCachingService.getInstance(myProject).scheduleWhenSettingsComputed(myFile, () -> {
          new OptimizeImportsProcessor(myProject, myFile).run();
        });
      }
    }
  }

  private boolean isExternalFormatterInUse() {
    return !(FormattingServiceUtil.findService(myFile, true, myOptions.getTextRangeType() == TextRangeType.WHOLE_FILE)
               instanceof CoreFormattingService);
  }

  private @NotNull AbstractLayoutCodeProcessor mixWithCleanupProcessor(@NotNull AbstractLayoutCodeProcessor processor) {
    if (myProcessSelectedText) {
      processor = new CodeCleanupCodeProcessor(processor, myEditor.getSelectionModel());
    }
    else {
      processor = new CodeCleanupCodeProcessor(processor);
    }
    return processor;
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

  private @NotNull AbstractLayoutCodeProcessor mixWithReformatProcessor(@Nullable AbstractLayoutCodeProcessor processor) {
    ReformatCodeProcessor reformatCodeProcessor;
    if (processor != null) {
      if (myProcessSelectedText) {
        reformatCodeProcessor = new ReformatCodeProcessor(processor, myEditor.getSelectionModel());
      }
      else {
        reformatCodeProcessor = new ReformatCodeProcessor(processor, myProcessChangesTextOnly);
      }
    }
    else {
      if (myProcessSelectedText) {
        reformatCodeProcessor = new ReformatCodeProcessor(myFile, myEditor.getSelectionModel());
      }
      else {
        reformatCodeProcessor = new ReformatCodeProcessor(myFile, myProcessChangesTextOnly);
      }
    }
    if (myOptions.doNotKeepLineBreaks()) {
      reformatCodeProcessor.setDoNotKeepLineBreaks(myFile);
    }
    return reformatCodeProcessor;
  }

  private static @NotNull String joinWithCommaAndCapitalize(String reformatNotification, String rearrangeNotification) {
    String firstNotificationLine = reformatNotification != null ? reformatNotification : rearrangeNotification;
    if (reformatNotification != null && rearrangeNotification != null) {
      firstNotificationLine += ", " + rearrangeNotification;
    }
    firstNotificationLine = StringUtil.capitalize(firstNotificationLine);
    return firstNotificationLine;
  }

  private static void showHint(@NotNull Editor editor, @NotNull MessageBuilder messageBuilder) {
    showHint(editor, messageBuilder.getMessage(), messageBuilder.createHyperlinkListener());
  }

  public static void showHint(@NotNull Editor editor, @NotNull @NlsContexts.HintText String info, @Nullable HyperlinkListener hyperlinkListener) {
    JComponent component = HintUtil.createInformationLabel(info, hyperlinkListener, null, null);
    LightweightHint hint = new LightweightHint(component);

    int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
    if (EditorUtil.isPrimaryCaretVisible(editor)) {
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

  private static boolean shouldNotify() {
    if (isInHeadlessMode()) return false;
    EditorSettingsExternalizable es = EditorSettingsExternalizable.getInstance();
    return es.isShowNotificationAfterReformat();
  }

  private static boolean isInHeadlessMode() {
    Application application = ApplicationManager.getApplication();
    return application.isUnitTestMode() || application.isHeadlessEnvironment();
  }

  private abstract static class MessageBuilder {
    public abstract @NlsContexts.HintText String getMessage();

    public abstract @NotNull Runnable getHyperlinkRunnable();

    public final HyperlinkListener createHyperlinkListener() {
      return new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
          getHyperlinkRunnable().run();
        }
      };
    }
  }

  private final class DisabledFormattingMessageBuilder extends MessageBuilder {
    @Override
    public @NotNull String getMessage() {
      VirtualFile virtualFile = myFile.getVirtualFile();
      String message = virtualFile == null ? LangBundle.message("formatter.unavailable.message")
                                           : LangBundle.message("formatter.unavailable.for.0.message", virtualFile.getName());
      return new HtmlBuilder().append(message).append(
        HtmlChunk.p().child(
          HtmlChunk.span().child(
            HtmlChunk.link("", LangBundle.message("formatter.unavailable.show.settings.link"))
          )
        )
      ).wrapWithHtmlBody().toString();
    }

    @Override
    public @NotNull Runnable getHyperlinkRunnable() {
      return () -> ShowSettingsUtilImpl.showSettingsDialog(myProject, "preferences.sourceCode", "Do not format");
    }
  }

  private static final class ShowReformatDialogRunnable implements Runnable {
    private final Editor myEditor;

    private ShowReformatDialogRunnable(Editor editor) {
      myEditor = editor;
    }

    @Override
    public void run() {
      AnAction action = ActionManager.getInstance().getAction("ShowReformatFileDialog");
      DataManager manager = DataManager.getInstance();
      if (manager != null) {
        DataContext context = manager.getDataContext(myEditor.getContentComponent());
        action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", context));
      }
    }
  }

  private final class FormattedMessageBuilder extends MessageBuilder {
    @Override
    public @NotNull String getMessage() {
      HtmlBuilder builder = new HtmlBuilder();
      LayoutCodeInfoCollector notifications = myProcessor.getInfoCollector();
      LOG.assertTrue(notifications != null);

      if (notifications.isEmpty() && !myNoChangesDetected) {
        if (notifications.getSecondFormatNotification() != null) {
          builder.append(notifications.getSecondFormatNotification()).br();
        }
        else if (myProcessChangesTextOnly) {
          builder.append(LangBundle.message("formatter.in.editor.message.already.formatted")).br();
        }
        else {
          builder.append(LangBundle.message("formatter.in.editor.message.content.already.formatted")).br();
        }
      }
      else {
        if (notifications.hasReformatOrRearrangeNotification()) {
          String reformatInfo = notifications.getReformatCodeNotification();
          String rearrangeInfo = notifications.getRearrangeCodeNotification();

          builder.append(joinWithCommaAndCapitalize(reformatInfo, rearrangeInfo));

          if (myProcessChangesTextOnly) {
            builder.append(LangBundle.message("formatter.in.editor.message.changes.since.last.revision"));
          }

          builder.br();
        }
        else if (myNoChangesDetected) {
          builder.append(LangBundle.message("formatter.in.editor.message.no.changes.since.last.revision")).br();
        }

        String optimizeImportsNotification = notifications.getOptimizeImportsNotification();
        if (optimizeImportsNotification != null) {
          builder.append(optimizeImportsNotification).br();
        }
        if (notifications.getSecondFormatNotification() != null) {
          builder.append(notifications.getSecondFormatNotification()).br();
        }
      }

      String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ShowReformatFileDialog"));
      String color = ColorUtil.toHtmlColor(JBColor.gray);

      builder.append(HtmlChunk.span("color:"+color)
                              .child(HtmlChunk.raw(LangBundle.message("formatter.in.editor.link.show.reformat.dialog"))).addText(shortcutText));

      return builder.wrapWith("html").toString();
    }

    @Override
    public @NotNull Runnable getHyperlinkRunnable() {
      return new ShowReformatDialogRunnable(myEditor);
    }
  }
}
