package com.intellij.localvcs.integration;

import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistory implements SettingsSavingComponent {
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

  public static void mark(Project p, VirtualFile f) {
    getInstance(p).mark(f);
  }

  public static byte[] getByteContent(Project p, VirtualFile f, RevisionTimestampComparator c) {
    return getInstance(p).getByteContent(f, c);
  }

  public static byte[] getLastMarkedByteContent(Project p, VirtualFile f) {
    return getInstance(p).getLastMarkedByteContent(f);
  }

  public static boolean isUnderControl(Project p, VirtualFile f) {
    return getInstance(p).isUnderControl(f);
  }

  public static boolean hasUnavailableContent(Project p, VirtualFile f) {
    return getInstance(p).hasUnavailableContent(f);
  }

  public static boolean isEnabled(Project p) {
    return System.getProperty("UseOldLocalHistory") == null;
  }

  // todo make it private
  protected static LocalHistory getInstance(Project p) {
    return p.getComponent(LocalHistory.class);
  }

  public static LocalHistoryConfiguration getConfiguration() {
    return LocalHistoryConfiguration.getInstance();
  }

  protected abstract LocalHistoryAction startAction(String name);

  protected abstract void putLabel(String name);

  protected abstract void putLabel(String path, String name);

  protected abstract Checkpoint putCheckpoint();

  protected abstract void mark(VirtualFile f);

  protected abstract byte[] getLastMarkedByteContent(VirtualFile f);

  protected abstract byte[] getByteContent(VirtualFile f, RevisionTimestampComparator c);

  protected abstract boolean isUnderControl(VirtualFile f);

  protected abstract boolean hasUnavailableContent(VirtualFile f);
}
