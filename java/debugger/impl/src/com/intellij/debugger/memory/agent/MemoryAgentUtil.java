// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.memory.agent.extractor.AgentExtractor;
import com.intellij.debugger.memory.ui.JavaReferenceInfo;
import com.intellij.debugger.memory.ui.SizedReferenceInfo;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Bitness;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;

public class MemoryAgentUtil {
  private static final Logger LOG = Logger.getInstance(MemoryAgentUtil.class);
  private static final String MEMORY_AGENT_EXTRACT_DIRECTORY = "memory.agent.extract.dir";
  private static final Key<Boolean> LISTEN_MEMORY_AGENT_STARTUP_FAILED = Key.create("LISTEN_MEMORY_AGENT_STARTUP_FAILED");
  private static final Key<Boolean> IS_DEBUGGER_ATTACHED_KEY = Key.create("IS_DEBUGGER_ATTACHED_KEY");
  private static final Key<Boolean> IS_JAVA_DEBUG_PROCESS_KEY = Key.create("IS_JAVA_DEBUG_PROCESS_KEY");

  private static final int ESTIMATE_OBJECTS_SIZE_LIMIT = 2000;

  public static void addMemoryAgent(@NotNull JavaParameters parameters) {
    if (!DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT) {
      return;
    }

    if (!isPlatformSupported()) {
      LOG.warn("Could not use memory agent on current OS.");
      DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT = false;
      return;
    }

    if (isIbmJdk(parameters)) {
      LOG.info("Do not attach memory agent for IBM jdk");
      return;
    }

    ParametersList parametersList = parameters.getVMParametersList();
    if (parametersList.getParameters().stream().anyMatch(x -> x.contains("memory_agent"))) return;
    boolean isInDebugMode = Registry.is("debugger.memory.agent.debug");
    Path agentFile = null;
    String errorMessage = null;
    long start = System.currentTimeMillis();
    try {
      agentFile = getAgentFile(isInDebugMode, parameters.getJdkPath());
    }
    catch (InterruptedException e) {
      errorMessage = "Interrupted";
    }
    catch (ExecutionException e) {
      LOG.warn(e.getCause());
      errorMessage = "Exception thrown (see logs for details)";
    }
    catch (TimeoutException e) {
      errorMessage = "Timeout";
    }
    catch (CantRunException e) {
      errorMessage = e.getMessage();
    }
    if (errorMessage != null || agentFile == null) {
      LOG.warn("Could not extract agent: " + errorMessage);
      return;
    }

    LOG.info("Memory agent extracting took " + (System.currentTimeMillis() - start) + " ms");
    String agentFileName = agentFile.getFileName().toString();
    String path = JavaExecutionUtil.handleSpacesInAgentPath(agentFile.toAbsolutePath().toString(), "debugger-memory-agent",
                                                            MEMORY_AGENT_EXTRACT_DIRECTORY, f -> agentFileName.equals(f.getName()));
    if (path == null) {
      return;
    }

    String args = "";
    if (isInDebugMode) {
      args = "5";// Enable debug messages
    }
    path += "=" + args;
    parametersList.add("-agentpath:" + path);
    listenIfStartupFailed();
  }

  @NotNull
  public static List<JavaReferenceInfo> tryCalculateSizes(@NotNull EvaluationContextImpl context,
                                                          @NotNull List<JavaReferenceInfo> objects) {
    MemoryAgent agent = MemoryAgent.get(context.getDebugProcess());
    if (!agent.capabilities().canEstimateObjectsSizes()) return objects;
    if (objects.size() > ESTIMATE_OBJECTS_SIZE_LIMIT) {
      LOG.info("Too many objects to estimate their sizes");
      return objects;
    }
    try {
      long[] sizes = agent.estimateObjectsSizes(context, ContainerUtil.map(objects, x -> x.getObjectReference()));
      return IntStreamEx.range(0, objects.size())
        .mapToObj(i -> new SizedReferenceInfo(objects.get(i).getObjectReference(), sizes[i]))
        .reverseSorted(Comparator.comparing(x -> x.size()))
        .map(x -> (JavaReferenceInfo)x)
        .toList();
    }
    catch (EvaluateException e) {
      LOG.error("Could not estimate objects sizes", e);
    }

    return objects;
  }

