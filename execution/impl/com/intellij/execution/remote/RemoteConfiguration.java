/*
 * @author Jeka
 */
package com.intellij.execution.remote;

import com.intellij.debugger.engine.RemoteStateState;
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.Collection;

public class RemoteConfiguration extends ModuleBasedConfiguration {

  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void readExternal(final Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public boolean USE_SOCKET_TRANSPORT;
  public boolean SERVER_MODE;
  public String SHMEM_ADDRESS;
  public String HOST;
  public String PORT;

  public RemoteConfiguration(final String name, final Project project, ConfigurationFactory configurationFactory) {
    super(name, new RunConfigurationModule(project, true), configurationFactory);
  }

  public RemoteConnection createRemoteConnection() {
    return new RemoteConnection(USE_SOCKET_TRANSPORT, HOST, USE_SOCKET_TRANSPORT ? PORT : SHMEM_ADDRESS, SERVER_MODE);
  }

  public RunProfileState getState(final DataContext context,
                                  final RunnerInfo runnerInfo,
                                  RunnerSettings runnerSettings,
                                  ConfigurationPerRunnerSettings configurationSettings) {
    GenericDebuggerRunnerSettings debuggerSettings = ((GenericDebuggerRunnerSettings)runnerSettings.getData());
    debuggerSettings.LOCAL = false;
    debuggerSettings.setDebugPort(USE_SOCKET_TRANSPORT ? PORT : SHMEM_ADDRESS);
    debuggerSettings.setTransport(USE_SOCKET_TRANSPORT ? DebuggerSettings.SOCKET_TRANSPORT : DebuggerSettings.SHMEM_TRANSPORT);
    return new RemoteStateState(getProject(), createRemoteConnection(), runnerSettings, configurationSettings);
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<RemoteConfiguration> group = new SettingsEditorGroup<RemoteConfiguration>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new RemoteConfigurable());
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel());
    return group;
  }

  protected ModuleBasedConfiguration createInstance() {
    return new RemoteConfiguration(getName(), getProject(), RemoteConfigurationType.getInstance().getConfigurationFactories()[0]);
  }

  public Collection<Module> getValidModules() {
    return getAllModules();
  }

  
}