package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.impl.IdeFrame;

import java.awt.*;
import java.awt.event.WindowEvent;

/**
 * @author Vladimir Kondratyev
 */
public class CloseWindowAction extends AnAction{
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
    }else if(!(focusedWindow instanceof IdeFrame)){
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
