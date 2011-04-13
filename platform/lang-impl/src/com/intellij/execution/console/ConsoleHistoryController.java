/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.console;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.process.ConsoleHistoryModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actions.ContentChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.xml.XppReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author gregsh
 */
public class ConsoleHistoryController {

  private static final Logger LOG = Logger.getInstance("com.intellij.execution.console.ConsoleHistoryController");

  private final String myType;
  private final String myId;
  private final LanguageConsoleImpl myConsole;
  private final ConsoleHistoryModel myModel;
  private final AnAction myHistoryNext = new MyAction(true);
  private final AnAction myHistoryPrev = new MyAction(false);
  private final AnAction myBrowseHistory = new MyBrowseAction();
  private boolean myMultiline;
  private long myLastSaveStamp;


  public ConsoleHistoryController(@NotNull final String type,
                                  @Nullable final String persistenceId,
                                  @NotNull final LanguageConsoleImpl console,
                                  @NotNull final ConsoleHistoryModel model) {
    myType = type;
    myId = StringUtil.isEmpty(persistenceId)? console.getProject().getLocation() : persistenceId;
    myConsole = console;
    myModel = model;
  }

  public boolean isMultiline() {
    return myMultiline;
  }

  public ConsoleHistoryController setMultiline(boolean  multiline) {
    myMultiline = multiline;
    return this;
  }

  public ConsoleHistoryModel getModel() {
    return myModel;
  }

  public void install() {
    if (myId != null) {
      ApplicationManager.getApplication().getMessageBus().connect(myConsole).subscribe(
        ProjectEx.ProjectSaved.TOPIC, new ProjectEx.ProjectSaved() {
          @Override
          public void saved(@NotNull final Project project) {
            saveHistory();
          }
        });
      Disposer.register(myConsole, new Disposable() {
        @Override
        public void dispose() {
          saveHistory();
        }
      });
      loadHistory();
    }
    configureActions();
    myLastSaveStamp = myModel.getModificationCount();
  }

  private void configureActions() {
    EmptyAction.setupAction(myHistoryNext, "Console.History.Next", null);
    EmptyAction.setupAction(myHistoryPrev, "Console.History.Previous", null);
    EmptyAction.setupAction(myBrowseHistory, "Console.History.Browse", null);
    if (!myMultiline) {
      myHistoryNext.registerCustomShortcutSet(KeyEvent.VK_UP, 0, null);
      myHistoryPrev.registerCustomShortcutSet(KeyEvent.VK_DOWN, 0, null);
    }
    myHistoryNext.registerCustomShortcutSet(myHistoryNext.getShortcutSet(), myConsole.getCurrentEditor().getComponent());
    myHistoryPrev.registerCustomShortcutSet(myHistoryPrev.getShortcutSet(), myConsole.getCurrentEditor().getComponent());
    myBrowseHistory.registerCustomShortcutSet(myBrowseHistory.getShortcutSet(), myConsole.getCurrentEditor().getComponent());
  }

  private File getFile() {
    final StringBuilder sb = new StringBuilder().append(PathManager.getSystemPath()).append(File.separator).append("userHistory").
      append(File.separator).append(myType).append(Long.toHexString(StringHash.calc(myId))).append(".hist.xml");
    return new File(sb.toString());
  }


