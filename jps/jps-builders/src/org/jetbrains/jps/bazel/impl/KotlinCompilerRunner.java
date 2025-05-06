// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.jps.bazel.*;
import org.jetbrains.jps.bazel.runner.CompilerRunner;
import org.jetbrains.jps.bazel.runner.OutputSink;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.common.messages.MessageCollectorImpl;
import org.jetbrains.kotlin.cli.pipeline.AbstractCliPipeline;
import org.jetbrains.kotlin.config.Services;
import org.jetbrains.kotlin.incremental.*;
import org.jetbrains.kotlin.incremental.components.*;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents;
import org.jetbrains.kotlin.progress.CompilationCanceledException;
import org.jetbrains.kotlin.progress.CompilationCanceledStatus;

import java.util.List;

import static org.jetbrains.kotlin.cli.common.ExitCode.OK;


public class KotlinCompilerRunner implements CompilerRunner {
  private final BuildContext myContext;

  public KotlinCompilerRunner(BuildContext context, StorageManager storageManager) {
    myContext = context;
  }

  @Override
  public String getName() {
    return "Kotlinc Runner";
  }

  @Override
  public boolean canCompile(NodeSource src) {
    return src.toString().endsWith(".kt");
  }

  @Override
  public ExitCode compile(Iterable<NodeSource> sources, DiagnosticSink diagnostic, OutputSink out) {
    try {
      K2JVMCompilerArguments kotlinCompilerArgs = buildKotlinCompilerArguments(myContext);
      Services services = buildServices(kotlinCompilerArgs.getModuleName());
      MessageCollector messageCollector = new MessageCollectorImpl();
      //outputItemCollector
      AbstractCliPipeline<K2JVMCompilerArguments> pipeline = createPipeline();
      org.jetbrains.kotlin.cli.common.ExitCode exitCode = pipeline.execute(kotlinCompilerArgs, services, messageCollector);
      if (exitCode != OK) {
        return ExitCode.ERROR;
      }
    }
    catch (Exception e) {
      diagnostic.report(Message.create(this, e));
      return ExitCode.ERROR;
    }
    return ExitCode.OK;
  }

  private Services buildServices(String moduleName) {
    Services.Builder builder = new Services.Builder();
    builder.register(LookupTracker.class, new LookupTrackerImpl(LookupTracker.DO_NOTHING.INSTANCE));
    builder.register(ExpectActualTracker.class, new ExpectActualTrackerImpl());
    builder.register(InlineConstTracker.class, new InlineConstTrackerImpl());
    builder.register(EnumWhenTracker.class, new EnumWhenTrackerImpl());
    builder.register(ImportTracker.class, new ImportTrackerImpl());
    builder.register(IncrementalCompilationComponents.class,
                     new KotlinIncrementalCompilationComponents(moduleName, new KotlinIncrementalCacheImpl()));

    builder.register(CompilationCanceledStatus.class, new CompilationCanceledStatus() {
      @Override
      public void checkCanceled() {
        if (myContext.isCanceled()) throw new CompilationCanceledException();
      }
    });

    return builder.build();
  }

  private AbstractCliPipeline<K2JVMCompilerArguments> createPipeline() {
    return null; // TODO: Implement proper pipeline creation
  }

  private static K2JVMCompilerArguments buildKotlinCompilerArguments(BuildContext myContext) {
    List<String> builderArgs = myContext.getBuilderArgs().getKotlinCompilerArgs();
    // TODO: transform BuilderArgs to K2JVMCompilerArguments
    return new K2JVMCompilerArguments();
  }
}
