// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness

import org.jetbrains.bazel.wasmjs.test.harness.cli.BazelRunnerInvocationTest
import org.jetbrains.bazel.wasmjs.test.harness.cli.BazelTestEnvironmentTest
import org.jetbrains.bazel.wasmjs.test.harness.cli.TestRunExecutionTestState
import org.jetbrains.bazel.wasmjs.test.harness.runner.JUnitXmlWriterTest
import org.jetbrains.bazel.wasmjs.test.harness.runner.IndexHtmlGeneratorTest
import org.jetbrains.bazel.wasmjs.test.harness.runner.NpmEntryResolverTest
import org.jetbrains.bazel.wasmjs.test.harness.runner.StaticServerTest
import org.jetbrains.bazel.wasmjs.test.harness.runner.TestPageUriTest
import org.jetbrains.bazel.wasmjs.test.harness.runner.TestRunStateTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
  TestRunStateTests::class,
  JUnitXmlWriterTest::class,
  NpmEntryResolverTest::class,
  IndexHtmlGeneratorTest::class,
  StaticServerTest::class,
  TestPageUriTest::class,
  BazelRunnerInvocationTest::class,
  BazelTestEnvironmentTest::class,
  TestRunExecutionTestState::class,
  // Fails when a test class exists on the classpath but is missing from this list.
  AllTestsCompletenessTest::class,
)
class AllTests
