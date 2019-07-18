// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurableNew;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class CellPluginComponent extends JPanel {
  public static final Color GRAY_COLOR = JBColor.namedColor("Label.infoForeground", new JBColor(Gray._120, Gray._135));
  private static final Color HOVER_COLOR = JBColor.namedColor("Plugins.lightSelectionBackground", new JBColor(0xF5F9FF, 0x36393B));

  public static boolean HANDLE_FOCUS_ON_SELECTION = true;

  public final IdeaPluginDescriptor myPlugin;

  protected LinkLabel myIconLabel;
  protected LinkLabel myName;
  protected JEditorPane myDescription;
  protected MouseListener myHoverNameListener;

  protected EventHandler.SelectionType mySelection = EventHandler.SelectionType.NONE;

  protected CellPluginComponent(@NotNull IdeaPluginDescriptor plugin) {
    myPlugin = plugin;
  }

  @NotNull
  public IdeaPluginDescriptor getPluginDescriptor() {
    return myPlugin;
  }

  protected void addIconComponent(@NotNull JPanel parent, @Nullable Object constraints) {
    myIconLabel = new LinkLabel(null, AllIcons.Plugins.PluginLogo_40);
    myIconLabel.setVerticalAlignment(SwingConstants.TOP);
    myIconLabel.setOpaque(false);
    parent.add(myIconLabel, constraints);
  }

  protected void addNameComponent(@NotNull JPanel parent) {
    myName = new LinkComponent() {
      @Override
      public String getToolTipText() {
        return getPreferredSize().width > getWidth() ? super.getToolTipText() : null;
      }
    };
    myName.setText(myPlugin.getName());
    myName.setToolTipText(myPlugin.getName());
    parent.add(RelativeFont.BOLD.install(myName));
  }

  protected void updateIcon(boolean errors, boolean disabled) {
    myIconLabel.setIcon(PluginLogo.getIcon(myPlugin, false, PluginManagerConfigurableNew.isJBPlugin(myPlugin), errors, disabled));
  }

  protected void addDescriptionComponent(@NotNull JPanel parent, @Nullable String description, @NotNull LineFunction function) {
    if (StringUtil.isEmptyOrSpaces(description)) {
      return;
    }

    myDescription = new JEditorPane() {
      @Override
      public Dimension getPreferredSize() {
        if (getWidth() == 0 || getHeight() == 0) {
          setSize(new JBDimension(180, 20));
        }
        Integer property = (Integer)getClientProperty("parent.width");
        int width = property == null ? JBUIScale.scale(180) : property;
        View view = getUI().getRootView(this);
        view.setSize(width, Integer.MAX_VALUE);
        return new Dimension(width, function.getHeight(this));
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        function.paintComponent(this, g);
      }
    };

    PluginManagerConfigurableNew.installTiny(myDescription);
    myDescription.setEditable(false);
    myDescription.setFocusable(false);
    myDescription.setOpaque(false);
    myDescription.setBorder(null);
    myDescription.setCaret(EmptyCaret.INSTANCE);

    myDescription.setEditorKit(new UIUtil.JBWordWrapHtmlEditorKit());
    myDescription.setText(XmlStringUtil.wrapInHtml(description));

    parent.add(myDescription);
  }

  @NotNull
  public EventHandler.SelectionType getSelection() {
    return mySelection;
  }

  public void setSelection(@NotNull EventHandler.SelectionType type) {
    setSelection(type, type == EventHandler.SelectionType.SELECTION);
  }

  public void setSelection(@NotNull EventHandler.SelectionType type, boolean scrollAndFocus) {
    mySelection = type;

    if (scrollAndFocus) {
      scrollToVisible();
      if (getParent() != null && type == EventHandler.SelectionType.SELECTION && HANDLE_FOCUS_ON_SELECTION) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(this, true));
      }
    }

    updateColors(type);
    repaint();
  }

  public void scrollToVisible() {
    JComponent parent = (JComponent)getParent();
    if (parent == null) {
      return;
    }

    Rectangle bounds = getBounds();
    if (!parent.getVisibleRect().contains(bounds)) {
      parent.scrollRectToVisible(bounds);
    }
  }

  protected void updateColors(@NotNull EventHandler.SelectionType type) {
    updateColors(GRAY_COLOR, type == EventHandler.SelectionType.NONE ? PluginManagerConfigurableNew.MAIN_BG_COLOR : HOVER_COLOR);
  }

  protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
    setBackground(background);

    if (myDescription != null) {
      myDescription.setForeground(grayedFg);
    }
  }

  protected void fullRepaint() {
    Container parent = getParent();
    parent.doLayout();
    parent.revalidate();
    parent.repaint();
  }

  public void setListeners(@NotNull LinkListener<? super IdeaPluginDescriptor> listener,
                           @NotNull LinkListener<String> searchListener,
                           @NotNull EventHandler eventHandler) {
    //noinspection unchecked
    myIconLabel.setListener(listener, myPlugin);
    //noinspection unchecked
    myName.setListener(listener, myPlugin);

    myHoverNameListener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent event) {
        myName.entered(event);
      }

      @Override
      public void mouseExited(MouseEvent event) {
        myName.exited(event);
      }
    };
    myIconLabel.addMouseListener(myHoverNameListener);

    eventHandler.addAll(this);
  }

  public void createPopupMenu(@NotNull DefaultActionGroup group, @NotNull List<? extends CellPluginComponent> selection) {
  }

  public void handleKeyAction(int keyCode, @NotNull List<? extends CellPluginComponent> selection) {
  }

  public abstract void showProgress();

  public abstract void hideProgress(boolean success);

  public void clearProgress() {
    throw new UnsupportedOperationException();
  }

  public void close() {
  }

  public abstract boolean isMarketplace();

  public void updateEnabledState() {
    throw new UnsupportedOperationException();
  }

  public void updateAfterUninstall() {
    throw new UnsupportedOperationException();
  }

  public void updateErrors() {
    throw new UnsupportedOperationException();
  }

  public void enableRestart() {
    throw new UnsupportedOperationException();
  }
}