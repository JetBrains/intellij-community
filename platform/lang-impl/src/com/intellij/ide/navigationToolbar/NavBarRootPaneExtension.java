/*
 * User: anna
 * Date: 12-Nov-2007
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.impl.IdeRootPaneNorthExtension;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class NavBarRootPaneExtension extends IdeRootPaneNorthExtension {
  private static final Icon CROSS_ICON = IconLoader.getIcon("/actions/cross.png");

  private JComponent myPanel;
  @NonNls public static final String NAV_BAR = "NavBar";
  private final Project myProject;
  private NavBarPanel myNavigationBar;

  public NavBarRootPaneExtension(Project project) {
    myProject = project;
  }

  public JComponent getComponent() {
    if (myPanel == null) {
      myPanel = new JPanel(new BorderLayout());

      myPanel.setBackground(UIUtil.getListBackground());
      myNavigationBar = new NavBarPanel(myProject);

      JScrollPane scroller = new JScrollPane(myNavigationBar);
      scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
      scroller.setHorizontalScrollBar(null);
      scroller.setBorder(null);

      myPanel.add(scroller, BorderLayout.CENTER);

      JLabel closeLabel = new JLabel(CROSS_ICON);

      closeLabel.addMouseListener(new MouseAdapter() {
        public void mouseClicked(final MouseEvent e) {
          UISettings.getInstance().SHOW_NAVIGATION_BAR = false;
          uiSettingsChanged(UISettings.getInstance());
        }
      });
      myPanel.add(closeLabel, BorderLayout.EAST);

      myPanel.putClientProperty("NavBarPanel", myNavigationBar);
      myNavigationBar.installBorder(0);
      myPanel.setBorder(myNavigationBar.getBorder());
      myNavigationBar.setBorder(null);
    }

    return myPanel;
  }

  public void uiSettingsChanged(final UISettings settings) {
    if (myNavigationBar != null) {
      myNavigationBar.updateState(settings.SHOW_NAVIGATION_BAR);
      myPanel.setVisible(settings.SHOW_NAVIGATION_BAR);
    }
  }

  @NonNls
  public String getKey() {
    return NAV_BAR;
  }

  public void dispose() {
    myPanel.setVisible(false);
    myPanel = null;
    myNavigationBar = null;
  }
}
