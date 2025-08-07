// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.testFramework;

import java.nio.file.Path;

/**
 * Test-only service used to resolve debugger agent artifacts when running tests (especially under Bazel).
 * <p>
 * In production and regular dev runs, the agent is resolved via standard mechanisms (e.g., BuildDependenciesJps).
 * But Bazel tests execute in a hermetic sandbox where downloads and arbitrary file access are restricted and all
 * inputs must be declared as dependencies (runfiles). To make the agent available in that environment, Bazel wires
 * the artifact into the test's classpath/runfiles. This service, implemented in test sources, knows how to locate
 * that artifact and provide its path to the runtime code.
 * <p>
 * The implementation must be present on the tests classpath and is discovered via {@link java.util.ServiceLoader}.
 */
public interface TestDebuggerAgentArtifactsProvider {

  Path getDebuggerAgentJar();

}
