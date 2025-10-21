// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.lang.invoke.MethodHandle;
import kotlin.Pair;

import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser.RegisteredPluginInfo;
import org.jetbrains.kotlin.compiler.plugin.*;

class RegisteredPluginInfoCreator {
  static RegisteredPluginInfo createPluginInfo(Pair<MethodHandle, MethodHandle> data,
                                               Map<String, List<CliOptionValue>> internalPluginIdToPluginOptions) throws Throwable {
    MethodHandle processorFactory = data.getSecond();
    CommandLineProcessor processor;
    if (processorFactory != null) {
      processor = (CommandLineProcessor) processorFactory.invoke();
    }
    else {
      processor = null;
    }
    List<CliOptionValue> pluginOptions = null;
    if (processor != null) {
      pluginOptions = internalPluginIdToPluginOptions.get(processor.getPluginId());
    }
    return new RegisteredPluginInfo(
      null, //componentRegistrar
      (CompilerPluginRegistrar) data.getFirst().invoke(),
      processor,
      pluginOptions != null ? pluginOptions : Collections.emptyList()
    );
  }
}