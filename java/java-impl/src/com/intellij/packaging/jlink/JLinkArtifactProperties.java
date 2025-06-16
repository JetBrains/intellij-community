// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.jlink;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class JLinkArtifactProperties extends ArtifactProperties<JLinkArtifactProperties> {
  @OptionTag(converter = CompressionLevelConverter.class)
  public CompressionLevel compressionLevel = CompressionLevel.ZERO;
  public boolean verbose;

  @VisibleForTesting
  public JLinkArtifactProperties() {
  }

  @VisibleForTesting
  public JLinkArtifactProperties(@NotNull CompressionLevel compressionLevel, boolean verbose) {
    this.compressionLevel = compressionLevel;
    this.verbose = verbose;
  }

  @Override
  public ArtifactPropertiesEditor createEditor(@NotNull ArtifactEditorContext context) {
    return new JLinkArtifactPropertiesEditor(this);
  }

  @Override
  public @NotNull JLinkArtifactProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JLinkArtifactProperties state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  /**
   * Same as org.jetbrains.jps.packaging.jlink.JpsJLinkProperties.CompressionLevel
   */
  @ApiStatus.Internal
  public enum CompressionLevel {
    ZERO(0),
    FIRST(1),
    SECOND(2);

    final int myValue;
    CompressionLevel(int value) {
      this.myValue = value;
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
