/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.fileView.FileViewEnvironment;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.update.UpdateEnvironment;

public abstract class AbstractVcs {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.AbstractVcs");

  protected final Project myProject;
  private boolean myIsStarted = false;

  public AbstractVcs(Project project) {
    myProject = project;
  }

  public abstract String getName();

  public abstract String getDisplayName();

  public abstract Configurable getConfigurable();

  public StandardOperationsProvider getStandardOperationsProvider() {
    return null;
  }

  public FileRenameProvider getFileRenamer() {
    return null;
  }

  public DirectoryRenameProvider getDirectoryRenamer() {
    return null;
  }

  public FileMoveProvider getFileMover() {
    return null;
  }

  public DirectoryMoveProvider getDirectoryMover() {
    return null;
  }

  public TransactionProvider getTransactionProvider() {
    return null;
  }

  public FileStatusProvider getFileStatusProvider() {
    return null;
  }

  public final VcsConfiguration getConfiguration() {
    return VcsConfiguration.getInstance(myProject);
  }

  public EditFileProvider getEditFileProvider() {
    return null;
  }

  public boolean supportsMarkSourcesAsCurrent() {
    return false;
  }

  public UpToDateRevisionProvider getUpToDateRevisionProvider() {
    return null;
  }

  public void doActivateActions(Module module) {

  }

  public boolean markExternalChangesAsUpToDate() {
    return false;
  }

  public void start() throws VcsException {
    myIsStarted = true;
  }

  public void shutdown() throws VcsException {
    LOG.assertTrue(myIsStarted);
    myIsStarted = false;
  }

  public FileViewEnvironment getFileViewEnvironment() {
    return null;
  }

  public CheckinEnvironment getCheckinEnvironment() {
    return null;
  }

  public VcsHistoryProvider getVcsHistoryProvider() {
    return null;
  }

  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return null;
  }

  public String getMenuItemText() {
    return getDisplayName();
  }

  public UpdateEnvironment getUpdateEnvironment() {
    return null;
  }

  public boolean fileIsUnderVcs(FilePath filePath) {
    return true;
  }

  public boolean fileExistsInVcs(FilePath path) {
    return true;
  }

  public UpdateEnvironment getStatusEnvironment() {
    return null;
  }
}
