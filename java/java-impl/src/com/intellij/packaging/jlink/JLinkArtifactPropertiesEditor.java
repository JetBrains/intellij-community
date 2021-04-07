// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.jlink;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Optional;

import static com.intellij.packaging.jlink.JLinkArtifactProperties.CompressionLevel;

final class JLinkArtifactPropertiesEditor extends ArtifactPropertiesEditor {
  private final JLinkArtifactProperties myProperties;
  private final Map<CompressionLevel, @Nls String> myCompressionLevelText;

  private ComboBox<CompressionLevel> myCompressionLevel;
  private JCheckBox myVerbose;

  JLinkArtifactPropertiesEditor(@NotNull JLinkArtifactProperties properties) {
    myProperties = properties;
    myCompressionLevelText = Map.of(CompressionLevel.ZERO, JavaBundle.message("packaging.jlink.compression.zero.level"),
                                    CompressionLevel.FIRST, JavaBundle.message("packaging.jlink.compression.first.level"),
                                    CompressionLevel.SECOND, JavaBundle.message("packaging.jlink.compression.second.level"));
  }

  @Nls
  @Override
  public String getTabName() {
    return JavaBundle.message("packaging.jlink.artifact.name");
  }

  @Override
  public @Nullable JComponent createComponent() {
    final FormBuilder builder = new FormBuilder();

    myCompressionLevel = new ComboBox<>(CompressionLevel.values());
    myCompressionLevel.setItem(myProperties.compressionLevel);
    myCompressionLevel.setRenderer(
      SimpleListCellRenderer.create(myCompressionLevelText.get(CompressionLevel.ZERO),
                                    level -> myCompressionLevelText.getOrDefault(level, String.valueOf(level.myValue))));
    builder.addLabeledComponent(JavaBundle.message("packaging.jlink.compression.level"), myCompressionLevel);

    myVerbose = new JCheckBox(JavaBundle.message("packaging.jlink.verbose.tracing"), myProperties.verbose);
    builder.addComponent(myVerbose);

    return builder.getPanel();
  }

  @Override
  public boolean isModified() {
    if (myProperties.compressionLevel != myCompressionLevel.getItem()) return true;
    if (myProperties.verbose != myVerbose.isSelected()) return true;
    return false;
  }

  @Override
  public void apply() {
    myProperties.compressionLevel = Optional.ofNullable(myCompressionLevel.getItem()).orElse(CompressionLevel.ZERO);
    myProperties.verbose = myVerbose.isSelected();
  }
}
