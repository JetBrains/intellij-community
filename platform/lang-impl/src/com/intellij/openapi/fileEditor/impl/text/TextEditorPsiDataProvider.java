/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.EditorHelper;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.EditorDataProvider;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;

import static com.intellij.openapi.actionSystem.AnActionEvent.injectedId;
import static com.intellij.openapi.actionSystem.LangDataKeys.*;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

public class TextEditorPsiDataProvider implements EditorDataProvider {
  @Nullable
  public Object getData(final String dataId, final Editor e, final VirtualFile file) {
    if (!file.isValid()) return null;

    if (dataId.equals(injectedId(EDITOR.getName()))) {
      if (PsiDocumentManager.getInstance(e.getProject()).isUncommited(e.getDocument())) {
        return e;
      }
      else {
        return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(e, getPsiFile(e, file));
      }
    }
    if (dataId.equals(injectedId(PSI_ELEMENT.getName()))) {
      return getPsiElementIn((Editor)getData(injectedId(EDITOR.getName()), e, file), file);
    }
    if (PSI_ELEMENT.is(dataId)){
      return getPsiElementIn(e, file);
    }
    if (dataId.equals(injectedId(LANGUAGE.getName()))) {
      PsiFile psiFile = (PsiFile)getData(injectedId(PSI_FILE.getName()), e, file);
      Editor editor = (Editor)getData(injectedId(EDITOR.getName()), e, file);
      if (psiFile == null || editor == null) return null;
      return getLanguageAtCurrentPositionInEditor(editor, psiFile);
    }
    if (LANGUAGE.is(dataId)) {
      final PsiFile psiFile = getPsiFile(e, file);
      if (psiFile == null) return null;
      return getLanguageAtCurrentPositionInEditor(e, psiFile);
    }
    if (dataId.equals(injectedId(VIRTUAL_FILE.getName()))) {
      PsiFile psiFile = (PsiFile)getData(injectedId(PSI_FILE.getName()), e, file);
      if (psiFile == null) return null;
      return psiFile.getVirtualFile();
    }
    if (dataId.equals(injectedId(PSI_FILE.getName()))) {
      Editor editor = (Editor)getData(injectedId(EDITOR.getName()), e, file);
      if (editor == null) {
        return null;
      }
      final Project project = editor.getProject();
      if (project == null) {
        return null;
      }
      return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }
    if (PSI_FILE.is(dataId)) {
      return getPsiFile(e, file);
    }
    if (IDE_VIEW.is(dataId)) {
      final PsiFile psiFile = PsiManager.getInstance(e.getProject()).findFile(file);
      final PsiDirectory psiDirectory = psiFile != null ? psiFile.getParent() : null;
      if (psiDirectory != null && psiDirectory.isPhysical()) {
        return new IdeView() {

          public void selectElement(final PsiElement element) {
            Editor editor = EditorHelper.openInEditor(element);
            if (editor != null) {
              ToolWindowManager.getInstance(element.getProject()).activateEditorComponent();
            }
          }

          public PsiDirectory[] getDirectories() {
            return new PsiDirectory[]{psiDirectory};
          }

          public PsiDirectory getOrChooseDirectory() {
            return psiDirectory;
          }
        };
      }
    }
    if (CONTEXT_LANGUAGES.is(dataId)) {
      return computeLanguages(e, file);
    }
    return null;
  }

  private static Language getLanguageAtCurrentPositionInEditor(final Editor editor, final PsiFile psiFile) {
    int mostProbablyCorrectLanguageOffset = getOffset(editor);

    return PsiUtilBase.getLanguageAtOffset(psiFile, mostProbablyCorrectLanguageOffset);
  }

  private static int getOffset(Editor editor) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    int caretOffset = editor.getCaretModel().getOffset();
    return caretOffset == selectionModel.getSelectionStart() ||
                                            caretOffset == selectionModel.getSelectionEnd()
                                            ? selectionModel.getSelectionStart()
                                            : caretOffset;
  }

  @Nullable
  private static PsiElement getPsiElementIn(final Editor editor, VirtualFile file) {
    final PsiFile psiFile = getPsiFile(editor, file);
    if (psiFile == null) return null;

    try {
      return TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase.getInstance().getReferenceSearchFlags());
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Nullable
  private static PsiFile getPsiFile(Editor e, VirtualFile file) {
    if (!file.isValid()) {
      return null; // fix for SCR 40329
    }
    final Project project = e.getProject();
    if (project == null) {
      return null;
    }
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile != null && psiFile.isValid() ? psiFile : null;
  }

  private Language[] computeLanguages(Editor editor, VirtualFile file) {
    LinkedHashSet<Language> set = new LinkedHashSet<Language>(4);
    Language injectedLanguage = (Language)getData(injectedId(LANGUAGE.getName()), editor, file);
    addIfNotNull(injectedLanguage, set);
    Language language = (Language)getData(LANGUAGE.getName(), editor, file);
    addIfNotNull(language, set);
    PsiFile psiFile = (PsiFile)getData(PSI_FILE.getName(), editor, file);
    if (psiFile != null) {
      addIfNotNull(psiFile.getViewProvider().getBaseLanguage(), set);
    }
    return set.toArray(new Language[set.size()]);
  }
}
