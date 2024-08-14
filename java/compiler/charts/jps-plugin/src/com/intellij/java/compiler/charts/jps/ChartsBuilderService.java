// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.jps;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.management.OperatingSystemMXBean;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.service.SharedThreadPool;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ChartsBuilderService extends BuilderService {
  public static final String COMPILATION_STATISTIC_BUILDER_ID = "jps.compile.statistic";
  public static final String COMPILATION_STATUS_BUILDER_ID = "jps.compile.status";

  @Override
  public @NotNull List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return List.of(new ChartsModuleLevelBuilder());
  }

  private static class ChartsModuleLevelBuilder extends ModuleLevelBuilder {
    private static final Logger LOG = Logger.getInstance(ChartsModuleLevelBuilder.class);

    protected ChartsModuleLevelBuilder() {
      super(BuilderCategory.TRANSLATOR);
      LOG.debug(CompilationChartsJpsBundle.message("compilation.charts.jps.registered"));
    }

    @Override
    public ExitCode build(@NotNull CompileContext context,
                          @NotNull ModuleChunk chunk,
                          @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                          @NotNull OutputConsumer outputConsumer) {
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
      context.processMessage(new CompilationStatusBuilderMessage("START"));
      SharedThreadPool.getInstance().execute(new CompileStatisticService(context));
    }

    @Override
    public void buildFinished(@NotNull CompileContext context) {
      context.processMessage(new CompilationStatusBuilderMessage("FINISH"));
    }

    @Override
    public void chunkBuildStarted(@NotNull CompileContext context, @NotNull ModuleChunk chunk) {
      context.processMessage(CompileStatisticBuilderMessage.create(chunk.getTargets(), "STARTED"));
    }

    @Override
    public void chunkBuildFinished(@NotNull CompileContext context, @NotNull ModuleChunk chunk) {
      context.processMessage(CompileStatisticBuilderMessage.create(chunk.getTargets(), "FINISHED"));
    }
  }

  private static class CompileStatisticService implements Runnable {
    private final MemoryMXBean memory;
    private final OperatingSystemMXBean os;
    private final CompileContext context;

    private CompileStatisticService(CompileContext context) {
      memory = ManagementFactory.getMemoryMXBean();
      os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
      this.context = context;
    }

    @Override
    public void run() {
      while (true) {
        try {
          BuildMessage message = CompileStatisticBuilderMessage.create(memory, os);
          if (message != null) context.processMessage(message);
          TimeUnit.SECONDS.sleep(1);
        }
        catch (Throwable e) {
          break;
        }
      }
    }
  }
}
