// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.jlink;

import com.intellij.java.JavaBundle;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

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

  enum CompressionLevel {
    ZERO("packaging.jlink.compression.zero.level"),
    FIRST("packaging.jlink.compression.first.level"),
    SECOND("packaging.jlink.compression.second.level");

    @Nls
    final String myText;
    CompressionLevel(@NotNull String textKey) {
      myText = JavaBundle.message(textKey);
    }

    @Nullable
    static CompressionLevel getLevelByText(@NotNull String text) {
      return Stream.of(values()).filter(v -> v.myText.equals(text)).findFirst().orElse(null);
    }
  }
}
