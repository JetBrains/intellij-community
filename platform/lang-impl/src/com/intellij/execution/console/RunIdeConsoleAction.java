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
import com.intellij.ide.script.IdeScriptBindings;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.script.IdeScriptEngine;
import org.jetbrains.ide.script.IdeScriptEngineManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * @author gregsh
 */
public class RunIdeConsoleAction extends DumbAwareAction {
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
      ContainerUtil.map(languages, (NotNullFunction<String, AnAction>)language -> new DumbAwareAction(language) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e1) {
          runConsole(e1, language);
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
    String command = getCommandText(project, editor);
    if (StringUtil.isEmptyOrSpaces(command)) return;
    String profile = getProfileText(file);
    RunContentDescriptor descriptor = getConsoleView(project, file);
    ConsoleViewImpl consoleView = (ConsoleViewImpl)descriptor.getExecutionConsole();

    prepareEngine(project, engine, descriptor);
    try {
      long ts = System.currentTimeMillis();
      //myHistoryController.getModel().addToHistory(command);
      consoleView.print("> " + command, ConsoleViewContentType.USER_INPUT);
      consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      String script = profile == null ? command : profile + "\n" + command;
      Object o = engine.eval(script);
      String prefix = "["+(StringUtil.formatDuration(System.currentTimeMillis() - ts))+"]";
      consoleView.print(prefix + "=> " + o, ConsoleViewContentType.NORMAL_OUTPUT);
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

  private static void prepareEngine(@NotNull Project project, @NotNull IdeScriptEngine engine, @NotNull RunContentDescriptor descriptor) {
    IdeScriptBindings.ensureIdeIsBound(project, engine);
    ensureOutputIsRedirected(engine, descriptor);
  }

  @Nullable
  private static String getProfileText(@NotNull VirtualFile file) {
    try {
      VirtualFile folder = file.getParent();
      VirtualFile profileChild = folder == null ? null : folder.findChild(".profile." + file.getExtension());
      return profileChild == null ? null : StringUtil.nullize(VfsUtilCore.loadText(profileChild));
    }
    catch (IOException ignored) {
    }
    return null;
  }

  @NotNull
  private static String getCommandText(@NotNull Project project, @NotNull Editor editor) {
    TextRange selectedRange = EditorUtil.getSelectionInAnyMode(editor);
    Document document = editor.getDocument();
    if (selectedRange.isEmpty()) {
      int line = document.getLineNumber(selectedRange.getStartOffset());
      selectedRange = TextRange.create(document.getLineStartOffset(line), document.getLineEndOffset(line));

      // try detect a non-trivial composite PSI element if there's a PSI file
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null && file.getFirstChild() != null && file.getFirstChild() != file.getLastChild()) {
        PsiElement e1 = file.findElementAt(selectedRange.getStartOffset());
        PsiElement e2 = file.findElementAt(selectedRange.getEndOffset());
        while (e1 != e2 && (e1 instanceof PsiWhiteSpace || e1 != null && StringUtil.isEmptyOrSpaces(e1.getText()))) {
          e1 = ObjectUtils.chooseNotNull(e1.getNextSibling(), PsiTreeUtil.getDeepestFirst(e1.getParent()));
        }
        while (e1 != e2 && (e2 instanceof PsiWhiteSpace || e2 != null && StringUtil.isEmptyOrSpaces(e2.getText()))) {
          e2 = ObjectUtils.chooseNotNull(e2.getPrevSibling(), PsiTreeUtil.getDeepestLast(e2.getParent()));
        }
        if (e1 instanceof LeafPsiElement) e1 = e1.getParent();
        if (e2 instanceof LeafPsiElement) e2 = e2.getParent();
        PsiElement parent = e1 == null ? e2 : e2 == null ? e1 : PsiTreeUtil.findCommonParent(e1, e2);
        if (parent != null && parent != file) {
          selectedRange = parent.getTextRange();
        }
      }
    }
    return document.getText(selectedRange);
  }

  private static void selectContent(RunContentDescriptor descriptor) {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ConsoleViewImpl consoleView = ObjectUtils.assertNotNull((ConsoleViewImpl)descriptor.getExecutionConsole());
    ExecutionManager.getInstance(consoleView.getProject()).getContentManager().toFrontRunContent(executor, descriptor);
  }

  @NotNull
  private static RunContentDescriptor getConsoleView(@NotNull Project project, @NotNull VirtualFile file) {
    PsiFile psiFile = ObjectUtils.assertNotNull(PsiManager.getInstance(project).findFile(file));
    WeakReference<RunContentDescriptor> ref = psiFile.getCopyableUserData(DESCRIPTOR_KEY);
    RunContentDescriptor descriptor = ref == null ? null : ref.get();
    if (descriptor == null || descriptor.getExecutionConsole() == null) {
      descriptor = createConsoleView(project, psiFile);
      psiFile.putCopyableUserData(DESCRIPTOR_KEY, new WeakReference<>(descriptor));
    }
    return descriptor;
  }

  @NotNull
  private static RunContentDescriptor createConsoleView(@NotNull Project project, @NotNull PsiFile psiFile) {
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

    return descriptor;
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
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      String extension = virtualFile.getExtension();
      if (extension != null && (engine == null || !engine.getFileExtensions().contains(extension))) {
        engine = IdeScriptEngineManager.getInstance().getEngineForFileExtension(extension, null);
      }
      if (engine == null) {
        LOG.warn("Script engine not found for: " + virtualFile.getName());
      }
      else {
        executeQuery(project, virtualFile, editor, engine);
      }
    }
  }

  private static void ensureOutputIsRedirected(@NotNull IdeScriptEngine engine, @NotNull RunContentDescriptor descriptor) {
    ConsoleWriter stdOutWriter = ObjectUtils.tryCast(engine.getStdOut(), ConsoleWriter.class);
    ConsoleWriter stdErrWriter = ObjectUtils.tryCast(engine.getStdErr(), ConsoleWriter.class);
    if (stdOutWriter != null && stdOutWriter.getDescriptor() == descriptor &&
        stdErrWriter != null && stdErrWriter.getDescriptor() == descriptor) {
      return;
    }

    WeakReference<RunContentDescriptor> ref = new WeakReference<>(descriptor);
    engine.setStdOut(new ConsoleWriter(ref, ConsoleViewContentType.NORMAL_OUTPUT));
    engine.setStdErr(new ConsoleWriter(ref, ConsoleViewContentType.ERROR_OUTPUT));
  }

  private static class ConsoleWriter extends Writer {
    private final WeakReference<RunContentDescriptor> myDescriptor;
    private final ConsoleViewContentType myOutputType;

    private ConsoleWriter(@NotNull WeakReference<RunContentDescriptor> descriptor, @NotNull ConsoleViewContentType outputType) {
      myDescriptor = descriptor;
      myOutputType = outputType;
    }

    @Nullable
    public RunContentDescriptor getDescriptor() {
      return myDescriptor.get();
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
