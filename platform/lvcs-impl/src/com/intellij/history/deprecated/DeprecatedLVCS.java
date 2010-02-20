/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.deprecated;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.localVcs.*;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeprecatedLVCS extends LocalVcs implements ProjectComponent {
  private final Project myProject;
  private final LocalHistoryConfiguration myConfiguration;

  public DeprecatedLVCS(Project project, LocalHistoryConfiguration configuration) {
    myProject = project;
    myConfiguration = configuration;
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "DeprecatedLocalVcs";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void save() {
  }

  public Project getProject() {
    return myProject;
  }

  public String[] getRootPaths() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nullable
  public LvcsFile findFile(String filePath) {
    return null;
  }

  @Nullable
  public LvcsFile findFile(String filePath, boolean ignoreDeleted) {
    return null;
  }

  @Nullable
  public LvcsFileRevision findFileRevisionByDate(String filePath, long date) {
    return null;
  }

  @Nullable
  public LvcsDirectory findDirectory(String dirPath) {
    return null;
  }

  @Nullable
  public LvcsDirectory findDirectory(String dirPath, boolean ignoreDeleted) {
    return null;
  }

  public LvcsLabel addLabel(String name, String path) {
    return doPutLabel(name, path);
  }

  public LvcsLabel addLabel(byte type, String name, String path) {
    return doPutLabel(name, path);
  }

  private LvcsLabel doPutLabel(String name, String path) {
    LocalHistory.putSystemLabel(myProject, name);
    return null;
  }

  public LvcsAction startAction(final String name, String path, boolean isExternalChanges) {
    return new LvcsAction() {
      LocalHistoryAction a = LocalHistory.startAction(myProject, name);

      public void finish() {
        a.finish();
      }

      public String getName() {
        return name;
      }
    };
  }

  public LvcsRevision[] getRevisions(final String path, final LvcsLabel label) {
    return new LvcsRevision[0];
  }

  public LvcsRevision[] getRevisions(final LvcsLabel label1, final LvcsLabel label2) {
    return new LvcsRevision[0];
  }

  public boolean isUnderVcs(VirtualFile file) {
    return false;
  }

  public boolean isAvailable() {
    return true;
  }

  public LocalVcsPurgingProvider getLocalVcsPurgingProvider() {
    return new LocalVcsPurgingProvider() {
      public void registerLocker(LocalVcsItemsLocker locker) {
      }

      public void unregisterLocker(LocalVcsItemsLocker locker) {
      }

      public boolean itemCanBePurged(LvcsRevision lvcsRevisionFor) {
        return true;
      }
    };
  }

  public LvcsLabel[] getAllLabels() {
    return new LvcsLabel[0];
  }

  public void addLvcsLabelListener(LvcsLabelListener listener) {
  }

  public void removeLvcsLabelListener(final LvcsLabelListener listener) {
  }

  public LocalHistoryConfiguration getConfiguration() {
    return myConfiguration;
  }
}
