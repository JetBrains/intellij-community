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
package com.intellij.ui.components.breadcrumbs;

import javax.swing.Icon;

/**
 * @author Sergey.Malenkov
 */
public interface Crumb {
  default Icon getIcon() { return null; }

  default String getText() { return toString(); }

  default String getTooltip() { return null; }

  class Impl implements Crumb {
    private final Icon icon;
    private final String text;
    private final String tooltip;

    public Impl(Icon icon, String text, String tooltip) {
      this.icon = icon;
      this.text = text;
      this.tooltip = tooltip;
    }

    @Override
    public Icon getIcon() {
      return icon;
    }

    @Override
    public String getTooltip() {
      return tooltip;
    }

    @Override
    public String toString() {
      return text;
    }
  }
}
