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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Base class to execute push command.
 */
public abstract class Pusher<Repo extends Repository, Source extends PushSource, Target extends PushTarget> {

  /**
   * Perform push for all given repositories.
   *
   * @param pushSpecs        push specs for each repository telling what to push and where.
   * @param additionalOption some additional push option(s), which are received from
   *                         {@link PushSupport#createOptionsPanel() the additional panel} if the plugin has one.
   * @param force            if true then force push should be performed.
   */
  public abstract void push(@NotNull Map<Repo, PushSpec<Source, Target>> pushSpecs,
                            @Nullable VcsPushOptionValue additionalOption,
                            boolean force);

}

