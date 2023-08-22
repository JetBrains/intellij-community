// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * An interface that allows file chooser actions to interact with the file chooser panel.
 *
 * @see com.intellij.openapi.fileChooser.actions.FileChooserAction
 */
@ApiStatus.NonExtendable
public interface FileChooserPanel {
  @ApiStatus.Internal
  DataKey<FileChooserPanel> DATA_KEY = DataKey.create("file.chooser.panel.api");

  @NotNull JComponent getComponent();

  void load(@Nullable Path path);
  void loadParent();

  boolean hasHistory(boolean backward);
  void loadHistory(boolean backward);

  void reload(@Nullable Path focusOn);
  void reloadAfter(@NotNull ThrowableComputable<@Nullable Path, IOException> task) throws IOException;

  boolean pathBar();
  boolean togglePathBar();

  boolean hiddenFiles();
  boolean toggleHiddenFiles();

  boolean projectDetection();
  boolean toggleProjectDetection();

  @Nullable Path currentDirectory();
  @NotNull List<Path> selectedPaths();
}
