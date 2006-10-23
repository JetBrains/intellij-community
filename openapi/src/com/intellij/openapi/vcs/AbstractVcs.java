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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.fileView.FileViewEnvironment;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.localVcs.LocalVcsItemsLocker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * The base class for a version control system integrated with IDEA.
 *
 * @see ProjectLevelVcsManager
 * @see ModuleLevelVcsManager
 */
public abstract class AbstractVcs {
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.AbstractVcs");

  protected final Project myProject;
  private boolean myIsStarted = false;
  private VcsShowSettingOption myCheckinOption;
  private VcsShowSettingOption myUpdateOption;
  private VcsShowSettingOption myStatusOption;

  private int myActiveModulesCount = 0;


  public AbstractVcs(Project project) {
    myProject = project;
  }

  @NonNls
  public abstract String getName();

  @NonNls
  public abstract String getDisplayName();

  public abstract Configurable getConfigurable();

  public TransactionProvider getTransactionProvider() {
    return null;
  }

  public ChangeProvider getChangeProvider() {
    return null;
  }

  public final VcsConfiguration getConfiguration() {
    return VcsConfiguration.getInstance(myProject);
  }

  /**
   * Returns the interface for performing check out / edit file operations.
   *
   * @return the interface implementation, or null if none is provided.
   */
  @Nullable
  public EditFileProvider getEditFileProvider() {
    return null;
  }

  public boolean supportsMarkSourcesAsCurrent() {
    return false;
  }

  public LocalVcsItemsLocker getItemsLocker() {
    return null;
  }

  public void doActivateActions(Module module) {
  }

  protected void activate() {

  }

  public void attachModule(final Module module) {
    myActiveModulesCount++;
    if (myActiveModulesCount == 1) {
      activate();
    }
  }

  public void detachModule(final Module module) {
    myActiveModulesCount--;
    if (myActiveModulesCount == 0) {
      deactivate();
    }
  }

  protected void deactivate() {
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

  /**
   * Returns the interface for performing checkin / commit / submit operations.
   *
   * @return the checkin interface, or null if checkins are not supported by the VCS.
   */
  @Nullable public CheckinEnvironment getCheckinEnvironment() {
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

  public AnnotationProvider getAnnotationProvider() {
    return null;
  }

  public DiffProvider getDiffProvider() {
    return null;
  }

  public VcsShowSettingOption getCheckinOptions() {
    return myCheckinOption;
  }

  public VcsShowSettingOption getUpdateOptions() {
    return myUpdateOption;
  }


  public VcsShowSettingOption getStatusOptions() {
    return myStatusOption;
  }

  public void loadSettings() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);

    if (getCheckinEnvironment() != null) {
      myCheckinOption = vcsManager.getStandardOption(VcsConfiguration.StandardOption.CHECKIN, this);
    }

    if (getUpdateEnvironment() != null) {
      myUpdateOption = vcsManager.getStandardOption(VcsConfiguration.StandardOption.UPDATE, this);
    }

    if (getStatusEnvironment() != null) {
      myStatusOption = vcsManager.getStandardOption(VcsConfiguration.StandardOption.STATUS, this);
    }
  }

  public FileStatus[] getProvidedStatuses() {
    return null;
  }

  /**
   * Returns the interface for selecting file version numbers.
   *
   * @return the revision selector implementation, or null if none is provided.
   * @since 5.0.2
   */
  @Nullable
  public RevisionSelector getRevisionSelector() {
    return null;
  }

  public UpdateEnvironment getIntegrateEnvironment() {
    return null;
  }

  /**
   * Returns the name of the "checkin" action.
   *
   * @return the name of the "checkin" action.
   * @since 6.5
   */
  public String getCheckinOperationName() {
    return VcsBundle.message("commit.dialog.default.commit.operation.name");
  }
}

