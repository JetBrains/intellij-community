// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.execution.ui.UIExperiment;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTabbedPaneUI;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.*;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.function.Supplier;

public final class ServiceViewUIUtils {
  private ServiceViewUIUtils() {
  }

  public static boolean isNewServicesUIEnabled() {
    return !PlatformUtils.isDataGrip() && UIExperiment.isNewDebuggerUIEnabled();
  }

  public static @NotNull ContentUI getServicesAlignedTabbedPaneContentUI() {
    TabbedPaneContentUI contentUI = new TabbedPaneContentUI(SwingConstants.TOP);
    JComponent component = contentUI.getComponent();
    if (component instanceof TabbedPaneWrapper.TabbedPaneHolder holder) {
      JComponent holderComponent = holder.getTabbedPaneWrapper().getTabbedPane().getComponent();
      if (holderComponent instanceof JTabbedPane tabbedPane) {
        tabbedPane.setUI(new ServiceViewDetailsTabbedPaneUI());
        tabbedPane.addPropertyChangeListener("UI", new PropertyChangeListener() {
          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            if (!(evt.getNewValue() instanceof ServiceViewDetailsTabbedPaneUI)) {
              tabbedPane.setUI(new ServiceViewDetailsTabbedPaneUI());
            }
          }
        });

        if (ServiceViewUIUtils.isNewServicesUIEnabled()) {
          tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              ContentManager contentManager = contentUI.getManager();
              if (contentManager != null) {
                tabbedPane.removeChangeListener(this);
                ServicesTabbedPaneContentManagerListener listener = new ServicesTabbedPaneContentManagerListener(() -> {
                  TabbedPaneUI ui = tabbedPane.getUI();
                  return ui instanceof ServiceViewDetailsTabbedPaneUI servicesUI ? servicesUI.getToolbarWrapper() : null;
                });
                Content content = contentManager.getSelectedContent();
                if (content != null) {
                  // Process current selection.
                  ContentManagerEvent event = new ContentManagerEvent(contentManager,
                                                                      content,
                                                                      contentManager.getIndexOfContent(content),
                                                                      ContentManagerEvent.ContentOperation.add);
                  listener.selectionChanged(event);
                }
                contentManager.addContentManagerListener(listener);
              }
            }
          });
        }
      }
    }
    return contentUI;
  }

  public static @NotNull JPanel getServicesAlignedPanelWrapper(@NotNull JComponent wrapped) {
    return new NonOpaquePanel(wrapped) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = JBRunnerTabs.getTabLabelPreferredHeight();
        return size;
      }
    };
  }

  public static @NotNull JComponent wrapServicesAligned(@NotNull ActionToolbar toolbar) {
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(JBUI.Borders.empty());
    return new NonOpaquePanel(toolbarComponent) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (size.height > 0) {
          size.height = JBUI.scale(JBUI.unscale(JBRunnerTabs.getTabLabelPreferredHeight()) - 1); // without bottom border
        }
        return size;
      }
    };
  }

  private static final class ServiceViewDetailsTabbedPaneUI extends DarculaTabbedPaneUI {
    private JComponent myToolbarWrapper;
    private LayoutManager myOriginalLayout;

    JComponent getToolbarWrapper() {
      return myToolbarWrapper;
    }

    @Override
    protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
      Insets borderInsets = getContentBorderInsets(tabPlacement);
      return JBRunnerTabs.getTabLabelPreferredHeight() - borderInsets.top - borderInsets.bottom;
    }

    @Override
    protected void installComponents() {
      super.installComponents();
      if (!isNewServicesUIEnabled()) return;

      myToolbarWrapper = new TabbedPaneToolbarWrapper();
      tabPane.add(myToolbarWrapper);
      myOriginalLayout = tabPane.getLayout();
      tabPane.setLayout(new ServiceViewDetailsTabbedPaneLayout());
    }

    @Override
    protected void uninstallComponents() {
      super.uninstallComponents();
      if (myToolbarWrapper != null) {
        tabPane.remove(myToolbarWrapper);
      }
    }

    @Override
    public void uninstallUI(JComponent c) {
      if (tabPane.getLayout() instanceof ServiceViewDetailsTabbedPaneLayout &&
          myOriginalLayout != null) {
        tabPane.setLayout(myOriginalLayout);
      }
      super.uninstallUI(c);
      myToolbarWrapper = null;
      myOriginalLayout = null;
    }

    private final class ServiceViewDetailsTabbedPaneLayout extends TabbedPaneLayout {
      @Override
      public void layoutContainer(Container parent) {
        super.layoutContainer(parent);

        int count = tabPane.getTabCount();
        if (count == 0) return;

        Component lastTabComponent = tabPane.getTabComponentAt(count - 1);
        if (lastTabComponent == null) return;

        Rectangle lastTabBounds = new Rectangle();
        getTabBounds(count - 1, lastTabBounds);
        Dimension preferredSize = myToolbarWrapper.getPreferredSize();
        Rectangle tabPaneBounds = tabPane.getBounds();
        int width = Math.min(preferredSize.width, tabPaneBounds.width - lastTabBounds.x - lastTabBounds.width);
        myToolbarWrapper.setBounds(lastTabBounds.x + lastTabBounds.width, lastTabBounds.y, width, preferredSize.height);
      }
    }
  }

  private static final class TabbedPaneToolbarWrapper extends NonOpaquePanel implements UIResource {
    TabbedPaneToolbarWrapper() {
      super(new BorderLayout());
    }
  }

  private static final class ServicesTabbedPaneContentManagerListener implements ContentManagerListener {
    private final Supplier<JComponent> myToolbarWrapperSupplier;

    ServicesTabbedPaneContentManagerListener(Supplier<JComponent> toolbarWrapperSupplier) {
      myToolbarWrapperSupplier = toolbarWrapperSupplier;
    }

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      JComponent toolbarWrapper = myToolbarWrapperSupplier.get();
      if (toolbarWrapper == null) return;

      int index = event.getIndex();
      if (index != -1 && event.getOperation() != ContentManagerEvent.ContentOperation.remove) {
        Content content = event.getContent();
        ActionGroup actionGroup = content.getActions();
        if (actionGroup != null) {
          toolbarWrapper.setVisible(true);

          ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(content.getPlace(), actionGroup, true);
          toolbar.setTargetComponent(content.getActionsContextComponent());
          toolbarWrapper.removeAll();
          toolbarWrapper.add(wrapServicesAligned(toolbar), BorderLayout.CENTER);

          toolbarWrapper.revalidate();
          toolbarWrapper.repaint();
        }
        else {
          toolbarWrapper.setVisible(false);
        }
      }
    }
  }
}
