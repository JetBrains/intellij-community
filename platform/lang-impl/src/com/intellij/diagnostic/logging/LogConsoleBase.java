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

package com.intellij.diagnostic.logging;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.FilterComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class LogConsoleBase extends AdditionalTabComponent implements LogConsole, LogFilterListener {
  private static final Logger LOG = Logger.getInstance("com.intellij.diagnostic.logging.LogConsoleImpl");

  private ConsoleView myConsole;
  private final LightProcessHandler myProcessHandler = new LightProcessHandler();
  private ReaderThread myReaderThread;
  private StringBuffer myOriginalDocument = null;
  private String myLineUnderSelection = null;
  private int myLineOffset = -1;
  private LogContentPreprocessor myContentPreprocessor;
  private String myTitle = null;
  private boolean myWasInitialized;
  private final JPanel myTopComponent = new JPanel(new BorderLayout());
  private ActionGroup myActions;
  private final boolean myBuildInActions;
  private LogFilterModel myModel;

  private FilterComponent myFilter = new FilterComponent("LOG_FILTER_HISTORY", 5) {
    public void filter() {
      myModel.updateCustomFilter(getFilter());
    }
  };
  private JPanel mySearchComponent;
  private JComboBox myLogFilterCombo;
  private JPanel myTextFilterWrapper;

  public LogConsoleBase(Project project, @Nullable Reader reader, String title, final boolean buildInActions, LogFilterModel model) {
    super(new BorderLayout());
    myTitle = title;
    myModel = model;
    myReaderThread = new ReaderThread(reader);
    myBuildInActions = buildInActions;
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    myConsole = builder.getConsole();
    myConsole.attachToProcess(myProcessHandler);
    myModel.addFilterListener(this);
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public LogConsoleBase(Project project, File file, long skippedContents, String title, boolean buildInActions, LogFilterModel model) {
    this(project, getReader(file, skippedContents), title, buildInActions, model);
  }

  public LogConsoleBase(Project project, Reader reader, long skippedContents, String title, boolean buildInActions, LogFilterModel model) {
    this(project, getReader(reader, skippedContents), title, buildInActions, model);
  }

  @Nullable
  private static Reader getReader(Reader reader, long skippedContents) {
    reader = new BufferedReader(reader);
    try {
      reader.skip(skippedContents);
    }
    catch (IOException e) {
      reader = null;
    }
    return reader;
  }

  @Nullable
  private static Reader getReader(File file, long skippedContents) {
    Reader reader = null;
    try {
      try {
        final FileInputStream inputStream = new FileInputStream(file);
        reader = new BufferedReader(new InputStreamReader(inputStream));
        if (file.length() >= skippedContents) { //do not skip forward
          inputStream.skip(skippedContents);
        }
      }
      catch (FileNotFoundException e) {
        if (FileUtil.createIfDoesntExist(file)) {
          reader = new BufferedReader(new FileReader(file));
        }
      }
    }
    catch (Throwable e) {
      reader = null;
    }
    return reader;
  }

  public void setFilterModel(LogFilterModel model) {
    if (myModel != null) {
      myModel.removeFilterListener(this);
    }
    myModel = model;
    myModel.addFilterListener(this);
  }

  public LogFilterModel getFilterModel() {
    return myModel;
  }

  public LogContentPreprocessor getContentPreprocessor() {
    return myContentPreprocessor;
  }

  public void setContentPreprocessor(final LogContentPreprocessor contentPreprocessor) {
    myContentPreprocessor = contentPreprocessor;
  }

  @SuppressWarnings({"NonStaticInitializer"})
  private JComponent createToolbar() {
    String customFilter = myModel.getCustomFilter();

    myFilter.reset();
    myFilter.setSelectedItem(customFilter != null ? customFilter : "");
    new AnAction() {
      {
        registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK)),
                                  LogConsoleBase.this);
      }

      public void actionPerformed(final AnActionEvent e) {
        myFilter.requestFocusInWindow();
      }
    };

    if (myBuildInActions) {
      final JComponent tbComp =
        ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getOrCreateActions(), true).getComponent();
      myTopComponent.add(tbComp, BorderLayout.CENTER);
      myTopComponent.add(getSearchComponent(), BorderLayout.EAST);
    }


    return myTopComponent;
  }

  public ActionGroup getOrCreateActions() {
    if (myActions != null) return myActions;
    DefaultActionGroup group = new DefaultActionGroup();

    final AnAction[] actions = myConsole.createConsoleActions();
    for (AnAction action : actions) {
      group.add(action);
    }

    group.addSeparator();

    /*for (final LogFilter filter : filters) {
      group.add(new ToggleAction(filter.getName(), filter.getName(), filter.getIcon()) {
        public boolean isSelected(AnActionEvent e) {
          return prefs.isFilterSelected(filter);
        }

        public void setSelected(AnActionEvent e, boolean state) {
          prefs.setFilterSelected(filter, state);
        }
      });
    }*/

    myActions = group;

    return myActions;
  }

  public void onFilterStateChange(final LogFilter filter) {
    filterConsoleOutput(new Condition<String>() {
      public boolean value(final String line) {
        return myModel.isApplicable(line);
      }
    });
  }

  public void onTextFilterChange() {
    filterConsoleOutput(new Condition<String>() {
      public boolean value(final String line) {
        return myModel.isApplicable(line);
      }
    });
  }

  @NotNull
  public JComponent getComponent() {
    if (!myWasInitialized) {
      myWasInitialized = true;
      add(myConsole.getComponent(), BorderLayout.CENTER);
      add(createToolbar(), BorderLayout.NORTH);
    }
    return this;
  }

  public abstract boolean isActive();

  public void activate() {
    if (myReaderThread == null) return;
    if (isActive() && !myReaderThread.myRunning) {
      myFilter.setSelectedItem(myModel.getCustomFilter());
      myReaderThread.startRunning();
      ApplicationManager.getApplication().executeOnPooledThread(myReaderThread);
    }
    else if (!isActive() && myReaderThread.myRunning) {
      myReaderThread.stopRunning();
    }
  }

  public void stateChanged(final ChangeEvent e) {
    activate();
  }

  public String getTabTitle() {
    return myTitle;
  }

  public void dispose() {
    myModel.removeFilterListener(this);
    if (myReaderThread != null && myReaderThread.myReader != null) {
      myReaderThread.stopRunning();
      try {
        myReaderThread.myReader.close();
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      myReaderThread.myReader = null;
      myReaderThread = null;
    }
    if (myConsole != null) {
      Disposer.dispose(myConsole);
      myConsole = null;
    }
    if (myFilter != null) {
      myFilter.dispose();
      myFilter = null;
    }
    myOriginalDocument = null;
  }

  private void stopRunning() {
    if (myReaderThread != null && !isActive()) {
      myReaderThread.stopRunning();
    }
  }

  protected synchronized void addMessage(final String text) {
    if (text == null) return;
    if (myContentPreprocessor != null) {
      final java.util.List<LogFragment> fragments = myContentPreprocessor.parseLogLine(text + "\n");
      myOriginalDocument = getOriginalDocument();
      for (LogFragment fragment : fragments) {
        myProcessHandler.notifyTextAvailable(fragment.getText(), fragment.getOutputType());
        if (myOriginalDocument != null) {
          myOriginalDocument.append(fragment.getText());
        }
      }
    }
    else {
      if (myModel.isApplicable(text)) {
        Key key = myModel.processLine(text);
        if (key != null) {
          myProcessHandler.notifyTextAvailable(text + "\n", key);
        }
      }
      myOriginalDocument = getOriginalDocument();
      if (myOriginalDocument != null) {
        myOriginalDocument.append(text).append("\n");
      }
    }
  }

  public void attachStopLogConsoleTrackingListener(final ProcessHandler process) {
    if (process != null) {
      final ProcessAdapter stopListener = new ProcessAdapter() {
        public void processTerminated(final ProcessEvent event) {
          process.removeProcessListener(this);
          stopRunning();
        }
      };
      process.addProcessListener(stopListener);
    }
  }

  private StringBuffer getOriginalDocument() {
    if (myOriginalDocument == null) {
      final Editor editor = getEditor();
      if (editor != null) {
        myOriginalDocument = new StringBuffer(editor.getDocument().getText());
      }
    }
    return myOriginalDocument;
  }

  @Nullable
  private Editor getEditor() {
    return myConsole != null ? PlatformDataKeys.EDITOR.getData((DataProvider) myConsole) : null;
  }

  private synchronized void filterConsoleOutput(Condition<String> isApplicable) {
    myOriginalDocument = getOriginalDocument();
    if (myOriginalDocument != null) {
      final Editor editor = getEditor();
      LOG.assertTrue(editor != null);
      final Document document = editor.getDocument();
      final int caretOffset = editor.getCaretModel().getOffset();
      if (caretOffset > -1) {
        int line = document.getLineNumber(caretOffset);
        if (line > -1 && line < document.getLineCount()) {
          final int startOffset = document.getLineStartOffset(line);
          myLineUnderSelection = document.getText().substring(startOffset, document.getLineEndOffset(line));
          myLineOffset = caretOffset - startOffset;
        }
      }
      myConsole.clear();
      final String[] lines = myOriginalDocument.toString().split("\n");
      int offset = 0;
      boolean caretPositioned = false;
      for (String line : lines) {
        if (printMessageToConsole(line, isApplicable)) {
          if (!caretPositioned) {
            if (Comparing.strEqual(myLineUnderSelection, line)) {
              caretPositioned = true;
              offset += myLineOffset != -1 ? myLineOffset : 0;
            }
            else {
              offset += line.length() + 1;
            }
          }
        }
      }
      myConsole.scrollTo(offset);
    }
  }

  private boolean printMessageToConsole(String line, Condition<String> isApplicable) {
    if (myContentPreprocessor != null) {
      List<LogFragment> fragments = myContentPreprocessor.parseLogLine(line + '\n');
      for (LogFragment fragment : fragments) {
        ConsoleViewContentType consoleViewType = ConsoleViewContentType.getConsoleViewType(fragment.getOutputType());
        if (consoleViewType != null) {
          myConsole.print(fragment.getText(), consoleViewType);
        }
      }
    }
    else if (isApplicable.value(line)) {
      Key key = myModel.processLine(line);
      if (key != null) {
        ConsoleViewContentType type = ConsoleViewContentType.getConsoleViewType(key);
        if (type != null) {
          myConsole.print(line + "\n", type);
        }
      }
    }
    return true;
  }

  public ActionGroup getToolbarActions() {
    return getOrCreateActions();
  }

  public String getToolbarPlace() {
    return ActionPlaces.UNKNOWN;
  }

  public JComponent getToolbarContextComponent() {
    return myConsole.getComponent();
  }

  public JComponent getPreferredFocusableComponent() {
    return myConsole.getPreferredFocusableComponent();
  }

  public String getTitle() {
    return myTitle;
  }

  public synchronized void clear() {
    myConsole.clear();
    myOriginalDocument = null;
  }

  public JComponent getSearchComponent() {
    List<? extends LogFilter> filters = myModel.getLogFilters();
    myLogFilterCombo.setModel(new DefaultComboBoxModel(filters.toArray(new LogFilter[filters.size()])));
    for (LogFilter filter : filters) {
      if (myModel.isFilterSelected(filter)) {
        myLogFilterCombo.setSelectedItem(filter);
        break;
      }
    }
    myLogFilterCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final LogFilter filter = (LogFilter)myLogFilterCombo.getSelectedItem();
        myModel.selectFilter(filter);
      }
    });
    myTextFilterWrapper.removeAll();
    myTextFilterWrapper.add(myFilter);
    return mySearchComponent;
  }

  public boolean isContentBuiltIn() {
    return myBuildInActions;
  }

  public void writeToConsole(String text, Key outputType) {
    myProcessHandler.notifyTextAvailable(text, outputType);
  }

  private static class LightProcessHandler extends ProcessHandler {
    protected void destroyProcessImpl() {
      throw new UnsupportedOperationException();
    }

    protected void detachProcessImpl() {
      throw new UnsupportedOperationException();
    }

    public boolean detachIsDefault() {
      return false;
    }

    @Nullable
    public OutputStream getProcessInput() {
      return null;
    }
  }

  protected class ReaderThread implements Runnable {
    private BufferedReader myReader;
    private boolean myRunning = false;

    public ReaderThread(@Nullable Reader reader) {
      myReader = reader != null ? new BufferedReader(reader) : null;
    }

    public void run() {
      if (myReader == null) return;
      while (myRunning) {
        try {
          int i = 0;
          while (i++ < 100) {
            if (myRunning && myReader != null && myReader.ready()) {
              addMessage(myReader.readLine());
            }
            else {
              break;
            }
          }
          synchronized (this) {
            wait(100);
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
        catch (InterruptedException e) {
          Disposer.dispose(LogConsoleBase.this);
        }
      }
    }

    public void startRunning() {
      myRunning = true;
    }

    public void stopRunning() {
      myRunning = false;
      synchronized (this) {
        notifyAll();
      }
    }
  }
}
