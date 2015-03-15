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
import com.intellij.openapi.util.text.StringUtil;
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
import org.jetbrains.ide.PooledThreadExecutor;

import javax.script.*;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author gregsh
 */
public class RunIdeConsoleAction extends DumbAwareAction {

  private static final String IDE = "IDE";
  private static final String DEFAULT_FILE_NAME = "ide-scripting";

  private static final Key<WeakReference<RunContentDescriptor>> DESCRIPTOR_KEY = Key.create("DESCRIPTOR_KEY");
  private static final Logger LOG = Logger.getInstance(RunIdeConsoleAction.class);

  static class Engines {
    static final Map<String, ScriptEngineFactory> ourEngines = ContainerUtil.newConcurrentMap();
    static final Future<List<ScriptEngineFactory>> ourFuture;
    static {
      ourFuture = PooledThreadExecutor.INSTANCE.submit(
        new Callable<List<ScriptEngineFactory>>() {
          @Override
          public List<ScriptEngineFactory> call() throws Exception {
            try {
              LOG.info("Loading javax.script.* engines...");
              List<ScriptEngineFactory> factories = new ScriptEngineManager().getEngineFactories();
              for (ScriptEngineFactory factory : factories) {
                List<String> extensions = factory.getExtensions();
                LOG.info(factory.getClass().getName() + ": *." + StringUtil.join(extensions, "/") + ": " +
                         factory.getLanguageName() + "/" + factory.getLanguageVersion() +
                         " (" + factory.getEngineName() + "/" + factory.getEngineVersion() + ")");
                for (String ext : extensions) {
                  ourEngines.put(ext, factory);
                }
              }
              return factories;
            }
            catch (Throwable e) {
              LOG.error(e);
            }
            return Collections.emptyList();
          }
        });
    }

    public static void prepareEngines(boolean force) {
      if (!force) return;
      try {
        ourFuture.get();
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    boolean hasEngines = !Engines.ourFuture.isDone() || !Engines.ourEngines.isEmpty();
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && hasEngines);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Engines.prepareEngines(true);
    if (Engines.ourEngines.size() == 1) {
      runConsole(e, Engines.ourEngines.values().iterator().next());
    }
    else {
      List<ScriptEngineFactory> engines = ContainerUtil.newArrayList(Engines.ourEngines.values());
      ContainerUtil.removeDuplicates(engines);
      DefaultActionGroup actions = new DefaultActionGroup(
        ContainerUtil.map(engines, new NotNullFunction<ScriptEngineFactory, AnAction>() {
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
    catch (IOException ex) {
      LOG.error(ex);
    }
  }

  public static void configureConsole(@NotNull VirtualFile file, @NotNull FileEditorManager source) {
    Engines.prepareEngines(false);
    //new ConsoleHistoryController(IdeConsoleRootType.getInstance(), engine.getFactory().getLanguageName())
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
                                   @NotNull ScriptEngine engine) {
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
    catch (Exception e) {
      //noinspection ThrowableResultOfMethodCallIgnored
      Throwable ex = ExceptionUtil.getRootCause(e);
      consoleView.print(ex.getClass().getSimpleName() + ": " + ex.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
      consoleView.print("\n", ConsoleViewContentType.ERROR_OUTPUT);
    }
    selectContent(descriptor);
  }

  private static void printInContent(RunContentDescriptor descriptor, Object o, ConsoleViewContentType contentType) {
    selectContent(descriptor);
    ConsoleViewImpl consoleView = (ConsoleViewImpl)descriptor.getExecutionConsole();
    consoleView.print(o + "\n", contentType);
  }

  private static void selectContent(RunContentDescriptor descriptor) {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ConsoleViewImpl consoleView = ObjectUtils.assertNotNull((ConsoleViewImpl)descriptor.getExecutionConsole());
    ExecutionManager.getInstance(consoleView.getProject()).getContentManager().toFrontRunContent(executor, descriptor);
  }

  @NotNull
  private static RunContentDescriptor getConsoleView(@NotNull Project project, @NotNull VirtualFile file, @NotNull ScriptEngine engine) {
    PsiFile psiFile = ObjectUtils.assertNotNull(PsiManager.getInstance(project).findFile(file));

    WeakReference<RunContentDescriptor> ref = psiFile.getCopyableUserData(DESCRIPTOR_KEY);
    RunContentDescriptor existing = ref == null ? null : ref.get();
    if (existing != null && existing.getExecutionConsole() != null) {
      return ensureIdeBound(project, existing, engine);
    }
    ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    JComponent panel = new JPanel(new BorderLayout());
    panel.add(consoleView.getComponent(), BorderLayout.CENTER);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);
    toolbar.setTargetComponent(consoleView.getComponent());
    panel.add(toolbar.getComponent(), BorderLayout.WEST);
    final RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, null, panel, file.getName()) {
      @Override
      public boolean isContentReuseProhibited() {
        return true;
      }
    };

    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    toolbarActions.addAll(consoleView.createConsoleActions());
    toolbarActions.add(new CloseAction(executor, descriptor, project));
    psiFile.putCopyableUserData(DESCRIPTOR_KEY, new WeakReference<RunContentDescriptor>(descriptor));
    ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
    return ensureIdeBound(project, descriptor, engine);
  }

  private static RunContentDescriptor ensureIdeBound(@NotNull Project project,
                                                     @NotNull RunContentDescriptor descriptor,
                                                     @NotNull ScriptEngine engine) {
    Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
    if (!bindings.containsKey(IDE)) {
      bindings.put(IDE, new IDE(project, descriptor));
    }
    return descriptor;
  }

  private static class MyRunAction extends DumbAwareAction {

    private ScriptEngine engine;

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
      Engines.prepareEngines(true);
      ScriptEngineFactory factory = Engines.ourEngines.get(virtualFile.getExtension());
      if (engine == null || !engine.getFactory().getClass().isInstance(factory)) {
        engine = factory == null ? null : factory.getScriptEngine();
      }
      if (engine == null) {
        LOG.warn("Script engine not found for: " + virtualFile.getName());
      }
      else {
        executeQuery(project, virtualFile, editor, engine);
      }
    }
  }

  public static class IDE {
    public final Application application = ApplicationManager.getApplication();
    public final Project project;

    private final Map<Object, Object> bindings = ContainerUtil.newConcurrentMap();
    private final WeakReference<RunContentDescriptor> descriptor;

    IDE(Project project, RunContentDescriptor descriptor) {
      this.project = project;
      this.descriptor = new WeakReference<RunContentDescriptor>(descriptor);
    }

    public void print(Object o) {
      printInContent(descriptor.get(), o, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    public void error(Object o) {
      printInContent(descriptor.get(), o, ConsoleViewContentType.ERROR_OUTPUT);
    }

    public Object put(Object key, Object value) {
      return value == null ? bindings.remove(key) : bindings.put(key, value);
    }

    public Object get(Object key) {
      return bindings.get(key);
    }
  }
}
