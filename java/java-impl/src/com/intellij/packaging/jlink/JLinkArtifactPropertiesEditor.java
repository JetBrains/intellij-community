// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.jlink;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EnumSet;
import java.util.Optional;

import static com.intellij.packaging.jlink.JLinkArtifactProperties.CompressionLevel;

public class JLinkArtifactPropertiesEditor extends ArtifactPropertiesEditor {
  private final JLinkArtifactProperties myProperties;
  private final Project myProject;

  private ComboBox<Integer> myCompressionLevel;
  private JCheckBox myVerbose;

  public JLinkArtifactPropertiesEditor(@NotNull JLinkArtifactProperties properties, @NotNull Project project) {
    myProperties = properties;
    myProject = project;
  }

  @Nls
  @Override
  public String getTabName() {
    return "JLink";
  }

  @Override
  public @Nullable JComponent createComponent() {
    final FormBuilder builder = new FormBuilder();

    myCompressionLevel = new ComboBox<>(EnumSet.allOf(CompressionLevel.class).stream().map(level -> level.myValue).toArray(Integer[]::new));
    myCompressionLevel.setItem(myProperties.compressionLevel.myValue);
    builder.addLabeledComponent("Compress level", myCompressionLevel);

    myVerbose = new JCheckBox("Enable verbose tracing", myProperties.verbose);
    builder.addComponent(myVerbose);

    return builder.getPanel();
  }

  @Override
  public boolean isModified() {
    if (myProperties.compressionLevel != CompressionLevel.getLevelByValue(myCompressionLevel.getItem())) return true;
    if (myProperties.verbose != myVerbose.isSelected()) return true;
    return false;
  }

  @Override
  public void apply() {
    myProperties.compressionLevel = Optional.ofNullable(myCompressionLevel.getItem())
      .map(i -> CompressionLevel.getLevelByValue(i))
      .orElse(CompressionLevel.ZERO);
    myProperties.verbose = myVerbose.isSelected();
  }
}
