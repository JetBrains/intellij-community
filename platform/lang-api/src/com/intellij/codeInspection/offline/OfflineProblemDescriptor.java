// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.offline;

import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class OfflineProblemDescriptor {
  private String myType;
  private String myFQName;
  private @InspectionMessage String myDescription;
  private List<String> myHints;
  private int myProblemIndex;
  private int myLine;
  private int myOffset;
  private String myModuleName;

  public String getType() {
    return myType;
  }

  public void setType(String type) {
    myType = type;
  }

  public String getFQName() {
    return myFQName;
  }

  public void setFQName(String FQName) {
    myFQName = FQName;                              
  }

  public @InspectionMessage String getDescription() {
    return myDescription;
  }

  public void setDescription(@InspectionMessage String description) {
    myDescription = description;
  }

  public List<String> getHints() {
    return myHints;
  }

  public void setHints(@NotNull List<@NotNull String> hints) {
    myHints = hints;
  }

  public int getProblemIndex() {
    return myProblemIndex;
  }

  public void setProblemIndex(int problemIndex) {
    myProblemIndex = problemIndex;
  }

  public int getLine() {
    return myLine;
  }

  public void setLine(int line) {
    myLine = line;
  }

  public void setOffset(int offset) {
    myOffset = offset;
  }

  public int getOffset() {
    return myOffset;
  }

  public @Nullable RefEntity getRefElement(@NotNull RefManager refManager) {
    return ReadAction.compute(() -> refManager.getProject().isDisposed() ? null : refManager.getReference(myType, myFQName));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    OfflineProblemDescriptor that = (OfflineProblemDescriptor)o;

    if (myLine != that.myLine) return false;
    if (myProblemIndex != that.myProblemIndex) return false;
    if (!Objects.equals(myDescription, that.myDescription)) return false;
    if (!Objects.equals(myFQName, that.myFQName)) return false;
    if (!Objects.equals(myHints, that.myHints)) return false;
    if (!Objects.equals(myModuleName, that.myModuleName)) return false;
    if (!Objects.equals(myType, that.myType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = Objects.hashCode(myType);
    result = 31 * result + (myFQName != null ? myFQName.hashCode() : 0);
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    result = 31 * result + (myHints != null ? myHints.hashCode() : 0);
    result = 31 * result + myProblemIndex;
    result = 31 * result + myLine;
    result = 31 * result + (myModuleName != null ? myModuleName.hashCode() : 0);
    return result;
  }

  public void setModule(String moduleName) {
    myModuleName = moduleName;
  }

  public String getModuleName() {
    return myModuleName;
  }

  @Override
  public String toString() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myFQName;
    }
    else {
      return myDescription;
    }
  }
}
