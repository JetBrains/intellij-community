/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import java.util.EventObject;

public abstract class PsiTreeChangeEvent extends EventObject {
  public static final String PROP_FILE_NAME = "fileName";
  public static final String PROP_DIRECTORY_NAME  = "directoryName";
  public static final String PROP_WRITABLE = "writable";

  public static final String PROP_ROOTS = "roots";

  public static final String PROP_FILE_TYPES = "propFileTypes";

  protected PsiManager myManager;

  protected PsiElement myParent;
  protected PsiElement myOldParent;
  protected PsiElement myNewParent;
  protected PsiElement myChild;
  protected PsiElement myOldChild;
  protected PsiElement myNewChild;

  protected PsiFile myFile;
  protected int myOffset;
  protected int myOldLength;

  protected PsiElement myElement;
  protected String myPropertyName;
  protected Object myOldValue;
  protected Object myNewValue;

  protected PsiTreeChangeEvent(PsiManager manager) {
    super(manager);
    myManager = manager;
  }

  public PsiManager getManager(){
    return myManager;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiElement getOldParent() {
    return myOldParent;
  }

  public PsiElement getNewParent() {
    return myNewParent;
  }

  public PsiElement getChild() {
    return myChild;
  }

  public PsiElement getOldChild() {
    return myOldChild;
  }

  public PsiElement getNewChild() {
    return myNewChild;
  }

  public PsiElement getElement(){
    return myElement;
  }

  public String getPropertyName(){
    return myPropertyName;
  }

  public Object getOldValue(){
    return myOldValue;
  }

  public Object getNewValue(){
    return myNewValue;
  }

  public PsiFile getFile() {
    return myFile;
  }

}

