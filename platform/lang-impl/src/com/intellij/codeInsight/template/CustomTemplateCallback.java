// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.diagnostic.CoreAttachmentFactory;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomTemplateCallback {
  private static final Logger LOGGER = Logger.getInstance(CustomTemplateCallback.class);
  private final TemplateManager myTemplateManager;
  private final @NotNull Editor myEditor;
  private final @NotNull PsiFile myPsiFile;
  private final int myOffset;
  private final @NotNull Project myProject;
  private final boolean myInInjectedFragment;
  protected Set<TemplateContextType> myApplicableContextTypes;

  public CustomTemplateCallback(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    myProject = psiFile.getProject();
    myTemplateManager = TemplateManager.getInstance(myProject);

    int parentEditorOffset = getOffset(editor);
    PsiElement element = InjectedLanguageManager.getInstance(psiFile.getProject()).findInjectedElementAt(psiFile, parentEditorOffset);
    myPsiFile = element != null ? element.getContainingFile() : psiFile;

    myInInjectedFragment = InjectedLanguageManager.getInstance(myProject).isInjectedFragment(myPsiFile);
    myEditor = myInInjectedFragment ? InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, psiFile, parentEditorOffset) : editor;
    myOffset = myInInjectedFragment ? getOffset(myEditor) : parentEditorOffset;
  }

  public TemplateManager getTemplateManager() {
    return myTemplateManager;
  }

  public @NotNull PsiFile getFile() {
    return myPsiFile;
  }

  public @NotNull PsiElement getContext() {
    return getContext(myPsiFile, getOffset(), myInInjectedFragment);
  }

  public int getOffset() {
    return myOffset;
  }

  public static int getOffset(@NotNull Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    return selectionModel.hasSelection() ? selectionModel.getSelectionStart() : Math.max(editor.getCaretModel().getOffset() - 1, 0);
  }

  public @Nullable TemplateImpl findApplicableTemplate(@NotNull @NlsSafe String key) {
    return ContainerUtil.getFirstItem(findApplicableTemplates(key));
  }

  public @NotNull List<TemplateImpl> findApplicableTemplates(@NotNull @NlsSafe String key) {
    List<TemplateImpl> result = new ArrayList<>();
    for (TemplateImpl candidate : getMatchingTemplates(key)) {
      if (isAvailableTemplate(candidate)) {
        result.add(candidate);
      }
    }
    return result;
  }

  private boolean isAvailableTemplate(@NotNull TemplateImpl template) {
    if (myApplicableContextTypes == null) {
      myApplicableContextTypes =
        TemplateManagerImpl.getApplicableContextTypes(TemplateActionContext.create(myPsiFile, myEditor, myOffset, myOffset, false));
    }
    return !template.isDeactivated() && TemplateManagerImpl.isApplicable(template, myApplicableContextTypes);
  }

  public void startTemplate(@NotNull Template template, Map<String, String> predefinedValues, TemplateEditingListener listener) {
    if (myInInjectedFragment) {
      template.setToReformat(false);
    }
    myTemplateManager.startTemplate(myEditor, template, false, predefinedValues, listener);
  }

  private static @NotNull List<TemplateImpl> getMatchingTemplates(@NotNull String templateKey) {
    TemplateSettings settings = TemplateSettings.getInstance();
    List<TemplateImpl> candidates = new ArrayList<>();
    for (TemplateImpl template : settings.getTemplates(templateKey)) {
      if (!template.isDeactivated()) {
        candidates.add(template);
      }
    }
    return candidates;
  }

  public @NotNull Editor getEditor() {
    return myEditor;
  }

  public @NotNull FileType getFileType() {
    return myPsiFile.getFileType();
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public void deleteTemplateKey(@NotNull String key) {
    deleteTemplateKey(key, myEditor.getCaretModel().getOffset());
  }

  public void deleteTemplateKey(@NotNull String key, int caretAt) {
    int templateStart = caretAt - key.length();
    myEditor.getDocument().deleteString(templateStart, caretAt);
    myEditor.getCaretModel().moveToOffset(templateStart);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();
  }

  public static @NotNull PsiElement getContext(@NotNull PsiFile psiFile, int offset) {
    return getContext(psiFile, offset, true);
  }

  public static @NotNull PsiElement getContext(@NotNull PsiFile psiFile, int offset, boolean searchInInjectedFragment) {
    PsiElement element = null;
    if (searchInInjectedFragment && !InjectedLanguageManager.getInstance(psiFile.getProject()).isInjectedFragment(psiFile)) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
      Document document = documentManager.getDocument(psiFile);
      if (document != null && !documentManager.isCommitted(document)) {
        LOGGER.error("Trying to access to injected template context on uncommited document, offset = " + offset,
                     CoreAttachmentFactory.createAttachment(psiFile.getVirtualFile()));
      }
      else {
        element = InjectedLanguageManager.getInstance(psiFile.getProject()).findInjectedElementAt(psiFile, offset);
      }
    }
    if (element == null) {
      element = PsiUtilCore.getElementAtOffset(psiFile, offset);
    }
    return element;
  }

  public boolean isInInjectedFragment() {
    return myInInjectedFragment;
  }
}
