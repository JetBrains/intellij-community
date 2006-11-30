package com.intellij.localvcs.integration;

import com.intellij.localvcs.ChangeList;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.RootEntry;
import com.intellij.localvcs.Storage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LocalVcsComponent implements ProjectComponent {
  private StartupManager myStartupManager;
  private ProjectRootManagerEx myRootManager;
  private VirtualFileManager myFileManager;
  private LocalVcs myVcs;
  private LocalVcsService myService;

  public LocalVcsComponent(StartupManager sm, ProjectRootManagerEx rm, VirtualFileManager fm) {
    myStartupManager = sm;
    myRootManager = rm;
    myFileManager = fm;
  }

  public void initComponent() {
    if (isDisabled()) {
      //System.out.println("localvcs is disabled");
      return;
    }
    //System.out.println("localvcs is initialized");


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
    myService = new LocalVcsService(myVcs, myStartupManager, myRootManager, myFileManager);
  }

  public void disposeComponent() {
    if (isDisabled()) return;
    Disposer.dispose(myService);
    //System.out.println("localvcs is disposed");
  }

  private boolean isDisabled() {
    if (System.getProperty("localvcs.enabled") != null) return false;
    if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    return true;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "NewLocalVcs";
  }

  public LocalVcs getLocalVcs() {
    return myVcs;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }
}
