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
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.panels.OpaquePanel;
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
      myWrapperPanel = new JPanel(new BorderLayout());
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
        final JComponent component = actionToolbar.getComponent();
        component.setBackground(Color.WHITE);
        myRunPanel = new JPanel(new BorderLayout());
        final Color color = myRunPanel.getBackground() != null ? myRunPanel.getBackground().darker() : Color.darkGray;
        myRunPanel.setBackground(Color.WHITE);
        myRunPanel.add(component);
        myRunPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 1, 0, color),
                                                                BorderFactory.createEmptyBorder(1, needGap ? 5 : 1, 0, 0)));
        myWrapperPanel.add(myRunPanel, BorderLayout.EAST);
      }
    }
    else if (!show && myRunPanel != null) {
      myWrapperPanel.remove(myRunPanel);
      myRunPanel = null;
    }
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
    final JComponent result = new OpaquePanel.List(new BorderLayout());
    result.setBackground(UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground());
    myNavigationBar = new NavBarPanel(myProject);

    JScrollPane scroller = ScrollPaneFactory.createScrollPane(myNavigationBar);
    scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
    scroller.setHorizontalScrollBar(null);
    scroller.setBorder(null);

    result.add(scroller, BorderLayout.CENTER);

    JLabel closeLabel = new JLabel(CROSS_ICON);

    closeLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        UISettings.getInstance().SHOW_NAVIGATION_BAR = false;
        uiSettingsChanged(UISettings.getInstance());
      }
    });
    result.add(closeLabel, BorderLayout.EAST);

    myNavigationBar.setBorder(new NavBarBorder(true, 0));
    result.setBorder(myNavigationBar.getBorder());
    myNavigationBar.setBorder(null);
    return result;
  }

  public void uiSettingsChanged(final UISettings settings) {
    if (myNavigationBar != null) {
      myNavigationBar.updateState(settings.SHOW_NAVIGATION_BAR);
      myWrapperPanel.setVisible(settings.SHOW_NAVIGATION_BAR);
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
