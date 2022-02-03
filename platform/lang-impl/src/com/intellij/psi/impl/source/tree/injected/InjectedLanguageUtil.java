// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.BooleanRunnable;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * @deprecated Use {@link InjectedLanguageManager} instead
 */
@Deprecated
public final class InjectedLanguageUtil extends InjectedLanguageUtilBase {
  public static final Key<Boolean> FRANKENSTEIN_INJECTION = InjectedLanguageManager.FRANKENSTEIN_INJECTION;

  private static final Comparator<PsiFile> LONGEST_INJECTION_HOST_RANGE_COMPARATOR = Comparator.comparing(
    psiFile -> InjectedLanguageManager.getInstance(psiFile.getProject()).getInjectionHost(psiFile),
    Comparator.nullsLast(Comparator.comparingInt(PsiElement::getTextLength))
  );

  public static void enumerate(@NotNull DocumentWindow documentWindow,
                               @NotNull PsiFile hostPsiFile,
                               @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    Segment[] ranges = documentWindow.getHostRanges();
    Segment rangeMarker = ranges.length > 0 ? ranges[0] : null;
    PsiElement element = rangeMarker == null ? null : hostPsiFile.findElementAt(rangeMarker.getStartOffset());
    if (element != null) {
      enumerate(element, hostPsiFile, true, visitor);
    }
  }

  /**
   * Invocation of this method on uncommitted {@code file} can lead to unexpected results, including throwing an exception!
   */
  @Contract("null,_->null;!null,_->!null")
  public static Editor getEditorForInjectedLanguageNoCommit(@Nullable Editor editor, @Nullable PsiFile file) {
    if (editor == null || file == null || editor instanceof EditorWindow) return editor;

    int offset = editor.getCaretModel().getOffset();
    return getEditorForInjectedLanguageNoCommit(editor, file, offset);
  }

  /**
   * Invocation of this method on uncommitted {@code file} can lead to unexpected results, including throwing an exception!
   */
  public static Editor getEditorForInjectedLanguageNoCommit(@Nullable Editor editor, @Nullable Caret caret, @Nullable PsiFile file) {
    if (editor == null || file == null || editor instanceof EditorWindow || caret == null) return editor;

    PsiFile injectedFile = findInjectedPsiNoCommit(file, caret.getOffset());
    return getInjectedEditorForInjectedFile(editor, caret, injectedFile);
  }

  /**
   * Invocation of this method on uncommitted {@code file} can lead to unexpected results, including throwing an exception!
   */
  public static Caret getCaretForInjectedLanguageNoCommit(@Nullable Caret caret, @Nullable PsiFile file) {
    if (caret == null || file == null || caret instanceof InjectedCaret) return caret;

    PsiFile injectedFile = findInjectedPsiNoCommit(file, caret.getOffset());
    Editor injectedEditor = getInjectedEditorForInjectedFile(caret.getEditor(), injectedFile);
    if (!(injectedEditor instanceof EditorWindow)) {
      return caret;
    }
    for (Caret injectedCaret : injectedEditor.getCaretModel().getAllCarets()) {
      if (((InjectedCaret)injectedCaret).getDelegate() == caret) {
        return injectedCaret;
      }
    }
    return null;
  }

  /**
   * Finds injected language in expression
   *
   * @param expression  where to find
   * @param classToFind class that represents language we look for
   * @param <T>         class that represents language we look for
   * @return instance of class that represents language we look for or null of not found
   */
  @Nullable
  @SuppressWarnings("unchecked") // We check types dynamically (using isAssignableFrom)
  public static <T extends PsiFileBase> T findInjectedFile(@NotNull final PsiElement expression,
                                                           @NotNull final Class<T> classToFind) {
    final List<Pair<PsiElement, TextRange>> files =
      InjectedLanguageManager.getInstance(expression.getProject()).getInjectedPsiFiles(expression);
    if (files == null) {
      return null;
    }
    for (final Pair<PsiElement, TextRange> fileInfo : files) {
      final PsiElement injectedFile = fileInfo.first;
      if (classToFind.isAssignableFrom(injectedFile.getClass())) {
        return (T)injectedFile;
      }
    }
    return null;
  }

  /**
   * Invocation of this method on uncommitted {@code file} can lead to unexpected results, including throwing an exception!
   */
  @Contract("null,_,_->null;!null,_,_->!null")
  public static Editor getEditorForInjectedLanguageNoCommit(@Nullable Editor editor, @Nullable PsiFile file, final int offset) {
    if (editor == null || file == null || editor instanceof EditorWindow) return editor;
    PsiFile injectedFile = findInjectedPsiNoCommit(file, offset);
    return getInjectedEditorForInjectedFile(editor, injectedFile);
  }

