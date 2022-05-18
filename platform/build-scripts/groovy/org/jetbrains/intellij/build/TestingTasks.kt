// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import com.intellij.testFramework.SkipInHeadlessEnvironment;
import org.jetbrains.intellij.build.impl.TestingTasksImpl;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public abstract class TestingTasks {
  /**
   * @param defaultMainModule    main module to be used instead of {@link TestingOptions#mainModule} if it isn't specified
   * @param rootExcludeCondition if not {@code null} tests from modules which sources are fit this predicate will be skipped
   */
  public abstract void runTests(List<String> additionalJvmOptions, String defaultMainModule, Predicate<File> rootExcludeCondition);

  /**
   * Run all tests annotated with {@link SkipInHeadlessEnvironment}
   */
  public abstract void runTestsSkippedInHeadlessEnvironment();

  public abstract Path createSnapshotsDirectory();

  /**
   * <p>Updates given jvm args, system properties and classpath with common parameters used for running tests
   * (Xmx, debugging, config path) etc.
   *
   * <p>The values passed as parameters have priority over the default ones, added in this method.
   *
   * <p>Mutates incoming collections.
   */
  public abstract void prepareEnvForTestRun(List<String> jvmArgs,
                                            Map<String, String> systemProperties,
                                            List<String> classPath,
                                            boolean remoteDebugging);

  public static TestingTasks create(CompilationContext context, TestingOptions options) {
    return new TestingTasksImpl(context, options);
  }

  public static TestingTasks create(CompilationContext context) {
    return TestingTasks.create(context, new TestingOptions());
  }
}
