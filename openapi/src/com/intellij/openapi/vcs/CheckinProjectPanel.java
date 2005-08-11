/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.VcsOperation;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.Collection;
import java.util.List;


public interface CheckinProjectPanel extends Refreshable {

  @SuppressWarnings({"HardCodedStringLiteral"})
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

  Collection<VirtualFile> getRoots();

  void setCommitMessage(final String currentDescription);
}
