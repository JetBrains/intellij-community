/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

import javax.swing.*;
import java.awt.*;


public abstract class PanelWithActionsAndCloseButton extends JPanel implements DataProvider {
  protected final ContentManager myContentManager;
  private final String myHelpId;
  private final DefaultActionGroup myToolbalGroup = new DefaultActionGroup(null, false);

  public PanelWithActionsAndCloseButton(ContentManager contentManager, String helpId) {
    super(new BorderLayout());
    myContentManager = contentManager;
    myHelpId = helpId;
  }

  public String getHelpId() {
    return myHelpId;
  }

  protected void init(){
    addActionsTo(myToolbalGroup);
    myToolbalGroup.add(new MyCloseAction());
    myToolbalGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP));


    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, myToolbalGroup, false)
        .getComponent(), BorderLayout.WEST);
    add(createCenterPanel(), BorderLayout.CENTER);
  }

  public Object getData(String dataId) {
    if (DataConstants.HELP_ID.equals(dataId)){
      return myHelpId;
    }
    return null;
  }

  protected abstract JComponent createCenterPanel();

  protected void addActionsTo(DefaultActionGroup group){

  }

  protected void dispose(){

  }

  private class MyCloseAction extends AnAction {
    public MyCloseAction() {
      super("Close", null, IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      dispose();
      Content content = myContentManager.getContent(PanelWithActionsAndCloseButton.this);
      if (content != null) {
        myContentManager.removeContent(content);
      }
    }
  }
}
