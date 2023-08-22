// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ConsoleViewRunningState;
import com.intellij.execution.jshell.protocol.Event;
import com.intellij.execution.jshell.protocol.*;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eugene Zhuravlev
 */
public final class JShellHandler {
  private static final Logger LOG = Logger.getInstance(JShellHandler.class);
  private static final int DEBUG_PORT = -1;
  public static final Key<JShellHandler> MARKER_KEY = Key.create("JShell console key");
  private static final Charset ourCharset = StandardCharsets.UTF_8;

  private static final Executor EXECUTOR = DefaultRunExecutor.getRunExecutorInstance();
  private static final String JSHELL_FRONTEND_JAR = "jshell-frontend.jar";

  private final @NotNull Project myProject;
  private final @NotNull RunContentDescriptor myRunContent;
  private final @NotNull ConsoleViewImpl myConsoleView;
  private final @NotNull OSProcessHandler myProcessHandler;
  private final @NotNull MessageReader<Response> myMessageReader;
  private final @NotNull MessageWriter<Request> myMessageWriter;
  private final @NotNull ExecutorService myTaskQueue = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(
    ExecutionBundle.message("jshell.command.queue"));
  private final AtomicReference<Collection<String>> myEvalClasspathRef = new AtomicReference<>(null);

  private JShellHandler(@NotNull Project project,
                        @NotNull RunContentDescriptor descriptor,
                        @NotNull ConsoleViewImpl consoleView,
                        @NotNull VirtualFile contentFile,
                        @NotNull OSProcessHandler processHandler) throws Exception {
    myProject = project;
    myRunContent = descriptor;
    myConsoleView = consoleView;
    myProcessHandler = processHandler;

    final PipedInputStream is = new PipedInputStream();
    final OutputStreamWriter readerSink = new OutputStreamWriter(new PipedOutputStream(is), StandardCharsets.UTF_8);
    myMessageReader = new MessageReader<>(is, Response.class);
    myMessageWriter = new MessageWriter<>(processHandler.getProcessInput());

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          try {
            readerSink.write(event.getText());
            readerSink.flush();
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
        else {
          myConsoleView.print(event.getText(), outputType == ProcessOutputTypes.STDERR? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.SYSTEM_OUTPUT);
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        if (getAssociatedHandler(contentFile) == JShellHandler.this) {
          // process terminated either by closing file or by close action
          contentFile.putUserData(MARKER_KEY, null);
          try {
            readerSink.close();
          }
          catch (IOException ignored) {
          }
        }
      }
    });

    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (file.equals(contentFile)) {
          // if file was closed then kill process and hide console content
          JShellHandler.this.stop();
        }
      }
    });

    contentFile.putUserData(MARKER_KEY, this);
    consoleView.attachToProcess(processHandler);
  }

  public static @Nullable JShellHandler getAssociatedHandler(@NotNull VirtualFile virtualFile) {
    return virtualFile.getUserData(MARKER_KEY);
  }

  @RequiresEdt
  public static @NotNull JShellHandler create(@NotNull Project project,
                                              @NotNull VirtualFile contentFile,
                                              @Nullable Module module,
                                              @Nullable Sdk alternateSdk) throws Exception {
    final OSProcessHandler processHandler = launchProcess(project, module, alternateSdk);

    final String title = JShellDiagnostic.TITLE + " " + contentFile.getNameWithoutExtension();

    final ConsoleViewImpl consoleView = new MyConsoleView(project);
    final RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, processHandler, new JPanel(new BorderLayout()), title);
    final JShellHandler jshellHandler = new JShellHandler(project, descriptor, consoleView, contentFile, processHandler);

    // init classpath for evaluation
    final Set<String> cp = new LinkedHashSet<>();
    final Computable<OrderEnumerator> orderEnumerator = module != null ?
                                                        () -> ModuleRootManager.getInstance(module).orderEntries() :
                                                        () -> ProjectRootManager.getInstance(project).orderEntries();
    ApplicationManager.getApplication().runReadAction(() -> {
      cp.addAll(orderEnumerator.compute().librariesOnly().recursively().withoutSdk().getPathsList().getPathList());
    });
    if (!cp.isEmpty()) {
      jshellHandler.myEvalClasspathRef.set(cp);
    }

    // must call getComponent before createConsoleActions()
    final JComponent consoleViewComponent = consoleView.getComponent();

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    //actionGroup.add(new BuildAndRestartConsoleAction(module, project, defaultExecutor, descriptor, restarter(project, contentFile)));
    //actionGroup.addSeparator();
    actionGroup.addAll(consoleView.createConsoleActions());
    actionGroup.add(new CloseAction(EXECUTOR, descriptor, project) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        jshellHandler.stop();
        if (!processHandler.waitFor(10000)) {
          processHandler.destroyProcess();
        }
        super.actionPerformed(e);
      }
    });

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("JShellHandler", actionGroup, false);
    toolbar.setTargetComponent(consoleViewComponent);

    final JComponent ui = descriptor.getComponent();
    ui.add(consoleViewComponent, BorderLayout.CENTER);
    ui.add(toolbar.getComponent(), BorderLayout.WEST);

    processHandler.startNotify();

    RunContentManager.getInstance(project).showRunContent(EXECUTOR, descriptor);
    return jshellHandler;
  }

  // todo: do we need to include project's compiled classes into the classpath or libraries only?
  // todo: if we include project classes, make sure they are compiled
  private static OSProcessHandler launchProcess(@NotNull Project project,
                                                @Nullable Module module,
                                                @Nullable Sdk alternateSdk) throws Exception{
    final Sdk sdk = alternateSdk != null? alternateSdk :
                    module != null? ModuleRootManager.getInstance(module).getSdk() :
                    ProjectRootManager.getInstance(project).getProjectSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType javaSdkType)) {
      throw new ExecException(
        (sdk != null ? "Expected Java SDK" : " SDK is not configured") +
        (module != null? " for module " + module.getName() : " for project " + project.getName())
      );
    }
    final JavaSdkVersion sdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(sdk);
    if (sdkVersion == null) {
      throw new ExecException("Cannot determine version for JDK " + sdk.getName() + ". Please re-configure the JDK.");
    }
    if (!sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_9)) {
      throw new ExecException("JDK version is " + sdkVersion.getDescription() + ". JDK 9 or higher is needed to run JShell.");
    }
    final String vmExePath = javaSdkType.getVMExecutablePath(sdk);
    if (vmExePath == null) {
      throw new ExecException("Cannot determine path to VM executable for JDK " + sdk.getName() + ". Please re-configure the JDK.");
    }
    final File executableFile = new File(vmExePath);
    final String frontEndPath = findFrontEndLibrary();
    if (frontEndPath == null) {
      throw new ExecException("Library " + JSHELL_FRONTEND_JAR + " not found in IDE classpath");
    }
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.setExePath(executableFile.getAbsolutePath());
    cmdLine.setWorkDirectory(executableFile.getParent());
    cmdLine.setCharset(ourCharset);
    if (DEBUG_PORT > 0) {
      cmdLine.addParameter("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + DEBUG_PORT);
    }

    final StringBuilder launchCp = new StringBuilder().append(frontEndPath);
    final String protocolJar = getLibPath(Endpoint.class);
    if (protocolJar != null) {
      launchCp.append(File.pathSeparator).append(protocolJar);
    }
    if (launchCp.length() > 0) {
      cmdLine.addParameter("-classpath");
      cmdLine.addParameter(launchCp.toString());
    }
    cmdLine.addParameter("com.intellij.execution.jshell.frontend.Main");

    // init classpath for evaluation
    //final Set<String> cp = new LinkedHashSet<>();
    //final Computable<OrderEnumerator> orderEnumerator = module != null ? () -> ModuleRootManager.getInstance(module).orderEntries()
    //                                                                   : () -> ProjectRootManager.getInstance(project).orderEntries();
    //ApplicationManager.getApplication().runReadAction(() -> {
    //  cp.addAll(orderEnumerator.compute().librariesOnly().recursively().withoutSdk().getPathsList().getPathList());
    //});

    //final File cpFile;
    //if (!cp.isEmpty()) {
    //  cpFile = FileUtilRt.createTempFile("_jshell_classpath_", "", true);
    //  try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cpFile), StandardCharsets.UTF_8))) {
    //    for (String path : cp) {
    //      writer.write(path);
    //      writer.newLine();
    //    }
    //  }
    //  cmdLine.addParameter("--@class-path");
    //  cmdLine.addParameter(cpFile.getAbsolutePath());
    //}
    //else {
    //  cpFile = null;
    //}

    final OSProcessHandler processHandler = new OSProcessHandler(cmdLine);
    //if (cpFile != null) {
    //  processHandler.addProcessListener(new ProcessAdapter() {
    //    @Override
    //    public void processTerminated(ProcessEvent event) {
    //      FileUtil.delete(cpFile);
    //    }
    //  });
    //}
    return processHandler;
  }

  private static String findFrontEndLibrary() {
    final String path = PathManager.getResourceRoot(JShellHandler.class.getClassLoader(), "com/intellij/execution/jshell/frontend/Marker.class");
    return path != null? path : JSHELL_FRONTEND_JAR;
  }

  private static String getLibPath(final Class<?> aClass) {
    return PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
  }

  public void stop() {
    myProcessHandler.destroyProcess(); // use force
    RunContentManager.getInstance(myProject).removeRunContent(EXECUTOR, myRunContent);
  }

  public void toFront() {
    RunContentManager.getInstance(myProject).toFrontRunContent(EXECUTOR, myRunContent);
  }

  @Nullable
  public Future<Response> evaluate(@NotNull String code) {
    return StringUtil.isEmptyOrSpaces(code) ? null : myTaskQueue.submit(() -> sendInput(new Request(nextUid(), Request.Command.EVAL, code)));
  }

  public void dropState() {
    myTaskQueue.execute(() -> sendInput(new Request(nextUid(), Request.Command.DROP_STATE, null)));
  }

  private static String nextUid() {
    return UUID.randomUUID().toString();
  }

  @Nullable
  private Response sendInput(final Request request) {
    final boolean alive = !myProcessHandler.isProcessTerminating() && !myProcessHandler.isProcessTerminated();
    if (alive) {
      // consume evaluation classpath, if any
      final Collection<String> cp = myEvalClasspathRef.getAndSet(null);
      if (cp != null) {
        for (String path : cp) {
          request.addClasspathItem(path);
        }
      }
      UIUtil.invokeLaterIfNeeded(() ->
      myConsoleView.performWhenNoDeferredOutput(() -> {
        try {
          myMessageWriter.send(request);
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }));
    }
    final StringBuilder stdOut = new StringBuilder();
    Response response = null;
    try {
      response = myMessageReader.receive(unparsedText -> stdOut.append(unparsedText));
      return response;
    }
    catch (IOException e) {
      LOG.info(e);
    }
    finally {
      renderResponse(request, response, stdOut.toString());
    }
    return null;
  }

  private void renderResponse(Request request, @Nullable Response response, String stdOut) {
    //myConsoleView.print("\n-------------------evaluation " + response.getUid() + "------------------------", ConsoleViewContentType.NORMAL_OUTPUT);
    if (response != null) {
      final List<Event> events = response.getEvents();
      if (events != null) {
        if (request.getCommand() == Request.Command.DROP_STATE) {
          int droppedCount = 0;
          for (Event event : events) {
            final CodeSnippet.Status prevStatus = event.getPreviousStatus();
            final CodeSnippet.Status status = event.getStatus();
            if (event.getSnippet() != null && prevStatus != status && status == CodeSnippet.Status.DROPPED) {
              droppedCount++;
            }
          }
          JShellDiagnostic.notifyInfo(JavaCompilerBundle.message("jshell.dropped.x.code.snippets", droppedCount), myProject);
        }
        else {
          for (Event event : events) {
            if (event.getCauseSnippet() == null) {
              final String exception = event.getExceptionText();
              if (!StringUtil.isEmptyOrSpaces(exception)) {
                myConsoleView.print("\n" + exception, ConsoleViewContentType.SYSTEM_OUTPUT);
              }
              final String diagnostic = event.getDiagnostic();
              if (!StringUtil.isEmptyOrSpaces(diagnostic)) {
                myConsoleView.print("\n" + diagnostic, ConsoleViewContentType.SYSTEM_OUTPUT);
              }

              final String descr = getEventDescription(event);
              if (!StringUtil.isEmptyOrSpaces(descr)) {
                myConsoleView.print("\n" + descr, ConsoleViewContentType.SYSTEM_OUTPUT);
              }
              final CodeSnippet snippet = event.getSnippet();
              final String value = snippet != null && !snippet.getSubKind().hasValue()? null : event.getValue();
              if (value != null) {
                myConsoleView.print(" = " + (value.isEmpty()? "\"\"" : value), ConsoleViewContentType.NORMAL_OUTPUT);
              }
            }
          }
        }
      }
    }

    if (!StringUtil.isEmpty(stdOut)) {
      //myConsoleView.print("\n-----evaluation output-----\n", ConsoleViewContentType.NORMAL_OUTPUT);
      myConsoleView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
      // delegate unparsed text directly to console
      if (!"\n".equals(stdOut) /*hack to ignore possible empty line before 'message-begin' merker*/) {
        myConsoleView.print(stdOut, ConsoleViewContentType.NORMAL_OUTPUT);
      }
    }
  }

  private static String getEventDescription(Event event) {
    final CodeSnippet snippet = event.getSnippet();
    if (event.getCauseSnippet() != null || snippet == null) {
      return "";
    }
    final CodeSnippet.Status status = event.getStatus();
    final CodeSnippet.Kind kind = snippet.getKind();
    final CodeSnippet.SubKind subKind = snippet.getSubKind();

    String presentation = snippet.getPresentation();
    if (presentation == null || subKind == CodeSnippet.SubKind.TEMP_VAR_EXPRESSION_SUBKIND) {
      presentation = StringUtil.trim(snippet.getCodeText());
    }

    String actionLabel;
    if (event.getPreviousStatus() == CodeSnippet.Status.NONEXISTENT && status.isDefined()) {
      if (subKind == CodeSnippet.SubKind.VAR_DECLARATION_WITH_INITIALIZER_SUBKIND ||
          /*subKind == CodeSnippet.SubKind.TEMP_VAR_EXPRESSION_SUBKIND ||*/
          !subKind.isExecutable()) {
        actionLabel = "Defined";
      }
      else {
        actionLabel = "";
      }
    }
    else if (status == CodeSnippet.Status.REJECTED){
      actionLabel = "Rejected";
    }
    else if (status == CodeSnippet.Status.DROPPED) {
      actionLabel = "Dropped";
    }
    else if (status == CodeSnippet.Status.OVERWRITTEN) {
      actionLabel = "Overwritten";
    }
    else {
      actionLabel = "";
    }

    String kindLabel;
    if (kind == CodeSnippet.Kind.TYPE_DECL) {
      if (subKind == CodeSnippet.SubKind.INTERFACE_SUBKIND) {
        kindLabel = "interface";
      }
      else if (subKind == CodeSnippet.SubKind.ENUM_SUBKIND) {
        kindLabel = "enum";
      }
      else if (subKind == CodeSnippet.SubKind.ANNOTATION_TYPE_SUBKIND) {
        kindLabel = "annotation";
      }
      else {
        kindLabel = "class";
      }
    }
    else if (kind == CodeSnippet.Kind.VAR){
      kindLabel = subKind == CodeSnippet.SubKind.TEMP_VAR_EXPRESSION_SUBKIND ? ""/*"temp var"*/ : "field";
    }
    else if (kind == CodeSnippet.Kind.METHOD) {
      kindLabel = "method";
    }
    else if (kind == CodeSnippet.Kind.IMPORT) {
      kindLabel = subKind == CodeSnippet.SubKind.STATIC_IMPORT_ON_DEMAND_SUBKIND || subKind == CodeSnippet.SubKind.SINGLE_STATIC_IMPORT_SUBKIND ? "static import" : "import";
    }
    else {
      kindLabel = "";
    }

    final StringBuilder descr = new StringBuilder();
    descr.append(actionLabel);
    if (!actionLabel.isEmpty()) {
      descr.append(" ");
    }
    if (!kindLabel.isEmpty()) {
      descr.append(kindLabel).append(" ");
    }
    descr.append(presentation);

    return descr.toString();
  }

  private static class MyConsoleView extends ConsoleViewImpl {
    MyConsoleView(Project project) {
      super(project, GlobalSearchScope.allScope(project), true, new ConsoleState.NotStartedStated() {
        @NotNull
        @Override
        public ConsoleState attachTo(@NotNull ConsoleViewImpl console, @NotNull ProcessHandler processHandler) {
          // do not automatically display all the text that is sent/recieved between processes
          // the ootput from console will be formatted and sent to console view
          return new ConsoleViewRunningState(console, processHandler, this, false, false);
        }
      }, true);
    }
  }

  private static final class ExecException extends Exception {
    ExecException(String message) {
      super(message);
    }

    ExecException(String message, Throwable cause) {
      super(message, cause);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }
}
