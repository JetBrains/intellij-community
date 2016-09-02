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

/*
 * User: anna
 * Date: 05-Jan-2007
 */
package com.intellij.codeInspection.offline;

import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class OfflineProblemDescriptor {
  public String myType;
  public String myFQName;
  public String myDescription;
  public List<String> myHints;
  public int myProblemIndex;
  public int myLine;
  public String myModuleName;

  public String getType() {
    return myType;
  }

  public void setType(final String type) {
    myType = type;
  }

  public String getFQName() {
    return myFQName;
  }

  public void setFQName(final String FQName) {
    myFQName = FQName;                              
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(final String description) {
    myDescription = description;
  }

  public List<String> getHints() {
    return myHints;
  }

  public void setHints(final List<String> hints) {
    myHints = hints;
  }

  public int getProblemIndex() {
    return myProblemIndex;
  }

  public void setProblemIndex(final int problemIndex) {
    myProblemIndex = problemIndex;
  }

  public int getLine() {
    return myLine;
  }

  public void setLine(final int line) {
    myLine = line;
  }

  @Nullable
  public RefEntity getRefElement(final RefManager refManager) {
    return ReadAction.compute(() -> refManager.getReference(myType, myFQName));
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final OfflineProblemDescriptor that = (OfflineProblemDescriptor)o;

    if (myLine != that.myLine) return false;
    if (myProblemIndex != that.myProblemIndex) return false;
    if (myDescription != null ? !myDescription.equals(that.myDescription) : that.myDescription != null) return false;
    if (myFQName != null ? !myFQName.equals(that.myFQName) : that.myFQName != null) return false;
    if (myHints != null ? !myHints.equals(that.myHints) : that.myHints != null) return false;
    if (myModuleName != null ? !myModuleName.equals(that.myModuleName) : that.myModuleName != null) return false;
    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myType != null ? myType.hashCode() : 0);
    result = 31 * result + (myFQName != null ? myFQName.hashCode() : 0);
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    result = 31 * result + (myHints != null ? myHints.hashCode() : 0);
    result = 31 * result + myProblemIndex;
    result = 31 * result + myLine;
    result = 31 * result + (myModuleName != null ? myModuleName.hashCode() : 0);
    return result;
  }

  public void setModule(final String moduleName) {
    myModuleName = moduleName;
  }

  public String getModuleName() {
    return myModuleName;
  }

  @Override
  public String toString() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myFQName;
    } else {
      return myDescription;
    }
  }
}
