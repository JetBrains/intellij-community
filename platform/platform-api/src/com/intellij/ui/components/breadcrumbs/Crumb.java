// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.breadcrumbs;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.Action;
import javax.swing.Icon;
import java.util.Collection;

/**
 * @author Sergey.Malenkov
 */
public interface Crumb {
  default Icon getIcon() { return null; }

  default String getText() { return toString(); }

  default String getTooltip() { return null; }

  /**
   * @return a list of actions for context menu
   */
  @NotNull
  default Collection<? extends Action> getContextActions() {
    return ContainerUtil.emptyList();
  }


  class Impl implements Crumb {
    private final Icon icon;
    private final String text;
    private final String tooltip;
    private final Collection<? extends Action> actions;

    public Impl(Icon icon, String text, String tooltip, Action... actions) {
      this.icon = icon;
      this.text = text;
      this.tooltip = tooltip;
      this.actions = actions == null || actions.length == 0
                     ? ContainerUtil.emptyList()
                     : ContainerUtil.list(actions);
    }

    @Override
    public Icon getIcon() {
      return icon;
    }

    @Override
    public String getTooltip() {
      return tooltip;
    }

    @NotNull
    @Override
    public Collection<? extends Action> getContextActions() {
      return actions;
    }

    @Override
    public String toString() {
      return text;
    }
  }
}
