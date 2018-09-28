// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.PathUtil;
import com.jetbrains.sa.SaJdwp;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.tools.jdi.SocketListeningConnector;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author egor
 */
public class SAJDWPRemoteConnection extends PidRemoteConnection {
  private static final Logger LOG = Logger.getInstance(SAJDWPRemoteConnection.class);

  public SAJDWPRemoteConnection(String pid) {
    super(pid);
    setServerMode(true);
    setAddress("0");
  }

  @Override
  public Connector getConnector(DebugProcessImpl debugProcess) {
    return new SAJDWPListeningConnector(debugProcess);
  }

  public static boolean isAvailable() {
    return true;
  }

  public class SAJDWPListeningConnector extends SocketListeningConnector {
    private final DebugProcessImpl myDebugProcess;
    private String myCurrentAddress;

    public SAJDWPListeningConnector(DebugProcessImpl process) {
      myDebugProcess = process;
    }

    @Override
    public String startListening(Map<String, ? extends Argument> arguments) throws IOException, IllegalConnectorArgumentsException {
      myCurrentAddress = super.startListening(null, arguments);
      return myCurrentAddress;
    }

    @Override
    public VirtualMachine accept(Map<String, ? extends Argument> map) throws IOException, IllegalConnectorArgumentsException {
      try {
        List<String> commands = SaJdwp.getServerProcessCommand(getPid(), myCurrentAddress, false, PathUtil.getJarPathForClass(SaJdwp.class));
        GeneralCommandLine commandLine = new GeneralCommandLine(commands);
        startServer(commandLine, false);
      } catch (IOException e) {
        throw e;
      } catch (Exception e) {
        throw new IOException("Unable to start sa-jdwp server", e);
      }

      return super.accept(map);
    }

    private void startServer(GeneralCommandLine commandLine, boolean sudo) throws Exception {
      if (sudo) {
        commandLine = ExecUtil.sudoCommand(commandLine, "Please enter your password to attach with su privileges: ");
      }
      GeneralCommandLine finalCommandLine = commandLine;
      new CapturingProcessHandler(commandLine) {
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
