// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView.smartTree;

import com.intellij.openapi.util.NlsActions.ActionDescription;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The default implementation of the ActionPresentation interface, specifying the presentation
 * information for a grouping, sorting or filtering action displayed in a generic tree.
 */

public class ActionPresentationData implements ActionPresentation {
  private final @ActionText String myText;
  private final @ActionDescription String myDescription;
  private final Icon myIcon;

  /**
   * Creates an action presentation with the specified text, description and icon.
   *
   * @param text        the name of the action, displayed in the tooltip for the toolbar button.
   * @param description the description of the action, displayed in the status bar when the mouse
   *                    is over the toolbar button.
   * @param icon        the icon for the action, displayed on the toolbar button.
   */

  public ActionPresentationData(@NotNull @ActionText String text, @ActionDescription String description, Icon icon) {
    myText = text;
    myDescription = description;
    myIcon = icon;
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }
}
