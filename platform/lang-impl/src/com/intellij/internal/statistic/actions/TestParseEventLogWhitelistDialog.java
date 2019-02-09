// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.service.fus.FUSWhitelist;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class TestParseEventLogWhitelistDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(TestParseEventLogWhitelistDialog.class);

  private static final int IN_DIVIDER_LOCATION = 650;
  private static final int IN_OUT_DIVIDER_LOCATION = 500;
  private JPanel myMainPanel;
  private JPanel myWhitelistPanel;
  private JEditorPane myEventLogPanel;
  private JSplitPane myInputDataSplitPane;
  private JSplitPane myInputOutputSplitPane;
  private JEditorPane myResultPane;

  private final Project myProject;
  private final EditorEx myEditor;

  protected TestParseEventLogWhitelistDialog(@NotNull Project project, @Nullable Editor selectedEditor) {
    super(project);
    myProject = project;
    setOKButtonText("&Filter Event Log");
    setCancelButtonText("&Close");
    Disposer.register(myProject, getDisposable());
    VirtualFile selectedFile = selectedEditor == null ? null : FileDocumentManager.getInstance().getFile(selectedEditor.getDocument());
    setTitle(selectedFile == null ? "Event Log Filter" : "Event Log Filter by: " + selectedFile.getName());
    myEditor = initEditor(selectedEditor);
    myEditor.getSettings().setLineMarkerAreaShown(false);

    init();
    if (selectedEditor != null) {
      doOKAction();

      ApplicationManager.getApplication().invokeLater(() -> {
        IdeFocusManager.getGlobalInstance()
          .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myEditor.getContentComponent(), true));
        myEditor.getCaretModel().moveToOffset(selectedEditor.getCaretModel().getOffset());
        myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }, ModalityState.stateForComponent(myMainPanel));
    }
  }

  @NotNull
  private EditorEx initEditor(@Nullable Editor selectedEditor) {
    if (selectedEditor != null) {
      return (EditorEx)EditorFactory.getInstance().createEditor(selectedEditor.getDocument(), myProject);
    }
    else {
      Document document = EditorFactory.getInstance().createDocument(StringUtil.notNullize("{}"));
      EditorEx editor = (EditorEx)EditorFactory.getInstance().createEditor(document, myProject);
      editor.getSelectionModel().setSelection(0, document.getTextLength());
      return editor;
    }
  }

  @Override
  protected void init() {
    configEditorPanel(myProject, myWhitelistPanel, myEditor);

    myInputDataSplitPane.setDividerLocation(IN_DIVIDER_LOCATION);
    myInputOutputSplitPane.setDividerLocation(IN_OUT_DIVIDER_LOCATION);
    super.init();
  }

  private static void configEditorPanel(@NotNull Project project, @NotNull JPanel panel, @NotNull EditorEx editor) {
    panel.setLayout(new BorderLayout());
    panel.add(editor.getComponent(), BorderLayout.CENTER);

    editor.getSettings().setFoldingOutlineShown(false);
    final FileType fileType = FileTypeManager.getInstance().findFileTypeByName("JSON");
    final LightVirtualFile lightFile = new LightVirtualFile("Dummy.json", fileType, "");

    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, lightFile);
    try {
      editor.setHighlighter(highlighter);
    }
    catch (Throwable e) {
      LOG.warn(e);
    }
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return TestParseEventLogWhitelistDialog.class.getCanonicalName();
  }

  @SuppressWarnings("TestOnlyProblems")
  @Override
  protected void doOKAction() {
    myEditor.getSelectionModel().removeSelection();
    updateOutputText("");

    final BuildNumber build = BuildNumber.fromString(EventLogConfiguration.INSTANCE.getBuild());
    final FUSWhitelist whitelist = FUStatisticsWhiteListGroupsService.parseApprovedGroups(myEditor.getDocument().getText(), build);
    try {
      final String parsed = parseLogAndFilter(new LogEventWhitelistFilter(whitelist), myEventLogPanel.getText());
      updateOutputText(parsed.trim());
    }
    catch (IOException | ParseEventLogWhitelistException e) {
      Messages.showErrorDialog(myProject, e.getMessage(), "Failed Applying Whitelist to Event Log");
    }
  }

  private void updateOutputText(@NotNull String text) {
    myResultPane.setText(text);
  }

  @NotNull
  private static String parseLogAndFilter(@NotNull LogEventFilter filter, @NotNull String text)
    throws IOException, ParseEventLogWhitelistException {
    final File log = FileUtil.createTempFile("feature-event-log", ".log");
    try {
      FileUtil.writeToFile(log, text);
      final LogEventRecordRequest request = LogEventRecordRequest.Companion.create(log, filter, true);
      if (request == null) {
        throw new ParseEventLogWhitelistException("Failed parsing event log");
      }
      return LogEventSerializer.INSTANCE.toString(request);
    }
    finally {
      FileUtil.delete(log);
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEventLogPanel;
  }

  @Override
  public void dispose() {
    if (!myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
    super.dispose();
  }

  public static class ParseEventLogWhitelistException extends Exception {
    public ParseEventLogWhitelistException(String s) {
      super(s);
    }
  }
}
