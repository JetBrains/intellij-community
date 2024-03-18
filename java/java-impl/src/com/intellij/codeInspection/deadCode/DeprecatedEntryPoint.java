// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class DeprecatedEntryPoint extends EntryPoint {
  public boolean DEPRECATED_ENTRY_POINT = true;

  @Override
  public void readExternal(Element element) {
    XmlSerializer.deserializeInto(element, this);
  }

  @Override
  public void writeExternal(Element element) {
    XmlSerializer.serializeObjectInto(this, element);
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return JavaBundle.message("checkbox.deprecated.members");
  }

  @Override
  public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    return isEntryPoint(psiElement);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)psiElement).isDeprecated();
  }

  @Override
  public boolean isSelected() {
    return DEPRECATED_ENTRY_POINT;
  }

  @Override
  public void setSelected(boolean selected) {
    DEPRECATED_ENTRY_POINT = selected;
  }
}
