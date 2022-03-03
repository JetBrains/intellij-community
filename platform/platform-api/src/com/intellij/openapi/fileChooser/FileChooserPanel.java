// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * An interface that allows file chooser actions to interact with the file chooser panel.
 *
 * @see com.intellij.openapi.fileChooser.actions.FileChooserAction
 */
@ApiStatus.NonExtendable
public interface FileChooserPanel {
  @ApiStatus.Internal
  DataKey<FileChooserPanel> DATA_KEY = DataKey.create("file.chooser.panel.api");

  void load(@Nullable Path path);
  void reload();

  boolean showPathBar();
  void showPathBar(boolean show);

  boolean showHiddenFiles();
  void showHiddenFiles(boolean show);
}
