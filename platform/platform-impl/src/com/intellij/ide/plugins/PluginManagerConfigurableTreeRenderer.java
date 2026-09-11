// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.ide.plugins.newui.PluginUpdateSubscription;
import com.intellij.ide.plugins.newui.PluginUpdatesEvent;
import com.intellij.openapi.options.ConfigurableTreeRenderer;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.components.Badge;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.event.AncestorEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class PluginManagerConfigurableTreeRenderer extends AncestorListenerAdapter implements ConfigurableTreeRenderer, Consumer<PluginUpdatesEvent> {
  private final Badge myCountBadge = new Badge("0", Badge.ColorType.GRAY_SECONDARY);
  private final JLabel myCountLabel = new JLabel(myCountBadge);

  private PluginUpdateSubscription myUpdateSubscription;
  private SimpleTree myTree;
  private @NlsSafe String myCountValue;

  @Override
  public @Nullable Pair<Component, Layout> getDecorator(@NotNull JComponent tree,
                                                        @Nullable UnnamedConfigurable configurable,
                                                        boolean selected) {
    if (configurable instanceof PluginManagerConfigurable pluginManagerConfigurable) {
      pluginManagerConfigurable.setOpenSourceFromSettings();
    }
    if (myTree == null) {
      myUpdateSubscription = PluginUpdatesService.getInstance().subscribe(this);
      tree.addAncestorListener(this);
      myTree = (SimpleTree)tree;
    }

    if (myCountValue == null) {
      return null;
    }

    String count = StringUtil.defaultIfEmpty(myCountValue, "0");
    myCountBadge.setText(count);
    myCountLabel.getAccessibleContext().setAccessibleName(count);

    return Pair.create(
      myCountLabel, (renderer, bounds, text, right, textBaseline) -> {
        Dimension size = renderer.getPreferredSize();
        int preferredWidth = size.width;
        int preferredHeight = size.height;

        renderer.setBounds(
          right.x + (right.width - preferredWidth) / 2 - JBUIScale.scale(20),
          bounds.y + (bounds.height - preferredHeight) / 2,
          preferredWidth,
          preferredHeight
        );
        renderer.doLayout();
      }
    );
  }

  @Override
  public void ancestorRemoved(AncestorEvent event) {
    myUpdateSubscription.cancel();
  }

  @Override
  public void accept(PluginUpdatesEvent results) {
    String oldCountValue = myCountValue;
    int countValue = results == null ? 0 : results.getEnabledUpdates().size();
    myCountValue = countValue == 0 ? null : Integer.toString(countValue);
    if (myTree != null && !StringUtil.equals(oldCountValue, myCountValue)) {
      myTree.repaint();
    }
  }
}
