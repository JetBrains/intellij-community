// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.testFramework;

import java.nio.file.Path;

/**
 * Test-only service used to resolve debugger agent artifacts when running tests.
 * <p>
 * In production and regular dev runs, the agent is resolved via standard mechanisms (e.g., BuildDependenciesJps).
 * Bazel tests execute in a hermetic sandbox where all inputs must be declared as runfiles. This service is implemented
 * in test sources. It reports whether its runfiles are available and resolves the agent from them.
 * <p>
 * The implementation must be present on the tests classpath and is discovered via {@link java.util.ServiceLoader}.
 */
public interface TestDebuggerAgentArtifactsProvider {

  boolean isAvailable();

  Path getDebuggerAgentJar();

}
