// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.jlink;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.packaging.jlink.JLinkArtifactProperties.CompressionLevel;

final class JLinkArtifactPropertiesEditor extends ArtifactPropertiesEditor {
  private final JLinkArtifactProperties myProperties;
  private final CompressionLevelRenderer myCompressionLevelRenderer = new CompressionLevelRenderer();

  private ComboBox<String> myCompressionLevel;
  private JCheckBox myVerbose;

  JLinkArtifactPropertiesEditor(@NotNull JLinkArtifactProperties properties) {
    myProperties = properties;
  }

  @Nls
  @Override
  public String getTabName() {
    return JavaBundle.message("packaging.jlink.artifact.name");
  }

  @Override
  public @Nullable JComponent createComponent() {
    final FormBuilder builder = new FormBuilder();

    myCompressionLevel = new ComboBox<>(myCompressionLevelRenderer.getLevels().toArray(String[]::new));
    myCompressionLevel.setItem(myCompressionLevelRenderer.getTextByLevel(myProperties.compressionLevel));
    builder.addLabeledComponent(JavaBundle.message("packaging.jlink.compression.level"), myCompressionLevel);

    myVerbose = new JCheckBox(JavaBundle.message("packaging.jlink.verbose.tracing"), myProperties.verbose);
    builder.addComponent(myVerbose);

    return builder.getPanel();
  }

  @Override
  public boolean isModified() {
    if (myProperties.compressionLevel != myCompressionLevelRenderer.getLevelByText(myCompressionLevel.getItem())) return true;
    if (myProperties.verbose != myVerbose.isSelected()) return true;
    return false;
  }

  @Override
  public void apply() {
    myProperties.compressionLevel = Optional.ofNullable(myCompressionLevel.getItem())
      .map(i -> myCompressionLevelRenderer.getLevelByText(i))
      .orElse(CompressionLevel.ZERO);
    myProperties.verbose = myVerbose.isSelected();
  }

  private static class CompressionLevelRenderer {
    private final EnumMap<CompressionLevel, @Nls String> myTextByLevel = new EnumMap<>(CompressionLevel.class);
    private final Map<String, CompressionLevel> myLevelByText = new HashMap<>();

    private CompressionLevelRenderer() {
      String zeroLevel = JavaBundle.message("packaging.jlink.compression.zero.level");
      myTextByLevel.put(CompressionLevel.ZERO, zeroLevel);
      myLevelByText.put(zeroLevel, CompressionLevel.ZERO);

      String firstLevel = JavaBundle.message("packaging.jlink.compression.first.level");
      myTextByLevel.put(CompressionLevel.FIRST, firstLevel);
      myLevelByText.put(firstLevel, CompressionLevel.FIRST);

      String secondLevel = JavaBundle.message("packaging.jlink.compression.second.level");
      myTextByLevel.put(CompressionLevel.SECOND, secondLevel);
      myLevelByText.put(secondLevel, CompressionLevel.SECOND);
    }

    @NotNull
    List<String> getLevels() {
      return List.copyOf(myTextByLevel.values());
    }

    @NotNull
    @Nls
    String getTextByLevel(@Nullable CompressionLevel level) {
      if (level == null) return myTextByLevel.get(CompressionLevel.ZERO);
      return myTextByLevel.getOrDefault(level, myTextByLevel.get(CompressionLevel.ZERO));
    }

    @Nullable
    CompressionLevel getLevelByText(@NotNull String text) {
      return myLevelByText.get(text);
    }
  }
}
