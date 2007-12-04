package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;

import javax.swing.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 24, 2005
 * Time: 10:44:40 PM
 * To change this template use File | Settings | File Templates.
 */

class NoUpdatesDialog extends AbstractUpdateDialog {

  protected NoUpdatesDialog(final boolean canBeParent, final List<PluginDownloader> updatePlugins, boolean enableLink) {
    super(canBeParent, enableLink, updatePlugins);
    setTitle(IdeBundle.message("updates.info.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    return new NoUpdatesPanel().myPanel;
  }

  protected Action[] createActions() {
    final Action cancelAction = getCancelAction();
    cancelAction.putValue(Action.NAME, CommonBundle.getCloseButtonText());
    if (myUploadedPlugins != null) {
      return new Action[] {getOKAction(), cancelAction};
    }
    return new Action[]{cancelAction};
  }

  private class NoUpdatesPanel {
    private JPanel myPanel;
    private JPanel myPluginsPanel;
    private JEditorPane myEditorPane;

    public NoUpdatesPanel() {
      initPluginsPanel(myPanel, myPluginsPanel, myEditorPane);
    }
  }
}
