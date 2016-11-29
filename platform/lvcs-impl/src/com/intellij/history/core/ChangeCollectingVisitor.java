/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.history.core;

import com.intellij.history.core.changes.*;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

public class ChangeCollectingVisitor extends ChangeVisitor {
  private String myPath;
  private final String myProjectId;
  private final Pattern myPattern;
  private ChangeSet myCurrentChangeSet;
  private boolean myExists = true;
  private boolean myDoNotAddAnythingElseFromCurrentChangeSet = false;
  private final LinkedHashSet<ChangeSet> myResult = new LinkedHashSet<>();

  public ChangeCollectingVisitor(String path, String projectId, @Nullable String pattern) {
    myPath = path;
    myProjectId = projectId;
    myPattern = pattern == null ? null : Pattern.compile(NameUtil.buildRegexp(pattern, 0, true, true), Pattern.CASE_INSENSITIVE);
  }

  public List<ChangeSet> getChanges() {
    return new ArrayList<>(myResult);
  }

  public String getPath() {
    return myPath;
  }

  @Override
  public void begin(ChangeSet c) throws StopVisitingException {
    myCurrentChangeSet = c;
  }

  @Override
  public void end(ChangeSet c) throws StopVisitingException {
    myCurrentChangeSet = null;
    myDoNotAddAnythingElseFromCurrentChangeSet = false;
  }

  @Override
  public void visit(PutLabelChange c) throws StopVisitingException {
    doVisit(c);
  }

  @Override
  public void visit(StructuralChange c) throws StopVisitingException {
    doVisit(c);
  }

  private void doVisit(Change c) {
    if (skippedDueToNonexistence(c)) return;
    addIfAffectsAndRevert(c);
  }

  @Override
  public void visit(CreateEntryChange c) throws StopVisitingException {
    if (skippedDueToNonexistence(c)) return;
    addIfAffectsAndRevert(c);
    if (c.isCreationalFor(myPath)) myExists = false;
  }

  @Override
  public void visit(DeleteChange c) throws StopVisitingException {
    if (skippedDueToNonexistence(c)) {
      if (c.isDeletionOf(myPath)) {
        addIfAffectsAndRevert(c);
        myExists = true;
        myDoNotAddAnythingElseFromCurrentChangeSet = true;
      }
    } else {
      addIfAffectsAndRevert(c);
    }
  }

  private void addIfAffectsAndRevert(Change c) {
    if (!myDoNotAddAnythingElseFromCurrentChangeSet && (c.affectsPath(myPath) || c.affectsProject(myProjectId))) {
      if (myPattern == null || c.affectsMatching(myPattern)) {
        myResult.add(myCurrentChangeSet);
      }
    }
    if (c instanceof StructuralChange) myPath = ((StructuralChange)c).revertPath(myPath);
  }

  private boolean skippedDueToNonexistence(Change c) {
    if (myExists) return false;
    if (c instanceof StructuralChange) myPath = ((StructuralChange)c).revertPath(myPath);
    return true;
  }
}
