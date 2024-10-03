// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.JBColor;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.MethodInvocator;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @author tav
 */
@SuppressWarnings("ConcatenationWithEmptyString")
final class HidpiInfo extends AnAction implements DumbAware {

  private static final boolean ENABLED = JreHiDpiUtil.isJreHiDPIEnabled();

  private static final String JRE_HIDPI_MODE_TEXT = "Per-monitor DPI-aware";

  private static final String MONITOR_RESOLUTION_TEXT = "Monitor resolution";

  private static final String SYS_SCALE_TEXT = "Monitor scale";

  private static final String USR_SCALE_TEXT = "User (IDE) scale";

  // must be not constant to avoid call to JBUIScale
  public static String getUsrScaleDesc() {
    return "<html><span style='font-size:x-small'>The global IDE scale factor" +
           (JBUIScale.DEBUG_USER_SCALE_FACTOR.getValue() != null ?
            ", overridden by the debug property." :
     ", derived from the main font size: <code>$LABEL_FONT_SIZE" +
     (ENABLED ? "pt" : "px") + "</code><br>" +
     "<code>" + (SystemInfo.isMac ? "Preferences " : "Settings ") +
     "> Appearance & Behaviour > Appearance > Override default font") +
           "</code></span></html>";
  }

  private JBPopup popup;

