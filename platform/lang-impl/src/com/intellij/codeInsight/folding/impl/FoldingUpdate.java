/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.folding.impl;

import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.folding.impl.UpdateFoldRegionsOperation.ApplyDefaultStateMode.EXCEPT_CARET_REGION;
import static com.intellij.codeInsight.folding.impl.UpdateFoldRegionsOperation.ApplyDefaultStateMode.NO;

public class FoldingUpdate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.FoldingUpdate");

  private static final Key<ParameterizedCachedValue<Runnable, Couple<Boolean>>> CODE_FOLDING_KEY = Key.create("code folding");
  private static final Key<String> CODE_FOLDING_FILE_EXTENSION_KEY = Key.create("code folding file extension");

  private static final Comparator<PsiElement> COMPARE_BY_OFFSET_REVERSED = new Comparator<PsiElement>() {
    @Override
    public int compare(PsiElement element, PsiElement element1) {
      int startOffsetDiff = element1.getTextRange().getStartOffset() - element.getTextRange().getStartOffset();
      return startOffsetDiff == 0 ? element1.getTextRange().getEndOffset() - element.getTextRange().getEndOffset() : startOffsetDiff;
    }
  };

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

    ParameterizedCachedValue<Runnable, Couple<Boolean>> value = editor.getUserData(CODE_FOLDING_KEY);
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
    
    if (value != null && value.hasUpToDateValue() && !applyDefaultState) return null;
    if (quick) return getUpdateResult(file, document, quick, project, editor, applyDefaultState).getValue();
    
    return CachedValuesManager.getManager(project).getParameterizedCachedValue(
      editor, CODE_FOLDING_KEY, new ParameterizedCachedValueProvider<Runnable, Couple<Boolean>>() {
        @Override
        public CachedValueProvider.Result<Runnable> compute(Couple<Boolean> param) {
          Document document = editor.getDocument();
          PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
          return getUpdateResult(file, document, param.first, project, editor, param.second);
        }
      }, false, Couple.of(quick, applyDefaultState));
  }

  private static CachedValueProvider.Result<Runnable> getUpdateResult(PsiFile file,
                                                                      @NotNull Document document,
                                                                      boolean quick,
                                                                      final Project project,
                                                                      final Editor editor,
                                                                      final boolean applyDefaultState) {

    final FoldingMap elementsToFoldMap = getFoldingsFor(file, document, quick);
    final UpdateFoldRegionsOperation operation = new UpdateFoldRegionsOperation(project, editor, file, elementsToFoldMap,
                                                                                applyDefaultState ? EXCEPT_CARET_REGION : NO, 
                                                                                !applyDefaultState, false);
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        editor.getFoldingModel().runBatchFoldingOperationDoNotCollapseCaret(operation);
      }
    };
    Set<Object> dependencies = new HashSet<Object>();
    dependencies.add(document);
    dependencies.add(editor.getFoldingModel());
    for (FoldingDescriptor descriptor : elementsToFoldMap.values()) {
      dependencies.addAll(descriptor.getDependencies());
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

    List<DocumentWindow> injectedDocuments = InjectedLanguageUtil.getCachedInjectedDocuments(file);
    if (injectedDocuments.isEmpty()) return null;
    final List<EditorWindow> injectedEditors = new ArrayList<EditorWindow>();
    final List<PsiFile> injectedFiles = new ArrayList<PsiFile>();
    final List<FoldingMap> maps = new ArrayList<FoldingMap>();
    for (final DocumentWindow injectedDocument : injectedDocuments) {
      if (!injectedDocument.isValid()) {
        continue;
      }
      InjectedLanguageUtil.enumerate(injectedDocument, file, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        @Override
        public void visit(@NotNull PsiFile injectedFile, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          if (!injectedFile.isValid()) return;
          Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile);
          if (!(injectedEditor instanceof EditorWindow)) return;

          injectedEditors.add((EditorWindow)injectedEditor);
          injectedFiles.add(injectedFile);
          final FoldingMap map = new FoldingMap();
          maps.add(map);
          getFoldingsFor(injectedFile, injectedEditor.getDocument(), map, false);
        }
      });
    }

    return new Runnable() {
      @Override
      public void run() {
        final ArrayList<Runnable> updateOperations = new ArrayList<Runnable>(injectedEditors.size());
        for (int i = 0; i < injectedEditors.size(); i++) {
          EditorWindow injectedEditor = injectedEditors.get(i);
          PsiFile injectedFile = injectedFiles.get(i);
          if (!injectedEditor.getDocument().isValid()) continue;
          FoldingMap map = maps.get(i);
          updateOperations.add(new UpdateFoldRegionsOperation(project, injectedEditor, injectedFile, map,
                                                              applyDefaultState ? EXCEPT_CARET_REGION : NO, !applyDefaultState, true));
        }
        foldingModel.runBatchFoldingOperation(new Runnable() {
          @Override
          public void run() {
            for (Runnable operation : updateOperations) {
              operation.run();
            }
          }
        });

        editor.putUserData(LAST_UPDATE_INJECTED_STAMP_KEY, timeStamp);
      }
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
  public static boolean supportsDumbModeFolding(@NotNull PsiFile file) {
    final FileViewProvider viewProvider = file.getViewProvider();
    for (final Language language : viewProvider.getLanguages()) {
      final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);
      if(foldingBuilder != null && !DumbService.isDumbAware(foldingBuilder))
        return false;
    }
    return true;
  }

  static FoldingMap getFoldingsFor(@NotNull PsiFile file, @NotNull Document document, boolean quick) {
    FoldingMap foldingMap = new FoldingMap();
    if (file instanceof PsiCompiledFile) {
      file = ((PsiCompiledFile)file).getDecompiledPsiFile();
    }
    getFoldingsFor(file, document, foldingMap, quick);
    return foldingMap;
  }

  private static void getFoldingsFor(@NotNull PsiFile file,
                                     @NotNull Document document,
                                     @NotNull FoldingMap elementsToFoldMap,
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
          elementsToFoldMap.putValue(psiElement, descriptor);
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
                               : new Attachment[0]);
  }

  public static class FoldingMap extends MultiMap<PsiElement, FoldingDescriptor>{
    public FoldingMap() {
    }

    public FoldingMap(FoldingMap map) {
      super(map);
    }
    
    @NotNull
    @Override
    protected Map<PsiElement, Collection<FoldingDescriptor>> createMap() {
      return new TreeMap<PsiElement, Collection<FoldingDescriptor>>(COMPARE_BY_OFFSET_REVERSED);
    }

    @NotNull
    @Override
    protected Collection<FoldingDescriptor> createCollection() {
      return new ArrayList<FoldingDescriptor>(1);
    }
  }
}
