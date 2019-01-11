// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;

import java.io.File;

public class MemoryAgentManager {
  private static final Logger LOG = Logger.getInstance(MemoryAgentManager.class);

  public static void addMemoryAgent(JavaParameters parameters) {
    if (!Registry.is("debugger.enable.memory.agent")) return;
    ParametersList parametersList = parameters.getVMParametersList();
    if (parametersList.getParameters().stream().anyMatch(x -> x.contains("memory_agent"))) return;
    File extractedAgent = new AgentExtractor().extract();
    if (extractedAgent == null) {
      LOG.warn("Could not extract agent");
      return;
    }
    parametersList.add("-agentpath:" + extractedAgent.getAbsolutePath());
  }
}