  private final int myCopyIconSize = AllIcons.General.CopyHovered.getIconWidth();
  private final int myCopyIconPlateSize = myCopyIconSize * 2;
  private Rectangle myCopyIconRect;
  private boolean myDrawCopyIcon;
  private boolean myIsCopyIconActive;

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) {
      return;
    }

    Rectangle bounds = activeFrame.getGraphicsConfiguration().getBounds();
    String _USR_SCALE_DESC = getUsrScaleDesc().replace("$LABEL_FONT_SIZE", "" + StartupUiUtil.getLabelFont().getSize());

    String[] columns = new String[]{
      "Property", "Value", "Description"
    };

    String[][] data = new String[][]{
      {JRE_HIDPI_MODE_TEXT, ENABLED ? "enabled" : "disabled",
        "<html><span style='font-size:x-small'>When enabled, the IDE UI scaling honors per-monitor DPI.<br>" +
            (SystemInfo.isWindows ?
        "To " + (ENABLED ? "disable" : "enable") + " set the JVM option <code>-Dsun.java2d.uiScale.enabled=" +
        (ENABLED ? "false" : "true") + "</code> and restart.</span></html>" :
         "The mode can not be changed on this platform.")},
      {MONITOR_RESOLUTION_TEXT, "" + bounds.width + "x" + bounds.height, "<html><span style='font-size:x-small'>" +
                                                                           (ENABLED ?
      "The current monitor resolution" :
      "The main monitor resolution") +
                                                                           " (in user space).</span></html>"},
      {SYS_SCALE_TEXT, "" + JBUIScale.sysScale(activeFrame), "<html><span style='font-size:x-small'>" +
                                                               (ENABLED ?
      "The current monitor scale factor" :
      "The main monitor scale factor") +
                                                               ".</span></html>"},
      {USR_SCALE_TEXT, "" + JBUIScale.scale(1f), _USR_SCALE_DESC},
    };

    if (SystemInfo.isLinux && SystemInfo.isJetBrainsJvm) {
      try {
        Class<?> cls = Class.forName("sun.awt.X11GraphicsDevice");
        MethodInvocator getDpiInfo = new MethodInvocator(cls, "getDpiInfo");
        if (getDpiInfo.isAvailable()) {
          GraphicsDevice gd = null;
          try {
            gd = activeFrame.getGraphicsConfiguration().getDevice();
            String[][] dpiInfo = (String[][])getDpiInfo.invoke(gd);
            if (dpiInfo != null && dpiInfo.length > 0) {
              for (String[] row : dpiInfo) {
                row[2] = "<html><span style='font-size:x-small'>" + row[2] + "</span></html>";
              }
              String[][] _exData = new String[data.length + dpiInfo.length][];
              System.arraycopy(data, 0, _exData, 0, data.length);
              System.arraycopy(dpiInfo, 0, _exData, data.length, dpiInfo.length);
              data = _exData;
            }
          } catch (IllegalArgumentException e) {
            Logger.getInstance(HidpiInfo.class).warn("Unexpected GraphicsDevice type (value): " + (gd != null ? gd.getClass().getName() : "null"));
          }
        }
      }
      catch (ClassNotFoundException ignore) {
      }
    }
    final String[][] exData = data;

    JTable table = new JTable(exData, columns) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        return (table1, value, isSelected, hasFocus, row1, column1) -> label(exData[row1][column1]);
      }

      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (myDrawCopyIcon) {
          Rectangle rect = getBounds();
          g.setColor(JBColor.background());
          g.fillRect(rect.width - myCopyIconPlateSize, rect.height - myCopyIconPlateSize,
                     myCopyIconPlateSize, myCopyIconPlateSize);
          int offset = (myCopyIconPlateSize + myCopyIconSize) / 2;
          AllIcons.General.CopyHovered.paintIcon(this, g, rect.width - offset, rect.height - offset);
        }
      }
    };

    final Supplier<Rectangle> getCopyIconRect = () -> {
      if (myCopyIconRect == null) {
        myCopyIconRect = table.getBounds();
        myCopyIconRect.x = myCopyIconRect.width - myCopyIconPlateSize;
        myCopyIconRect.y = myCopyIconRect.height - myCopyIconPlateSize;
        myCopyIconRect.width = myCopyIconRect.height = myCopyIconPlateSize;
      }
      return myCopyIconRect;
    };
    final String[][] _data = data;

    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myDrawCopyIcon = true;
        table.repaint(getCopyIconRect.get());
      }
      @Override
      public void mouseExited(MouseEvent e) {
        myDrawCopyIcon = false;
        table.repaint(getCopyIconRect.get());
      }
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myIsCopyIconActive) {
          StringBuilder builder = new StringBuilder();
          for (String[] datum : _data) {
            builder.append(datum[0]).append(" : ").append(datum[1]).append("\n");
          }
          try {
            CopyPasteManager.getInstance().setContents(new StringSelection(builder.toString()));
          }
          catch (Exception ignore) {}
          popup.cancel();
        }
      }
    });

    table.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        if (getCopyIconRect.get().contains(e.getPoint())) {
          myIsCopyIconActive = true;
          table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        else {
          myIsCopyIconActive = false;
          table.setCursor(Cursor.getDefaultCursor());
        }
      }
    });

    BiFunction<Integer, Integer, Dimension> size = (row, col) -> label(exData[row][col]).getPreferredSize();
    int padding = JBUIScale.scale(10);

    table.setColumnSelectionAllowed(true);
    TableColumnModel tcm = table.getColumnModel();
    tcm.getColumn(0).setPreferredWidth(size.apply(0, 0).width + padding);
    tcm.getColumn(1).setPreferredWidth(size.apply(1, 1).width + padding);
    tcm.getColumn(2).setPreferredWidth(size.apply(0, 2).width + padding);
    for (int i = 0; i < table.getRowCount(); i++) {
      table.setRowHeight(i, size.apply(0, 2).height);
    }

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(table, BorderLayout.CENTER);
    tablePanel.add(table.getTableHeader(), BorderLayout.NORTH);

    popup = JBPopupFactory.
      getInstance().
      createComponentPopupBuilder(tablePanel, null).
      setTitle("HiDPI Info").
      createPopup();
    popup.showInCenterOf(activeFrame);
  }

  private static JLabel label(String text) {
    JLabel label = new JLabel(text);
    label.setBorder(JBUI.Borders.empty(2));
    return label;
  }
}
