package com.intellij.tools.build.bazel.jvmIncBuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.NodeSource;

import java.util.Set;

public interface ResourceGroup extends NodeSourceSnapshot{
  ResourceGroup EMPTY = new ResourceGroup() {
    @Override
    public @NotNull Iterable<@NotNull NodeSource> getElements() {
      return Set.of();
    }

    @Override
    public @NotNull String getDigest(NodeSource src) {
      return "";
    }

    @Override
    public String getStripPrefix() {
      return "";
    }

    @Override
    public String getAddPrefix() {
      return "";
    }
  };

  String getStripPrefix();

  String getAddPrefix();

}
