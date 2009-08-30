
package com.intellij.debugger.actions;

import com.intellij.debugger.DebugException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

public class ThrowDebugExceptionAction extends AnAction implements DumbAware {

  public void actionPerformed(AnActionEvent event) {
    try{
      throw new DebugException();
    }
    catch(DebugException e){
    }
  }
}
