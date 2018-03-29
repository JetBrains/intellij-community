// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.breadcrumbs;

import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.Action;
import javax.swing.Icon;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

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
  default List<? extends Action> getContextActions() {
    return emptyList();
  }


  class Impl implements Crumb {
    private final Icon icon;
    private final String text;
    private final String tooltip;
    private final List<? extends Action> actions;

    public Impl(@NotNull BreadcrumbsProvider provider, @NotNull PsiElement element) {
      this(provider.getElementIcon(element),
           provider.getElementInfo(element),
           provider.getElementTooltip(element),
           provider.getContextActions(element));
    }

    public Impl(Icon icon, String text, String tooltip, Action... actions) {
      this(icon, text, tooltip, actions == null || actions.length == 0 ? null : asList(actions));
    }

    public Impl(Icon icon, String text, String tooltip, List<? extends Action> actions) {
      this.icon = icon;
      this.text = text;
      this.tooltip = tooltip;
      this.actions = actions;
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
    public List<? extends Action> getContextActions() {
      return actions != null ? actions : emptyList();
    }

    @Override
    public String toString() {
      return text;
    }
  }
}
