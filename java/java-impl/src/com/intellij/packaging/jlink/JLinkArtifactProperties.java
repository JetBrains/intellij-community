// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.jlink;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

final class JLinkArtifactProperties extends ArtifactProperties<JLinkArtifactProperties> {
  public CompressionLevel compressionLevel = CompressionLevel.ZERO;
  public boolean verbose;

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
  enum CompressionLevel {
    ZERO(0),
    FIRST(1),
    SECOND(2);

    final int myValue;
    CompressionLevel(int value) {
      this.myValue = value;
    }
  }
}
