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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Base class to provide vcs-specific info
 */

public abstract class PushSupport {
  public static final ExtensionPointName<PushSupport> PUSH_SUPPORT_EP = ExtensionPointName.create("com.intellij.pushSupport");

  @NotNull
  public abstract AbstractVcs getVcs();

  @NotNull
  public abstract Pusher getPusher();

  @NotNull
  public abstract OutgoingCommitsProvider getOutgoingCommitsProvider();

  /**
   * @return Default push destination
   */
  @Nullable
  public abstract PushTarget getDefaultTarget(@NotNull Repository repository);

  /**
   * @return All remembered remote destinations used for completion
   */
  @NotNull
  public abstract Collection<String> getTargetNames(@NotNull Repository repository);

  /**
   * @return current source(branch) for repository
   */
  @NotNull
  public abstract PushSource getSource(@NotNull Repository repository);

  /** Create destination target from user input string
   * @return
   */
  @Nullable
  public abstract PushTarget createTarget(String targetName);

  /**
   * @return RepositoryManager for vcs
   */
  @NotNull
  public abstract RepositoryManager<? extends Repository> getRepositoryManager();

  @Nullable
  public VcsPushOptionsPanel getVcsPushOptionsPanel(){
    return null;
  }
}
