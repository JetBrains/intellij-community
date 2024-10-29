// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonUtil;
import com.intellij.json.navigation.JsonQualifiedNameKind;
import com.intellij.json.navigation.JsonQualifiedNameProvider;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class JsonCopyPointerAction extends CopyReferenceAction {
  public JsonCopyPointerAction() {
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(JsonBundle.message("action.JsonCopyPointer.text"));
    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    VirtualFile file = editor == null ? null : FileDocumentManager.getInstance().getFile(editor.getDocument());
    e.getPresentation().setVisible(file != null && JsonUtil.isJsonFile(file, editor.getProject()));
  }

  @Override
  protected @NlsSafe String getQualifiedName(Editor editor, List<? extends PsiElement> elements) {
    if (elements.size() != 1) return null;
    return JsonQualifiedNameProvider.generateQualifiedName(elements.get(0), JsonQualifiedNameKind.JsonPointer);
  }

  @Override
  protected @NotNull List<PsiElement> getPsiElements(DataContext dataContext, Editor editor) {
    List<PsiElement> elements = super.getPsiElements(dataContext, editor);
    if (!elements.isEmpty()) return elements;
    PsiElement location = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN).getPsiLocation();
    if (location == null) return elements;
    PsiElement parent = location.getParent();
    return parent != null ? Collections.singletonList(parent) : elements;
  }
}
