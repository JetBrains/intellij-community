// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.charts.jps;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.sun.management.OperatingSystemMXBean;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ChartsBuilderService extends BuilderService {
  public static String COMPILATION_STATISTIC_BUILDER_ID = "jps.compile.statistic";

  @Override
  public @NotNull List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return List.of(new ChartsModuleLevelBuilder());
  }

  private static class ChartsModuleLevelBuilder extends ModuleLevelBuilder {
    private ScheduledFuture<?> myStatisticsReporter = null;
    private Runnable myStatisticsRunnable = null;

    protected ChartsModuleLevelBuilder() {
      super(BuilderCategory.TRANSLATOR);
    }

    @Override
    public ExitCode build(@NotNull CompileContext context,
                          @NotNull ModuleChunk chunk,
                          @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                          @NotNull OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
      return ExitCode.NOTHING_DONE;
    }

    @Override
    public @NotNull List<String> getCompilableFileExtensions() {
      return List.of();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Override
    public @NotNull String getPresentableName() {
      return StringUtil.capitalize(getBuilderName());
    }

    public static @NotNull @NlsSafe String getBuilderName() {
      return "charts";
    }

    @Override
    public void buildStarted(@NotNull CompileContext context) {
      final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
      final OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

      myStatisticsRunnable = () -> context.processMessage(CompileStatisticBuilderMessage.create(memory, os));
      myStatisticsReporter = AppExecutorUtil.createBoundedScheduledExecutorService("IncProjectBuilder metrics reporter", 1)
        .scheduleWithFixedDelay(myStatisticsRunnable, 0, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void buildFinished(@NotNull CompileContext context) {
      if (myStatisticsRunnable != null) {
        myStatisticsRunnable.run();
        myStatisticsRunnable = null;
      }
      if (myStatisticsReporter != null) {
        myStatisticsReporter.cancel(true);
        myStatisticsReporter = null;
      }
    }

    @Override
    public void chunkBuildStarted(@NotNull CompileContext context, @NotNull ModuleChunk chunk) {
      context.processMessage(CompileStatisticBuilderMessage.create(chunk.getTargets(), "STARTED"));
      if (myStatisticsRunnable != null) myStatisticsRunnable.run();
    }

    @Override
    public void chunkBuildFinished(@NotNull CompileContext context, @NotNull ModuleChunk chunk) {
      context.processMessage(CompileStatisticBuilderMessage.create(chunk.getTargets(), "FINISHED"));
      if (myStatisticsRunnable != null) myStatisticsRunnable.run();
    }
  }
}
