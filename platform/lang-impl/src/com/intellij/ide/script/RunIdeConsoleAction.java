// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.script;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.Writer;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author gregsh
 */
public final class RunIdeConsoleAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(RunIdeConsoleAction.class);

  private static final String DEFAULT_FILE_NAME = "ide-scripting";

  private static final Key<IdeScriptEngineManager.EngineInfo> SELECTED_ENGINE_INFO_KEY = Key.create("SELECTED_ENGINE_INFO_KEY");
  private static final Key<Trinity<IdeScriptEngine, IdeScriptEngineManager.EngineInfo, VirtualFile>> SCRIPT_ENGINE_KEY =
    Key.create("SCRIPT_ENGINE_KEY");

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    List<IdeScriptEngineManager.EngineInfo> infos = IdeScriptEngineManager.getInstance().getEngineInfos();
    chooseScriptEngineAndRun(e, infos, engineInfo -> runConsole(project, engineInfo));
  }

  static void chooseScriptEngineAndRun(@NotNull AnActionEvent e,
                                       @NotNull List<? extends IdeScriptEngineManager.EngineInfo> infos,
                                       @NotNull Consumer<? super IdeScriptEngineManager.EngineInfo> onChosen) {
    if (infos.size() == 1) {
      onChosen.consume(infos.iterator().next());
      return;
    }

    List<? extends AnAction> actions = JBIterable.from(infos).map(engineInfo -> {
      String lang = engineInfo.languageName;
      String eng = engineInfo.engineName;
      if (StringUtil.toLowerCase(lang).equals(lang)) lang = StringUtil.capitalize(lang);
      if (StringUtil.toLowerCase(eng).equals(eng)) eng = StringUtil.capitalize(eng);
      String name = lang + " (" + eng + ")";
      PluginDescriptor plugin = engineInfo.plugin;
      String description = LangBundle.message("action.engine.description", lang, eng,
                                              plugin == null ? "" : LangBundle.message("action.plugin.description") + plugin.getName());
      return new DumbAwareAction(name, description, null) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e1) {
          onChosen.consume(engineInfo);
        }
      };
    })
      .sort(Comparator.comparing(o -> o.getTemplatePresentation().getText()))
      .toList();
    DefaultActionGroup actionGroup = new DefaultActionGroup(actions);
    JBPopupFactory.getInstance().createActionGroupPopup(
      ExecutionBundle.message("popup.title.script.engine"), actionGroup, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, false)
      .showInBestPositionFor(e.getDataContext());
  }

  private static void runConsole(@NotNull Project project, @NotNull IdeScriptEngineManager.EngineInfo info) {
    List<String> extensions = info.fileExtensions;
    try {
      String pathName = PathUtil.makeFileName(DEFAULT_FILE_NAME, ContainerUtil.getFirstItem(extensions));
      VirtualFile virtualFile = IdeConsoleRootType.getInstance().findFile(project, pathName, ScratchFileService.Option.create_if_missing);
      if (virtualFile != null) {
        virtualFile.putUserData(SELECTED_ENGINE_INFO_KEY, info);
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
                                   @NotNull IdeScriptEngineManager.EngineInfo engineInfo) {
    String command = getCommandText(project, editor);
    if (StringUtil.isEmptyOrSpaces(command)) return;
    String profile = getProfileText(file);
    RunContentDescriptor descriptor = getConsoleView(project, file, engineInfo);
    IdeScriptEngine engine;
    Content content = descriptor.getAttachedContent();
    if (content == null) {
      LOG.error("Attached content expected");
      return;
    }
    else {
      Trinity<IdeScriptEngine, IdeScriptEngineManager.EngineInfo, VirtualFile> data = content.getUserData(SCRIPT_ENGINE_KEY);
      if (data != null) {
        engine = data.first;
      }
      else {
        engine = IdeScriptEngineManager.getInstance().getEngine(engineInfo, null);
        if (engine == null) {
          LOG.error("Script engine not found for: " + file.getName());
          return;
        }
        content.putUserData(SCRIPT_ENGINE_KEY, Trinity.create(engine, engineInfo, file));
      }
    }

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
      Throwable ex = ExceptionUtil.getRootCause(e);
      String message = StringUtil.notNullize(StringUtil.nullize(ex.getMessage()), ex.toString());
      consoleView.print(ex.getClass().getSimpleName() + ": " + message, ConsoleViewContentType.ERROR_OUTPUT);
      consoleView.print("\n", ConsoleViewContentType.ERROR_OUTPUT);
    }
    selectContent(descriptor);
  }

  private static void prepareEngine(@NotNull Project project, @NotNull IdeScriptEngine engine, @NotNull RunContentDescriptor descriptor) {
    IdeConsoleScriptBindings.ensureIdeIsBound(project, engine);
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
    if (!selectedRange.isEmpty()) {
      return document.getText(selectedRange);
    }
    int line = document.getLineNumber(selectedRange.getStartOffset());
    int lineStart = document.getLineStartOffset(line);
    int lineEnd = document.getLineEndOffset(line);
    String lineText = document.getText(TextRange.create(lineStart, lineEnd));

    // try to detect a non-trivial composite PSI element if there's a PSI file
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file != null && !StringUtil.isEmptyOrSpaces(lineText)) {
      int start = lineStart, end = lineEnd;
      while (start < end && Character.isWhitespace(lineText.charAt(start - lineStart))) start ++;
      while (end > start && Character.isWhitespace(lineText.charAt(end - 1 - lineStart))) end --;
      PsiElement e1 = file.findElementAt(start);
      PsiElement e2 = file.findElementAt(end > start ? end - 1 : end);
      PsiElement parent = e1 != null && e2 != null ? PsiTreeUtil.findCommonParent(e1, e2) : ObjectUtils.chooseNotNull(e1, e2);
      if (parent != null && parent != file) {
        TextRange combined = parent.getTextRange().union(TextRange.create(lineStart, lineEnd));
        editor.getSelectionModel().setSelection(combined.getStartOffset(), combined.getEndOffset());
        return document.getText(combined);
      }
    }
    return lineText;
  }

  private static void selectContent(RunContentDescriptor descriptor) {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ConsoleViewImpl consoleView = Objects.requireNonNull((ConsoleViewImpl)descriptor.getExecutionConsole());
    RunContentManager.getInstance(consoleView.getProject()).toFrontRunContent(executor, descriptor);
  }

  @NotNull
  private static RunContentDescriptor getConsoleView(@NotNull Project project,
                                                     @NotNull VirtualFile file,
                                                     @NotNull IdeScriptEngineManager.EngineInfo engineInfo) {
    for (RunContentDescriptor existing : RunContentManager.getInstance(project).getAllDescriptors()) {
      Content content = existing.getAttachedContent();
      if (content == null) continue;
      Trinity<IdeScriptEngine, IdeScriptEngineManager.EngineInfo, VirtualFile> data = content.getUserData(SCRIPT_ENGINE_KEY);
      if (data == null) continue;
      if (!Comparing.equal(file, data.third)) continue;
      if (!Comparing.equal(engineInfo, data.second)) continue;
      return existing;
    }

    ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    JComponent panel = new JPanel(new BorderLayout());
    panel.add(consoleView.getComponent(), BorderLayout.CENTER);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("RunIdeConsole", toolbarActions, false);
    toolbar.setTargetComponent(consoleView.getComponent());
    panel.add(toolbar.getComponent(), BorderLayout.WEST);

    RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, null, panel, file.getName()) {
      @Override
      public boolean isContentReuseProhibited() {
        return true;
      }
    };
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    toolbarActions.addAll(consoleView.createConsoleActions());
    toolbarActions.add(new CloseAction(executor, descriptor, project));
    RunContentManager.getInstance(project).showRunContent(executor, descriptor);

    return descriptor;
  }

  private static class MyRunAction extends DumbAwareAction {

    private IdeScriptEngineManager.EngineInfo engineInfo;

    @Override
    public void update(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
      e.getPresentation().setEnabledAndVisible(
        project != null && editor != null && virtualFile != null && (
          engineInfo != null ||
          virtualFile.getUserData(SELECTED_ENGINE_INFO_KEY) != null ||
          virtualFile.getExtension() != null));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
      if (project == null || editor == null || virtualFile == null) return;
      String extension = virtualFile.getExtension();
      IdeScriptEngineManager.EngineInfo engineInfo0 = virtualFile.getUserData(SELECTED_ENGINE_INFO_KEY);
      if (engineInfo == null && engineInfo0 == null && extension == null) return;

      PsiDocumentManager.getInstance(project).commitAllDocuments();

      if (engineInfo == null ||
          engineInfo0 != null && !Comparing.equal(engineInfo0, engineInfo) ||
          extension != null && !engineInfo.fileExtensions.contains(extension)) {
        virtualFile.putUserData(SELECTED_ENGINE_INFO_KEY, null);

        List<IdeScriptEngineManager.EngineInfo> infos =
          engineInfo0 != null
          ? Collections.singletonList(engineInfo0)
          : JBIterable.from(IdeScriptEngineManager.getInstance().getEngineInfos())
            .filter(o -> o.fileExtensions.contains(extension))
            .toList();

        chooseScriptEngineAndRun(e, infos, selectedInfo -> {
          engineInfo = selectedInfo;
          executeQuery(project, virtualFile, editor, engineInfo);
        });
      }
      else {
        executeQuery(project, virtualFile, editor, engineInfo);
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
    engine.setStdOut(new ConsoleWriter(ref, ProcessOutputTypes.STDOUT));
    engine.setStdErr(new ConsoleWriter(ref, ProcessOutputTypes.STDERR));
  }

  private static final class ConsoleWriter extends Writer {
    private final @NotNull Reference<? extends RunContentDescriptor> myDescriptor;
    private final Key<?> myOutputType;
    private final AnsiEscapeDecoder myAnsiEscapeDecoder;

    private ConsoleWriter(@NotNull Reference<? extends RunContentDescriptor> descriptor, Key<?> outputType) {
      myDescriptor = descriptor;
      myOutputType = outputType;
      myAnsiEscapeDecoder = new AnsiEscapeDecoder();
    }

    @Nullable
    public RunContentDescriptor getDescriptor() {
      return myDescriptor.get();
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
      RunContentDescriptor descriptor = myDescriptor.get();
      ConsoleViewImpl console = ObjectUtils.tryCast(descriptor != null ? descriptor.getExecutionConsole() : null, ConsoleViewImpl.class);
      String text = new String(cbuf, off, len);
      if (console == null) {
        LOG.info(myOutputType + ": " + text);
      }
      else {
        myAnsiEscapeDecoder.escapeText(text, myOutputType, (s, attr) -> {
          console.print(s, ConsoleViewContentType.getConsoleViewType(attr));
        });
      }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  }
}
