/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.ui.SimpleColoredComponent.formatLink;
import static com.intellij.ui.SimpleColoredComponent.formatText;

public abstract class HtmlListCellRenderer<T> extends ListCellRendererWrapper<T> {
  private StringBuilder myText;

  public HtmlListCellRenderer() { }

  public HtmlListCellRenderer(@SuppressWarnings("UnusedParameters") final ListCellRenderer listCellRenderer) { }

  @Override
  public final void customize(final JList list, final T value, final int index, final boolean selected, final boolean hasFocus) {
    myText = new StringBuilder();
    try {
      doCustomize(list, value, index, selected, hasFocus);

      if (myText.length() == 0) {
        setText(null);
      }
      else {
        myText.insert(0, "<html><body style=\"white-space:nowrap\">");
        myText.append("</body></html>");
        setText(myText.toString());
      }
    }
    finally {
      myText = null;
    }
  }

  protected abstract void doCustomize(final JList list, final T value, final int index, final boolean selected, final boolean hasFocus);

  public void append(@NotNull final String fragment) {
    append(fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes) {
    formatText(myText, fragment, attributes);
  }

  public void appendLink(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, @NotNull final String url) {
    formatLink(myText, fragment, attributes, url);
  }

  public void append(@NotNull final SimpleColoredText text) {
    int length = text.getTexts().size();
    for (int i = 0; i < length; i++) {
      String fragment = text.getTexts().get(i);
      SimpleTextAttributes attributes = text.getAttributes().get(i);
      append(fragment, attributes);
    }
  }
}
