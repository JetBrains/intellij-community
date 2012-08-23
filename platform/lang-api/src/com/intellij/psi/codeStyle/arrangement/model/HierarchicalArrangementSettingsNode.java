/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link #getCurrent() Wraps} {@link ArrangementMatchCondition} in order to allow to build {@link #getChild() hierarchy}
 * from a plain sequence of them
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 1:23 PM
 */
public class HierarchicalArrangementSettingsNode {

  @NotNull private final ArrangementMatchCondition           myCurrent;
  @Nullable private      HierarchicalArrangementSettingsNode myChild;

  public HierarchicalArrangementSettingsNode(@NotNull ArrangementMatchCondition current) {
    myCurrent = current;
  }

  @NotNull
  public ArrangementMatchCondition getCurrent() {
    return myCurrent;
  }

  @Nullable
  public HierarchicalArrangementSettingsNode getChild() {
    return myChild;
  }

  public void setChild(@Nullable HierarchicalArrangementSettingsNode child) {
    myChild = child;
  }

  @Override
  public String toString() {
    return myCurrent.toString();
  }
}