  @NotNull
  public static Editor getInjectedEditorForInjectedFile(@NotNull Editor hostEditor, @Nullable final PsiFile injectedFile) {
    return getInjectedEditorForInjectedFile(hostEditor, hostEditor.getCaretModel().getCurrentCaret(), injectedFile);
  }

  /**
   * @param hostCaret if not {@code null}, take into account caret's selection (in case it's not contained completely in injected fragment,
   *                  return host editor)
   */
  @NotNull
  public static Editor getInjectedEditorForInjectedFile(@NotNull Editor hostEditor,
                                                        @Nullable Caret hostCaret,
                                                        @Nullable final PsiFile injectedFile) {
    if (injectedFile == null || hostEditor instanceof EditorWindow || hostEditor.isDisposed()) return hostEditor;
    Project project = hostEditor.getProject();
    if (project == null) project = injectedFile.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(injectedFile);
    if (!(document instanceof DocumentWindowImpl)) return hostEditor;
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)document;
    if (hostCaret != null && hostCaret.hasSelection()) {
      int selstart = hostCaret.getSelectionStart();
      if (selstart != -1) {
        int selend = Math.max(selstart, hostCaret.getSelectionEnd());
        if (!documentWindow.containsRange(selstart, selend)) {
          // selection spreads out the injected editor range
          return hostEditor;
        }
      }
    }
    if (!documentWindow.isValid()) {
      return hostEditor; // since the moment we got hold of injectedFile and this moment call, document may have been dirtied
    }
    EditorWindowTrackerImpl tracker = (EditorWindowTrackerImpl)ApplicationManager.getApplication().getService(EditorWindowTracker.class);
    return tracker.createEditor(documentWindow, (EditorImpl)hostEditor, injectedFile);
  }

  public static Editor openEditorFor(@NotNull PsiFile file, @NotNull Project project) {
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    // may return editor injected in current selection in the host editor, not for the file passed as argument
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (virtualFile instanceof VirtualFileWindow) {
      virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
    }
    Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile, -1), false);
    if (editor == null || editor instanceof EditorWindow || editor.isDisposed()) return editor;
    if (document instanceof DocumentWindowImpl) {
      EditorWindowTrackerImpl tracker = (EditorWindowTrackerImpl)ApplicationManager.getApplication().getService(EditorWindowTracker.class);
      return tracker.createEditor((DocumentWindowImpl)document, (EditorImpl)editor, file);
    }
    return editor;
  }

  @NotNull
  public static Editor getTopLevelEditor(@NotNull Editor editor) {
    return InjectedLanguageEditorUtil.getTopLevelEditor(editor);
  }

  public static int hostToInjectedUnescaped(DocumentWindow window, int hostOffset) {
    Place shreds = ((DocumentWindowImpl)window).getShreds();
    Segment hostRangeMarker = shreds.get(0).getHostRangeMarker();
    if (hostRangeMarker == null || hostOffset < hostRangeMarker.getStartOffset()) {
      return shreds.get(0).getPrefix().length();
    }
    StringBuilder chars = new StringBuilder();
    int unescaped = 0;
    for (int i = 0; i < shreds.size(); i++, chars.setLength(0)) {
      PsiLanguageInjectionHost.Shred shred = shreds.get(i);
      int prefixLength = shred.getPrefix().length();
      int suffixLength = shred.getSuffix().length();
      PsiLanguageInjectionHost host = shred.getHost();
      TextRange rangeInsideHost = shred.getRangeInsideHost();
      LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = host == null ? null : host.createLiteralTextEscaper();
      unescaped += prefixLength;
      Segment currentRange = shred.getHostRangeMarker();
      if (currentRange == null) continue;
      Segment nextRange = i == shreds.size() - 1 ? null : shreds.get(i + 1).getHostRangeMarker();
      if (nextRange == null || hostOffset < nextRange.getStartOffset()) {
        hostOffset = Math.min(hostOffset, currentRange.getEndOffset());
        int inHost = hostOffset - currentRange.getStartOffset();
        if (escaper != null && escaper.decode(rangeInsideHost, chars)) {
          int found = ObjectUtils.binarySearch(
            0, inHost, index -> Comparing.compare(escaper.getOffsetInHost(index, TextRange.create(0, host.getTextLength())), inHost));
          return unescaped + (found >= 0 ? found : -found - 1);
        }
        return unescaped + inHost;
      }
      if (escaper != null && escaper.decode(rangeInsideHost, chars)) {
        unescaped += chars.length();
      }
      else {
        unescaped += currentRange.getEndOffset() - currentRange.getStartOffset();
      }
      unescaped += suffixLength;
    }
    return unescaped - shreds.get(shreds.size() - 1).getSuffix().length();
  }

  @Nullable
  public static DocumentWindow getDocumentWindow(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile instanceof VirtualFileWindow) return ((VirtualFileWindow)virtualFile).getDocumentWindow();
    return null;
  }

  public static boolean isHighlightInjectionBackground(@Nullable PsiLanguageInjectionHost host) {
    return !(host instanceof InjectionBackgroundSuppressor);
  }

  public static int getInjectedStart(@NotNull List<? extends PsiLanguageInjectionHost.Shred> places) {
    PsiLanguageInjectionHost.Shred shred = places.get(0);
    PsiLanguageInjectionHost host = shred.getHost();
    assert host != null;
    return shred.getRangeInsideHost().getStartOffset() + host.getTextRange().getStartOffset();
  }

  @Nullable
  public static PsiElement findElementInInjected(@NotNull PsiLanguageInjectionHost injectionHost, final int offset) {
    final Ref<PsiElement> ref = Ref.create();
    enumerate(injectionHost, (injectedPsi, places) -> ref.set(injectedPsi.findElementAt(offset - getInjectedStart(places))));
    return ref.get();
  }

  public static <T> void putInjectedFileUserData(@NotNull PsiElement element,
                                                 @NotNull Language language,
                                                 @NotNull Key<T> key,
                                                 @Nullable T value) {
    PsiFile file = getCachedInjectedFileWithLanguage(element, language);
    if (file != null) {
      file.putUserData(key, value);
    }
  }

  @Nullable
  public static PsiFile getCachedInjectedFileWithLanguage(@NotNull PsiElement element, @NotNull Language language) {
    if (!element.isValid()) return null;
    PsiFile containingFile = PsiUtilCore.getTemplateLanguageFile(element.getContainingFile());
    if (containingFile == null || !containingFile.isValid()) return null;
    return InjectedLanguageManager.getInstance(containingFile.getProject())
      .getCachedInjectedDocumentsInRange(containingFile, element.getTextRange())
      .stream()
      .map(documentWindow -> PsiDocumentManager.getInstance(containingFile.getProject()).getPsiFile(documentWindow))
      .filter(file -> file != null && file.getLanguage() == LanguageSubstitutors.getInstance()
        .substituteLanguage(language, file.getVirtualFile(), file.getProject()))
      .max(LONGEST_INJECTION_HOST_RANGE_COMPARATOR)
      .orElse(null);
  }

  /**
   * Start injecting the reference in {@code language} in this place.
   * Unlike {@link MultiHostRegistrar#startInjecting(Language)} this method doesn't inject the full blown file in the other language.
   * Instead, it just marks some range as a reference in some language.
   * For example, you can inject file reference into string literal.
   * After that, it won't be highlighted as an injected fragment but still can be subject to e.g. "Goto declaraion" action.
   */
  public static void injectReference(@NotNull MultiHostRegistrar registrar,
                                     @NotNull Language language,
                                     @NotNull String prefix,
                                     @NotNull String suffix,
                                     @NotNull PsiLanguageInjectionHost host,
                                     @NotNull TextRange rangeInsideHost) {
    ((InjectionRegistrarImpl)registrar).injectReference(language, prefix, suffix, host, rangeInsideHost);
  }

  // null means failed to reparse
  public static BooleanRunnable reparse(@NotNull PsiFile injectedPsiFile,
                                        @NotNull DocumentWindow injectedDocument,
                                        @NotNull PsiFile hostPsiFile,
                                        @NotNull Document hostDocument,
                                        @NotNull FileViewProvider hostViewProvider,
                                        @NotNull ProgressIndicator indicator,
                                        @NotNull ASTNode oldRoot,
                                        @NotNull ASTNode newRoot,
                                        @NotNull PsiDocumentManagerBase documentManager) {
    Language language = injectedPsiFile.getLanguage();
    InjectedFileViewProvider provider = (InjectedFileViewProvider)injectedPsiFile.getViewProvider();
    VirtualFile oldInjectedVFile = provider.getVirtualFile();
    VirtualFile hostVirtualFile = hostViewProvider.getVirtualFile();
    BooleanRunnable runnable = InjectionRegistrarImpl
      .reparse(language, (DocumentWindowImpl)injectedDocument, injectedPsiFile, (VirtualFileWindow)oldInjectedVFile, hostVirtualFile, hostPsiFile,
               (DocumentEx)hostDocument,
               indicator, oldRoot, newRoot, documentManager);
    if (runnable == null) {
      ApplicationManager.getApplication().getService(EditorWindowTracker.class).disposeEditorFor(injectedDocument);
    }
    return runnable;
  }
}
