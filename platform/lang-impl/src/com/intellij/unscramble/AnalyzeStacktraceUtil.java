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

package com.intellij.unscramble;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * @author yole
 */
public class AnalyzeStacktraceUtil {
  public static ExtensionPointName<Filter> EP_NAME = ExtensionPointName.create("com.intellij.analyzeStacktraceFilter");

  private AnalyzeStacktraceUtil() {
  }

  public static void printStacktrace(final ConsoleView consoleView, final String unscrambledTrace) {
    consoleView.clear();
    consoleView.print(unscrambledTrace+"\n", ConsoleViewContentType.ERROR_OUTPUT);
    consoleView.performWhenNoDeferredOutput(
      new Runnable() {
        public void run() {
          consoleView.scrollTo(0);
        }
      }
    );
  }

  @Nullable
  public static String getTextInClipboard() {
    final Transferable contents = CopyPasteManagerEx.getInstance().getContents();
    if (contents != null) {
      try {
        return (String)contents.getTransferData(DataFlavor.stringFlavor);
      } catch (Exception e) {
        //no luck
      }
    }
    return null;
  }

  public interface ConsoleFactory {
    JComponent createConsoleComponent(ConsoleView consoleView, DefaultActionGroup toolbarActions);
  }

  public static ConsoleView addConsole(Project project, @Nullable ConsoleFactory consoleFactory, final String tabTitle) {
    final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    for(Filter filter: Extensions.getExtensions(EP_NAME, project)) {
      builder.addFilter(filter);
    }

    final ConsoleView consoleView = builder.getConsole();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    JComponent consoleComponent = consoleFactory != null
                                  ? consoleFactory.createConsoleComponent(consoleView, toolbarActions)
                                  : new MyConsolePanel(consoleView, toolbarActions);
    final RunContentDescriptor descriptor =
      new RunContentDescriptor(consoleView, null, consoleComponent, tabTitle) {
      public boolean isContentReuseProhibited() {
        return true;
      }
    };

    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    toolbarActions.add(new CloseAction(executor, descriptor, project));
    for (AnAction action: consoleView.createConsoleActions()) {
      toolbarActions.add(action);
    }
    ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
    return consoleView;
  }

  private static final class MyConsolePanel extends JPanel {
    public MyConsolePanel(ExecutionConsole consoleView, ActionGroup toolbarActions) {
      super(new BorderLayout());
      JPanel toolbarPanel = new JPanel(new BorderLayout());
      toolbarPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions,false).getComponent());
      add(toolbarPanel, BorderLayout.WEST);
      add(consoleView.getComponent(), BorderLayout.CENTER);
    }
  }

  public static StacktraceEditorPanel createEditorPanel(Project project, @NotNull Disposable parentDisposable) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument("");
    Editor editor = editorFactory.createEditor(document);
    EditorSettings settings = editor.getSettings();
    settings.setFoldingOutlineShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setLineNumbersShown(false);
    settings.setRightMarginShown(false);

    StacktraceEditorPanel editorPanel = new StacktraceEditorPanel(project, editor);
    editorPanel.setPreferredSize(new Dimension(600, 400));
    Disposer.register(parentDisposable, editorPanel);
    return editorPanel;
  }

  public static final class StacktraceEditorPanel extends JPanel implements DataProvider, Disposable {
    private final Project myProject;
    private final Editor myEditor;

    public StacktraceEditorPanel(Project project, Editor editor) {
      super(new BorderLayout());
      myProject = project;
      myEditor = editor;
      add(myEditor.getComponent());
    }

    public Object getData(String dataId) {
      if (PlatformDataKeys.EDITOR.is(dataId)) {
        return myEditor;
      }
      return null;
    }

    public Editor getEditor() {
      return myEditor;
    }

    public final void setText(@NotNull final String text) {
      Runnable runnable = new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              final Document document = myEditor.getDocument();
              document.replaceString(0, document.getTextLength(), StringUtil.convertLineSeparators(text));
            }
          });
        }
      };
      CommandProcessor.getInstance().executeCommand(myProject, runnable, "", this);
    }

    public void pasteTextFromClipboard() {
      String text = getTextInClipboard();
      if (text != null) {
        setText(text);
      }

    }

    public void dispose() {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }

    public String getText() {
      return myEditor.getDocument().getText();
    }

    public JComponent getEditorComponent() {
      return myEditor.getContentComponent();
    }
  }
}
