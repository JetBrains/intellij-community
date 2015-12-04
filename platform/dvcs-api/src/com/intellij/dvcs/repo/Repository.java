/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.dvcs.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>
 * Repository is a representation of a Git repository stored under the specified directory.
 * It stores the information about the repository, which is frequently requested by other plugin components.
 * All get-methods (like {@link #getCurrentRevision()}) are just getters of the correspondent fields and thus are very fast.
 * </p>
 * <p>
 * The Repository is updated "externally" by the appropriate Updater class}, when correspondent {@code .git/ or .hg/} service files
 * change.
 * </p>
 * <p>
 * To force asynchronous update, it is enough to call {@link VirtualFile#refresh(boolean, boolean) refresh} on the root directory.
 * </p>
 * <p>
 * To make a synchronous update of the repository call {@link #update()}.
 * Updating requires reading from disk, so it may take some time, however, updating the whole community repository took ~10 ms at the time
 * of measurement, so must be fast enough. Better not to be called in AWT though.
 * <p/>
 * <p>
 * Getters and setters (update...()-methods) are not synchronized intentionally - to avoid live- and deadlocks.
 * GitRepository is updated asynchronously,
 * so even if the getters would have been synchronized, it wouldn't guarantee that they return actual values (as they are in .git).
 * <br/>
 * If one needs a really 100 % up-to-date value, one should call {@link #update()} and then get...().
 * update() is a synchronous read from repository file (.git or .hg), so it is guaranteed to query the real value.
 * </p>
 */
public interface Repository extends Disposable {

  /**
   * Current state of the repository.
   */
  enum State {
    /**
     * HEAD is on branch, no merge process is in progress (and no rebase as well).
     */
    NORMAL,

    /**
     * During merge (for instance, merge failed with conflicts that weren't immediately resolved).
     */
    MERGING {
      @NotNull
      @Override
      public String toString() {
        return "Merging";
      }
    },

    /**
     * During rebase.
     */
    REBASING {
      @NotNull
      @Override
      public String toString() {
        return "Rebasing";
      }
    },

    /**
     * During Cherry-pick/grafting.
     */
    GRAFTING {
      @NotNull
      @Override
      public String toString() {
        return "Grafting";
      }
    },


    /**
     * Detached HEAD state, but not during rebase (for example, manual checkout of a commit hash).
     */
    DETACHED
  }

  @NotNull
  VirtualFile getRoot();

  @NotNull
  String getPresentableUrl();

  @NotNull
  Project getProject();

  @NotNull
  State getState();

  @Nullable
  String getCurrentBranchName();

  @NotNull
  AbstractVcs getVcs();

  /**
   * Returns the hash of the revision, which HEAD currently points to.
   * Returns null only in the case of a fresh repository, when no commit have been made.
   */
  @Nullable
  String getCurrentRevision();

  /**
   * @return true if current repository is "fresh", i.e. if no commits have been made yet.
   */
  boolean isFresh();

  /**
   * Synchronously updates the Repository by reading information about it from disk (e.g. for Git: from .git/config and .git/refs/...)
   */
  void update();

  /**
   * Returns a detailed String representation suitable for logging purposes.
   */
  @NotNull
  String toLogString();
}