  public static void setupAgent(@NotNull DebugProcessImpl debugProcess) {
    if (!DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT) return;
    if (DebuggerUtilsImpl.isRemote(debugProcess)) {
      // we do not support remote debugging with memory agent yet since some operations are too expensive
      return;
    }
    debugProcess.addDebugProcessListener(new DebugProcessAdapterImpl() {
      private final AtomicBoolean isInitialized = new AtomicBoolean(false);

      @Override
      public void paused(@NotNull SuspendContextImpl suspendContext) {
        EvaluationContextImpl context = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
        if (context.isEvaluationPossible()) {
          if (isInitialized.compareAndSet(false, true)) {
            debugProcess.removeDebugProcessListener(this);
            MemoryAgentOperations.initializeAgent(context);
          }
        }
      }
    });
  }

  public static boolean isPlatformSupported() {
    return SystemInfo.isWindows || SystemInfo.isMac || SystemInfo.isLinux;
  }

  private static boolean isIbmJdk(@NotNull JavaParameters parameters) {
    Sdk jdk = parameters.getJdk();
    String vendor = jdk == null ? null : JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VENDOR);
    return vendor != null && StringUtil.containsIgnoreCase(vendor, "ibm");
  }

  private static Path getAgentFile(boolean isInDebugMode, String jdkPath)
    throws InterruptedException, ExecutionException, TimeoutException {
    if (isInDebugMode) {
      String debugAgentPath = Registry.get("debugger.memory.agent.debug.path").asString();
      if (!debugAgentPath.isEmpty()) {
        LOG.info("Local memory agent will be used: " + debugAgentPath);
        return Paths.get(debugAgentPath);
      }
    }

    return ApplicationManager.getApplication()
      .executeOnPooledThread(() -> new AgentExtractor().extract(detectAgentKind(jdkPath), getAgentDirectory()))
      .get(1, TimeUnit.SECONDS);
  }

  private static AgentExtractor.AgentLibraryType detectAgentKind(String jdkPath) {
    LOG.assertTrue(isPlatformSupported());
    if (SystemInfo.isLinux) return AgentExtractor.AgentLibraryType.LINUX;
    if (SystemInfo.isMac) return AgentExtractor.AgentLibraryType.MACOS;
    JdkVersionDetector.JdkVersionInfo versionInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(jdkPath);
    if (versionInfo == null) {
      LOG.warn("Could not detect jdk bitness. x64 will be used.");
      return AgentExtractor.AgentLibraryType.WINDOWS64;
    }

    return Bitness.x32.equals(versionInfo.bitness) ? AgentExtractor.AgentLibraryType.WINDOWS32 : AgentExtractor.AgentLibraryType.WINDOWS64;
  }

  @NotNull
  private static File getAgentDirectory() {
    String agentDirectory = System.getProperty(MEMORY_AGENT_EXTRACT_DIRECTORY);
    if (agentDirectory != null) {
      File file = new File(agentDirectory);
      if (file.exists() || file.mkdirs()) {
        return file;
      }

      LOG.info("Directory specified in property \"" + MEMORY_AGENT_EXTRACT_DIRECTORY +
               "\" not found. Default tmp directory will be used");
    }

    return new File(FileUtil.getTempDirectory());
  }

  /**
   * Many things may go wrong when you are trying to start JVM with an attached native agent. Most of them happen on the VM startup.
   * The purpose of this method is to try catch cases when VM failed to initialize because of memory agent and suggest user
   * disable the agent.
   */
  private static void listenIfStartupFailed() {
    Project project = JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables();
    if (Boolean.TRUE.equals(project.getUserData(LISTEN_MEMORY_AGENT_STARTUP_FAILED))) return;
    project.putUserData(LISTEN_MEMORY_AGENT_STARTUP_FAILED, true);

    project.getMessageBus().connect().subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        if (executorId != DefaultDebugExecutor.EXECUTOR_ID) return;
        DebugProcess debugProcess = DebuggerManager.getInstance(env.getProject()).getDebugProcess(handler);
        if (debugProcess == null) return;
        handler.putUserData(IS_JAVA_DEBUG_PROCESS_KEY, true);
        if (debugProcess.isAttached()) {
          handler.putUserData(IS_DEBUGGER_ATTACHED_KEY, true);
        }
        else {
          debugProcess.addDebugProcessListener(new DebugProcessListener() {
            @Override
            public void processAttached(@NotNull DebugProcess process) {
              process.getProcessHandler().putUserData(IS_DEBUGGER_ATTACHED_KEY, true);
              process.removeDebugProcessListener(this);
            }
          });
        }
      }

      @Override
      public void processTerminated(@NotNull String executorId,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
        // make sure this is a JVM debug process and it has terminated abnormally
        if (executorId != DefaultDebugExecutor.EXECUTOR_ID || exitCode == 0 || !isJvmDebugProcess(handler)) return;

        // skip if the VM successfully started since the debugger had been attached
        if (wasDebuggerAttached(handler)) return;
        RunContentDescriptor content = env.getContentToReuse();
        if (content == null) return;

        ExecutionConsole console = content.getExecutionConsole();
        if (!(console instanceof ConsoleViewImpl)) return;

        ConsoleViewImpl consoleView = (ConsoleViewImpl)console;
        ApplicationManager.getApplication().invokeLater(() -> {
          if (consoleView.hasDeferredOutput()) {
            consoleView.flushDeferredText();
          }
          Editor editor = consoleView.getEditor();
          if (editor == null) return;
          String[] outputLines = StringUtil.splitByLines(editor.getDocument().getText());
          List<String> mentions = StreamEx.of(outputLines).skip(1).filter(x -> x.contains("memory_agent")).limit(10).toList();
          if (outputLines.length >= 1 && outputLines[0].contains("memory_agent") && !mentions.isEmpty()) {
            Project project = env.getProject();
            String name = env.getRunProfile().getName();
            String windowId = ExecutionManager.getInstance(project).getContentManager().getToolWindowIdByEnvironment(env);

            Attachment[] mentionsInOutput = StreamEx.of(mentions).map(x -> new Attachment("agent_mention.txt", x))
              .toArray(Attachment.EMPTY_ARRAY);
            RuntimeExceptionWithAttachments exception =
              new RuntimeExceptionWithAttachments("Could not start debug process with memory agent", mentionsInOutput);
            String checkboxName = DebuggerBundle.message("label.debugger.general.configurable.enable.memory.agent");
            String description =
              "Memory agent could not be loaded. <a href=\"Disable\">Disable</a> the agent. To enable it back use \"" +
              checkboxName + "\" option in File | Settings | Build, Execution, Deployment | Debugger";
            ExecutionUtil.handleExecutionError(project, windowId, name, exception, description, new DisablingMemoryAgentListener());
            LOG.error(exception);
          }
        }, project.getDisposed());
      }

      private boolean isJvmDebugProcess(@NotNull ProcessHandler handler) {
        return Boolean.TRUE.equals(handler.getUserData(IS_JAVA_DEBUG_PROCESS_KEY));
      }

      private boolean wasDebuggerAttached(@NotNull ProcessHandler handler) {
        return Boolean.TRUE.equals(handler.getUserData(IS_DEBUGGER_ATTACHED_KEY));
      }
    });
  }

  private static class DisablingMemoryAgentListener implements HyperlinkListener {
    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
        DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT = false;
      }
    }
  }
}
