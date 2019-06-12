// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class GridCellPluginComponent extends CellPluginComponent {
  private final MyPluginModel myPluginModel;
  private JLabel myLastUpdated;
  private JLabel myDownloads;
  private JLabel myRating;
  private final JButton myInstallButton = new InstallButton(false);
  private JComponent myLastComponent;
  private List<TagComponent> myTagComponents;
  private ProgressIndicatorEx myIndicator;

  public GridCellPluginComponent(@NotNull MyPluginModel pluginsModel,
                                 @NotNull IdeaPluginDescriptor plugin,
                                 @NotNull TagBuilder tagBuilder) {
    super(plugin);
    myPluginModel = pluginsModel;
    pluginsModel.addComponent(this);

    JPanel container = new NonOpaquePanel(new BorderLayout(JBUIScale.scale(10), 0));
    add(container);
    addIconComponent(container, BorderLayout.WEST);

    JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(PluginManagerConfigurableNew.offset5(), JBUIScale.scale(181)));
    container.add(centerPanel);

    addNameComponent(centerPanel);
    addTags(centerPanel, tagBuilder);
    addDescriptionComponent(centerPanel, PluginManagerConfigurableNew.getShortDescription(myPlugin, false), new LineFunction(3, true));

    createMetricsPanel(centerPanel);

    addInstallButton();

    setOpaque(true);
    setBorder(JBUI.Borders.empty(10));

    setLayout(new AbstractLayoutManager() {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        Dimension size = container.getPreferredSize();
        size.height += PluginManagerConfigurableNew.offset5();
        size.height += myLastComponent.getPreferredSize().height;
        JBInsets.addTo(size, parent.getInsets());
        return size;
      }

      @Override
      public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        Dimension size = container.getPreferredSize();
        Rectangle bounds = new Rectangle(insets.left, insets.top, size.width, size.height);
        container.setBounds(bounds);
        container.doLayout();

        Point location = centerPanel.getLocation();
        Dimension buttonSize = myLastComponent.getPreferredSize();
        Border border = myLastComponent.getBorder();
        int borderOffset = border == null ? 0 : border.getBorderInsets(myLastComponent).left;
        myLastComponent
          .setBounds(bounds.x + location.x - borderOffset, bounds.y + PluginManagerConfigurableNew.offset5() + bounds.height,
                     Math.min(buttonSize.width, size.width),
                     buttonSize.height);
      }
    });

    updateIcon(false, false);
    updateColors(EventHandler.SelectionType.NONE);
  }

  private void createMetricsPanel(@NotNull JPanel centerPanel) {
    if (!(myPlugin instanceof PluginNode)) {
      return;
    }

    String downloads = PluginManagerConfigurableNew.getDownloads(myPlugin);
    String date = PluginManagerConfigurableNew.getLastUpdatedDate(myPlugin);
    String rating = PluginManagerConfigurableNew.getRating(myPlugin);

    if (downloads != null || date != null || rating != null) {
      JPanel panel = new NonOpaquePanel(new HorizontalLayout(JBUIScale.scale(7)));
      centerPanel.add(panel);

      if (date != null) {
        myLastUpdated = createRatingLabel(panel, date, AllIcons.Plugins.Updated);
      }
      if (downloads != null) {
        myDownloads = createRatingLabel(panel, downloads, AllIcons.Plugins.Downloads);
      }
      if (rating != null) {
        myRating = createRatingLabel(panel, rating, AllIcons.Plugins.Rating);
      }
    }
  }

  @NotNull
  static JLabel createRatingLabel(@NotNull JPanel panel, @NotNull String text, @Nullable Icon icon) {
    return createRatingLabel(panel, null, text, icon, null, true);
  }

  @NotNull
  static JLabel createRatingLabel(@NotNull JPanel panel,
                                  @Nullable Object constraints,
                                  @NotNull String text,
                                  @Nullable Icon icon,
                                  @Nullable Color color,
                                  boolean tiny) {
    JLabel label = new JLabel(text, icon, SwingConstants.CENTER);
    label.setOpaque(false);
    label.setIconTextGap(2);
    if (color != null) {
      label.setForeground(color);
    }
    panel.add(tiny ? PluginManagerConfigurableNew.installTiny(label) : label, constraints);
    return label;
  }

  private void addInstallButton() {
    if (InstalledPluginsState.getInstance().wasInstalled(myPlugin.getPluginId())) {
      add(myLastComponent = new RestartButton(myPluginModel));
      return;
    }

    myInstallButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, null));
    myInstallButton.setEnabled(PluginManager.getPlugin(myPlugin.getPluginId()) == null);
    add(myLastComponent = myInstallButton);

    if (MyPluginModel.isInstallingOrUpdate(myPlugin)) {
      showProgress(false);
    }
  }

  @Override
  public void showProgress() {
    showProgress(true);
  }

  private void showProgress(boolean repaint) {
    TwoLineProgressIndicator indicator = new TwoLineProgressIndicator();
    indicator.setCancelRunnable(() -> myPluginModel.finishInstall(myPlugin, false, false));
    myIndicator = indicator;

    myInstallButton.setVisible(false);
    add(myLastComponent = indicator.getComponent());
    doLayout();

    MyPluginModel.addProgress(myPlugin, indicator);

    if (repaint) {
      fullRepaint();
    }
  }

  @Override
  public void hideProgress(boolean success) {
    myIndicator = null;
    JComponent lastComponent = myLastComponent;
    if (success) {
      add(myLastComponent = new RestartButton(myPluginModel));
    }
    else {
      myLastComponent = myInstallButton;
      myInstallButton.setVisible(true);
    }
    remove(lastComponent);
    doLayout();
    fullRepaint();
  }

  private void addTags(@NotNull JPanel parent, @NotNull TagBuilder tagBuilder) {
    List<String> tags = PluginManagerConfigurableNew.getTags(myPlugin);
    if (tags.isEmpty()) {
      return;
    }

    NonOpaquePanel panel = new NonOpaquePanel(new HorizontalLayout(JBUIScale.scale(6)));
    parent.add(panel);

    myTagComponents = new ArrayList<>();

    for (String tag : tags) {
      TagComponent component = tagBuilder.createTagComponent(tag);
      panel.add(component);
      myTagComponents.add(component);
    }
  }

  @Override
  public void setListeners(@NotNull LinkListener<? super IdeaPluginDescriptor> listener,
                           @NotNull LinkListener<String> searchListener,
                           @NotNull EventHandler eventHandler) {
    super.setListeners(listener, searchListener, eventHandler);

    if (myDescription != null) {
      UIUtil.setCursor(myDescription, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      myDescription.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (SwingUtilities.isLeftMouseButton(event)) {
            listener.linkSelected(myName, myPlugin);
          }
        }
      });
      myDescription.addMouseListener(myHoverNameListener);
    }

    if (myTagComponents != null) {
      for (TagComponent component : myTagComponents) {
        //noinspection unchecked
        component.setListener(searchListener, SearchQueryParser.getTagQuery(component.getText()));
      }
      myTagComponents = null;
    }
  }

  @Override
  protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
    super.updateColors(grayedFg, background);

    if (myLastUpdated != null) {
      myLastUpdated.setForeground(grayedFg);
    }
    if (myDownloads != null) {
      myDownloads.setForeground(grayedFg);
    }
    if (myRating != null) {
      myRating.setForeground(grayedFg);
    }
  }

  @Override
  public void close() {
    if (myIndicator != null) {
      MyPluginModel.removeProgress(myPlugin, myIndicator);
      myIndicator = null;
    }
    myPluginModel.removeComponent(this);
  }

  @Override
  public boolean isMarketplace() {
    return true;
  }
}