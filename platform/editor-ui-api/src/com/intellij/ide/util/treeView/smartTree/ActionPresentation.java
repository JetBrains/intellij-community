// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView.smartTree;

import com.intellij.openapi.util.NlsActions.ActionDescription;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The presentation information for a grouping, sorting or filtering action displayed in
 * a generic tree.
 *
 * @see TreeAction#getPresentation()
 * @see ActionPresentationData
 */

public interface ActionPresentation {
  /**
   * Returns the name of the action, displayed in the tooltip for the toolbar button.
   *
   * @return the action name.
   */
  @NotNull
  @ActionText
  String getText();

  /**
   * Returns the description of the action, displayed in the status bar when the mouse
   * is over the toolbar button.
   *
   * @return the action description.
   */
  @ActionDescription
  String getDescription();

  /**
   * Returns the icon for the action, displayed on the toolbar button.
   *
   * @return the action icon.
   */
  Icon getIcon();
}
