// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.editor;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.json.navigation.JsonQualifiedNameKind;
import com.intellij.json.navigation.JsonQualifiedNameProvider;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class JsonCopyPointerAction extends CopyReferenceAction {
  public JsonCopyPointerAction() {
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText("Copy JSON Pointer");
  }

  @Override
  protected String getQualifiedName(Editor editor, List<PsiElement> elements) {
    if (elements.size() != 1) return null;
    return JsonQualifiedNameProvider.generateQualifiedName(elements.get(0), JsonQualifiedNameKind.JsonPointer);
  }

  @NotNull
  @Override
  protected List<PsiElement> getPsiElements(DataContext dataContext, Editor editor) {
    List<PsiElement> elements = super.getPsiElements(dataContext, editor);
    if (!elements.isEmpty()) return elements;
    PsiElement location = ConfigurationContext.getFromContext(dataContext).getPsiLocation();
    if (location == null) return elements;
    PsiElement parent = location.getParent();
    return parent != null ? Collections.singletonList(parent) : elements;
  }
}
