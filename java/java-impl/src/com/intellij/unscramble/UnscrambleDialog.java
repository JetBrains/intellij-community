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

/**
 * @author cdr
 */
package com.intellij.unscramble;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UnscrambleDialog extends DialogWrapper{
  @NonNls private static final String PROPERTY_LOG_FILE_HISTORY_URLS = "UNSCRAMBLE_LOG_FILE_URL";
  @NonNls private static final String PROPERTY_LOG_FILE_LAST_URL = "UNSCRAMBLE_LOG_FILE_LAST_URL";
  @NonNls private static final String PROPERTY_UNSCRAMBLER_NAME_USED = "UNSCRAMBLER_NAME_USED";

  private final Project myProject;
  private JPanel myEditorPanel;
  private JPanel myLogFileChooserPanel;
  private JComboBox myUnscrambleChooser;
  private JPanel myPanel;
  private TextFieldWithHistory myLogFile;
  private JCheckBox myUseUnscrambler;
  private JPanel myUnscramblePanel;
  protected AnalyzeStacktraceUtil.StacktraceEditorPanel myStacktraceEditorPanel;

  public UnscrambleDialog(Project project) {
    super(false);
    myProject = project;

    populateRegisteredUnscramblerList();
    myUnscrambleChooser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        UnscrambleSupport unscrambleSupport = getSelectedUnscrambler();
        GuiUtils.enableChildren(myLogFileChooserPanel, unscrambleSupport != null);
      }
    });
    myUseUnscrambler.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        useUnscramblerChanged();
      }
    });
    createLogFileChooser();
    createEditor();
    reset();

    setTitle(IdeBundle.message("unscramble.dialog.title"));
    init();
  }

  private void useUnscramblerChanged() {
    boolean selected = myUseUnscrambler.isSelected();
    GuiUtils.enableChildren(myUnscramblePanel, selected, myUseUnscrambler);
  }

  private void reset() {
    final List<String> savedUrls = getSavedLogFileUrls();
    myLogFile.setHistorySize(10);
    myLogFile.setHistory(savedUrls);

    String lastUrl = getLastUsedLogUrl();
    if (lastUrl == null && !savedUrls.isEmpty()) {
      lastUrl = savedUrls.get(savedUrls.size() - 1);
    }
    if (lastUrl != null) {
      myLogFile.setText(lastUrl);
      myLogFile.setSelectedItem(lastUrl);
    }
    final UnscrambleSupport selectedUnscrambler = getSavedUnscrambler();

    final int count = myUnscrambleChooser.getItemCount();
    int index = 0;
    if (selectedUnscrambler != null) {
      for (int i = 0; i < count; i++) {
        final UnscrambleSupport unscrambleSupport = (UnscrambleSupport)myUnscrambleChooser.getItemAt(i);
        if (unscrambleSupport != null && Comparing.strEqual(unscrambleSupport.getPresentableName(), selectedUnscrambler.getPresentableName())) {
          index = i;
          break;
        }
      }
    }
    if (count > 0) {
      myUseUnscrambler.setEnabled(true);
      myUnscrambleChooser.setSelectedIndex(index);
      myUseUnscrambler.setSelected(selectedUnscrambler != null);
    }
    else {
      myUseUnscrambler.setEnabled(false);
    }

    useUnscramblerChanged();
    myStacktraceEditorPanel.pasteTextFromClipboard();
  }

  public static String getLastUsedLogUrl() {
    return PropertiesComponent.getInstance().getValue(PROPERTY_LOG_FILE_LAST_URL);
  }

  @Nullable
  public static UnscrambleSupport getSavedUnscrambler() {
    final List<UnscrambleSupport> registeredUnscramblers = getRegisteredUnscramblers();
    final String savedUnscramblerName = PropertiesComponent.getInstance().getValue(PROPERTY_UNSCRAMBLER_NAME_USED);
    UnscrambleSupport selectedUnscrambler = null;
    for (final UnscrambleSupport unscrambleSupport : registeredUnscramblers) {
      if (Comparing.strEqual(unscrambleSupport.getPresentableName(), savedUnscramblerName)) {
        selectedUnscrambler = unscrambleSupport;
      }
    }
    return selectedUnscrambler;
  }

  public static List<String> getSavedLogFileUrls() {
    final List<String> res = new ArrayList<String>();
    final String savedUrl = PropertiesComponent.getInstance().getValue(PROPERTY_LOG_FILE_HISTORY_URLS);
    final String[] strings = savedUrl == null ? ArrayUtil.EMPTY_STRING_ARRAY : savedUrl.split(":::");
    for (int i = 0; i != strings.length; ++i) {
      res.add(strings[i]);
    }
    return res;
  }

  @Nullable
  private UnscrambleSupport getSelectedUnscrambler() {
    if (!myUseUnscrambler.isSelected()) return null;
    return (UnscrambleSupport)myUnscrambleChooser.getSelectedItem();
  }

  private void createEditor() {
    myStacktraceEditorPanel = AnalyzeStacktraceUtil.createEditorPanel(myProject, myDisposable);
    myEditorPanel.setLayout(new BorderLayout());
    myEditorPanel.add(myStacktraceEditorPanel, BorderLayout.CENTER);
  }

  protected Action[] createActions(){
    return new Action[]{createNormalizeTextAction(), getOKAction(), getCancelAction(), getHelpAction()};
  }

  private void createLogFileChooser() {
    myLogFile = new TextFieldWithHistory();
    JPanel panel = GuiUtils.constructFieldWithBrowseButton(myLogFile, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        VirtualFile[] files = FileChooser.chooseFiles(myLogFile, descriptor);
        if (files.length != 0) {
          myLogFile.setText(FileUtil.toSystemDependentName(files[files.length-1].getPath()));
        }
      }
    });
    myLogFileChooserPanel.setLayout(new BorderLayout());
    myLogFileChooserPanel.add(panel, BorderLayout.CENTER);
  }

  private void populateRegisteredUnscramblerList() {
    List<UnscrambleSupport> unscrambleComponents = getRegisteredUnscramblers();

    //myUnscrambleChooser.addItem(null);
    for (final UnscrambleSupport unscrambleSupport : unscrambleComponents) {
      myUnscrambleChooser.addItem(unscrambleSupport);
    }
    myUnscrambleChooser.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        UnscrambleSupport unscrambleSupport = (UnscrambleSupport)value;
        setText(unscrambleSupport == null ? IdeBundle.message("unscramble.no.unscrambler.item") : unscrambleSupport.getPresentableName());
        return this;
      }
    });
  }

  private static List<UnscrambleSupport> getRegisteredUnscramblers() {
    final UnscrambleSupport[] components = Extensions.getExtensions(UnscrambleSupport.EP_NAME);
    return Arrays.asList(components);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public void dispose() {
    if (isOK()){
      final List list = myLogFile.getHistory();
      String res = null;
      for (Object aList : list) {
        final String s = (String)aList;
        if (res == null) {
          res = s;
        }
        else {
          res = res + ":::" + s;
        }
      }
      PropertiesComponent.getInstance().setValue(PROPERTY_LOG_FILE_HISTORY_URLS, res);
      UnscrambleSupport selectedUnscrambler = getSelectedUnscrambler();
      PropertiesComponent.getInstance().setValue(PROPERTY_UNSCRAMBLER_NAME_USED, selectedUnscrambler == null ? null : selectedUnscrambler.getPresentableName());

      PropertiesComponent.getInstance().setValue(PROPERTY_LOG_FILE_LAST_URL, myLogFile.getText());
    }
    super.dispose();
  }

  public void setText(String trace) {
    myStacktraceEditorPanel.setText(trace);
  }

  public Action createNormalizeTextAction() {
    return new NormalizeTextAction();
  }

  private final class NormalizeTextAction extends AbstractAction {
    public NormalizeTextAction(){
      putValue(NAME, IdeBundle.message("unscramble.normalize.button"));
      putValue(DEFAULT_ACTION, Boolean.FALSE);
    }

    public void actionPerformed(ActionEvent e){
      String text = myStacktraceEditorPanel.getText();
      myStacktraceEditorPanel.setText(normalizeText(text));
    }

  }

  public static String normalizeText(@NonNls String text) {
    StringBuilder builder = new StringBuilder(text.length());

    text = text.replaceAll("(\\S[ \\t\\x0B\\f\\r]+)(at\\s+)", "$1\n$2");
    String[] lines = text.split("\n");

    boolean first = true;
    boolean inAuxInfo = false;
    for (String line : lines) {
      //noinspection HardCodedStringLiteral
      if (!inAuxInfo && (line.startsWith("JNI global references") || line.trim().equals("Heap"))) {
        builder.append("\n");
        inAuxInfo = true;
      }
      if (inAuxInfo) {
        builder.append(trimSuffix(line)).append("\n");
        continue;
      }
      if (!first && mustHaveNewLineBefore(line)) {
        builder.append("\n");
        if (line.startsWith("\"")) builder.append("\n"); // Additional linebreak for thread names
      }
      first = false;
      int i = builder.lastIndexOf("\n");
      CharSequence lastLine = i == -1 ? builder : builder.subSequence(i + 1, builder.length());
      if (lastLine.toString().matches("\\s*at") && !line.matches("\\s+.*")) builder.append(" "); // separate 'at' from file name
      builder.append(trimSuffix(line));
    }
    return builder.toString();
  }

  private static String trimSuffix(final String line) {
    int len = line.length();

    while ((0 < len) && (line.charAt(len-1) <= ' ')) {
        len--;
    }
    return (len < line.length()) ? line.substring(0, len) : line;
  }

  private static boolean mustHaveNewLineBefore(String line) {
    final int nonws = CharArrayUtil.shiftForward(line, 0, " \t");
    if (nonws < line.length()) {
      line = line.substring(nonws);
    }

    if (line.startsWith("at")) return true;        // Start of the new stackframe entry
    if (line.startsWith("Caused")) return true;    // Caused by message
    if (line.startsWith("- locked")) return true;  // "Locked a monitor" logging
    if (line.startsWith("- waiting")) return true; // "Waiting for monitor" logging
    if (line.startsWith("- parking to wait")) return true;
    if (line.startsWith("java.lang.Thread.State")) return true;
    if (line.startsWith("\"")) return true;        // Start of the new thread (thread name)

    return false;
  }

  protected void doOKAction() {
    if (performUnscramble()) {
      myLogFile.addCurrentTextToHistory();
      close(OK_EXIT_CODE);
    }
  }

  public void doHelpAction() {
    HelpManager.getInstance().invokeHelp("find.analyzeStackTrace");
  }

  private boolean performUnscramble() {
    UnscrambleSupport selectedUnscrambler = getSelectedUnscrambler();
    return showUnscrambledText(selectedUnscrambler, myLogFile.getText(), myProject, myStacktraceEditorPanel.getText());
  }

  static boolean showUnscrambledText(UnscrambleSupport unscrambleSupport, String logName, Project project, String textToUnscramble) {
    String unscrambledTrace = unscrambleSupport == null ? textToUnscramble : unscrambleSupport.unscramble(project,textToUnscramble, logName);
    if (unscrambledTrace == null) return false;
    List<ThreadState> threadStates = ThreadDumpParser.parse(unscrambledTrace);
    final ConsoleView consoleView = addConsole(project, threadStates);
    AnalyzeStacktraceUtil.printStacktrace(consoleView, unscrambledTrace);
    return true;
  }

  public static ConsoleView addConsole(final Project project, final List<ThreadState> threadDump) {
    return AnalyzeStacktraceUtil.addConsole(project, threadDump.size() > 1 ? new AnalyzeStacktraceUtil.ConsoleFactory() {
      public JComponent createConsoleComponent(ConsoleView consoleView, DefaultActionGroup toolbarActions) {
        return new ThreadDumpPanel(project, consoleView, toolbarActions, threadDump);
      }
    } : null, IdeBundle.message("unscramble.unscrambled.stacktrace.tab"));
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.unscramble.UnscrambleDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myStacktraceEditorPanel.getEditorComponent();
  }
}