package com.intellij.execution.ui.layout;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface LayoutStateDefaults {

  LayoutStateDefaults initTabDefaults(int tabId, @Nullable String defaultTabText, @Nullable Icon defaultTabIcon);

  LayoutStateDefaults initFocusContent(@NotNull String id, final String condition);

}