  private void loadHistory() {
    final File file = getFile();
    if (!file.exists()) return;
    HierarchicalStreamReader xmlReader = null;
    try {
      xmlReader = new XppReader(new FileReader(file));
      loadHistory(xmlReader);
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
    finally {
      if (xmlReader != null) {
        xmlReader.close();
      }
    }

  }

  private void saveHistory() {
    if (myLastSaveStamp == myModel.getModificationCount()) return;

    final File file = getFile();
    final File dir = file.getParentFile();
    if (!dir.exists() && !dir.mkdirs() || !dir.isDirectory()) {
      LOG.error("failed to create folder: "+dir.getAbsolutePath());
      return;
    }
    final File tmpFile = new File(dir, file.getName()+".tmp");
    FileOutputStream os = null;
    try {
      final XmlSerializer serializer = XmlPullParserFactory.newInstance().newSerializer();
      try {
        serializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-indentation", "  ");
      }
      catch (Exception e) {
        // not recognized
      }
      serializer.setOutput(new PrintWriter(os = new FileOutputStream(tmpFile)));
      saveHistory(serializer);
      file.delete();
      FileUtil.rename(tmpFile, file);
      myLastSaveStamp = myModel.getModificationCount();
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
    finally {
      try {
        os.close();
      }
      catch (Exception e) {
        // nothing
      }
      cleanupOldFiles(dir);
    }
  }

  private static void cleanupOldFiles(final File dir) {
    final long keep2weeks = 2 * 1000L * 60 * 60 * 24 * 7;
    final long curTime = System.currentTimeMillis();
    for (File file : dir.listFiles()) {
      if (file.isFile() && file.getName().endsWith(".hist.xml") && curTime - file.lastModified() > keep2weeks) {
        file.delete();
      }
    }
  }


  public AnAction getHistoryNext() {
    return myHistoryNext;
  }

  public AnAction getHistoryPrev() {
    return myHistoryPrev;
  }

  public AnAction getBrowseHistory() {
    return myBrowseHistory;
  }

  protected void actionTriggered(final String command) {
    final Editor editor = myConsole.getCurrentEditor();
    final Document document = editor.getDocument();
    new WriteCommandAction(myConsole.getProject(), myConsole.getFile()) {
      protected void run(final Result result) throws Throwable {
        document.setText(StringUtil.notNullize(command));
        editor.getCaretModel().moveToOffset(document.getTextLength());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }.execute();
  }


  private class MyAction extends AnAction {
    private boolean myNext;

    public MyAction(final boolean next) {
      myNext = next;
      getTemplatePresentation().setVisible(false);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      actionTriggered(myNext ? myModel.getHistoryNext() : myModel.getHistoryPrev());
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myModel.hasHistory(myNext) &&
                                     (myMultiline || canMoveInEditor(myNext)));
    }
  }

  private boolean canMoveInEditor(final boolean next) {
    final Editor consoleEditor = myConsole.getCurrentEditor();
    final Document document = consoleEditor.getDocument();
    final CaretModel caretModel = consoleEditor.getCaretModel();

    if (LookupManager.getActiveLookup(consoleEditor) != null) return false;

    if (next) {
      return document.getLineNumber(caretModel.getOffset()) == 0;
    }
    else {
      return document.getLineNumber(caretModel.getOffset()) == document.getLineCount() - 1 &&
             StringUtil.isEmptyOrSpaces(document.getText().substring(caretModel.getOffset()));
    }
  }


  private void loadHistory(final HierarchicalStreamReader in) {
    if (!in.getNodeName().equals("console-history")) return;
    final String id = in.getAttribute("id");
    if (!myId.equals(id)) return;
    final ArrayList<String> entries = new ArrayList<String>();
    while (in.hasMoreChildren()) {
      in.moveDown();
      if ("history-entry".equals(in.getNodeName())) {
        entries.add(in.getValue());
      }
      in.moveUp();
    }
    for (ListIterator<String> iterator = entries.listIterator(entries.size()); iterator.hasPrevious(); ) {
      final String entry = iterator.previous();
      myModel.addToHistory(entry);

    }
  }

  private void saveHistory(final XmlSerializer out) throws IOException {
    out.startDocument("UTF8", null);
    out.startTag(null, "console-history");
    out.attribute(null, "id", myId);
    for (String s : myModel.getHistory()) {
      out.startTag(null, "history-entry");
      out.text(s);
      out.endTag(null, "history-entry");
    }
    out.endTag(null, "console-history");
    out.endDocument();
  }

  private class MyBrowseAction extends AnAction {

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(myModel.getHistorySize() > 0);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final ContentChooser<String> chooser = new ContentChooser<String>(myConsole.getProject(), myConsole.getTitle(), true) {

        @Override
        protected void removeContentAt(final String content) {
          myModel.removeFromHistory(content);
        }

        @Override
        protected String getStringRepresentationFor(final String content) {
          return content;
        }

        @Override
        protected List<String> getContents() {
          return myModel.getHistory();
        }
      };
      chooser.show();
      if (chooser.isOK()) {
        actionTriggered(myModel.getHistory().get(chooser.getSelectedIndex()));
      }
    }
  }
}
