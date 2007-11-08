package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Nov 6, 2003
 * Time: 4:05:51 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DropAnErrorAction extends AnAction {
  public DropAnErrorAction() {
    super ("Drop an error");
  }

  public void actionPerformed(AnActionEvent e) {
    /*
    Project p = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    final StatusBar bar = WindowManager.getInstance().getStatusBar(p);
    bar.fireNotificationPopup(new JLabel("<html><body><br><b>       Notifier      </b><br><br></body></html>"));
    */
    
    Logger.getInstance("test").error("Test");
  }
}
