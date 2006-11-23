package com.intellij.localvcs.integration;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.ChangeList;
import com.intellij.localvcs.RootEntry;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class LocalVcsComponent implements ProjectComponent {
  private Project myProject;
  public LocalVcs vcs = new LocalVcs(new Storage(null) {
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

  public LocalVcsComponent(Project p) {
    myProject = p;
  }

  public void projectOpened() {
    ProjectRootManager m = ProjectRootManager.getInstance(myProject);

    try {
      System.out.println("started");

      long before = System.currentTimeMillis();
      Updater.update(vcs, m.getContentRoots());
      long after = System.currentTimeMillis();

      long time = after - before;
      System.out.println("time = " + time);
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
