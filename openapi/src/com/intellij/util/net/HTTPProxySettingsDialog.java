package com.intellij.util.net;

import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 21, 2003
 * Time: 4:35:44 PM
 * To change this template use Options | File Templates.
 */
public class HTTPProxySettingsDialog extends DialogWrapper {
  private HTTPProxySettingsPanel panel;
  private Action okAction;
  private Action cancelAction;

  public HTTPProxySettingsDialog() {
    super(false);
    setTitle("HTTP Proxy Settings");
    panel = new HTTPProxySettingsPanel();

    okAction = new AbstractAction ("OK") {
      public void actionPerformed(ActionEvent e) {
        panel.apply();
        dispose();
      }
    };
    okAction.putValue(DEFAULT_ACTION, "true");
    cancelAction = new AbstractAction("Cancel") {
      public void actionPerformed(ActionEvent e) {
        dispose();
      }
    };
    init();
  }

  protected JComponent createCenterPanel() {
    return panel;
  }

  protected Action[] createActions() {
    return new Action [] {okAction, cancelAction};
  }
}
