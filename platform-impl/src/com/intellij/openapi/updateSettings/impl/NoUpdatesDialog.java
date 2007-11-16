package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 24, 2005
 * Time: 10:44:40 PM
 * To change this template use File | Settings | File Templates.
 */

class NoUpdatesDialog extends DialogWrapper {
  private NoUpdatesPanel myNoUpdatesPanel;
  private final String myUploadedPlugins;

  protected NoUpdatesDialog(final boolean canBeParent, final String updatePlugins) {
    super(canBeParent);
    myUploadedPlugins = updatePlugins;
    setTitle(IdeBundle.message("updates.info.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    myNoUpdatesPanel = new NoUpdatesPanel();
    return myNoUpdatesPanel.myPanel;
  }

  protected Action[] createActions() {
    final Action cancelAction = getCancelAction();
    cancelAction.putValue(Action.NAME, CommonBundle.getCloseButtonText());
    if (myUploadedPlugins != null) {
      final Action okAction = getOKAction();
      okAction.putValue(Action.NAME, IdeBundle.message("update.plugins.shutdown.action"));
      cancelAction.putValue(Action.NAME, IdeBundle.message("update.plugins.update.later.action"));
      return new Action[] {okAction, cancelAction};
    }
    return new Action[]{cancelAction};
  }

  protected void doOKAction() {
    if (myUploadedPlugins != null) {
      ApplicationManagerEx.getApplicationEx().exit(true);
    }
    super.doOKAction();
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public void setLinkEnabled(final boolean enableLink) {
    if (enableLink) {
      myNoUpdatesPanel.myUpdatesLink.setForeground(Color.BLUE); // TODO: specify correct color
      myNoUpdatesPanel.myUpdatesLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myNoUpdatesPanel.myUpdatesLink.setToolTipText(IdeBundle.message("updates.open.settings.link"));
      myNoUpdatesPanel.myUpdatesLink.addMouseListener(new MouseListener() {
        public void mouseClicked(MouseEvent e) {
          UpdateSettingsConfigurable updatesSettings = UpdateSettingsConfigurable.getInstance();
          updatesSettings.setCheckNowEnabled(false);
          ShowSettingsUtil.getInstance().editConfigurable(myNoUpdatesPanel.myPanel, updatesSettings);
          updatesSettings.setCheckNowEnabled(true);
        }
        public void mouseEntered(MouseEvent e) {
        }
        public void mouseExited(MouseEvent e) {
        }
        public void mousePressed(MouseEvent e) {
        }
        public void mouseReleased(MouseEvent e) {
        }
      });
    }
  }

  private class NoUpdatesPanel {
    private JLabel myUpdatesLink;
    private JPanel myPanel;
    private JLabel myUpdatedPlugins;

    public NoUpdatesPanel() {
      myUpdatedPlugins.setVisible(myUploadedPlugins != null);
      myUpdatedPlugins.setText(myUploadedPlugins != null ? myUploadedPlugins : "");
      LabelTextReplacingUtil.replaceText(myPanel);
    }
  }
}
