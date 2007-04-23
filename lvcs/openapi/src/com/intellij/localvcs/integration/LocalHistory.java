package com.intellij.localvcs.integration;

import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistory implements SettingsSavingComponent {
  // make it private
  public static LocalHistory getInstance(Project p) {
    return p.getComponent(LocalHistory.class);
  }

  public static LocalHistoryAction startAction(Project p, String name) {
    return getInstance(p).startAction(name);
  }

  public static void putLabel(Project p, String name) {
    getInstance(p).putLabel(name);
  }

  public static void putLabel(Project p, String path, String name) {
    getInstance(p).putLabel(path, name);
  }

  public static Checkpoint putCheckpoint(Project p) {
    return getInstance(p).putCheckpoint();
  }

  public static byte[] getByteContentAt(Project p, VirtualFile f, long timestamp) {
    return getInstance(p).getByteContentAt(f, timestamp);
  }

  public static boolean isUnderControl(Project p, VirtualFile f) {
    return getInstance(p).isUnderControl(f);
  }

  public static boolean isEnabled(Project p) {
    return getInstance(p).isEnabled();
  }

  protected abstract LocalHistoryAction startAction(String name);

  protected abstract void putLabel(String name);

  protected abstract void putLabel(String path, String name);

  protected abstract Checkpoint putCheckpoint();

  protected abstract byte[] getByteContentAt(VirtualFile f, long timestamp);

  protected abstract boolean isUnderControl(VirtualFile f);

  protected abstract boolean isEnabled();
}
