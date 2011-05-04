/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 12-Nov-2007
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class NavBarRootPaneExtension extends IdeRootPaneNorthExtension {
  private static final Icon CROSS_ICON = IconLoader.getIcon("/actions/cross.png");

  private JComponent myWrapperPanel;
  @NonNls public static final String NAV_BAR = "NavBar";
  private final Project myProject;
  private NavBarPanel myNavigationBar;
  private JPanel myRunPanel;

  public NavBarRootPaneExtension(Project project) {
    myProject = project;

    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        toggleRunPanel(!source.SHOW_MAIN_TOOLBAR);
      }
    }, this);
  }

  @Override
  public IdeRootPaneNorthExtension copy() {
    return new NavBarRootPaneExtension(myProject);
  }

  public JComponent getComponent() {
    if (myWrapperPanel == null) {
      myWrapperPanel = new JPanel(new BorderLayout()) {
        @Override
        protected void paintChildren(Graphics g) {
          super.paintChildren(g);
          if (UIUtil.isUnderAquaLookAndFeel() && !UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
            final Rectangle r = getBounds();
            g.setColor(new Color(255, 255, 255, 90));
            g.drawLine(0, r.height - 4, r.width, r.height - 4);
            g.setColor(new Color(0, 0, 0, 90));
            g.drawLine(0, r.height - 3, r.width, r.height - 3);
            g.setColor(new Color(0, 0, 0, 20));
            g.drawLine(0, r.height - 2, r.width, r.height - 2);
          }
        }

        @Override
        protected void paintComponent(Graphics g) {
          if (!UIUtil.isUnderAquaLookAndFeel()) {
            super.paintComponent(g);
            return;
          }

          final Rectangle r = getBounds();
          if (UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
            g.setColor(new Color(200, 200, 200));
            g.fillRect(0, 0, r.width, r.height);
          }
          else {
            UIUtil.drawGradientHToolbarBackground(g, r.width, r.height);
          }
        }

        @Override
        public Insets getInsets() {
          final Insets i = super.getInsets();
          if (!UIUtil.isUnderAquaLookAndFeel()) {
            return i;
          }
          
          return new Insets(i.top, i.left, i.bottom + 3, i.right);
        }
      };
      myWrapperPanel.add(buildNavBarPanel(), BorderLayout.CENTER);
      myWrapperPanel.putClientProperty("NavBarPanel", myNavigationBar);
      toggleRunPanel(!UISettings.getInstance().SHOW_MAIN_TOOLBAR);
    }

    return myWrapperPanel;
  }

  private void toggleRunPanel(final boolean show) {
    if (show && myRunPanel == null) {
      final ActionManager manager = ActionManager.getInstance();
      final AnAction toolbarRunGroup = manager.getAction("NavBarToolBar");
      if (toolbarRunGroup instanceof DefaultActionGroup) {
        final DefaultActionGroup group = (DefaultActionGroup)toolbarRunGroup;
        final boolean needGap = isNeedGap(group);
        final ActionToolbar actionToolbar = manager.createActionToolbar(ActionPlaces.UNKNOWN, group, true);
        actionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
        final JComponent component = actionToolbar.getComponent();
        component.setOpaque(false);
        myRunPanel = new JPanel(new BorderLayout());
        myRunPanel.setOpaque(false);
        myRunPanel.add(component, BorderLayout.CENTER);
        
        myRunPanel.setBorder(BorderFactory.createEmptyBorder(0, needGap ? 5 : 1, 0, 0));
        myWrapperPanel.add(myRunPanel, BorderLayout.EAST);
      }
    }
    else if (!show && myRunPanel != null) {
      myWrapperPanel.remove(myRunPanel);
      myRunPanel = null;
    }
  }

  private boolean isUndocked() {
    final Window ancestor = SwingUtilities.getWindowAncestor(myWrapperPanel);
    return ancestor != null && !(ancestor instanceof IdeFrameImpl);
  }
  
  private static boolean isNeedGap(final DefaultActionGroup group) {
    final AnAction firstAction = getFirstAction(group);
    return firstAction instanceof ComboBoxAction;
  }

  @Nullable
  private static AnAction getFirstAction(final DefaultActionGroup group) {
    AnAction firstAction = null;
    for (final AnAction action : group.getChildActionsOrStubs()) {
      if (action instanceof DefaultActionGroup) {
        firstAction = getFirstAction((DefaultActionGroup)action);
      } else if (action instanceof Separator || action instanceof ActionGroup) {
        continue;
      } else {
        firstAction = action;
        break;
      }

      if (firstAction != null) break;
    }

    return firstAction;
  }

  private JComponent buildNavBarPanel() {
    final JComponent result = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        if (UIUtil.isUnderAquaLookAndFeel()) {
          final Rectangle r = getBounds();
          final Graphics2D g2d = (Graphics2D)g;
          if (!UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
            //UIUtil.drawGradientHToolbarBackground(g, r.width, r.height);

            final Dimension d = getPreferredSize();
            final int topOffset = (r.height - d.height) / 2 + 2;
            //
            //g2d.setPaint(new GradientPaint(0, 0, new Color(240, 240, 240), 0, d.height, new Color(210, 210, 210)));
            //g.fillRect(0, topOffset, r.width, d.height);
            //
            //g.setColor(new Color(0, 0, 0, 90));
            //g.drawLine(0, topOffset, r.width, topOffset);
            //g.drawLine(0, topOffset + d.height, r.width - 1, topOffset + d.height);
            
            UIUtil.drawDoubleSpaceDottedLine(g2d, topOffset, topOffset + d.height - 1, r.width - 1, Color.GRAY, false);
          }
          else {
            final boolean undocked = isUndocked();

            g2d.setPaint(new GradientPaint(0, 0, new Color(240, 240, 240), 0, r.height, new Color(210, 210, 210)));
            g.fillRect(0, 0, r.width, r.height);

            if (!undocked) {
              g.setColor(new Color(255, 255, 255, 220));
              g.drawLine(0, 1, r.width, 1);
            }
            
            g.setColor(new Color(0, 0, 0, 80));
            if (!undocked) g.drawLine(0, 0, r.width, 0);
            g.drawLine(0, r.height - 1, r.width - 1, r.height - 1);
          }
        } else {
          super.paintComponent(g);
        }
      }

      @Override
      public void doLayout() {
        // align vertically
        final Rectangle r = getBounds();
        final Insets insets = getInsets();
        int x = insets.left;
        
        final Component navBar = getComponent(0);
        final Component closeLabel = getComponentCount() == 2 ? getComponent(1) : null;

        final Dimension preferredSize = navBar.getPreferredSize();
        final Dimension closePreferredSize = closeLabel == null ? new Dimension() : closeLabel.getPreferredSize();

        navBar.setBounds(x, insets.top + ((r.height - preferredSize.height - insets.top - insets.bottom) / 2),
                         r.width - insets.left - insets.right - closePreferredSize.width, preferredSize.height);
        
        if(closeLabel != null) {
          closeLabel.setBounds(x + r.width - insets.left - insets.right - closePreferredSize.width, 
                               insets.top + ((r.height - closePreferredSize.height - insets.top - insets.bottom) / 2),
                               closePreferredSize.width, closePreferredSize.height);
        }
      }
    };
    
    result.setBackground(UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground());
    result.setOpaque(!UIUtil.isUnderAquaLookAndFeel() || UISettings.getInstance().SHOW_MAIN_TOOLBAR);
    
    myNavigationBar = new NavBarPanel(myProject);
    myNavigationBar.getModel().setFixedComponent(true);
    
    JScrollPane scroller = ScrollPaneFactory.createScrollPane(myNavigationBar);
    scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scroller.setHorizontalScrollBar(null);
    scroller.setBorder(null);

    scroller.setOpaque(false);
    scroller.getViewport().setOpaque(false);

    result.add(scroller, BorderLayout.CENTER);

    if (!SystemInfo.isMac) {
      JLabel closeLabel = new JLabel(CROSS_ICON);
      closeLabel.addMouseListener(new MouseAdapter() {
        public void mouseClicked(final MouseEvent e) {
          UISettings.getInstance().SHOW_NAVIGATION_BAR = false;
          uiSettingsChanged(UISettings.getInstance());
        }
      });
      result.add(closeLabel, BorderLayout.EAST);
    }

    result.setBorder(UIUtil.isUnderAquaLookAndFeel() ? BorderFactory.createEmptyBorder(2, 0, 2, 4) : new NavBarBorder(true, 0));
    myNavigationBar.setBorder(null);
    
    return result;
  }

  public void uiSettingsChanged(final UISettings settings) {
    if (myNavigationBar != null) {
      myNavigationBar.updateState(settings.SHOW_NAVIGATION_BAR);
      myWrapperPanel.setVisible(settings.SHOW_NAVIGATION_BAR);
      
      if (myWrapperPanel.getComponentCount() > 0) {
        final Component c = myWrapperPanel.getComponent(0);
        if (c instanceof JComponent) ((JComponent)c).setOpaque(
          !UIUtil.isUnderAquaLookAndFeel() || UISettings.getInstance().SHOW_MAIN_TOOLBAR);
      }
    }
  }

  @NonNls
  public String getKey() {
    return NAV_BAR;
  }


  public void dispose() {
    myWrapperPanel.setVisible(false);
    myWrapperPanel = null;
    myRunPanel = null;
    myNavigationBar = null;
  }
}
