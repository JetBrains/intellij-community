/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.EditorHelper;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.EditorDataProvider;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedCaret;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;

import static com.intellij.openapi.actionSystem.AnActionEvent.injectedId;
import static com.intellij.openapi.actionSystem.LangDataKeys.*;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

public class TextEditorPsiDataProvider implements EditorDataProvider {
  @Override
  @Nullable
  public Object getData(@NotNull String dataId, @NotNull Editor e, @NotNull Caret caret) {
    if (e.isDisposed() || !(e instanceof EditorEx)) {
      return null;
    }
    VirtualFile file = ((EditorEx)e).getVirtualFile();
    if (file == null || !file.isValid()) return null;

    if (HOST_EDITOR.is(dataId)) {
      return e instanceof EditorWindow ? ((EditorWindow)e).getDelegate() : e;
    }
    if (CARET.is(dataId)) {
      return caret;
    }
    if (PSI_FILE.is(dataId)) {
      return getPsiFile(e, file);
    }
    if (IDE_VIEW.is(dataId)) {
      Project project = e.getProject();
      PsiFile psiFile = project == null ? null : PsiManager.getInstance(project).findFile(file);
      PsiDirectory psiDirectory = psiFile == null ? null : psiFile.getParent();
      if (psiDirectory != null && (psiDirectory.isPhysical() || ApplicationManager.getApplication().isUnitTestMode())) {
        return new IdeView() {

          @Override
          public void selectElement(final PsiElement element) {
            Editor editor = EditorHelper.openInEditor(element);
            if (editor != null) {
              ToolWindowManager.getInstance(element.getProject()).activateEditorComponent();
            }
          }

          @Override
          public PsiDirectory @NotNull [] getDirectories() {
            return new PsiDirectory[]{psiDirectory};
          }

          @Override
          public PsiDirectory getOrChooseDirectory() {
            return psiDirectory;
          }
        };
      }
    }
    if (PlatformDataKeys.SLOW_DATA_PROVIDERS.is(dataId)) {
      return Collections.<DataProvider>singletonList(o -> getSlowData(o, e, caret));
    }
    return null;
  }

  @Nullable
  private Object getSlowData(@NotNull String dataId, @NotNull Editor e, @NotNull Caret caret) {
    if (e.isDisposed() || !(e instanceof EditorEx)) {
      return null;
    }
    VirtualFile file = ((EditorEx)e).getVirtualFile();
    if (file == null || !file.isValid()) return null;

    Project project = e.getProject();
    if (dataId.equals(injectedId(EDITOR.getName()))) {
      if (project != null &&
          PsiDocumentManager.getInstance(project).isCommitted(e.getDocument()) &&
          InjectedLanguageManager.getInstance(project).mightHaveInjectedFragmentAtOffset(e.getDocument(), caret.getOffset())) {
        //noinspection deprecation
        return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(e, caret, getPsiFile(e, file));
      }
      return e;
    }
    if (dataId.equals(injectedId(CARET.getName()))) {
      Editor editor = (Editor)getSlowData(injectedId(EDITOR.getName()), e, caret);
      return editor == null ? null : getInjectedCaret(editor, caret);
    }
    if (dataId.equals(injectedId(VIRTUAL_FILE.getName()))) {
      PsiFile psiFile = (PsiFile)getSlowData(injectedId(PSI_FILE.getName()), e, caret);
      if (psiFile == null) return null;
      return psiFile.getVirtualFile();
    }
    if (dataId.equals(injectedId(PSI_FILE.getName()))) {
      Editor editor = (Editor)getSlowData(injectedId(EDITOR.getName()), e, caret);
      if (editor == null || project == null) return null;
      return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }
    if (dataId.equals(injectedId(PSI_ELEMENT.getName()))) {
      Editor editor = (Editor)getSlowData(injectedId(EDITOR.getName()), e, caret);
      if (editor == null) return null;
      Caret injectedCaret = getInjectedCaret(editor, caret);
      return getPsiElementIn(editor, injectedCaret, file);
    }
    if (PSI_ELEMENT.is(dataId)) {
      return getPsiElementIn(e, caret, file);
    }
    if (dataId.equals(injectedId(LANGUAGE.getName()))) {
      PsiFile psiFile = (PsiFile)getSlowData(injectedId(PSI_FILE.getName()), e, caret);
      Editor editor = (Editor)getSlowData(injectedId(EDITOR.getName()), e, caret);
      if (psiFile == null || editor == null) return null;
      Caret injectedCaret = getInjectedCaret(editor, caret);
      return getLanguageAtCurrentPositionInEditor(injectedCaret, psiFile);
    }
    if (LANGUAGE.is(dataId)) {
      PsiFile psiFile = getPsiFile(e, file);
      if (psiFile == null) return null;
      return getLanguageAtCurrentPositionInEditor(caret, psiFile);
    }
    if (CONTEXT_LANGUAGES.is(dataId)) {
      LinkedHashSet<Language> set = new LinkedHashSet<>(4);
      Language injectedLanguage = (Language)getSlowData(injectedId(LANGUAGE.getName()), e, caret);
      addIfNotNull(set, injectedLanguage);
      Language language = (Language)getSlowData(LANGUAGE.getName(), e, caret);
      addIfNotNull(set, language);
      PsiFile psiFile = (PsiFile)getData(PSI_FILE.getName(), e, caret);
      if (psiFile != null) {
        addIfNotNull(set, psiFile.getViewProvider().getBaseLanguage());
      }
      return set.toArray(new Language[0]);
    }
    return null;
  }

