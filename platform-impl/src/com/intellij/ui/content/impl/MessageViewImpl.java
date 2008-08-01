
package com.intellij.ui.content.impl;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Eugene Belyaev
 */
public class MessageViewImpl implements ProjectComponent, MessageView {
  private StartupManager myStartupManager;
  private ToolWindowManager myToolWindowManager;
  private ToolWindow myToolWindow;
  private List<Runnable> myPostponedRunnables = new ArrayList<Runnable>();

  public MessageViewImpl(final StartupManager startupManager, final ToolWindowManager toolWindowManager) {
    myStartupManager = startupManager;
    myToolWindowManager = toolWindowManager;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    myStartupManager.registerPostStartupActivity(new Runnable() {
      public void run() {
        myToolWindow = myToolWindowManager.registerToolWindow(ToolWindowId.MESSAGES_WINDOW, true, ToolWindowAnchor.BOTTOM);
        myToolWindow.setIcon(IconLoader.getIcon("/general/toolWindowMessages.png"));
        new ContentManagerWatcher(myToolWindow, getContentManager());
        for (Runnable postponedRunnable : myPostponedRunnables) {
          postponedRunnable.run();
        }
        myPostponedRunnables.clear();
      }
    });
  }

  public ContentManager getContentManager() {
    return myToolWindow.getContentManager();
  }

  public void runWhenInitialized(final Runnable runnable) {
    if (myToolWindow != null) {
      runnable.run();
    }
    else {
      myPostponedRunnables.add(runnable);
    }
  }

  public void projectClosed() {
    myToolWindowManager.unregisterToolWindow(ToolWindowId.MESSAGES_WINDOW);
  }

  @NotNull
  public String getComponentName() {
    return "MessageViewImpl";
  }
}
