// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.parameterInfo;

import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;

public interface UpdateParameterInfoContext extends ParameterInfoContext {
  void removeHint();

  void setParameterOwner(final PsiElement o);
  PsiElement getParameterOwner();

  void setHighlightedParameter(final Object parameter);
  Object getHighlightedParameter();
  void setCurrentParameter(final int index);
  boolean isUIComponentEnabled(int index);
  void setUIComponentEnabled(int index, boolean enabled);

  int getParameterListStart();

  Object[] getObjectsToView();
  
  boolean isPreservedOnHintHidden();
  void setPreservedOnHintHidden(boolean value);
  boolean isInnermostContext();
  
  UserDataHolderEx getCustomContext();
}
