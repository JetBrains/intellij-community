/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Document;

/**
 * @author Mike
 */
public abstract class LocalVcs implements SettingsSavingComponent {
  public static LocalVcs getInstance(Project project) {
    return project.getComponent(LocalVcs.class);
  }

  public abstract void save();

  public abstract String[] getRootPaths();

  public abstract LvcsFile findFile(String filePath);

  public abstract LvcsFile findFile(String filePath, boolean ignoreDeleted);

  public abstract LvcsFile findFile(String filePath, LvcsLabel label);

  public abstract LvcsDirectory findDirectory(String dirPath);

  public abstract LvcsDirectory findDirectory(String dirPath, boolean ignoreDeleted);

  public abstract LvcsDirectory findDirectory(String dirPath, LvcsLabel label);

  public abstract LvcsLabel addLabel(String name, String path);

  public abstract LvcsLabel addLabel(byte type, String name, String path);

  public abstract LvcsAction startAction(String action, String path, boolean isExternalChanges);

  public abstract LvcsRevision[] getChanges(String path, LvcsLabel label, boolean upToDateOnly);

  public abstract LvcsRevision[] getChanges(LvcsLabel label1, LvcsLabel label2);

  public abstract boolean isUnderVcs(VirtualFile file);

  public abstract int purge();

  public abstract boolean isAvailable();

  public abstract LocalVcsPurgingProvider getLocalVcsPurgingProvider();

  public abstract void markSourcesAsCurrent(String label);

  public abstract LvcsLabel[] getAllLabels();

  public abstract boolean rollbackToLabel(LvcsLabel label, boolean requestConfirmation, String confirmationMessage, String confirmationTitle);

  public abstract boolean rollbackToLabel(LvcsLabel label, boolean requestConfirmation);

  public abstract void addLvcsLabelListener(LvcsLabelListener listener);

  public abstract void removeLvcsLabelListener(LvcsLabelListener listener);

  public abstract UpToDateLineNumberProvider getUpToDateLineNumberProvider(Document document, String upToDateContent);
}
