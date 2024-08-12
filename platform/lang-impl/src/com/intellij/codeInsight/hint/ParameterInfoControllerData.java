// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ParameterInfoControllerData {

  protected final @NotNull ParameterInfoHandler<PsiElement, Object> myHandler;

  protected Object[] myDescriptors;

  protected boolean[] myDescriptorsEnabled;
  protected int myCurrentParameterIndex = -1;
  protected PsiElement myParameterOwner;
  protected Object myHighlighted;


  public ParameterInfoControllerData(@NotNull ParameterInfoHandler<PsiElement, Object> handler) {
    myHandler = handler;
  }

  public @NotNull ParameterInfoHandler<PsiElement, Object> getHandler() {
    return myHandler;
  }

  public Object[] getDescriptors() {
    return myDescriptors;
  }

  public void setDescriptors(Object[] descriptors) {
    myDescriptors = descriptors;
    myDescriptorsEnabled = new boolean[myDescriptors.length];
    Arrays.fill(myDescriptorsEnabled, true);
  }

  public boolean isDescriptorEnabled(int descriptorIndex) {
    return myDescriptorsEnabled[descriptorIndex];
  }

  public void setDescriptorEnabled(int descriptorIndex, boolean enabled) {
    myDescriptorsEnabled[descriptorIndex] = enabled;
  }

  public int getCurrentParameterIndex() {
    return myCurrentParameterIndex;
  }

  public void setCurrentParameterIndex(int currentParameterIndex) {
    myCurrentParameterIndex = currentParameterIndex;
  }

  public PsiElement getParameterOwner() {
    return myParameterOwner;
  }

  public void setParameterOwner(PsiElement parameterOwner) {
    myParameterOwner = parameterOwner;
  }

  public Object getHighlighted() {
    return myHighlighted;
  }

  public void setHighlighted(Object highlighted) {
    myHighlighted = highlighted;
  }

}
