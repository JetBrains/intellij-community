package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

public class KotlinVirtualFileProvider {
  private final OutputSink outputSink;

  public KotlinVirtualFileProvider(@NotNull OutputSink outputSink) {
    this.outputSink = outputSink;
  }

  public void findVfsChildren(@NotNull String parentName, @NotNull Consumer<String> dirConsumer, @NotNull Consumer<String> consumer) {
    for (String path : outputSink.list(parentName, false)) {
      if (ZipOutputBuilder.isDirectoryName(path)) {
        dirConsumer.accept(path);
      }
      else {
        consumer.accept(path);
      }
    }
  }

  public int getSize(@NotNull String relativePath) {
    return Objects.requireNonNull(outputSink.getFileContent(relativePath)).length;
  }

  @Nullable
  public byte[] getData(@NotNull String path) {
    return outputSink.getFileContent(path);
  }
}