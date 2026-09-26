// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.packaging.jlink;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.jps.model.ex.JpsElementBase;

@ApiStatus.Internal
public final class JpsJLinkProperties extends JpsElementBase<JpsJLinkProperties> {
  @OptionTag(converter = CompressionLevelConverter.class)
  public CompressionLevel compressionLevel = CompressionLevel.ZERO;
  public boolean verbose;

  JpsJLinkProperties() {
  }

  JpsJLinkProperties(@NotNull JpsJLinkProperties properties) {
    copyToThis(properties);
  }

  @VisibleForTesting
  public JpsJLinkProperties(@NotNull CompressionLevel compressionLevel, boolean verbose) {
    this.compressionLevel = compressionLevel;
    this.verbose = verbose;
  }

  @Override
  public @NotNull JpsJLinkProperties createCopy() {
    return new JpsJLinkProperties(this);
  }

  private void copyToThis(@NotNull JpsJLinkProperties copy) {
    compressionLevel = copy.compressionLevel;
    verbose = copy.verbose;
  }

  @ApiStatus.Internal
  public enum CompressionLevel {
    ZERO(0),
    FIRST(1),
    SECOND(2);

    final int myValue;
    CompressionLevel(int value) {
      this.myValue = value;
    }

    boolean hasCompression() {
      return this == FIRST || this == SECOND;
    }
  }

  private static final class CompressionLevelConverter extends Converter<CompressionLevel> {
    @Override
    public @Nullable CompressionLevel fromString(@NotNull String value) {
      int levelVal;
      try {
        levelVal = Integer.parseInt(value);
      }
      catch (NumberFormatException e) {
        return null;
      }
      return ContainerUtil.find(CompressionLevel.values(), level -> level.myValue == levelVal);
    }

    @Override
    public @NotNull String toString(@NotNull CompressionLevel value) {
      return String.valueOf(value.myValue);
    }
  }
}
