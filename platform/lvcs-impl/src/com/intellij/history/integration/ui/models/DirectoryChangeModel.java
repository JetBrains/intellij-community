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

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ContentRevision;

public class DirectoryChangeModel {
  private final Difference myDiff;
  private final IdeaGateway myGateway;

  public DirectoryChangeModel(Difference d, IdeaGateway gw) {
    myDiff = d;
    myGateway = gw;
  }

  public Difference getDifference() {
    return myDiff;
  }

  public boolean isFile() {
    return myDiff.isFile();
  }

  public String getEntryName(int i) {
    Entry e = getEntry(i);
    return e == null ? "" : e.getName();
  }

  public Entry getEntry(int i) {
    return i == 0 ? myDiff.getLeft() : myDiff.getRight();
  }

  public boolean canShowFileDifference() {
    return isFile() && isContentVersioned();
  }

  private boolean isContentVersioned() {
    Entry e = getEntry(0);
    if (e == null) e = getEntry(1);
    return myGateway.areContentChangesVersioned(e.getName());
  }

  public ContentRevision getContentRevision(int i) {
    return i == 0 ? myDiff.getLeftContentRevision(myGateway) : myDiff.getRightContentRevision(myGateway);
  }
}
