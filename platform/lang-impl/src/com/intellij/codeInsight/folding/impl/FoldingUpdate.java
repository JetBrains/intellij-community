/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.codeInsight.folding.impl;

import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.codeInsight.folding.impl.UpdateFoldRegionsOperation.ApplyDefaultStateMode.EXCEPT_CARET_REGION;
import static com.intellij.codeInsight.folding.impl.UpdateFoldRegionsOperation.ApplyDefaultStateMode.NO;

public class FoldingUpdate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.FoldingUpdate");

  static final Key<ParameterizedCachedValue<Runnable, Boolean>> CODE_FOLDING_KEY = Key.create("code folding");
  private static final Key<String> CODE_FOLDING_FILE_EXTENSION_KEY = Key.create("code folding file extension");

  private FoldingUpdate() {
  }

  @Nullable
  static Runnable updateFoldRegions(@NotNull final Editor editor, @NotNull PsiFile file, final boolean applyDefaultState, final boolean quick) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Project project = file.getProject();
    final Document document = editor.getDocument();
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).isUncommited(document));
    
    String currentFileExtension = null;
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      currentFileExtension = virtualFile.getExtension();
    }

    ParameterizedCachedValue<Runnable, Boolean> value = editor.getUserData(CODE_FOLDING_KEY);
    if (value != null) {
      // There was a problem that old fold regions have been cached on file extension change (e.g. *.java -> *.groovy).
      // We want to drop them in such circumstances.
      final String oldExtension = editor.getUserData(CODE_FOLDING_FILE_EXTENSION_KEY);
      if (oldExtension == null ? currentFileExtension != null : !oldExtension.equals(currentFileExtension)) {
        value = null;
        editor.putUserData(CODE_FOLDING_KEY, null);
      }
    }
    editor.putUserData(CODE_FOLDING_FILE_EXTENSION_KEY, currentFileExtension);
    
    if (value != null && value.hasUpToDateValue() && !applyDefaultState) return value.getValue(null); // param shouldn't matter, as the value is up-to-date
    if (quick) return getUpdateResult(file, document, true, project, editor, applyDefaultState).getValue();
    
    return CachedValuesManager.getManager(project).getParameterizedCachedValue(
      editor, CODE_FOLDING_KEY, param -> {
        Document document1 = editor.getDocument();
        PsiFile file1 = PsiDocumentManager.getInstance(project).getPsiFile(document1);
        return getUpdateResult(file1, document1, false, project, editor, param);
      }, false, applyDefaultState);
  }

  private static CachedValueProvider.Result<Runnable> getUpdateResult(PsiFile file,
                                                                      @NotNull Document document,
                                                                      boolean quick,
                                                                      final Project project,
                                                                      final Editor editor,
                                                                      final boolean applyDefaultState) {

    final List<RegionInfo> elementsToFold = getFoldingsFor(file, document, quick);
    final UpdateFoldRegionsOperation operation = new UpdateFoldRegionsOperation(project, editor, file, elementsToFold,
                                                                                applyDefaultState ? EXCEPT_CARET_REGION : NO, 
                                                                                !applyDefaultState, false);
    long documentTimestamp = document.getModificationStamp();
    int documentLength = document.getTextLength();
    AtomicBoolean alreadyExecuted = new AtomicBoolean();
    Runnable runnable = () -> {
      if (alreadyExecuted.compareAndSet(false, true)) {
        if (documentTimestamp != editor.getDocument().getModificationStamp()) {
          LOG.error("Document has changed since fold regions were calculated");
        }
        else if (documentLength != editor.getDocument().getTextLength()) {
          LOG.error("Document length has changed since fold regions were calculated");
        }
        editor.getFoldingModel().runBatchFoldingOperationDoNotCollapseCaret(operation);
      }
    };
    Set<Object> dependencies = new HashSet<>();
    dependencies.add(document);
    dependencies.add(editor.getFoldingModel());
    for (RegionInfo info : elementsToFold) {
      dependencies.addAll(info.descriptor.getDependencies());
    }
    return CachedValueProvider.Result.create(runnable, ArrayUtil.toObjectArray(dependencies));
  }

  private static final Key<Object> LAST_UPDATE_INJECTED_STAMP_KEY = Key.create("LAST_UPDATE_INJECTED_STAMP_KEY");
  @Nullable
  public static Runnable updateInjectedFoldRegions(@NotNull final Editor editor, @NotNull final PsiFile file, final boolean applyDefaultState) {
    if (file instanceof PsiCompiledElement) return null;
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Project project = file.getProject();
    Document document = editor.getDocument();
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).isUncommited(document));
    final FoldingModel foldingModel = editor.getFoldingModel();

    final long timeStamp = document.getModificationStamp();
    Object lastTimeStamp = editor.getUserData(LAST_UPDATE_INJECTED_STAMP_KEY);
    if (lastTimeStamp instanceof Long && ((Long)lastTimeStamp).longValue() == timeStamp) return null;

    List<DocumentWindow> injectedDocuments = InjectedLanguageManager.getInstance(project).getCachedInjectedDocumentsInRange(file, file.getTextRange());
    if (injectedDocuments.isEmpty()) return null;
    final List<EditorWindow> injectedEditors = new ArrayList<>();
    final List<PsiFile> injectedFiles = new ArrayList<>();
    final List<List<RegionInfo>> lists = new ArrayList<>();
    for (final DocumentWindow injectedDocument : injectedDocuments) {
      if (!injectedDocument.isValid()) {
        continue;
      }
      InjectedLanguageUtil.enumerate(injectedDocument, file, (injectedFile, places) -> {
        if (!injectedFile.isValid()) return;
        Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile);
        if (!(injectedEditor instanceof EditorWindow)) return;

        injectedEditors.add((EditorWindow)injectedEditor);
        injectedFiles.add(injectedFile);
        final List<RegionInfo> map = new ArrayList<>();
        lists.add(map);
        getFoldingsFor(injectedFile, injectedEditor.getDocument(), map, false);
      });
    }

    return () -> {
      final ArrayList<Runnable> updateOperations = new ArrayList<>(injectedEditors.size());
      for (int i = 0; i < injectedEditors.size(); i++) {
        EditorWindow injectedEditor = injectedEditors.get(i);
        PsiFile injectedFile = injectedFiles.get(i);
        if (!injectedEditor.getDocument().isValid()) continue;
        List<RegionInfo> list = lists.get(i);
        updateOperations.add(new UpdateFoldRegionsOperation(project, injectedEditor, injectedFile, list,
                                                            applyDefaultState ? EXCEPT_CARET_REGION : NO, !applyDefaultState, true));
      }
      foldingModel.runBatchFoldingOperation(() -> {
        for (Runnable operation : updateOperations) {
          operation.run();
        }
      });

      editor.putUserData(LAST_UPDATE_INJECTED_STAMP_KEY, timeStamp);
    };
  }

  /**
   * Checks the ability to initialize folding in the Dumb Mode. Due to language injections it may depend on
   * edited file and active injections (not yet implemented).
   *
   * @param editor the editor that holds file view
   * @return true  if folding initialization available in the Dumb Mode
   */
  public static boolean supportsDumbModeFolding(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null) {
        return supportsDumbModeFolding(file);
      }
    }
    return true;
  }

  /**
   * Checks the ability to initialize folding in the Dumb Mode for file.
   *
   * @param file the file to test
   * @return true  if folding initialization available in the Dumb Mode
   */
  static boolean supportsDumbModeFolding(@NotNull PsiFile file) {
    final FileViewProvider viewProvider = file.getViewProvider();
    for (final Language language : viewProvider.getLanguages()) {
      final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);
      if(foldingBuilder != null && !DumbService.isDumbAware(foldingBuilder))
        return false;
    }
    return true;
  }

  static List<RegionInfo> getFoldingsFor(@NotNull PsiFile file, @NotNull Document document, boolean quick) {
    List<RegionInfo> foldingMap = new ArrayList<>();
    if (file instanceof PsiCompiledFile) {
      file = ((PsiCompiledFile)file).getDecompiledPsiFile();
    }
    getFoldingsFor(file, document, foldingMap, quick);
    return foldingMap;
  }

  private static void getFoldingsFor(@NotNull PsiFile file,
                                     @NotNull Document document,
                                     @NotNull List<RegionInfo> elementsToFold,
                                     boolean quick) {
    final FileViewProvider viewProvider = file.getViewProvider();
    TextRange docRange = TextRange.from(0, document.getTextLength());
    for (final Language language : viewProvider.getLanguages()) {
      final PsiFile psi = viewProvider.getPsi(language);
      final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);
      if (psi != null && foldingBuilder != null) {
        for (FoldingDescriptor descriptor : LanguageFolding.buildFoldingDescriptors(foldingBuilder, psi, document, quick)) {
          PsiElement psiElement = descriptor.getElement().getPsi();
          if (psiElement == null) {
            LOG.error("No PSI for folding descriptor " + descriptor);
            continue;
          }
          if (!docRange.contains(descriptor.getRange())) {
            diagnoseIncorrectRange(psi, document, language, foldingBuilder, descriptor, psiElement);
            continue;
          }
          RegionInfo regionInfo = new RegionInfo(descriptor, psiElement);
          elementsToFold.add(regionInfo);
        }
      }
    }
  }

  private static void diagnoseIncorrectRange(@NotNull PsiFile file,
                                             @NotNull Document document,
                                             Language language,
                                             FoldingBuilder foldingBuilder, FoldingDescriptor descriptor, PsiElement psiElement) {
    String message = "Folding descriptor " + descriptor +
                     " made by " + foldingBuilder +
                     " for " + language +
                     " is outside document range" +
                     ", PSI element: " + psiElement +
                     ", PSI element range: " + psiElement.getTextRange() + "; " + DebugUtil.diagnosePsiDocumentInconsistency(psiElement, document);
    LOG.error(message, ApplicationManager.getApplication().isInternal()
                               ? new Attachment[]{AttachmentFactory.createAttachment(document), new Attachment("psiTree.txt", DebugUtil.psiToString(file, false, true))}
                               : Attachment.EMPTY_ARRAY);
  }

  static class RegionInfo {
    @NotNull
    public final FoldingDescriptor descriptor;
    public final PsiElement element;
    public final String signature;
    public final boolean collapsedByDefault;

    private RegionInfo(@NotNull FoldingDescriptor descriptor, @NotNull PsiElement psiElement) {
      this.descriptor = descriptor;
      this.element = psiElement;
      this.collapsedByDefault = FoldingPolicy.isCollapseByDefault(psiElement);
      this.signature = createSignature(psiElement);
    }

    private static String createSignature(@NotNull PsiElement element) {
      String signature = FoldingPolicy.getSignature(element);
      if (signature != null) {
        PsiFile containingFile = element.getContainingFile();
        PsiElement restoredElement = FoldingPolicy.restoreBySignature(containingFile, signature);
        if (!element.equals(restoredElement)) {
          StringBuilder trace = new StringBuilder();
          PsiElement restoredAgain = FoldingPolicy.restoreBySignature(containingFile, signature, trace);
          LOG.error("element: " + element + "(" + element.getText()
                    + "); restoredElement: " + restoredElement
                    + "; signature: '" + signature
                    + "'; file: " + containingFile
                    + "; injected: " + InjectedLanguageManager.getInstance(element.getProject()).isInjectedFragment(containingFile)
                    + "; languages: " + containingFile.getViewProvider().getLanguages()
                    + "; restored again: " + restoredAgain +
                    "; restore produces same results: " + (restoredAgain == restoredElement)
                    + "; trace:\n" + trace);
        }
      }
      return signature;
    }

    @Override
    public String toString() {
      return descriptor + ", collapsedByDefault=" + collapsedByDefault;
    }
  }
}
