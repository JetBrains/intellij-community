/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Mike
 */
public interface LvcsObject {
  LocalVcs getLocalVcs();

  LvcsRevision getRevision();
  LvcsRevision getRevision(LvcsLabel label);

  String getName();
  String getName(LvcsLabel label);

  String getAbsolutePath();
  String getAbsolutePath(LvcsLabel label);

  long getDate();
  long getDate(LvcsLabel label);

  boolean exists();
  boolean exists(LvcsLabel label);

  LvcsObject getParent();
  LvcsObject getParent(LvcsLabel label);

  void delete();

  void rename(String newName, VirtualFile file);
  void move(LvcsDirectory newParent, VirtualFile virtualFile);

  void scheduleForRemoval();
}
