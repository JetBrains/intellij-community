// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.AbstractConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.JvmPsiTypeConverterImpl;
import org.jetbrains.annotations.NotNull;

public final class PsiTypeControl extends JavaControlBase<PsiTypePanel> {
  public PsiTypeControl(DomWrapper<String> domWrapper, boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  @Override
  protected @NotNull String getValue() {
    String rawValue = super.getValue();
    try {
      PsiType psiType = JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(rawValue, null);
      String s = JvmPsiTypeConverterImpl.convertToString(psiType);
      if (s != null) {
        return s;
      }
    }
    catch (IncorrectOperationException ignored) { }
    return rawValue;
  }

  @Override
  protected void setValue(String value) {
    PsiType type = JvmPsiTypeConverterImpl.convertFromString(value, new AbstractConvertContext() {
      @Override
      public @NotNull DomElement getInvocationElement() {
        return getDomElement();
      }
    });
    if (type != null) {
      value = type.getCanonicalText();
    }
    super.setValue(value);
  }

  @Override
  protected EditorTextField getEditorTextField(@NotNull PsiTypePanel component) {
    return ((ReferenceEditorWithBrowseButton)component.getComponent(0)).getEditorTextField();
  }

  @Override
  protected PsiTypePanel createMainComponent(PsiTypePanel boundedComponent, Project project) {
    if (boundedComponent == null) {
      boundedComponent = new PsiTypePanel();
    }
    return initReferenceEditorWithBrowseButton(
      boundedComponent,
      new ReferenceEditorWithBrowseButton(null, project, s -> JavaReferenceEditorUtil.createTypeDocument(s, project), ""), this);
  }
}
