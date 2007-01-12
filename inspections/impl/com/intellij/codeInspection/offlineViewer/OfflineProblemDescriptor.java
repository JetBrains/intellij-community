/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 05-Jan-2007
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.SmartRefElementPointerImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class OfflineProblemDescriptor {
  private String myType;
  private String myFQName;
  private String myDescription;
  private List<String> myHints;
  private int myProblemIndex;
  private int myLine;
  private String[] myParentType;
  private String[] myParentFQName;
  private String myModuleName;

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

  public String[] getParentType() {
    return myParentType;
  }

  public void setParentType(final String[] parentType) {
    myParentType = parentType;
  }

  public String[] getParentFQName() {
    return myParentFQName;
  }

  public void setParentFQName(final String[] parentFQName) {
    myParentFQName = parentFQName;
  }

  @Nullable
  public RefElement getRefElement(final RefManager refManager) {
    final RefElement refElement = new SmartRefElementPointerImpl(myType, myFQName, refManager).getRefElement();
    if (refElement != null) {
      final PsiElement element = refElement.getElement();
      if (element != null && element.isValid()) {
        PsiDocumentManager.getInstance(element.getProject()).commitAllDocuments();
      }
    }
    return refElement;
  }

  @Nullable
  public OfflineProblemDescriptor getOwner() {
    if (myParentType != null && myParentFQName != null) {
      final OfflineProblemDescriptor descriptor = new OfflineProblemDescriptor();
      descriptor.setFQName(myParentFQName[0]);
      descriptor.setType(myParentType[0]);
      if (myParentType.length > 1 && myParentFQName.length > 1) {
        descriptor.setParentType(ArrayUtil.remove(myParentType, 0));
        descriptor.setParentFQName(ArrayUtil.remove(myParentFQName, 0));
      }
      return descriptor;
    }
    return null;
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final OfflineProblemDescriptor that = (OfflineProblemDescriptor)o;

    if (!myFQName.equals(that.myFQName)) return false;
    if (!myType.equals(that.myType)) return false;
    if (!Arrays.equals(myParentFQName, that.myParentFQName)) return false;
    if (!Arrays.equals(myParentType, that.myParentType)) return false;
    if (!myType.equals(that.myType)) return false;

    return true;
  }

  //build tree: do not include other fields
  public int hashCode() {
    int result;
    result = myType.hashCode();
    result = 31 * result + myFQName.hashCode();
    result = 31 * result + (myParentType != null ? Arrays.hashCode(myParentType) : 0);
    result = 31 * result + (myParentFQName != null ? Arrays.hashCode(myParentFQName) : 0);
    return result;
  }

  public void setModule(final String moduleName) {
    myModuleName = moduleName;
  }

  public String getModuleName() {
    return myModuleName;
  }
}