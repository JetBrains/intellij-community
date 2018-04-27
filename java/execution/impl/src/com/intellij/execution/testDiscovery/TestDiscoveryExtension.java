// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.rt.coverage.data.SingleTrFileDiscoveryProtocolDataListener;
import com.intellij.rt.coverage.data.SocketTestDiscoveryProtocolDataListener;
import com.intellij.rt.coverage.data.TestDiscoveryProjectData;
import com.intellij.rt.coverage.data.api.TestDiscoveryProtocolUtil;
import com.intellij.util.Alarm;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class TestDiscoveryExtension extends RunConfigurationExtension {
  public static final String TEST_DISCOVERY_REGISTRY_KEY = "testDiscovery.enabled";
  private static final String TEST_DISCOVERY_AGENT_PATH = "test.discovery.agent.path";

  private static final boolean USE_SOCKET = SystemProperties.getBooleanProperty("test.discovery.use.socket", true);
  public static final Key<TestDiscoveryDataSocketListener> SOCKET_LISTENER_KEY = Key.create("test.discovery.socket.data.listener");

  private static final Logger LOG = Logger.getInstance(TestDiscoveryExtension.class);

  @NotNull
  @Override
  public String getSerializationId() {
    return "testDiscovery";
  }

  @Override
  protected void attachToProcess(@NotNull final RunConfigurationBase configuration,
                                 @NotNull final ProcessHandler handler,
                                 @Nullable RunnerSettings runnerSettings) {
    if (runnerSettings == null && isApplicableFor(configuration)) {
      Disposable disposable = Disposer.newDisposable();
      final MessageBusConnection connection = configuration.getProject().getMessageBus().connect();
      TestDiscoveryDataSocketListener listener = SOCKET_LISTENER_KEY.get(configuration);
      if (listener == null) {
        final Alarm processTracesAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable);
        connection.subscribe(SMTRunnerEventsListener.TEST_STATUS, new SMTRunnerEventsAdapter() {
          @Override
          public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
            if (testsRoot.getHandler() != handler) return;
            processTracesAlarm.cancelAllRequests();
            processTracesAlarm.addRequest(() -> processTracesFile((JavaTestConfigurationBase)configuration), 0);
            connection.disconnect();
            Disposer.dispose(disposable);
          }
        });
      } else {
        listener.attach(handler);
      }
    }
  }

  @Override
  public void updateJavaParameters(RunConfigurationBase configuration, JavaParameters params, RunnerSettings runnerSettings) {
    if (runnerSettings != null || !isApplicableFor(configuration)) {
      return;
    }
    String agentPath = JavaExecutionUtil.handleSpacesInAgentPath(PathUtil.getJarPathForClass(TestDiscoveryProjectData.class), "testDiscovery", TEST_DISCOVERY_AGENT_PATH);
    if (agentPath == null) return;
    params.getVMParametersList().add("-javaagent:" + agentPath);
    TestDiscoveryDataSocketListener listener = tryInstallSocketListener(configuration);
    if (listener != null) {
      params.getVMParametersList().addProperty(SocketTestDiscoveryProtocolDataListener.PORT_PROP, Integer.toString(listener.getPort()));
      params.getVMParametersList().addProperty(SocketTestDiscoveryProtocolDataListener.HOST_PROP, "127.0.0.1");
      params.getVMParametersList().addProperty(TestDiscoveryProjectData.TEST_DISCOVERY_DATA_LISTENER_PROP, SocketTestDiscoveryProtocolDataListener.class.getName());
    } else {
      params.getVMParametersList().addProperty(SingleTrFileDiscoveryProtocolDataListener.TRACE_FILE, getTraceFilePath(configuration));
      params.getVMParametersList().addProperty(TestDiscoveryProjectData.TEST_DISCOVERY_DATA_LISTENER_PROP, SingleTrFileDiscoveryProtocolDataListener.class.getName());
    }
  }

  @NotNull
  private static String getTraceFilePath(RunConfigurationBase configuration) {
    return baseTestDiscoveryPathForProject(configuration.getProject()) + File.separator + configuration.getUniqueID() + ".tr";
  }

  @Override
  public boolean isListenerDisabled(RunConfigurationBase configuration, Object listener, RunnerSettings runnerSettings) {
    return listener instanceof TestDiscoveryListener && (runnerSettings != null || !isApplicableFor(configuration));
  }

  @Override
  public void readExternal(@NotNull final RunConfigurationBase runConfiguration, @NotNull Element element) throws InvalidDataException {}

  @Override
  public void writeExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  @Override
  protected boolean isApplicableFor(@NotNull final RunConfigurationBase configuration) {
    return configuration instanceof JavaTestConfigurationBase && Registry.is(TEST_DISCOVERY_REGISTRY_KEY);
  }

  @NotNull
  public static Path baseTestDiscoveryPathForProject(Project project) {
    return ProjectUtil.getProjectCachePath(project, "testDiscovery", true);
  }

  @Override
  public void cleanUserData(RunConfigurationBase runConfigurationBase) {
    runConfigurationBase.putUserData(SOCKET_LISTENER_KEY, null);
  }

  private static final Object ourTracesLock = new Object();
  
  private static void processTracesFile(JavaTestConfigurationBase configuration) {
    final String tracesFilePath = getTraceFilePath(configuration);
    final TestDiscoveryIndex testDiscoveryIndex = TestDiscoveryIndex.getInstance(configuration.getProject());
    String moduleName = getConfigurationModuleName(configuration);
    byte frameworkId = configuration.getTestFrameworkId();
    processTracesFile(tracesFilePath, moduleName, frameworkId, testDiscoveryIndex);
  }

  @SuppressWarnings("WeakerAccess")  // called via reflection from com.intellij.InternalTestDiscoveryListener.flushCurrentTraces()
  public static void processTracesFile(String tracesFilePath,
                                       String moduleName,
                                       byte frameworkId,
                                       TestDiscoveryIndex discoveryIndex) {
    final File tracesFile = new File(tracesFilePath);
    synchronized (ourTracesLock) {
      try {
        TestDiscoveryProtocolUtil.readFile(tracesFile, new IdeaTestDiscoveryProtocolReader(discoveryIndex, moduleName, frameworkId));
      }
      catch (IOException e) {
        LOG.error("Can not load " + tracesFilePath, e);
      } finally {
        FileUtil.delete(tracesFile);
      }
    }
  }

  @NotNull
  private static String getConfigurationModuleName(JavaTestConfigurationBase configuration) {
    return configuration.getConfigurationModule().getModuleName();
  }

  @Nullable
  private static TestDiscoveryDataSocketListener tryInstallSocketListener(@NotNull RunConfigurationBase configuration) {
    TestDiscoveryDataSocketListener listener = null;
    if (USE_SOCKET) {
      try {
        JavaTestConfigurationBase javaTestConfigurationBase = (JavaTestConfigurationBase)configuration;
        listener = new TestDiscoveryDataSocketListener(configuration.getProject(),
                                                       getConfigurationModuleName(javaTestConfigurationBase),
                                                       javaTestConfigurationBase.getTestFrameworkId());
        configuration.putUserData(SOCKET_LISTENER_KEY, listener);
      } catch (IOException e) {
        LOG.error(e);
      }
    }
    return listener;
  }
}