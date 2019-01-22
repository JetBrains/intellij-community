// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.actions;

import com.intellij.bootRuntime.bundles.Runtime;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task.Modal;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class BinTrayDialogAction extends AbstractAction {
  protected final Supplier<Runtime> selectedItemSupplier;
  protected final Runnable updateCallback;

  public BinTrayDialogAction(@NonNls String name, Supplier<Runtime> selectedItemSupplier, Runnable updateCallback) {
    super(name);
    this.selectedItemSupplier = selectedItemSupplier;
    this.updateCallback = updateCallback;
  }

  protected void runWithProgress(String title, final Consumer<ProgressIndicator> progressIndicatorConsumer) {
    ProgressManager.getInstance().run(new Modal(null, title, false) {
      @Override
      public void run(@NotNull ProgressIndicator progressIndicator) {
        progressIndicatorConsumer.accept(progressIndicator);
      }
    });
  }

  public String getFileName() {
    return selectedItemSupplier.get().getFileName();
  }

  public File getDownloadDirectoryFile() {
    String filePath = selectedItemSupplier.get().getFileName();
    return new File(PathManager.getPluginTempPath(), filePath);
  }
}
