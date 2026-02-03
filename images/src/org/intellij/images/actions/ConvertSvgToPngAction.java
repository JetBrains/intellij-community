// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.impl.SvgFileType;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertSvgToPngAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(ConvertSvgToPngAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile svgFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (svgFile == null) return;
    String path = svgFile.getPath();
    File inputFile = new File(svgFile.getPath());
    File outputFile = new File(path + ".png");
    ApplicationManager.getApplication().getService(ConvertSvgToPngService.class).convert(inputFile, outputFile);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean enabled = file != null && FileTypeRegistry.getInstance().isFileOfType(file, SvgFileType.INSTANCE);
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
