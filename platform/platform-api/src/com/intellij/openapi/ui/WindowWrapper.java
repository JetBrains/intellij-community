package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface WindowWrapper extends Disposable {
  enum Mode {FRAME, MODAL, NON_MODAL}

  void show();

  @Nullable
  Project getProject();

  @NotNull
  JComponent getComponent();

  @NotNull
  Mode getMode();

  @NotNull
  Window getWindow();

  void setTitle(@Nullable String title);

  void setImage(@Nullable Image image);

  void close();
}
