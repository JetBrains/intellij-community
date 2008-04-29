/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
*/
public class MemberChooserObjectBase implements MemberChooserObject {
  private final String myText;
  private final Icon myIcon;

  public MemberChooserObjectBase(final String text) {
    this(text, null);
  }

  public MemberChooserObjectBase(final String text, @Nullable final Icon icon) {
    myText = text;
    myIcon = icon;
  }

  public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
    component.append(myText, getTextAttributes(tree));
    component.setIcon(myIcon);
  }

  public String getText() {
    return myText;
  }

  protected SimpleTextAttributes getTextAttributes(JTree tree) {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, tree.getForeground());
  }

}
