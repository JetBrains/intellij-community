// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.IntentionFilterOwner;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PsiClassControl extends JavaControlBase<PsiClassPanel> {
  public PsiClassControl(DomWrapper<String> domWrapper, boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  @Override
  protected EditorTextField getEditorTextField(@NotNull PsiClassPanel component) {
    return ((ReferenceEditorWithBrowseButton)component.getComponent(0)).getEditorTextField();
  }

  @Override
  protected PsiClassPanel createMainComponent(PsiClassPanel boundedComponent, Project project) {
    if (boundedComponent == null) {
      boundedComponent = new PsiClassPanel();
    }
    ReferenceEditorWithBrowseButton editor = JavaReferenceEditorUtil.createReferenceEditorWithBrowseButton(null, "", project, true);
    Document document = editor.getChildComponent().getDocument();
    PsiCodeFragmentImpl fragment = (PsiCodeFragmentImpl) PsiDocumentManager.getInstance(project).getPsiFile(document);
    assert fragment != null;
    fragment.setIntentionActionsFilter(IntentionFilterOwner.IntentionActionsFilter.EVERYTHING_AVAILABLE);
    fragment.putUserData(ModuleUtilCore.KEY_MODULE, getDomWrapper().getExistingDomElement().getModule());
    return initReferenceEditorWithBrowseButton(boundedComponent, editor, this);
  }
}
