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
package com.intellij.psi.codeStyle.arrangement;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 9/19/12 6:41 PM
 */
public class ArrangementEntryDependencyInfo {

  @NotNull private final List<ArrangementEntryDependencyInfo> myDependentEntries = new ArrayList<>();
  
  @NotNull private final JavaElementArrangementEntry myAnchorEntry;

  public ArrangementEntryDependencyInfo(@NotNull JavaElementArrangementEntry entry) {
    myAnchorEntry= entry;
  }

  public void addDependentEntryInfo(@NotNull ArrangementEntryDependencyInfo info) {
    myDependentEntries.add(info);
  }
  
  @NotNull
  public List<ArrangementEntryDependencyInfo> getDependentEntriesInfos() {
    return myDependentEntries;
  }

  @NotNull
  public JavaElementArrangementEntry getAnchorEntry() {
    return myAnchorEntry;
  }

  @Override
  public String toString() {
    return myAnchorEntry.toString();
  }
}
