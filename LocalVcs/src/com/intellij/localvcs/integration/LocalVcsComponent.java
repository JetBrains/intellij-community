package com.intellij.localvcs.integration;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.ChangeList;
import com.intellij.localvcs.RootEntry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Timer;

public class LocalVcsComponent implements ProjectComponent {
  private Project myProject;
  public LocalVcs vcs = new LocalVcs(new Storage(null) {
    @Override
    public ChangeList loadChangeList() {
      return new ChangeList();
    }

    @Override
    public RootEntry loadRootEntry() {
      return new RootEntry("");
    }

    @Override
    public Integer loadCounter() {
      return 0;
    }
  });

  public long counter = 0;

  public LocalVcsComponent(Project p) {
    myProject = p;
  }

  public void projectOpened() {
    ProjectRootManager m = ProjectRootManager.getInstance(myProject);
    //
    //VirtualFile[] roots = m.getContentRoots();
    //for (int i = 0; i < roots.length - 1; i++) {
    //  VirtualFile rooti = roots[i];
    //  for (int j = 1; j < roots.length; j++) {
    //    VirtualFile rootj = roots[j];
    //    if (rooti.getPath().startsWith(rootj.getPath())
    //      || rootj.getPath().startsWith(rooti.getPath())) {
    //      System.out.println("collision: " + rooti.getPath() + " with " + rootj);
    //    }
    //  }
    //}

    try {
      System.out.println("started");
      long before = System.currentTimeMillis();
      Updater.updateRoots(vcs, m.getContentRoots());
      long after = System.currentTimeMillis();

      counter = after - before;
      System.out.println("counter = " + counter);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "NewLocalVcs";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
