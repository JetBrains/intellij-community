
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class ActivateToolWindowAction extends AnAction {
  private String myToolWindowId;

  /**
   * Creates an action which activates tool window with specified <code>toolWindowId</code>.
   */
  protected ActivateToolWindowAction(final String toolWindowId, final String text, final Icon icon){
    super(text, IdeBundle.message("action.activate.tool.window", toolWindowId), icon);
    myToolWindowId=toolWindowId;
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    ToolWindow toolWindow=ToolWindowManager.getInstance(project).getToolWindow(myToolWindowId);
    presentation.setEnabled(toolWindow!=null&&toolWindow.isAvailable());
    presentation.setVisible(toolWindow!=null);
    if (toolWindow != null) {
      presentation.setIcon(toolWindow.getIcon());
    }
  }

  public void actionPerformed(AnActionEvent e){
    Project project = e.getData(PlatformDataKeys.PROJECT);
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    if (windowManager.isEditorComponentActive() || !myToolWindowId.equals(windowManager.getActiveToolWindowId())) {
      windowManager.getToolWindow(myToolWindowId).activate(null);
    }
    else {
      windowManager.getToolWindow(myToolWindowId).hide(null);
    }
  }

  public String getToolWindowId() {
    return myToolWindowId;
  }

  /**
   * This is the "rule" method constructs <code>ID</code> of the action for activating tool window
   * with specified <code>ID</code>.
   * @param id <code>id</code> of tool window to be activated.
   */
  @NonNls
  public static String getActionIdForToolWindow(String id){
    return "Activate"+id.replaceAll(" ","")+"ToolWindow";
  }

  /**
   * @return mnemonic for action if it has Alt+digit/Meta+digit shortcut.
   * Otherwise the method returns <code>-1</code>. Meta mask is OK for
   * Mac OS X user, because Alt+digit types strange characters into the
   * editor.
   */
  public static int getMnemonicForToolWindow(String id){
    Keymap activeKeymap=KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = activeKeymap.getShortcuts(getActionIdForToolWindow(id));
    for (int i = 0; i < shortcuts.length; i++) {
      Shortcut shortcut = shortcuts[i];
      if (shortcut instanceof KeyboardShortcut) {
        KeyStroke keyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        int modifiers=keyStroke.getModifiers();
        if (
          (modifiers == (KeyEvent.ALT_DOWN_MASK|KeyEvent.ALT_MASK)) ||
          (modifiers == KeyEvent.ALT_MASK) ||
          (modifiers == KeyEvent.ALT_DOWN_MASK) ||
          (modifiers == (KeyEvent.META_DOWN_MASK|KeyEvent.META_MASK)) ||
          (modifiers == KeyEvent.META_MASK) ||
          (modifiers == KeyEvent.META_DOWN_MASK)
        ) {
          int keyCode = keyStroke.getKeyCode();
          if (KeyEvent.VK_0 <= keyCode && keyCode <= KeyEvent.VK_9) {
            char c = (char) ('0' + keyCode - KeyEvent.VK_0);
            return (int)c;
          }
        }
      }
    }
    return -1;
  }
}