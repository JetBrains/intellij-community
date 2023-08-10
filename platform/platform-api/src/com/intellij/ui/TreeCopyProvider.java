// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;


public class TreeCopyProvider implements CopyProvider {
  private static final Logger LOG = Logger.getInstance(TreeCopyProvider.class);
  private final JTree myTree;

  public TreeCopyProvider(final JTree tree) {
    myTree = tree;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    try {
      final Clipboard clipboard = myTree.getToolkit().getSystemClipboard();
      myTree.getTransferHandler().exportToClipboard(myTree, clipboard, TransferHandler.COPY);
    }
    catch(Exception ex) {
      // probably don't have clipboard access or something
      LOG.info(ex);
    }
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return myTree.getSelectionPath() != null;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }
}
