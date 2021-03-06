// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
*/
public class MemberChooserObjectBase implements MemberChooserObject {
  private final @NlsContexts.Label String myText;
  private final Icon myIcon;

  public MemberChooserObjectBase(@Nullable final @NlsContexts.Label String text) {
    this(text, null);
  }

  public MemberChooserObjectBase(@Nullable final @NlsContexts.Label String text, @Nullable final Icon icon) {
    myText = StringUtil.notNullize(text);
    myIcon = icon;
  }

  @Override
  public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
    SpeedSearchUtil.appendFragmentsForSpeedSearch(tree, getText(), getTextAttributes(tree), false, component);
    component.setIcon(myIcon);
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  protected SimpleTextAttributes getTextAttributes(JTree tree) {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, RenderingUtil.getForeground(tree));
  }

}
