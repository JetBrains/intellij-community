package com.intellij.localvcs.integration;

import com.intellij.localvcs.ChangeList;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.RootEntry;
import com.intellij.localvcs.Storage;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LocalVcsComponent implements ProjectComponent {
  private MessageBus myBus;
  private StartupManager myStartupManager;
  private ProjectRootManager myRootManager;
  private VirtualFileManager myFileManager;
  private LocalVcs myVcs;
  private LocalVcsService myService;

  public LocalVcsComponent(MessageBus b, StartupManager sm, ProjectRootManager rm, VirtualFileManager fm) {
    myBus = b;
    myStartupManager = sm;
    myRootManager = rm;
    myFileManager = fm;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "NewLocalVcs";
  }

  public LocalVcs getLocalVcs() {
    return myVcs;
  }

  public void initComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) return;

    myVcs = new LocalVcs(new Storage(null) {
      @Override
      public ChangeList loadChangeList() {
        return new ChangeList();
      }

      @Override
      public RootEntry loadRootEntry() {
        return new RootEntry();
      }

      @Override
      public Integer loadCounter() {
        return 0;
      }
    });
    myService = new LocalVcsService(myVcs, myBus, myStartupManager, myRootManager, myFileManager);
  }

  public void disposeComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) return;

    Disposer.dispose(myService);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }
}
