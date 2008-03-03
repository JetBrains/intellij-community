package com.intellij.platform;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author yole
 */
public class ProjectBaseDirectory {
  public static ProjectBaseDirectory getInstance(Project project) {
    return ServiceManager.getService(project, ProjectBaseDirectory.class);
  }

  private VirtualFile BASE_DIR;
  private CopyOnWriteArrayList<Listener> myListeners = new CopyOnWriteArrayList<Listener>();

  public VirtualFile getBaseDir(final VirtualFile baseDir) {
    if (getBaseDir() != null) {
      return getBaseDir();
    }
    return baseDir;
  }

  public VirtualFile getBaseDir() {
    return BASE_DIR;
  }

  public void setBaseDir(final VirtualFile baseDir) {
    BASE_DIR = baseDir;
    for(Listener listener: myListeners) {
      listener.baseDirChanged();
    }
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(Listener listener) {
    myListeners.remove(listener);
  }

  public static interface Listener {
    void baseDirChanged();
  }
}
