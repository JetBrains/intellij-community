/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.build;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 * @since 5/11/2016
 */
public class BuildScopeImpl implements BuildScope {
  private final Collection<? extends BuildTarget> myTargets;
  @Nullable
  private Object mySessionId;

  public BuildScopeImpl(Collection<? extends BuildTarget> targets) {
    myTargets = targets;
  }

  public BuildScopeImpl(Collection<? extends BuildTarget> targets, @Nullable Object sessionId) {
    myTargets = targets;
    mySessionId = sessionId;
  }

  @Override
  public Collection<? extends BuildTarget> getTargets() {
    return myTargets;
  }

  @Override
  public void setSessionId(@Nullable Object sessionId) {
    mySessionId = sessionId;
  }

  @Nullable
  @Override
  public Object getSessionId() {
    return mySessionId;
  }
}
