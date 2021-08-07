// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.unscramble;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.configurable.VcsContentAnnotationConfigurable;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.ar;

public class UnscrambleDialog extends DialogWrapper {
  @NonNls private static final String PROPERTY_LOG_FILE_HISTORY_URLS = "UNSCRAMBLE_LOG_FILE_URL";
  @NonNls private static final String PROPERTY_LOG_FILE_LAST_URL = "UNSCRAMBLE_LOG_FILE_LAST_URL";
  @NonNls private static final String PROPERTY_UNSCRAMBLER_NAME_USED = "UNSCRAMBLER_NAME_USED";
  private static final Condition<ThreadState> DEADLOCK_CONDITION = state -> state.isDeadlocked();
  private static final String[] IMPORTANT_THREAD_DUMP_WORDS = ar("tid", "nid", "wait", "parking", "prio", "os_prio", "java");

  private final Project myProject;
  private JPanel myEditorPanel;
  private JPanel myLogFileChooserPanel;
  private JComboBox<UnscrambleSupport> myUnscrambleChooser;
  private JPanel myPanel;
  private TextFieldWithHistory myLogFile;
  private JCheckBox myUseUnscrambler;
  private JPanel myUnscramblePanel;
  private JCheckBox myOnTheFly;
  private JPanel myBottomPanel;
  private JPanel mySettingsPanel;
  protected AnalyzeStacktraceUtil.StacktraceEditorPanel myStacktraceEditorPanel;
  private VcsContentAnnotationConfigurable myConfigurable;

