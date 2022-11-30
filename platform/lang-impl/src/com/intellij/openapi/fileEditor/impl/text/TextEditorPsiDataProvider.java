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
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.IdeView;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.InjectedDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.EditorDataProvider;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.temp.TempFileSystemMarker;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedCaret;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;

import static com.intellij.openapi.actionSystem.LangDataKeys.*;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

public class TextEditorPsiDataProvider implements EditorDataProvider {
  @Override
  @Nullable
  public Object getData(@NotNull String dataId, @NotNull Editor e, @NotNull Caret caret) {
    if (e.isDisposed() || !(e instanceof EditorEx)) {
      return null;
    }
    VirtualFile file = e.getVirtualFile();
    if (file == null || !file.isValid()) return null;

    if (HOST_EDITOR.is(dataId)) {
      return e instanceof EditorWindow ? ((EditorWindow)e).getDelegate() : e;
    }
    if (CARET.is(dataId)) {
      return caret;
    }
    if (IDE_VIEW.is(dataId)) {
      return getIdeView(e, file);
    }
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      return (DataProvider)slowId -> getSlowData(slowId, e, caret);
    }
    return null;
  }

  private static @Nullable IdeView getIdeView(@NotNull Editor e, @NotNull VirtualFile file) {
    Project project = e.getProject();
    if (project == null) return null;
    VirtualFileSystem fs = file.getFileSystem();
    boolean nonPhysical = fs instanceof NonPhysicalFileSystem || fs instanceof TempFileSystemMarker;
    if (nonPhysical && !ApplicationManager.getApplication().isUnitTestMode()) return null;
    return new IdeView() {

      @Override
      public void selectElement(PsiElement element) {
        NavigationUtil.activateFileWithPsiElement(element);
      }

      @Override
      public PsiDirectory @NotNull [] getDirectories() {
        PsiDirectory psiDirectory = getOrChooseDirectory();
        return psiDirectory == null ? PsiDirectory.EMPTY_ARRAY : new PsiDirectory[]{psiDirectory};
      }

      @Override
      public PsiDirectory getOrChooseDirectory() {
        VirtualFile parent = !file.isValid() ? null : file.getParent();
        return parent == null ? null : PsiManager.getInstance(project).findDirectory(parent);
      }
    };
  }

  @Nullable
  private Object getSlowData(@NotNull String dataId, @NotNull Editor e, @NotNull Caret caret) {
    if (e.isDisposed() || !(e instanceof EditorEx)) {
      return null;
    }
    VirtualFile file = e.getVirtualFile();
    if (file == null || !file.isValid()) return null;

    Project project = e.getProject();
    if (PSI_FILE.is(dataId)) {
      return getPsiFile(e, file);
    }
    if (InjectedDataKeys.EDITOR.is(dataId)) {
      if (project != null &&
          PsiDocumentManager.getInstance(project).isCommitted(e.getDocument()) &&
          InjectedLanguageManager.getInstance(project).mightHaveInjectedFragmentAtOffset(e.getDocument(), caret.getOffset())) {
        //noinspection deprecation
        return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(e, caret, getPsiFile(e, file));
      }
      return null;
    }
    if (InjectedDataKeys.CARET.is(dataId)) {
      return querySlowInjectedCaret(e, caret);
    }
    if (InjectedDataKeys.VIRTUAL_FILE.is(dataId)) {
      PsiFile psiFile = querySlowInjectedPsiFile(e, caret);
      if (psiFile == null) return null;
      return psiFile.getVirtualFile();
    }
    if (InjectedDataKeys.PSI_FILE.is(dataId)) {
      Editor editor = querySlowInjectedEditor(e, caret);
      if (editor == null || project == null) return null;
      return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }
    if (InjectedDataKeys.PSI_ELEMENT.is(dataId)) {
      EditorWindow editor = querySlowInjectedEditor(e, caret);
      if (editor == null) return null;
      InjectedCaret injectedCaret = getInjectedCaret(editor, caret);
      return injectedCaret == null ? null : getPsiElementIn(editor, injectedCaret, file);
    }
    if (PSI_ELEMENT.is(dataId)) {
      return getPsiElementIn(e, caret, file);
    }
    if (InjectedDataKeys.LANGUAGE.is(dataId)) {
      PsiFile psiFile = querySlowInjectedPsiFile(e, caret);
      if (psiFile == null) return null;
      InjectedCaret injectedCaret = querySlowInjectedCaret(e, caret);
      return injectedCaret == null ? null : getLanguageAtCurrentPositionInEditor(injectedCaret, psiFile);
    }
    if (MODULE.is(dataId)) {
      return project == null ? null : ModuleUtilCore.findModuleForFile(file, project);
    }
    if (LANGUAGE.is(dataId)) {
      PsiFile psiFile = getPsiFile(e, file);
      if (psiFile == null) return null;
      return getLanguageAtCurrentPositionInEditor(caret, psiFile);
    }
    if (CONTEXT_LANGUAGES.is(dataId)) {
      LinkedHashSet<Language> set = new LinkedHashSet<>(4);
      Language injectedLanguage = (Language)getSlowData(InjectedDataKeys.LANGUAGE.getName(), e, caret);
      addIfNotNull(set, injectedLanguage);
      Language language = (Language)getSlowData(LANGUAGE.getName(), e, caret);
      addIfNotNull(set, language);
      PsiFile psiFile = (PsiFile)getSlowData(PSI_FILE.getName(), e, caret);
      if (psiFile != null) {
        addIfNotNull(set, psiFile.getViewProvider().getBaseLanguage());
      }
      return set.toArray(new Language[0]);
    }
    return null;
  }

  // here there's a convention that query* methods below can call getSlowData() whereas get* methods can't
  private EditorWindow querySlowInjectedEditor(@NotNull Editor e, @NotNull Caret caret) {
    Object editor = getSlowData(InjectedDataKeys.EDITOR.getName(), e, caret);
    return editor instanceof EditorWindow ? (EditorWindow)editor : null;
  }

  private InjectedCaret querySlowInjectedCaret(@NotNull Editor e, @NotNull Caret caret) {
    EditorWindow editor = querySlowInjectedEditor(e, caret);
    return editor == null ? null : getInjectedCaret(editor, caret);
  }

  private PsiFile querySlowInjectedPsiFile(@NotNull Editor e, @NotNull Caret caret) {
    return (PsiFile)getSlowData(InjectedDataKeys.PSI_FILE.getName(), e, caret);
  }

  private static InjectedCaret getInjectedCaret(@NotNull EditorWindow editor, @NotNull Caret hostCaret) {
    if (hostCaret instanceof InjectedCaret) {
      return (InjectedCaret)hostCaret;
    }
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      if (((InjectedCaret)caret).getDelegate() == hostCaret) {
        return (InjectedCaret)caret;
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