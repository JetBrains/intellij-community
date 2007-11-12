package com.intellij.ui;

import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;

import javax.swing.*;

/**
 * @author yole
 */
public class TreeUIHelperImpl extends TreeUIHelper {
  public void installToolTipHandler(final JTree tree) {
    TreeToolTipHandler.install(tree);
  }

  public void installEditSourceOnDoubleClick(final JTree tree) {
    EditSourceOnDoubleClickHandler.install(tree);
  }

  public void installTreeSpeedSearch(final JTree tree) {
    new TreeSpeedSearch(tree);
  }

  public void installEditSourceOnEnterKeyHandler(final JTree tree) {
    EditSourceOnEnterKeyHandler.install(tree);
  }

  public void installSmartExpander(final JTree tree) {
    SmartExpander.installOn(tree);
  }

  public void installSelectionSaver(final JTree tree) {
    SelectionSaver.installOn(tree);
  }
}