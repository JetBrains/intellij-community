package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CustomContentLayoutSettings {

  Key<CustomContentLayoutSettings> KEY = Key.create("CUSTOM_LAYOUT_OPTIONS");

  @NotNull
  List<AnAction> getActions(@NotNull RunnerContentUi runnerContentUi);

  void restore();
}
