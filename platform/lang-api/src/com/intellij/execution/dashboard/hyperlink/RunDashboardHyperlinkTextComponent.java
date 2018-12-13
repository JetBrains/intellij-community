// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.hyperlink;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Aleev
 */
public class RunDashboardHyperlinkTextComponent extends RunDashboardHyperlinkComponentBase {
  private String myText;
  private boolean mySelected;
  private boolean myBold;

  public RunDashboardHyperlinkTextComponent(@Nullable RunDashboardHyperlinkComponentBase.LinkListener listener, @NotNull String text) {
    super(listener);
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public void setText(@NotNull String text) {
    myText = text;
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  public void setBold(boolean bold) {
    myBold = bold;
  }

  public void render(@NotNull SimpleColoredComponent renderer) {
    if (myText.isEmpty()) return;

    boolean isActive = mySelected || isAimed();
    SimpleTextAttributes linkTextAttributes = isActive
                                              ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, JBUI.CurrentTheme.Link.linkHoverColor())
                                              : new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.linkColor());

    if (myBold) {
      linkTextAttributes = SimpleTextAttributes.merge(linkTextAttributes, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
    renderer.append(myText, linkTextAttributes, this);
  }
}
