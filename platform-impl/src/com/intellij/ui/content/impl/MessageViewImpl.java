
package com.intellij.ui.content.impl;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;

/**
 * @author Eugene Belyaev
 */
public class MessageViewImpl implements ProjectComponent, MessageView {
  private Project myProject;
  private ToolWindow myToolWindow;

  public MessageViewImpl(Project project) {
    myProject = project;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    myToolWindow = toolWindowManager.registerToolWindow(ToolWindowId.MESSAGES_WINDOW, true, ToolWindowAnchor.BOTTOM);
    myToolWindow.setIcon(IconLoader.getIcon("/general/toolWindowMessages.png"));
    new ContentManagerWatcher(myToolWindow, getContentManager());
  }

  public ContentManager getContentManager() {
    return myToolWindow.getContentManager();
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.MESSAGES_WINDOW);
  }

  public String getComponentName() {
    return "MessageViewImpl";
  }
}