  @NotNull
  private static Caret getInjectedCaret(@NotNull Editor editor, @NotNull Caret hostCaret) {
    if (!(editor instanceof EditorWindow) || hostCaret instanceof InjectedCaret) {
      return hostCaret;
    }
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      if (((InjectedCaret)caret).getDelegate() == hostCaret) {
        return caret;
      }
    }
    throw new IllegalArgumentException("Cannot find injected caret corresponding to " + hostCaret);
  }

  private static Language getLanguageAtCurrentPositionInEditor(Caret caret, final PsiFile psiFile) {
    int caretOffset = caret.getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == caret.getSelectionStart() ||
                                            caretOffset == caret.getSelectionEnd()
                                            ? caret.getSelectionStart()
                                            : caretOffset;
    if (caret.hasSelection()) {
      return getLanguageAtOffset(psiFile, mostProbablyCorrectLanguageOffset, caret.getSelectionEnd());
    }

    return PsiUtilCore.getLanguageAtOffset(psiFile, mostProbablyCorrectLanguageOffset);
  }

  private static Language getLanguageAtOffset(PsiFile psiFile, int mostProbablyCorrectLanguageOffset, int end) {
    final PsiElement elt = psiFile.findElementAt(mostProbablyCorrectLanguageOffset);
    if (elt == null) return psiFile.getLanguage();
    if (elt instanceof PsiWhiteSpace) {
      final int incremented = elt.getTextRange().getEndOffset() + 1;
      if (incremented <= end) {
        return getLanguageAtOffset(psiFile, incremented, end);
      }
    }
    return PsiUtilCore.findLanguageFromElement(elt);
  }

  @Nullable
  private static PsiElement getPsiElementIn(@NotNull Editor editor, @NotNull Caret caret, @NotNull VirtualFile file) {
    final PsiFile psiFile = getPsiFile(editor, file);
    if (psiFile == null) return null;

    try {
      TargetElementUtil util = TargetElementUtil.getInstance();
      return util.findTargetElement(editor, util.getReferenceSearchFlags(), caret.getOffset());
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Nullable
  private static PsiFile getPsiFile(@NotNull Editor e, @NotNull VirtualFile file) {
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
}
