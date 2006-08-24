/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

/**
 * Provides information about a change in the PSI tree of a project.
 *
 * @see PsiTreeChangeListener
 */
public abstract class PsiTreeChangeEvent extends EventObject {
  @NonNls public static final String PROP_FILE_NAME = "fileName";
  @NonNls public static final String PROP_DIRECTORY_NAME  = "directoryName";
  @NonNls public static final String PROP_WRITABLE = "writable";

  @NonNls public static final String PROP_ROOTS = "roots";

  @NonNls public static final String PROP_FILE_TYPES = "propFileTypes";

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

  @Nullable
  public PsiFile getFile() {
    return myFile;
  }

}

