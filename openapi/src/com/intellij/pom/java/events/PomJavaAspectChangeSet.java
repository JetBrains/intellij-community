/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

package com.intellij.pom.java.events;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.java.PomJavaAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.psi.PsiFile;

import java.util.List;
import java.util.ArrayList;

public class PomJavaAspectChangeSet implements PomChangeSet{
  private final PomModel myModel;
  private List<PomJavaChange> myChanges = new ArrayList<PomJavaChange>();
  private final PsiFile myChangedFile;


  public PomJavaAspectChangeSet(PomModel model, PsiFile fileChanged) {
    myModel = model;
    myChangedFile = fileChanged;
  }

  public void addChange(PomJavaChange change) {
    myChanges.add(change);
  }

  public List<PomJavaChange> getChanges() {
    return myChanges;
  }

  public PomModelAspect getAspect() {
    return myModel.getModelAspect(PomJavaAspect.class);
  }

  public PsiFile getChangedFile() {
    return myChangedFile;
  }
}
