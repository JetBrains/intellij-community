/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

/**
 * @author gregsh
 */
public class RunIdeConsoleAction extends DumbAwareAction {

  private static final String DEFAULT_FILE_NAME = "ide-scripting";
  private static final Key<WeakReference<ConsoleView>> CONSOLE_VIEW_KEY = Key.create("CONSOLE_VIEW_KEY");
  private static final Key<ConsoleHistoryController> HISTORY_CONTROLLER_KEY = Key.create("HISTORY_CONTROLLER_KEY");

  static class Engines {
    static final Map<String, ScriptEngineFactory> ourEngines = ContainerUtil.newLinkedHashMap();
    static  {
      for (ScriptEngineFactory factory : new ScriptEngineManager().getEngineFactories()) {
        ourEngines.put(factory.getLanguageName(), factory);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = !Engines.ourEngines.isEmpty() && e.getProject() != null;
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (Engines.ourEngines.size() == 1) {
      runConsole(e, Engines.ourEngines.values().iterator().next());
    }
    else {
      DefaultActionGroup actions = new DefaultActionGroup(
        ContainerUtil.map(Engines.ourEngines.values(), new NotNullFunction<ScriptEngineFactory, AnAction>() {
          @NotNull
          @Override
          public AnAction fun(final ScriptEngineFactory engine) {
            return new AnAction(engine.getLanguageName()) {
              @Override
              public void actionPerformed(@NotNull AnActionEvent e) {
                runConsole(e, engine);
              }
            };
          }
        })
      );
      JBPopupFactory.getInstance().createActionGroupPopup("Script Engine", actions, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, false).
        showInBestPositionFor(e.getDataContext());
    }
  }

  protected void runConsole(@NotNull AnActionEvent e, @NotNull ScriptEngineFactory engine) {
    Project project = e.getProject();
    if (project == null) return;
    List<String> extensions = engine.getExtensions();
    try {
      String pathName = PathUtil.makeFileName(DEFAULT_FILE_NAME, ContainerUtil.getFirstItem(extensions));
      VirtualFile virtualFile = IdeConsoleRootType.getInstance().findFile(project, pathName, ScratchFileService.Option.create_if_missing);
      if (virtualFile != null) {
        FileEditorManager.getInstance(project).openFile(virtualFile, true);
      }
    }
    catch (IOException ignored) {
    }
  }

  public static void configureConsole(@NotNull VirtualFile file, @NotNull FileEditorManager source) {
    ScriptEngine engine = findScriptEngine(file);
    if (engine == null) return;
    //new ConsoleHistoryController(IdeConsoleRootType.getInstance(), engine.getFactory().getLanguageName())
    MyRunAction runAction = new MyRunAction(engine);
    for (FileEditor fileEditor : source.getEditors(file)) {
      if (!(fileEditor instanceof TextEditor)) continue;
      Editor editor = ((TextEditor)fileEditor).getEditor();
      runAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, editor.getComponent());
    }
  }

  @Nullable
  private static ScriptEngine findScriptEngine(@NotNull VirtualFile file) {
    for (ScriptEngineFactory factory : Engines.ourEngines.values()) {
      if (factory.getExtensions().contains(file.getExtension())) {
        return factory.getScriptEngine();
      }
    }
    return null;
  }

  private static void executeQuery(@NotNull Project project,
                                   @NotNull VirtualFile file,
                                   @NotNull Editor editor,
                                   @NotNull ScriptEngine engine) {

    TextRange selectedRange = EditorUtil.getSelectionInAnyMode(editor);
    Document document = editor.getDocument();
    if (selectedRange.getLength() == 0) {
      int line = document.getLineNumber(selectedRange.getStartOffset());
      selectedRange = TextRange.create(document.getLineStartOffset(line), document.getLineEndOffset(line));
    }
    String command = document.getText(selectedRange);
    final ConsoleView consoleView = getConsoleView(project, file, engine);
    try {
      class IDE {
        public void print(String s) {
          consoleView.print(s + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
        }
        public void error(String s) {
          consoleView.print(s + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
      }

      // todo
      //myHistoryController.getModel().addToHistory(command);
      consoleView.print("> " + command, ConsoleViewContentType.USER_INPUT);
      consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      engine.getBindings(ScriptContext.ENGINE_SCOPE).put("IDE", new IDE());
      Object o = engine.eval(command);
      consoleView.print("=> " + o, ConsoleViewContentType.NORMAL_OUTPUT);
      consoleView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
      consoleView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
    }
    catch (Exception e) {
      consoleView.print(e.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
      //consoleView.print(ExceptionUtil.getThrowableText(e), ConsoleViewContentType.ERROR_OUTPUT);
      consoleView.print("\n", ConsoleViewContentType.ERROR_OUTPUT);
    }
    ((ConsoleViewImpl)consoleView).scrollToEnd();
  }

  @NotNull
  private static ConsoleView getConsoleView(@NotNull Project project, @NotNull VirtualFile file, @NotNull ScriptEngine engine) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    WeakReference<ConsoleView> ref = psiFile == null ? null : psiFile.getCopyableUserData(CONSOLE_VIEW_KEY);
    ConsoleView existing = ref == null ? null : ref.get();
    if (existing != null && !Disposer.isDisposed(existing)) return existing;
    ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    if (psiFile != null) psiFile.putCopyableUserData(CONSOLE_VIEW_KEY, new WeakReference<ConsoleView>(consoleView));
    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    JComponent consoleComponent = new JPanel(new BorderLayout());
    consoleComponent.add(consoleView.getComponent(), BorderLayout.CENTER);
    consoleComponent.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false).getComponent(),
                         BorderLayout.WEST);
    final RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, null, consoleComponent, file.getName()) {
      @Override
      public boolean isContentReuseProhibited() {
        return true;
      }
    };

    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    //toolbarActions.add(new DumbAwareAction("Rerun", null, AllIcons.Actions.Rerun) {
    //  @Override
    //  public void update(@NotNull AnActionEvent e) {
    //    ProgressIndicator indicator = indicatorRef.get();
    //    e.getPresentation().setEnabled(file.isValid() && (indicator == null || !indicator.isRunning()));
    //  }
    //
    //  @Override
    //  public void actionPerformed(@NotNull AnActionEvent e) {
    //    consoleView.clear();
    //    rerunRunnable.run();
    //  }
    //});
    toolbarActions.add(new CloseAction(executor, descriptor, project));
    for (AnAction action : consoleView.createConsoleActions()) {
      toolbarActions.add(action);
    }
    ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
    return consoleView;
  }


  private static class MyRunAction extends DumbAwareAction{
    private final ScriptEngine myEngine;

    public MyRunAction(ScriptEngine engine) {
      myEngine = engine;
    }

    @Override
    public void update(AnActionEvent e) {
      Project project = e.getProject();
      Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
      VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
      e.getPresentation().setEnabledAndVisible(project != null && editor != null && virtualFile != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Project project = e.getProject();
      Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
      VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
      if (project == null || editor == null || virtualFile == null) return;
      executeQuery(project, virtualFile, editor, myEngine);
    }
  }
}
