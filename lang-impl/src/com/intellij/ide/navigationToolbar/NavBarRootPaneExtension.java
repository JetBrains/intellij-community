/*
 * User: anna
 * Date: 12-Nov-2007
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.impl.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;

public class NavBarRootPaneExtension extends IdeRootPaneNorthExtension {
  private static final Icon CROSS_ICON = IconLoader.getIcon("/actions/cross.png");

  private NavBarPanel myNavigationBar;
  private JLabel myCloseNavBarLabel;
  @NonNls public static final String NAV_BAR = "NavBar";

  public void installComponent(final Project project, final JPanel northPanel) {
    if (myNavigationBar == null) {
      myNavigationBar = new NavBarPanel(project);
      final int iconWidth = CROSS_ICON.getIconWidth();
      final int iconHeight = CROSS_ICON.getIconHeight();
      myNavigationBar.cutBorder(2 * iconWidth + 2);
      northPanel.add(myNavigationBar, new GridBagConstraints(0, 1, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
      myCloseNavBarLabel = new JLabel(new Icon() {
        public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
          final GraphicsConfig config = new GraphicsConfig(g);
          config.setAntialiasing(true);

          Graphics2D g2d = (Graphics2D)g;

          final GeneralPath path = new GeneralPath();

          path.moveTo(-2, iconHeight + 1);
          path.curveTo(2 * iconWidth/3, 2 * iconHeight/3, iconWidth/3, iconHeight/3, iconWidth, 0);
          path.lineTo(2 * iconWidth, 0);
          path.lineTo(2 * iconWidth, iconHeight);
          path.lineTo(0, iconHeight);
          path.closePath();

          g2d.setPaint(UIUtil.getListBackground());
          g2d.fill(path);

          g2d.setPaint(myCloseNavBarLabel.getBackground().darker());
          g2d.draw(path);

          CROSS_ICON.paintIcon(c, g, x + iconWidth - 2, y + 1);

          config.restore();
        }

        public int getIconWidth() {
          return 2 * iconWidth;
        }

        public int getIconHeight() {
          return iconHeight;
        }
      });
      myCloseNavBarLabel.addMouseListener(new MouseAdapter() {
        public void mouseClicked(final MouseEvent e) {
          UISettings.getInstance().SHOW_NAVIGATION_BAR = false;
          uiSettingsChanged(UISettings.getInstance());
        }
      });
      northPanel.add(myCloseNavBarLabel, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.SOUTHEAST, GridBagConstraints.NONE,
                                                                  new Insets(0, 0, 0, 0), 0, 0));
    }
  }

  public void deinstallComponent(final JPanel northPanel) {
    if (myNavigationBar != null) {
      northPanel.remove(myNavigationBar);
      northPanel.remove(myCloseNavBarLabel);
    }
  }

  public JComponent getComponent() {
    return myNavigationBar;
  }

  public void uiSettingsChanged(final UISettings settings) {
    if (myNavigationBar != null) {
      if (settings.SHOW_NAVIGATION_BAR){
        myNavigationBar.installListeners();
      } else {
        myNavigationBar.uninstallListeners();
      }
      myNavigationBar.updateState(settings.SHOW_NAVIGATION_BAR);
      myNavigationBar.setVisible(settings.SHOW_NAVIGATION_BAR);
      myCloseNavBarLabel.setVisible(settings.SHOW_NAVIGATION_BAR);
    }
  }

  @NonNls
  public String getKey() {
    return NAV_BAR;
  }

  public void dispose() {
    if (myNavigationBar != null) {
      myNavigationBar.uninstallListeners();
    }
    myNavigationBar = null;
  }
}