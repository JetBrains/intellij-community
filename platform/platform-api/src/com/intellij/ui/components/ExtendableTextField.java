/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.components;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.util.ui.JBUI.scale;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

/**
 * @author Sergey Malenkov
 */
public class ExtendableTextField extends JBTextField {
  public static final String VARIANT = "extendable";
  private List<Extension> extensions = emptyList();

  public ExtendableTextField() {
    this(null);
  }

  public ExtendableTextField(int columns) {
    this(null, columns);
  }

  public ExtendableTextField(String text) {
    this(text, 20);
  }

  public ExtendableTextField(String text, int columns) {
    super(text, columns);
  }

  public List<Extension> getExtensions() {
    return extensions;
  }

  public void setExtensions(Extension... extensions) {
    setExtensions(asList(extensions));
  }

  public void setExtensions(Collection<Extension> extensions) {
    setExtensions(new ArrayList<>(extensions));
  }

  private void setExtensions(List<Extension> extensions) {
    putClientProperty("JTextField.variant", null);
    this.extensions = unmodifiableList(extensions);
    putClientProperty("JTextField.variant", VARIANT);
  }

  public interface Extension {
    Icon getIcon(boolean hovered);

    default int getIconGap() {
      return scale(5);
    }

    default int getPreferredSpace() {
      Icon icon1 = getIcon(true);
      Icon icon2 = getIcon(false);
      if (icon1 == null && icon2 == null) return 0;
      if (icon1 == null) return getIconGap() + icon2.getIconWidth();
      if (icon2 == null) return getIconGap() + icon1.getIconWidth();
      return getIconGap() + Math.max(icon1.getIconWidth(), icon2.getIconWidth());
    }

    default boolean isIconBeforeText() {
      return false;
    }

    default Runnable getActionOnClick() {
      return null;
    }

    default String getTooltip() {
      return null;
    }
  }
}
