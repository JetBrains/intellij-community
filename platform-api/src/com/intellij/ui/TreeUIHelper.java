package com.intellij.ui;

import com.intellij.openapi.components.ServiceManager;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class TreeUIHelper {
  public static TreeUIHelper getInstance() {
    return ServiceManager.getService(TreeUIHelper.class);
  }

  public abstract void installToolTipHandler(JTree tree);

  public abstract void installEditSourceOnDoubleClick(JTree tree);

  public abstract void installTreeSpeedSearch(JTree tree);
  public abstract void installListSpeedSearch(JList list);

  public abstract void installEditSourceOnEnterKeyHandler(JTree tree);

  public abstract void installSmartExpander(JTree tree);

  public abstract void installSelectionSaver(JTree tree);
}