  public UnscrambleDialog(@NotNull Project project) {
    super(false);
    myProject = project;

    populateRegisteredUnscramblerList();
    myUnscrambleChooser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UnscrambleSupport unscrambleSupport = getSelectedUnscrambler();
        GuiUtils.enableChildren(myLogFileChooserPanel, unscrambleSupport != null);
        updateUnscramblerSettings();
      }
    });
    myUseUnscrambler.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        useUnscramblerChanged();
      }
    });
    myOnTheFly.setSelected(Registry.get("analyze.exceptions.on.the.fly").asBoolean());
    myOnTheFly.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Registry.get("analyze.exceptions.on.the.fly").setValue(myOnTheFly.isSelected());
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
    if (selected) {
      updateUnscramblerSettings();
    }
  }

  private void updateUnscramblerSettings() {
    UnscrambleSupport unscrambleSupport = (UnscrambleSupport)myUnscrambleChooser.getSelectedItem();

    JComponent settingsComponent = unscrambleSupport == null ? null : unscrambleSupport.createSettingsComponent();
    mySettingsPanel.removeAll();
    if (settingsComponent != null) {
      mySettingsPanel.add(settingsComponent, BorderLayout.CENTER);
    }
    myUnscramblePanel.validate();
  }

  private void reset() {
    final List<String> savedUrls = getSavedLogFileUrls();
    myLogFile.setHistorySize(10);
    myLogFile.setHistory(savedUrls);

    String lastUrl = getPropertyValue(PROPERTY_LOG_FILE_LAST_URL); //NON-NLS URL is safe to show
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
        final UnscrambleSupport unscrambleSupport = myUnscrambleChooser.getItemAt(i);
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
    updateUnscramblerSettings();
    myStacktraceEditorPanel.pasteTextFromClipboard();
  }

  private void createUIComponents() {
    myBottomPanel = new JPanel(new BorderLayout());
    if (ProjectLevelVcsManager.getInstance(myProject).hasActiveVcss()) {
      myConfigurable = new VcsContentAnnotationConfigurable(myProject);
      myBottomPanel.add(myConfigurable.createComponent(), BorderLayout.CENTER);
      myConfigurable.reset();
    }
  }

  @Nullable
  private UnscrambleSupport getSavedUnscrambler() {
    final String savedUnscramblerName = getPropertyValue(PROPERTY_UNSCRAMBLER_NAME_USED);
    UnscrambleSupport selectedUnscrambler = null;
    for (UnscrambleSupport unscrambleSupport : UnscrambleSupport.EP_NAME.getExtensions()) {
      if (Comparing.strEqual(unscrambleSupport.getPresentableName(), savedUnscramblerName)) {
        selectedUnscrambler = unscrambleSupport;
      }
    }
    return selectedUnscrambler;
  }

  @NotNull
  public static List<String> getSavedLogFileUrls() {
    final List<String> res = new ArrayList<>();
    final String savedUrl = PropertiesComponent.getInstance().getValue(PROPERTY_LOG_FILE_HISTORY_URLS);
    if (savedUrl != null) {
      ContainerUtil.addAll(res, savedUrl.split(":::"));
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

  @Override
  protected Action @NotNull [] createActions() {
    return ArrayUtil.prepend(createNormalizeTextAction(), super.createActions());
  }

  @Override
  protected String getHelpId() {
    return "find.analyzeStackTrace";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    JRootPane pane = getRootPane();
    return pane != null ? pane.getDefaultButton() : super.getPreferredFocusedComponent();
  }

  private void createLogFileChooser() {
    myLogFile = new TextFieldWithHistory();
    JPanel panel = GuiUtils.constructFieldWithBrowseButton(myLogFile, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        FileChooser.chooseFiles(descriptor, myProject, null, files -> myLogFile.setText(FileUtil.toSystemDependentName(files.get(files.size() - 1).getPath())));
      }
    });
    myLogFileChooserPanel.setLayout(new BorderLayout());
    myLogFileChooserPanel.add(panel, BorderLayout.CENTER);
  }

  private void populateRegisteredUnscramblerList() {
    for (UnscrambleSupport unscrambleSupport : UnscrambleSupport.EP_NAME.getExtensions()) {
      myUnscrambleChooser.addItem(unscrambleSupport);
    }
    myUnscrambleChooser.setRenderer(SimpleListCellRenderer.create(
      JavaBundle.message("unscramble.no.unscrambler.item"), UnscrambleSupport::getPresentableName));
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public void dispose() {
    if (isOK()){
      final List<String> list = myLogFile.getHistory();
      PropertiesComponent.getInstance().setValue(PROPERTY_LOG_FILE_HISTORY_URLS, list.isEmpty() ? null : StringUtil.join(list, ":::"), null);
      UnscrambleSupport selectedUnscrambler = getSelectedUnscrambler();
      saveProperty(PROPERTY_UNSCRAMBLER_NAME_USED, selectedUnscrambler == null ? null : selectedUnscrambler.getPresentableName());
      saveProperty(PROPERTY_LOG_FILE_LAST_URL, StringUtil.nullize(myLogFile.getText()));
    }
    super.dispose();
  }

  // IDEA-125302 The Analyze Stacktrace menu option remembers only one log file across multiple projects
  private void saveProperty(@NotNull  String name, @Nullable String value) {
    PropertiesComponent.getInstance(myProject).setValue(name, value);
    PropertiesComponent.getInstance().setValue(name, value);
  }

  @Nullable
  private @NlsSafe String getPropertyValue(@NotNull String name) {
    String projectValue = PropertiesComponent.getInstance(myProject).getValue(name);
    if (projectValue != null) {
      return projectValue;
    }
    return PropertiesComponent.getInstance().getValue(name);
  }

  public void setText(String trace) {
    myStacktraceEditorPanel.setText(trace);
  }

  public Action createNormalizeTextAction() {
    return new NormalizeTextAction();
  }

  private final class NormalizeTextAction extends AbstractAction {
    NormalizeTextAction(){
      putValue(NAME, JavaBundle.message("unscramble.normalize.button"));
      putValue(DEFAULT_ACTION, Boolean.FALSE);
    }

    @Override
    public void actionPerformed(ActionEvent e){
      String text = myStacktraceEditorPanel.getText();
      myStacktraceEditorPanel.setText(normalizeText(text));
    }
  }

  public static String normalizeText(@NonNls String text) {
    StringBuilder builder = new StringBuilder(text.length());

    text = text.replaceAll("(\\S[ \\t\\x0B\\f\\r]+)(at\\s+)", "$1\n$2");
    text = text.replaceAll("(\\\\n|\\\\r|\\\\t)+(at\\s+)", "\n$2");
    String[] lines = text.split("\n");

    boolean first = true;
    boolean inAuxInfo = false;
    for (final String line : lines) {
      //noinspection HardCodedStringLiteral
      if (!inAuxInfo && (line.startsWith("JNI global references") || line.trim().equals("Heap"))) {
        builder.append("\n");
        inAuxInfo = true;
      }
      if (inAuxInfo) {
        builder.append(trimSuffix(line)).append("\n");
        continue;
      }
      if (line.startsWith("at breakpoint")) { // possible thread status mixed with "at ..."
        builder.append(" ").append(trimSuffix(line));
        continue;
      }
      if (!first && (mustHaveNewLineBefore(line) || StringUtil.endsWith(builder, ")"))) {
        if (!StringUtil.endsWith(builder, "\n")) builder.append("\n");
        if (line.startsWith("\"")) builder.append("\n"); // Additional line break for thread names
      }
      first = false;
      int i = builder.lastIndexOf("\n");
      CharSequence lastLine = i == -1 ? builder : builder.subSequence(i + 1, builder.length());
      if (!line.matches("\\s+.*") && lastLine.length() > 0) {
        if (lastLine.toString().matches("\\s*at") //separate 'at' from filename
            || ContainerUtil.or(IMPORTANT_THREAD_DUMP_WORDS, word -> line.startsWith(word))) {
          builder.append(" ");
        }
      }
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
    final int nonWs = CharArrayUtil.shiftForward(line, 0, " \t");
    if (nonWs < line.length()) {
      line = line.substring(nonWs);
    }

    if (line.startsWith("at")) return true;        // Start of the new stack frame entry
    if (line.startsWith("Caused")) return true;    // Caused by message
    if (line.startsWith("- locked")) return true;  // "Locked a monitor" logging
    if (line.startsWith("- waiting")) return true; // "Waiting for monitor" logging
    if (line.startsWith("- parking to wait")) return true;
    if (line.startsWith("java.lang.Thread.State")) return true;
    if (line.startsWith("\"")) return true;        // Start of the new thread (thread name)

    return false;
  }

  @Override
  protected void doOKAction() {
    if (myConfigurable != null && myConfigurable.isModified()) {
      myConfigurable.apply();
    }
    DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> {
      if (performUnscramble()) {
        myLogFile.addCurrentTextToHistory();
        close(OK_EXIT_CODE);
      }
    });
  }

  private boolean performUnscramble() {
    UnscrambleSupport selectedUnscrambler = getSelectedUnscrambler();
    JComponent settings = mySettingsPanel.getComponentCount() == 0 ? null : (JComponent)mySettingsPanel.getComponent(0);
    return showUnscrambledText(selectedUnscrambler, myLogFile.getText(), settings, myProject, myStacktraceEditorPanel.getText()) != null;
  }

  @Nullable
  static <T extends JComponent> RunContentDescriptor showUnscrambledText(@Nullable UnscrambleSupport<T> unscrambleSupport,
                                                                         String logName,
                                                                         @Nullable T settings,
                                                                         Project project,
                                                                         String textToUnscramble) {
    String unscrambledTrace = unscrambleSupport == null ? textToUnscramble : unscrambleSupport.unscramble(project,textToUnscramble, logName, settings);
    if (unscrambledTrace == null) return null;
    List<ThreadState> threadStates = ThreadDumpParser.parse(unscrambledTrace);
    return addConsole(project, threadStates, unscrambledTrace);
  }

  private static RunContentDescriptor addConsole(final Project project, final List<ThreadState> threadDump, String unscrambledTrace) {
    Icon icon = null;
    String message = JavaBundle.message("unscramble.unscrambled.stacktrace.tab");
    if (!threadDump.isEmpty()) {
      message = JavaBundle.message("unscramble.unscrambled.threaddump.tab");
      icon = AllIcons.Actions.Dump;
    }
    else {
      String name = getExceptionName(unscrambledTrace);
      if (name != null) {
        message = name;
        icon = AllIcons.Actions.Lightning;
      }
    }
    if (ContainerUtil.find(threadDump, DEADLOCK_CONDITION) != null) {
      message = JavaBundle.message("unscramble.unscrambled.deadlock.tab");
      icon = AllIcons.Debugger.KillProcess;
    }
    return AnalyzeStacktraceUtil.addConsole(project, threadDump.size() > 1 ? new ThreadDumpConsoleFactory(project, threadDump) : null, message, unscrambledTrace, icon);
  }

  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.unscramble.UnscrambleDialog";
  }

  @Nullable
  private static String getExceptionName(String unscrambledTrace) {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    BufferedReader reader = new BufferedReader(new StringReader(unscrambledTrace));
    for (int i = 0; i < 3; i++) {
      try {
        String line = reader.readLine();
        if (line == null) return null;
        String name = getExceptionAbbreviation(line);
        if (name != null) return name;
      }
      catch (IOException e) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static String getExceptionAbbreviation(String line) {
    line = StringUtil.trimStart(line.trim(), "Caused by: ");
    int classNameStart = 0;
    int classNameEnd = line.length();
    for (int j = 0; j < line.length(); j++) {
      char c = line.charAt(j);
      if (c == '.' || c == '$') {
        classNameStart = j + 1;
        continue;
      }
      if (c == ':') {
        classNameEnd = j;
        break;
      }
      if (!StringUtil.isJavaIdentifierPart(c)) {
        return null;
      }
    }
    if (classNameStart >= classNameEnd) return null;
    String clazz = line.substring(classNameStart, classNameEnd);
    String abbreviate = abbreviate(clazz);
    return abbreviate.length() > 1 ? abbreviate : clazz;
  }

  private static String abbreviate(String s) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
          char c = s.charAt(i);
          if (Character.isUpperCase(c)) {
              builder.append(c);
          }
      }
      return builder.toString();
  }
}
