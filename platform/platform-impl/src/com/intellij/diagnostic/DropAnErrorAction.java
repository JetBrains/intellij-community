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
package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Nov 6, 2003
 * Time: 4:05:51 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DropAnErrorAction extends DumbAwareAction {
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
