
package com.intellij.ui.content.impl;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class MessageViewImpl implements MessageView {
  private ToolWindow myToolWindow;
  private final List<Runnable> myPostponedRunnables = new ArrayList<Runnable>();

  public MessageViewImpl(final Project project, final StartupManager startupManager, final ToolWindowManager toolWindowManager) {
    final Runnable runnable = new Runnable() {
      public void run() {
        myToolWindow = toolWindowManager.registerToolWindow(ToolWindowId.MESSAGES_WINDOW, true, ToolWindowAnchor.BOTTOM, project);
        myToolWindow.setIcon(IconLoader.getIcon("/general/toolWindowMessages.png"));
        new ContentManagerWatcher(myToolWindow, getContentManager());
        for (Runnable postponedRunnable : myPostponedRunnables) {
          postponedRunnable.run();
        }
        myPostponedRunnables.clear();
      }
    };
    if (project.isInitialized()) {
      runnable.run();
    }
    else {
      startupManager.registerPostStartupActivity(new Runnable(){
        public void run() {
          runnable.run();
        }
      });
    }

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
}
