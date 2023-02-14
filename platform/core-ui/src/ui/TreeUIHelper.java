// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.Convertor;

import javax.swing.*;
import javax.swing.tree.TreePath;

public abstract class TreeUIHelper {
  public static TreeUIHelper getInstance() {
    return ApplicationManager.getApplication().getService(TreeUIHelper.class);
  }

  /**
   * @deprecated use JBTree class instead, it will automatically configure tool tips
   */
  @Deprecated(forRemoval = true)
  public abstract void installToolTipHandler(JTree tree);

  public abstract void installEditSourceOnDoubleClick(JTree tree);

  public abstract void installTreeSpeedSearch(JTree tree);
  public abstract void installListSpeedSearch(JList<?> list);
  public abstract void installTreeSpeedSearch(JTree tree, Convertor<? super TreePath, String> convertor, boolean canExpand);
  public abstract <T> void installListSpeedSearch(JList<T> list, Convertor<? super T, String> convertor);

  public abstract void installEditSourceOnEnterKeyHandler(JTree tree);

  public abstract void installSmartExpander(JTree tree);

  public abstract void installSelectionSaver(JTree tree);
}