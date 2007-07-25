package com.intellij.history;

import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistory implements SettingsSavingComponent {
  public static LocalHistoryAction startAction(Project p, String name) {
    return getInstance(p).startAction(name);
  }

  public static void putSystemLabel(Project p, String name) {
    getInstance(p).putSystemLabel(name, -1);
  }

  public static void putSystemLabel(Project p, String name, int color) {
    getInstance(p).putSystemLabel(name, color);
  }

  public static Checkpoint putCheckpoint(Project p) {
    return getInstance(p).putCheckpoint();
  }

  public static byte[] getByteContent(Project p, VirtualFile f, RevisionTimestampComparator c) {
    return getInstance(p).getByteContent(f, c);
  }

  public static boolean isUnderControl(Project p, VirtualFile f) {
    return getInstance(p).isUnderControl(f);
  }

  public static boolean hasUnavailableContent(Project p, VirtualFile f) {
    return getInstance(p).hasUnavailableContent(f);
  }

  private static LocalHistory getInstance(Project p) {
    return p.getComponent(LocalHistory.class);
  }

  protected abstract LocalHistoryAction startAction(String name);

  protected abstract void putSystemLabel(String name, int color);

  protected abstract Checkpoint putCheckpoint();

  protected abstract byte[] getByteContent(VirtualFile f, RevisionTimestampComparator c);

  protected abstract boolean isUnderControl(VirtualFile f);

  protected abstract boolean hasUnavailableContent(VirtualFile f);
}
