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
  public String myType;
  public String myFQName;
  public @InspectionMessage String myDescription;
  public List<String> myHints;
  public int myProblemIndex;
  public int myLine;
  public int myOffset;
  public String myModuleName;

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

  public void setHints(List<String> hints) {
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

  @Nullable
  public RefEntity getRefElement(@NotNull RefManager refManager) {
    return ReadAction.compute(() -> refManager.getProject().isDisposed() ? null : refManager.getReference(myType, myFQName));
  }

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
