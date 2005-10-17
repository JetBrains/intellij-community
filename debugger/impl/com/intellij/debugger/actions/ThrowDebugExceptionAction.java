
package com.intellij.debugger.actions;

import com.intellij.debugger.DebugException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ThrowDebugExceptionAction extends AnAction {

  public void actionPerformed(AnActionEvent event) {
    try{
      throw new DebugException();
    }
    catch(DebugException e){
    }
  }
}
