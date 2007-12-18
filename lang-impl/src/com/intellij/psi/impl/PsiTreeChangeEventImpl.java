package com.intellij.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;

public class PsiTreeChangeEventImpl extends PsiTreeChangeEvent{
  private int myCode;

  public PsiTreeChangeEventImpl(PsiManager manager) {
    super(manager);
  }

  public int getCode() {
    return myCode;
  }

  public void setCode(int code) {
    myCode = code;
  }

  public void setParent(PsiElement parent) {
    myParent = parent;
  }

  public void setOldParent(PsiElement oldParent) {
    myOldParent = oldParent;
  }

  public void setNewParent(PsiElement newParent) {
    myNewParent = newParent;
  }

  public void setChild(PsiElement child) {
    myChild = child;
  }

  public void setOldChild(PsiElement oldChild) {
    myOldChild = oldChild;
  }

  public void setNewChild(PsiElement newChild) {
    myNewChild = newChild;
  }

  public void setElement(PsiElement element) {
    myElement = element;
  }

  public void setPropertyName(String propertyName) {
    myPropertyName = propertyName;
  }

  public void setOldValue(Object oldValue) {
    myOldValue = oldValue;
  }

  public void setNewValue(Object newValue) {
    myNewValue = newValue;
  }

  public void setFile(PsiFile file) {
    myFile = file;
  }

  public void setOffset(int offset) {
    myOffset = offset;
  }

  public int getOffset() {
    return myOffset;
  }

  public void setOldLength(int oldLength) {
    myOldLength = oldLength;
  }

  public int getOldLength() {
    return myOldLength;
  }
}