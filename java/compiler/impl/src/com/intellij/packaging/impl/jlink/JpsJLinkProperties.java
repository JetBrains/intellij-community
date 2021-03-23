// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.jlink;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.stream.Stream;

public class JpsJLinkProperties extends JpsElementBase<JpsJLinkProperties> {
  public CompressionLevel compressionLevel = CompressionLevel.ZERO;
  public boolean verbose;

  public JpsJLinkProperties() {
  }

  public JpsJLinkProperties(@NotNull JpsJLinkProperties properties) {
    copyToThis(properties);
  }

  @Override
  public @NotNull JpsJLinkProperties createCopy() {
    return new JpsJLinkProperties(this);
  }

  @Override
  public void applyChanges(@NotNull JpsJLinkProperties modified) {
    copyToThis(modified);
  }

  private void copyToThis(@NotNull JpsJLinkProperties copy) {
    compressionLevel = copy.compressionLevel;
    verbose = copy.verbose;
  }

  public enum CompressionLevel {
    ZERO(0),
    FIRST(1),
    SECOND(2);

    public final int myValue;
    CompressionLevel(int value) {
      this.myValue = value;
    }

    @Nullable
    public static CompressionLevel getLevelByValue(int value) {
      return Stream.of(values()).filter(v -> v.myValue == value).findFirst().orElse(null);
    }

    public boolean hasCompression() {
      return this == FIRST || this == SECOND;
    }
  }
}
