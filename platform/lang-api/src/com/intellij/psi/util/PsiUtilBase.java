/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.psi.util;

import com.intellij.ide.DataManager;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class PsiUtilBase extends PsiUtilCore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PsiUtilBase");
  public static final Comparator<Language> LANGUAGE_COMPARATOR = new Comparator<Language>() {
    @Override
    public int compare(Language o1, Language o2) {
      return o1.getID().compareTo(o2.getID());
    }
  };

  public static int getRootIndex(PsiElement root) {
    ASTNode node = root.getNode();
    while(node != null && node.getTreeParent() != null) {
      node = node.getTreeParent();
    }
    if(node != null) root = node.getPsi();
    final PsiFile containingFile = root.getContainingFile();
    FileViewProvider provider = containingFile.getViewProvider();
    Set<Language> languages = provider.getLanguages();
    if (languages.size() == 1) {
      return 0;
    }
    List<Language> array = new ArrayList<Language>(languages);
    Collections.sort(array, LANGUAGE_COMPARATOR);
    for (int i = 0; i < array.size(); i++) {
      Language language = array.get(i);
      if (provider.getPsi(language) == containingFile) return i;
    }
    throw new RuntimeException("Cannot find root for: "+root);
  }

  public static boolean isUnderPsiRoot(PsiFile root, PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == root) return true;
    for (PsiFile psiRoot : root.getPsiRoots()) {
      if (containingFile == psiRoot) return true;
    }
    PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(root.getProject()).getInjectionHost(element);
    return host != null && isUnderPsiRoot(root, host);
  }

  @Nullable
  public static Language getLanguageInEditor(@NotNull final Editor editor, @NotNull final Project project) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;

    final SelectionModel selectionModel = editor.getSelectionModel();
    int caretOffset = editor.getCaretModel().getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == selectionModel.getSelectionStart() ||
                                            caretOffset == selectionModel.getSelectionEnd()
                                            ? selectionModel.getSelectionStart()
                                            : caretOffset;
    PsiElement elt = getElementAtOffset(file, mostProbablyCorrectLanguageOffset);
    Language lang = findLanguageFromElement(elt);

    if (selectionModel.hasSelection()) {
      final Language rangeLanguage = evaluateLanguageInRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), file);
      if (rangeLanguage == null) return file.getLanguage();

      lang = rangeLanguage;
    }

    return narrowLanguage(lang, file.getLanguage());
  }

  @Nullable
  public static PsiElement getElementAtCaret(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null) return null;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return file == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
  }

  @Nullable
  public static PsiFile getPsiFileInEditor(@NotNull final Editor editor, @NotNull final Project project) {
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;

    final Language language = getLanguageInEditor(editor, project);
    if (language == null) return file;

    if (language == file.getLanguage()) return file;

    final SelectionModel selectionModel = editor.getSelectionModel();
    int caretOffset = editor.getCaretModel().getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == selectionModel.getSelectionStart() ||
                                            caretOffset == selectionModel.getSelectionEnd()
                                            ? selectionModel.getSelectionStart()
                                            : caretOffset;
    return getPsiFileAtOffset(file, mostProbablyCorrectLanguageOffset);
  }

  public static PsiFile getPsiFileAtOffset(final PsiFile file, final int offset) {
    PsiElement elt = getElementAtOffset(file, offset);

    assert elt.isValid() : elt + "; file: "+file + "; isvalid: "+file.isValid();
    return elt.getContainingFile();
  }

  @Nullable
  public static Language reallyEvaluateLanguageInRange(final int start, final int end, final PsiFile file) {
    Language lang = null;
    int curOffset = start;
    do {
      PsiElement elt = getElementAtOffset(file, curOffset);

      if (!(elt instanceof PsiWhiteSpace)) {
        final Language language = findLanguageFromElement(elt);
        if (lang == null) {
          lang = language;
        }
        else if (lang != language) {
          return null;
        }
      }
      TextRange range = elt.getTextRange();
      if (range == null) {
        LOG.error("Null range for element " + elt + " of " + elt.getClass() + " in file " + file + " at offset " + curOffset);
        return file.getLanguage();
      }
      int endOffset = range.getEndOffset();
      curOffset = endOffset <= curOffset ? curOffset + 1 : endOffset;
    }
    while (curOffset < end);
    return narrowLanguage(lang, file.getLanguage());
  }

  @Nullable
  public static Language evaluateLanguageInRange(final int start, final int end, final PsiFile file) {
    PsiElement elt = getElementAtOffset(file, start);

    TextRange selectionRange = new TextRange(start, end);
    if (!(elt instanceof PsiFile)) {
      elt = elt.getParent();
      TextRange range = elt.getTextRange();
      assert range != null : "Range is null for " + elt + "; " + elt.getClass();
      while(!range.contains(selectionRange) && !(elt instanceof PsiFile)) {
        elt = elt.getParent();
        if (elt == null) break;
        range = elt.getTextRange();
        assert range != null : "Range is null for " + elt + "; " + elt.getClass();
      }

      if (elt != null) {
        return elt.getLanguage();
      }
    }

    return reallyEvaluateLanguageInRange(start, end, file);
  }

  @NotNull
  public static ASTNode getRoot(@NotNull ASTNode node) {
    ASTNode child = node;
    do {
      final ASTNode parent = child.getTreeParent();
      if (parent == null) return child;
      child = parent;
    }
    while (true);
  }

  /**
   * Tries to find editor for the given element.
   * <p/>
   * There are at least two approaches to achieve the target. Current method is intended to encapsulate both of them:
   * <pre>
   * <ul>
   *   <li>target editor works with a real file that remains at file system;</li>
   *   <li>target editor works with a virtual file;</li>
   * </ul>
   * </pre>
   *
   * @param element   target element
   * @return          editor that works with a given element if the one is found; <code>null</code> otherwise
   */
  @Nullable
  public static Editor findEditor(@NotNull PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    VirtualFile virtualFile = psiFile.getOriginalFile().getVirtualFile();
    if (virtualFile == null) {
      return null;
    }

    VirtualFileSystem fileSystem = virtualFile.getFileSystem();
    if (fileSystem instanceof LocalFileSystem) {
      // Try to find editor for the real file.
      final FileEditor[] editors = FileEditorManager.getInstance(psiFile.getProject()).getEditors(virtualFile);
      for (FileEditor editor : editors) {
        if (editor instanceof TextEditor) {
          return ((TextEditor)editor).getEditor();
        }
      }
    }
    else if (SwingUtilities.isEventDispatchThread()) {
      // We assume that data context from focus-based retrieval should success if performed from EDT.
      AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
      if (asyncResult.isDone()) {
        Editor editor = CommonDataKeys.EDITOR.getData(asyncResult.getResult());
        if (editor != null) {
          Document cachedDocument = PsiDocumentManager.getInstance(psiFile.getProject()).getCachedDocument(psiFile);
          // Ensure that target editor is found by checking its document against the one from given PSI element.
          if (cachedDocument == editor.getDocument()) {
            return editor;
          }
        }
      }
    }
    return null;
  }

  public static boolean isSymLink(@NotNull final PsiFileSystemItem element) {
    final VirtualFile virtualFile = element.getVirtualFile();
    return virtualFile != null && virtualFile.is(VFileProperty.SYMLINK);
  }

  @Nullable
  public static VirtualFile asVirtualFile(@Nullable PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      PsiFileSystemItem psiFileSystemItem = (PsiFileSystemItem)element;
      return psiFileSystemItem.isValid() ? psiFileSystemItem.getVirtualFile() : null;
    }
    else {
      return null;
    }
  }
}
