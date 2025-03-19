// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedCaret;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SelectWordHandler extends EditorActionHandler.ForEachCaret {
  private static final Logger LOG = Logger.getInstance(SelectWordHandler.class);

  private final EditorActionHandler myOriginalHandler;

  public SelectWordHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: execute(editor='" + editor + "')");
    }
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, caret, dataContext);
      }
      return;
    }
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    TextRange range = selectWord(caret, project);
    if (editor instanceof EditorWindow) {
      if (range == null || !isInsideEditableInjection((EditorWindow)editor, range, project) || TextRange.from(0, editor.getDocument().getTextLength()).equals(
        new TextRange(caret.getSelectionStart(), caret.getSelectionEnd()))) {
        editor = ((EditorWindow)editor).getDelegate();
        caret = ((InjectedCaret)caret).getDelegate();
        range = selectWord(caret, project);
      }
    }
    if (range == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, caret, dataContext);
      }
    }
    else {
      caret.setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  private static boolean isInsideEditableInjection(EditorWindow editor, TextRange range, Project project) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return true;
    List<TextRange> editables = InjectedLanguageManager.getInstance(project).intersectWithAllEditableFragments(file, range);

    return editables.size() == 1 && range.equals(editables.get(0));
  }

  private static @Nullable("null means unable to select") TextRange selectWord(@NotNull Caret caret, @NotNull Project project) {
    ThrowableComputable<TextRange, Exception> computable = () -> {
      return ReadAction.compute(() -> {
        return doSelectWord(caret, project);
      });
    };
    try {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(computable,
                                                                               EditorBundle.message("select.word.progress"),
                                                                               true, project);
    }
    catch (ProcessCanceledException pce) {
      return null;
    }
    catch (Exception e) {
      LOG.error("Cannot select word at given offset", e);
      return null;
    }
  }

  private static @Nullable TextRange doSelectWord(@NotNull Caret caret, @NotNull Project project) {
    Document document = caret.getEditor().getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;

    int caretOffset = adjustCaretOffset(caret);

    PsiElement element = findElementAt(file, caretOffset);
    if (element == null) {
      return null;
    }

    if (element instanceof PsiWhiteSpace && caretOffset > 0) {
      PsiElement anotherElement = findElementAt(file, caretOffset - 1);

      if (!(anotherElement instanceof PsiWhiteSpace)) {
        element = anotherElement;
      }
    }

    if (!(element instanceof PsiWhiteSpace && SelectWordUtil.canWhiteSpaceBeExpanded((PsiWhiteSpace) element, caretOffset, caret, caret.getEditor()))) {
      while (element instanceof PsiWhiteSpace || element != null && StringUtil.isEmptyOrSpaces(element.getText())) {
        while (element.getNextSibling() == null) {
          if (element instanceof PsiFile) return null;
          final PsiElement parent = element.getParent();
          final PsiElement[] children = parent.getChildren();

          if (children.length > 0 && children[children.length - 1] == element) {
            element = parent;
          }
          else {
            element = parent;
            break;
          }
        }

        if (element instanceof PsiFile) return null;
        element = element.getNextSibling();
        if (element == null) return null;
        TextRange range = element.getTextRange();
        if (range == null) return null; // Fix NPE (EA-29110)
        caretOffset = range.getStartOffset();
      }
    }

    if (element instanceof OuterLanguageElement) {
      PsiElement elementInOtherTree = file.getViewProvider().findElementAt(element.getTextOffset(), element.getLanguage());
      if (elementInOtherTree != null && elementInOtherTree.getContainingFile() != element.getContainingFile()) {
        while (elementInOtherTree != null && elementInOtherTree.getPrevSibling() == null) {
          elementInOtherTree = elementInOtherTree.getParent();
        }

        if (elementInOtherTree != null) {
          if (elementInOtherTree.getTextOffset() == caretOffset) element = elementInOtherTree;
        }
      }
    }

    checkElementRange(document, element);

    final TextRange selectionRange = new TextRange(caret.getSelectionStart(), caret.getSelectionEnd());

    final Ref<TextRange> minimumRange = new Ref<>(new TextRange(0, document.getTextLength()));

    SelectWordUtil.processRanges(element, document.getCharsSequence(), caretOffset, caret.getEditor(), range -> {
      if (range.contains(selectionRange) && !range.equals(selectionRange)) {
        if (minimumRange.get().contains(range)) {
          minimumRange.set(range);
          return true;
        }
      }
      return false;
    });

    return minimumRange.get();
  }

  private static void checkElementRange(Document document, PsiElement element) {
    if (element != null && element.getTextRange().getEndOffset() > document.getTextLength()) {
      throw new AssertionError(DebugUtil.diagnosePsiDocumentInconsistency(element, document));
    }
  }

  private static int adjustCaretOffset(@NotNull Caret caret) {
    int caretOffset = caret.getOffset();
    if (caretOffset == 0) {
      return caretOffset;
    }

    CharSequence text = caret.getEditor().getDocument().getCharsSequence();
    char prev = text.charAt(caretOffset - 1);
    if (caretOffset < text.length() &&
        !Character.isJavaIdentifierPart(text.charAt(caretOffset)) && Character.isJavaIdentifierPart(prev)) {
      return caretOffset - 1;
    }
    if ((caretOffset == text.length() || Character.isWhitespace(text.charAt(caretOffset))) && !Character.isWhitespace(prev)) {
      return caretOffset - 1;
    }
    if (caret.getSelectionEnd() == caretOffset && caret.getSelectionStart() < caretOffset) {
      return caretOffset - 1;
    }
    return caretOffset;
  }

  private static @Nullable PsiElement findElementAt(final @NotNull PsiFile file, final int caretOffset) {
    int offset = caretOffset > 0 && caretOffset == file.getTextLength()? caretOffset - 1 : caretOffset; // get element before caret if it is in the file end
    PsiElement elementAt = file.findElementAt(offset);
    return elementAt != null && isLanguageExtension(file, elementAt)
           ? file.getViewProvider().findElementAt(offset, file.getLanguage())
           : elementAt;
  }

  private static boolean isLanguageExtension(final @NotNull PsiFile file, final @NotNull PsiElement elementAt) {
    final Language elementLanguage = elementAt.getLanguage();
    if (file.getLanguage() instanceof CompositeLanguage compositeLanguage) {
      final Language[] extensions = compositeLanguage.getLanguageExtensionsForFile(file);
      return ArrayUtil.contains(elementLanguage, extensions);
    }
    return false;
  }

}