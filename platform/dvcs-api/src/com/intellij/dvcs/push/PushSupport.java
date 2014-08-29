/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.ui.SimpleColoredText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class to provide vcs-specific info
 */

public abstract class PushSupport<Repo extends Repository, Source extends PushSource, Target extends PushTarget> {

  public static final ExtensionPointName<PushSupport<? extends Repository, ? extends PushSource, ? extends PushTarget>> PUSH_SUPPORT_EP =
    ExtensionPointName.create("com.intellij.pushSupport");

  @NotNull
  public abstract AbstractVcs getVcs();

  @NotNull
  public abstract Pusher<Repo, Source, Target> getPusher();

  @NotNull
  public abstract OutgoingCommitsProvider<Repo, Source, Target> getOutgoingCommitsProvider();

  /**
   * @return Default push destination
   */
  @Nullable
  public abstract Target getDefaultTarget(@NotNull Repo repository);

  /**
   * @return All remote destinations which will be proposed to user in the target field completion.
   *         They will be shown in the same order as they appear in the returned list.
   */
  @NotNull
  public abstract List<String> getTargetNames(@NotNull Repo repository);

  /**
   * @return current source(branch) for repository
   */
  @NotNull
  public abstract Source getSource(@NotNull Repo repository);

  /**
   * Parse user input string, and create the VALID target for push
   *
   * @see #validateSpec(Repository, PushSpec)
   */
  @NotNull
  public abstract Target createTarget(@NotNull Repo repository, @NotNull String targetName);

  /**
   * @return RepositoryManager for vcs
   */
  @NotNull
  public abstract RepositoryManager<Repo> getRepositoryManager();

  @Nullable
  public VcsPushOptionsPanel getVcsPushOptionsPanel() {
    return null;
  }

  /**
   * @return null if target is valid for selected repository
   */
  @Nullable
  public abstract VcsError validate(@NotNull Repo repository, @Nullable String targetToValidate);

  public abstract SimpleColoredText renderTarget(@Nullable Target target);
}
