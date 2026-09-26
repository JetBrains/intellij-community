// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.orderedplugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared invocation log for the {@code OrderedTestPlugin*} compiler-plugin fixtures.
 * This class is deliberately not packaged into the on-the-fly plugin jars: the worker's plugin classloader
 * is parent-first, so the registrars resolve this class from the test classpath and all parties,
 * including the test itself, share the same static state.
 */
public final class PluginInvocationRecorder {
  private static final List<String> ourInvocations = Collections.synchronizedList(new ArrayList<>());

  private PluginInvocationRecorder() {
  }

  public static void record(String pluginId) {
    ourInvocations.add(pluginId);
  }

  public static void reset() {
    ourInvocations.clear();
  }

  public static List<String> getInvocations() {
    synchronized (ourInvocations) {
      return List.copyOf(ourInvocations);
    }
  }
}
