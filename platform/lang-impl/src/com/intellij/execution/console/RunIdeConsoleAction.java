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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.script.IdeScriptEngine;
import org.jetbrains.ide.script.IdeScriptEngineManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

/**
 * @author gregsh
 */
public class RunIdeConsoleAction extends DumbAwareAction {

  private static final String IDE = "IDE";
  private static final String DEFAULT_FILE_NAME = "ide-scripting";

  private static final Key<WeakReference<RunContentDescriptor>> DESCRIPTOR_KEY = Key.create("DESCRIPTOR_KEY");
  private static final Logger LOG = Logger.getInstance(RunIdeConsoleAction.class);

  @Override
  public void update(AnActionEvent e) {
    IdeScriptEngineManager manager = IdeScriptEngineManager.getInstance();
    e.getPresentation().setVisible(e.getProject() != null);
    e.getPresentation().setEnabled(manager.isInitialized() && !manager.getLanguages().isEmpty());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    List<String> languages = IdeScriptEngineManager.getInstance().getLanguages();
    if (languages.size() == 1) {
      runConsole(e, languages.iterator().next());
      return;
    }

    DefaultActionGroup actions = new DefaultActionGroup(
      ContainerUtil.map(languages, new NotNullFunction<String, AnAction>() {
        @NotNull
        @Override
        public AnAction fun(final String language) {
          return new AnAction(language) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              runConsole(e, language);
            }
          };
        }
      })
    );
    JBPopupFactory.getInstance().createActionGroupPopup("Script Engine", actions, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, false).
      showInBestPositionFor(e.getDataContext());
  }

  protected void runConsole(@NotNull AnActionEvent e, @NotNull String language) {
    Project project = e.getProject();
    if (project == null) return;

    List<String> extensions = IdeScriptEngineManager.getInstance().getFileExtensions(language);
    try {
      String pathName = PathUtil.makeFileName(DEFAULT_FILE_NAME, ContainerUtil.getFirstItem(extensions));
      VirtualFile virtualFile = IdeConsoleRootType.getInstance().findFile(project, pathName, ScratchFileService.Option.create_if_missing);
      if (virtualFile != null) {
        FileEditorManager.getInstance(project).openFile(virtualFile, true);
      }
    }
    catch (IOException ex) {
      LOG.error(ex);
    }
  }

  public static void configureConsole(@NotNull VirtualFile file, @NotNull FileEditorManager source) {
    MyRunAction runAction = new MyRunAction();
    for (FileEditor fileEditor : source.getEditors(file)) {
      if (!(fileEditor instanceof TextEditor)) continue;
      Editor editor = ((TextEditor)fileEditor).getEditor();
      runAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, editor.getComponent());
    }
  }

  private static void executeQuery(@NotNull Project project,
                                   @NotNull VirtualFile file,
                                   @NotNull Editor editor,
                                   @NotNull IdeScriptEngine engine) {
    TextRange selectedRange = EditorUtil.getSelectionInAnyMode(editor);
    Document document = editor.getDocument();
    if (selectedRange.getLength() == 0) {
      int line = document.getLineNumber(selectedRange.getStartOffset());
      selectedRange = TextRange.create(document.getLineStartOffset(line), document.getLineEndOffset(line));
    }
    VirtualFile profileChild = file.getParent().findChild(".profile." + file.getExtension());
    String profile = null;
    try {
      profile = profileChild == null ? "" : VfsUtilCore.loadText(profileChild);
    }
    catch (IOException ignored) {
    }
    String command = document.getText(selectedRange);
    RunContentDescriptor descriptor = getConsoleView(project, file, engine);
    ConsoleViewImpl consoleView = (ConsoleViewImpl)descriptor.getExecutionConsole();
    try {
      //myHistoryController.getModel().addToHistory(command);
      consoleView.print("> " + command, ConsoleViewContentType.USER_INPUT);
      consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      Object o = engine.eval(profile == null ? command : profile + "\n" + command);
      consoleView.print("=> " + o, ConsoleViewContentType.NORMAL_OUTPUT);
      consoleView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
    }
    catch (Throwable e) {
      //noinspection ThrowableResultOfMethodCallIgnored
      Throwable ex = ExceptionUtil.getRootCause(e);
      consoleView.print(ex.getClass().getSimpleName() + ": " + ex.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
      consoleView.print("\n", ConsoleViewContentType.ERROR_OUTPUT);
    }
    selectContent(descriptor);
  }

  private static void selectContent(RunContentDescriptor descriptor) {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ConsoleViewImpl consoleView = ObjectUtils.assertNotNull((ConsoleViewImpl)descriptor.getExecutionConsole());
    ExecutionManager.getInstance(consoleView.getProject()).getContentManager().toFrontRunContent(executor, descriptor);
  }

  @NotNull
  private static RunContentDescriptor getConsoleView(@NotNull Project project, @NotNull VirtualFile file, @NotNull IdeScriptEngine engine) {
    PsiFile psiFile = ObjectUtils.assertNotNull(PsiManager.getInstance(project).findFile(file));

    WeakReference<RunContentDescriptor> ref = psiFile.getCopyableUserData(DESCRIPTOR_KEY);
    RunContentDescriptor descriptor = ref == null ? null : ref.get();
    if (descriptor == null || descriptor.getExecutionConsole() == null) {
      descriptor = createConsoleView(project, engine, psiFile);
      psiFile.putCopyableUserData(DESCRIPTOR_KEY, new WeakReference<RunContentDescriptor>(descriptor));
    }
    ensureIdeBound(project, engine);

    return descriptor;
  }

  @NotNull
  private static RunContentDescriptor createConsoleView(@NotNull Project project, @NotNull IdeScriptEngine engine, @NotNull PsiFile psiFile) {
    ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    JComponent panel = new JPanel(new BorderLayout());
    panel.add(consoleView.getComponent(), BorderLayout.CENTER);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);
    toolbar.setTargetComponent(consoleView.getComponent());
    panel.add(toolbar.getComponent(), BorderLayout.WEST);

    final RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, null, panel, psiFile.getName()) {
      @Override
      public boolean isContentReuseProhibited() {
        return true;
      }
    };
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    toolbarActions.addAll(consoleView.createConsoleActions());
    toolbarActions.add(new CloseAction(executor, descriptor, project));
    ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);

    new ScriptEngineOutputHandler(descriptor).installOn(engine);

    return descriptor;
  }

  private static void ensureIdeBound(@NotNull Project project, @NotNull IdeScriptEngine engine) {
    Object oldIdeBinding = engine.getBinding(IDE);
    if (oldIdeBinding == null) {
      engine.setBinding(IDE, new IDE(project, engine));
    }
  }

  private static class MyRunAction extends DumbAwareAction {

    private IdeScriptEngine engine;

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

      String extension = virtualFile.getExtension();
      if (extension != null && (engine == null || !engine.getFileExtensions().contains(extension))) {
        engine = IdeScriptEngineManager.getInstance().getEngineForFileExtension(extension);
      }
      if (engine == null) {
        LOG.warn("Script engine not found for: " + virtualFile.getName());
      }
      else {
        executeQuery(project, virtualFile, editor, engine);
      }
    }
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static class ScriptEngineOutputHandler {
    private WeakReference<RunContentDescriptor> myDescriptor;

    private ConsoleWriter myStdOutWriter = new ConsoleWriter(ConsoleViewContentType.NORMAL_OUTPUT);
    private ConsoleWriter myStdErrWriter = new ConsoleWriter(ConsoleViewContentType.ERROR_OUTPUT);

    public ScriptEngineOutputHandler(@NotNull RunContentDescriptor descriptor) {
      myDescriptor = new WeakReference<RunContentDescriptor>(descriptor);
    }

    public void installOn(@NotNull IdeScriptEngine engine) {
      engine.setStdOut(myStdOutWriter);
      engine.setStdErr(myStdErrWriter);
    }

    private class ConsoleWriter extends Writer {
      private final ConsoleViewContentType myOutputType;

      private ConsoleWriter(ConsoleViewContentType outputType) {
        myOutputType = outputType;
      }

      @Override
      public void write(char[] cbuf, int off, int len) throws IOException {
        RunContentDescriptor descriptor = myDescriptor.get();
        ConsoleViewImpl console = ObjectUtils.tryCast(descriptor != null ? descriptor.getExecutionConsole() : null, ConsoleViewImpl.class);
        if (console == null) {
          //TODO ignore ?
          throw new IOException("The console is not available.");
        }
        console.print(new String(cbuf, off, len), myOutputType);
      }

      @Override
      public void flush() throws IOException {
      }

      @Override
      public void close() throws IOException {
      }
    }
  }

  public static class IDE {
    public final Application application = ApplicationManager.getApplication();
    public final Project project;

    private final Map<Object, Object> bindings = ContainerUtil.newConcurrentMap();
    private final IdeScriptEngine myEngine;

    IDE(Project project, IdeScriptEngine engine) {
      this.project = project;
      myEngine = engine;
    }

    public void print(Object o) {
      print(myEngine.getStdOut(), o);
    }

    public void error(Object o) {
      print(myEngine.getStdErr(), o);
    }

    public Object put(Object key, Object value) {
      return value == null ? bindings.remove(key) : bindings.put(key, value);
    }

    public Object get(Object key) {
      return bindings.get(key);
    }

    private static void print(Writer writer, Object o) {
      try {
        writer.append(String.valueOf(o));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
