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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.project.DumbAware;

import java.awt.*;
import java.awt.event.WindowEvent;

/**
 * @author Vladimir Kondratyev
 */
public class CloseWindowAction extends AnAction implements DumbAware {
  public CloseWindowAction(){
    setEnabledInModalContext(true);
  }

  private static Window getWindow(){
    Window focusedWindow=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    if(!(focusedWindow instanceof Dialog) && !(focusedWindow instanceof Frame)){
      return null;
    }
    if(focusedWindow instanceof Dialog){
      Dialog dialog=(Dialog)focusedWindow;
      if(!dialog.isUndecorated()){
        return focusedWindow;
      }else{
        return null;
      }
    }else if(!(focusedWindow instanceof IdeFrameImpl)){
      Frame frame=(Frame)focusedWindow;
      if(!frame.isUndecorated()){
        return focusedWindow;
      }else{
        return null;
      }
    }else{
      return null;
    }
  }

  public void actionPerformed(AnActionEvent e){
    Window window=getWindow();
    WindowEvent event=new WindowEvent(window,WindowEvent.WINDOW_CLOSING);
    window.dispatchEvent(event);
  }

  public void update(AnActionEvent e){
    super.update(e);
    e.getPresentation().setEnabled(getWindow()!=null);
  }
}
