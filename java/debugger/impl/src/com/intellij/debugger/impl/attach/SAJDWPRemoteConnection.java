// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.connect.Transport;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author egor
 */
public class SAJDWPRemoteConnection extends PidRemoteConnection {
  private static final Logger LOG = Logger.getInstance(SAJDWPRemoteConnection.class);
  private final List<String> myCommands;

  public SAJDWPRemoteConnection(String pid, List<String> commands) {
    super(pid);
    setServerMode(true);
    setDebuggerPort("0");
    myCommands = commands;
  }

  @Override
  public Connector getConnector(DebugProcessImpl debugProcess) throws ExecutionException {
    return new SAJDWPListeningConnector(debugProcess);
  }

  public class SAJDWPListeningConnector implements ListeningConnector {
    private final DebugProcessImpl myDebugProcess;
    private final ListeningConnector mySocketListeningConnector;

    public SAJDWPListeningConnector(DebugProcessImpl process) throws ExecutionException {
      mySocketListeningConnector = (ListeningConnector)DebugProcessImpl.findConnector(DebugProcessImpl.SOCKET_LISTENING_CONNECTOR_NAME);
      myDebugProcess = process;
    }

    @Override
    public String startListening(Map<String, ? extends Argument> arguments) throws IOException, IllegalConnectorArgumentsException {
      String address = mySocketListeningConnector.startListening(arguments);
      myCommands.set(myCommands.size() - 1, address); // last argument is a port, replace with the real value
      return address;
    }

    @Override
    public VirtualMachine accept(Map<String, ? extends Argument> map) throws IOException, IllegalConnectorArgumentsException {
      try {
        GeneralCommandLine commandLine = new GeneralCommandLine(myCommands);
        if (Registry.is("debugger.sa.jdwp.debug")) {
          commandLine.getParametersList().replaceOrPrepend("-agentlib:jdwp", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n");
        }
        startServer(commandLine, false);
      } catch (IOException e) {
        throw e;
      } catch (Exception e) {
        throw new IOException("Unable to start sa-jdwp server", e);
      }

      return mySocketListeningConnector.accept(map);
    }

    @Override
    public boolean supportsMultipleConnections() {
      return mySocketListeningConnector.supportsMultipleConnections();
    }

    @Override
    public void stopListening(Map<String, ? extends Argument> arguments) throws IOException, IllegalConnectorArgumentsException {
      mySocketListeningConnector.stopListening(arguments);
    }

    @Override
    public String name() {
      return "SAJDWPListeningConnector";
    }

    @Override
    public String description() {
      return "SAJDWPListeningConnector";
    }

    @Override
    public Transport transport() {
      return mySocketListeningConnector.transport();
    }

    @Override
    public Map<String, Argument> defaultArguments() {
      return mySocketListeningConnector.defaultArguments();
    }

    private void startServer(GeneralCommandLine commandLine, boolean sudo) throws Exception {
      if (sudo) {
        commandLine = ExecUtil.sudoCommand(commandLine, "Please enter your password to attach with su privileges: ");
      }
      GeneralCommandLine finalCommandLine = commandLine;
      new CapturingProcessHandler.Silent(commandLine) {
        @Override
        protected CapturingProcessAdapter createProcessAdapter(ProcessOutput processOutput) {
          return new CapturingProcessAdapter(processOutput) {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
              myDebugProcess.printToConsole(event.getText());
            }

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
              if (!sudo && myDebugProcess.isInInitialState()) {
                try {
                  startServer(finalCommandLine, true);
                }
                catch (Exception e) {
                  LOG.error(e);
                }
              }
              else {
                myDebugProcess.stop(true);
              }
            }
          };
        }
      }.startNotify();
    }
  }
}