// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.addToClasspathAgent;

import com.sun.tools.attach.VirtualMachine;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class AddToClasspathJavaAgent {
  public static void agentmain(String agentArgs, Instrumentation instrumentation) throws Exception {
    System.err.println("AGENTMAIN " + ProcessHandle.current().pid() + " " + agentArgs);

    for (String cp : agentArgs.split(File.pathSeparator)) {
      System.err.println("CLASSPATH ELEMENT: " + cp);

      File file = new File(cp);
      if (!file.exists()) {
        throw new FileNotFoundException("File does not exist: " + file);
      }

      instrumentation.appendToSystemClassLoaderSearch(new JarFile(file));
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 4 || !args[0].equals("attach-agent")) {
      System.err.println("Usage: ./app attach-agent PID AGENT_JAR AGENT_ARGUMENTS");
      System.exit(1);
    }

    String pid = args[1];
    String agentJar = args[2];
    String agentArguments = args[3];

    System.out.println("ADD-TO-CLASSPATH-MAIN: PID:" + pid + " AgentJar:" + agentJar + " Args:" + agentArguments);

    VirtualMachine virtualMachine = VirtualMachine.attach(pid);
    virtualMachine.loadAgent(agentJar, agentArguments);
    virtualMachine.detach();
  }
}
