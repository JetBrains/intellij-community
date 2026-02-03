package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.function.Consumer;

public class KotlinVirtualFileProvider {
  private final Reference<OutputSink> mySinkRef;

  public KotlinVirtualFileProvider(@NotNull OutputSink outputSink) {
    mySinkRef = new WeakReference<>(outputSink);
  }

  public void findVfsChildren(@NotNull String parentName, @NotNull Consumer<String> dirConsumer, @NotNull Consumer<String> consumer) {
    OutputSink outputSink = mySinkRef.get();
    if (outputSink != null) {
      for (String path : outputSink.list(parentName, false)) {
        if (ZipOutputBuilder.isDirectoryName(path)) {
          // dirConsumer expects directory name
          int begin = parentName.isEmpty()? 0 : parentName.length() + 1;
          int end = path.length() - 1;
          dirConsumer.accept(path.substring(begin, end));
        }
        else {
          // file consumer expects file path
          consumer.accept(path);
        }
      }
    }
  }

  public int getSize(@NotNull String relativePath) {
    OutputSink outputSink = mySinkRef.get();
    return outputSink == null? 0 :Objects.requireNonNull(outputSink.getFileContent(relativePath)).length;
  }

  @Nullable
  public byte[] getData(@NotNull String path) {
    OutputSink outputSink = mySinkRef.get();
    return outputSink == null? null : outputSink.getFileContent(path);
  }
}