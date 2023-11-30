// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core;

import com.intellij.history.core.changes.*;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

public final class ChangeCollectingVisitor extends ChangeVisitor {
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
  public void begin(ChangeSet c) {
    myCurrentChangeSet = c;
  }

  @Override
  public void end(ChangeSet c) {
    myCurrentChangeSet = null;
    myDoNotAddAnythingElseFromCurrentChangeSet = false;
  }

  @Override
  public void visit(PutLabelChange c) {
    doVisit(c);
  }

  @Override
  public void visit(StructuralChange c) {
    doVisit(c);
  }

  private void doVisit(Change c) {
    if (skippedDueToNonexistence(c)) return;
    addIfAffectsAndRevert(c);
  }

  @Override
  public void visit(CreateEntryChange c) {
    if (skippedDueToNonexistence(c)) return;
    addIfAffectsAndRevert(c);
    if (c.isCreationalFor(myPath)) myExists = false;
  }

  @Override
  public void visit(DeleteChange c) {
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
