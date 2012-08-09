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

import java.util.ArrayList;
import java.util.List;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 1:23 PM
 */
public class HierarchicalArrangementSettingsNode {

  @NotNull private final List<HierarchicalArrangementSettingsNode> myChildren = new ArrayList<HierarchicalArrangementSettingsNode>();
  
  @NotNull private final ArrangementSettingsNode myCurrent;

  public HierarchicalArrangementSettingsNode(@NotNull ArrangementSettingsNode current) {
    myCurrent = current;
  }

  @NotNull
  public ArrangementSettingsNode getCurrent() {
    return myCurrent;
  }

  @NotNull
  public List<HierarchicalArrangementSettingsNode> getChildren() {
    return myChildren;
  }

  public void addChild(@NotNull HierarchicalArrangementSettingsNode child) {
    myChildren.add(child);
  }

  @Override
  public String toString() {
    return myCurrent.toString();
  }
}
