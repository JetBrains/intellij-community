// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.ui.SimpleColoredComponent.formatLink;
import static com.intellij.ui.SimpleColoredComponent.formatText;

/** @deprecated the class is no longer used in API; use {@link SimpleColoredRenderer} instead */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public abstract class HtmlListCellRenderer<T> extends ListCellRendererWrapper<T> {
  private StringBuilder myText;

  public HtmlListCellRenderer() { }

  @Override
  public final void customize(JList list, T value, int index, boolean selected, boolean hasFocus) {
    myText = new StringBuilder();
    try {
      doCustomize(list, value, index, selected, hasFocus);

      if (myText.length() == 0) {
        setText(null);
      }
      else {
        setText(myText.insert(0, "<html><body style=\"white-space:nowrap\">").append("</body></html>").toString());
      }
    }
    finally {
      myText = null;
    }
  }

  protected abstract void doCustomize(JList list, T value, int index, boolean selected, boolean hasFocus);

  public void append(@NotNull String fragment) {
    append(fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
    formatText(myText, fragment, attributes);
  }

  public void appendLink(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, @NotNull String url) {
    formatLink(myText, fragment, attributes, url);
  }

  public void append(@NotNull SimpleColoredText text) {
    int length = text.getTexts().size();
    for (int i = 0; i < length; i++) {
      String fragment = text.getTexts().get(i);
      SimpleTextAttributes attributes = text.getAttributes().get(i);
      append(fragment, attributes);
    }
  }
}