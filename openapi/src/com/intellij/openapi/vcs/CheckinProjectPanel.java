/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.VcsOperation;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.Collection;
import java.util.List;


public interface CheckinProjectPanel extends Refreshable {

  String REVISIONS = "Revisions";

  JComponent getComponent();

  JComponent getPreferredFocusedComponent();

  boolean hasDiffs();

  /**
   * Adds selection change listener to the panel. To obtain selected object/objects use passed context with
   * {@link com.intellij.openapi.actionSystem.DataConstants#VIRTUAL_FILE} or
   * {@link com.intellij.openapi.actionSystem.DataConstants#VIRTUAL_FILE_ARRAY} constants
   */
  void addSelectionChangeListener(SelectionChangeListener listener);

  void removeSelectionChangeListener(SelectionChangeListener listener);

  Collection<VirtualFile> getVirtualFiles();

  Project getProject();

  List<VcsOperation> getCheckinOperations(CheckinEnvironment checkinEnvironment);
}
