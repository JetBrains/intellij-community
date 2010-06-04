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

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiParameterizedCachedValue;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
 */
public class InjectedLanguageUtil {
  static final Key<List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>>> HIGHLIGHT_TOKENS = Key.create("HIGHLIGHT_TOKENS");

  public static void forceInjectionOnElement(@NotNull PsiElement host) {
    enumerate(host, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
      }
    });
  }

  private static PsiElement loadTree(PsiElement host) {
    PsiFile file = host.getContainingFile();
    if (file instanceof DummyHolder) {
      PsiElement context = file.getContext();
      if (context != null) {
        PsiFile topFile = context.getContainingFile();
        topFile.getNode();  //load tree
        TextRange textRange = host.getTextRange().shiftRight(context.getTextRange().getStartOffset());

        PsiElement inLoadedTree =
          PsiTreeUtil.findElementOfClassAtRange(topFile, textRange.getStartOffset(), textRange.getEndOffset(), host.getClass());
        if (inLoadedTree != null) {
          host = inLoadedTree;
        }
      }
    }
    return host;
  }

  @Nullable
  public static List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull final PsiElement host) {
    final PsiElement inTree = loadTree(host);
    final List<Pair<PsiElement, TextRange>> result = new SmartList<Pair<PsiElement, TextRange>>();
    enumerate(inTree, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        for (PsiLanguageInjectionHost.Shred place : places) {
          if (place.host == inTree) {
            result.add(new Pair<PsiElement, TextRange>(injectedPsi, place.getRangeInsideHost()));
          }
        }
      }
    });
    return result.isEmpty() ? null : result;
  }

  public static TextRange toTextRange(RangeMarker marker) {
    return new ProperTextRange(marker.getStartOffset(), marker.getEndOffset());
  }

  public static List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> getHighlightTokens(PsiFile file) {
    return file.getUserData(HIGHLIGHT_TOKENS);
  }

  public static Place getShreds(PsiFile injectedFile) {
    FileViewProvider viewProvider = injectedFile.getViewProvider();
    if (!(viewProvider instanceof InjectedFileViewProvider)) return null;
    InjectedFileViewProvider myFileViewProvider = (InjectedFileViewProvider)viewProvider;
    return myFileViewProvider.getShreds();
  }

  public static void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    PsiFile containingFile = host.getContainingFile();
    enumerate(host, containingFile, visitor, true);
  }

  public static void enumerate(@NotNull PsiElement host, @NotNull PsiFile containingFile, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor, boolean probeUp) {
    //do not inject into nonphysical files except during completion
    if (!containingFile.isPhysical() && containingFile.getOriginalFile() == containingFile) {
      final PsiElement context = containingFile.getContext();
      if (context == null) return;

      final PsiFile file = context.getContainingFile();
      if (file == null || !file.isPhysical() && file.getOriginalFile() == file) return;
    }

    PsiElement inTree = loadTree(host);
    if (inTree != host) {
      host = inTree;
      containingFile = host.getContainingFile();
    }

    MultiHostRegistrarImpl registrar = probeElementsUp(host, containingFile, probeUp);
    if (registrar == null) return;
    List<Pair<Place, PsiFile>> places = registrar.result;
    for (Pair<Place, PsiFile> pair : places) {
      PsiFile injectedPsi = pair.second;
      visitor.visit(injectedPsi, pair.first);
    }
  }

  public static Editor getEditorForInjectedLanguageNoCommit(@Nullable Editor editor, @Nullable PsiFile file) {
    if (editor == null || file == null || editor instanceof EditorWindow) return editor;

    int offset = editor.getCaretModel().getOffset();
    return getEditorForInjectedLanguageNoCommit(editor, file, offset);
  }

  public static Editor getEditorForInjectedLanguageNoCommit(@Nullable Editor editor, @Nullable PsiFile file, final int offset) {
    if (editor == null || file == null || editor instanceof EditorWindow) return editor;
    PsiFile injectedFile = findInjectedPsiNoCommit(file, offset);
    return getInjectedEditorForInjectedFile(editor, injectedFile);
  }

  @NotNull
  public static Editor getInjectedEditorForInjectedFile(@NotNull Editor hostEditor, final PsiFile injectedFile) {
    if (injectedFile == null || hostEditor instanceof EditorWindow) return hostEditor;
    Document document = PsiDocumentManager.getInstance(hostEditor.getProject()).getDocument(injectedFile);
    if (!(document instanceof DocumentWindowImpl)) return hostEditor;
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)document;
    SelectionModel selectionModel = hostEditor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      int selstart = selectionModel.getSelectionStart();
      int selend = selectionModel.getSelectionEnd();
      if (!documentWindow.containsRange(selstart, selend)) {
        // selection spreads out the injected editor range
        return hostEditor;
      }
    }
    if (!documentWindow.isValid()) return hostEditor; // since the moment we got hold of injectedFile and this moment call, document may have been dirtied
    return EditorWindow.create(documentWindow, (EditorImpl)hostEditor, injectedFile);
  }

  public static PsiFile findInjectedPsiNoCommit(@NotNull PsiFile host, int offset) {
    PsiElement injected = findInjectedElementNoCommit(host, offset);
    if (injected != null) {
      return injected.getContainingFile();
    }
    return null;
  }

  // consider injected elements
  public static PsiElement findElementAtNoCommit(@NotNull PsiFile file, int offset) {
    if (!InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
      PsiElement injected = findInjectedElementNoCommit(file, offset);
      if (injected != null) {
        return injected;
      }
    }
    //PsiElement at = file.findElementAt(offset);
    FileViewProvider viewProvider = file.getViewProvider();
    return viewProvider.findElementAt(offset, viewProvider.getBaseLanguage());
  }

  private static final InjectedPsiCachedValueProvider INJECTED_PSI_PROVIDER = new InjectedPsiCachedValueProvider();
  private static final Key<ParameterizedCachedValue<MultiHostRegistrarImpl, PsiElement>> INJECTED_PSI_KEY = Key.create("INJECTED_PSI");
  private static MultiHostRegistrarImpl probeElementsUp(@NotNull PsiElement element, @NotNull PsiFile hostPsiFile, boolean probeUp) {
    PsiManager psiManager = hostPsiFile.getManager();
    final Project project = psiManager.getProject();
    InjectedLanguageManagerImpl injectedManager = InjectedLanguageManagerImpl.getInstanceImpl(project);
    if (injectedManager == null) return null; //for tests

    for (PsiElement current = element; current != null && current != hostPsiFile; current = current.getParent()) {
      ProgressManager.checkCanceled();
      if ("EL".equals(current.getLanguage().getID())) break;
      ParameterizedCachedValue<MultiHostRegistrarImpl, PsiElement> data = current.getUserData(INJECTED_PSI_KEY);
      MultiHostRegistrarImpl registrar;
      if (data == null) {
        registrar = InjectedPsiCachedValueProvider.doCompute(current, injectedManager, project, hostPsiFile);
        if (registrar != null) {
          // pollute user data only if there is injected fragment there
          ParameterizedCachedValue<MultiHostRegistrarImpl, PsiElement> cachedValue =
            CachedValuesManager.getManager(psiManager.getProject()).createParameterizedCachedValue(INJECTED_PSI_PROVIDER, false);
          Document hostDocument = hostPsiFile.getViewProvider().getDocument();
          CachedValueProvider.Result<MultiHostRegistrarImpl> result = CachedValueProvider.Result.create(registrar, PsiModificationTracker.MODIFICATION_COUNT, hostDocument);
          ((PsiParameterizedCachedValue<MultiHostRegistrarImpl, PsiElement>)cachedValue).setValue(result);
          current.putUserData(INJECTED_PSI_KEY, cachedValue);
        }
      }
      else {
        registrar = data.getValue(current);
      }
      if (registrar != null) {
        List<Pair<Place, PsiFile>> places = registrar.result;
        // check that injections found intersect with queried element
        TextRange elementRange = element.getTextRange();
        for (Pair<Place, PsiFile> pair : places) {
          Place place = pair.first;
          for (PsiLanguageInjectionHost.Shred shred : place) {
            if (shred.host.getTextRange().intersects(elementRange)) {
              if (place.isValid()) return registrar;
            }
          }
        }
      }
      if (!probeUp) break;
    }
    return null;
  }

  @Nullable
  public static PsiElement findInjectedElementNoCommitWithOffset(@NotNull PsiFile file, final int offset) {
    Project project = file.getProject();
    if (InjectedLanguageManager.getInstance(project).isInjectedFragment(file)) return null;
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

    FileViewProvider provider = file.getViewProvider();
    for (Language language : provider.getLanguages()) {

      PsiElement element = provider.findElementAt(offset, language);
      if (element == null) {
        continue;
      }
      PsiElement injected = findInside(element, file, offset, documentManager);
      if (injected != null) return injected;
    }
    return null;
  }

  public static PsiElement findInjectedElementNoCommit(@NotNull PsiFile hostFile, final int offset) {
    PsiElement inj = findInjectedElementNoCommitWithOffset(hostFile, offset);
    if (inj != null) return inj;
    if (offset != 0) {
      inj = findInjectedElementNoCommitWithOffset(hostFile, offset - 1);
    }
    return inj;
  }

  private static PsiElement findInside(@NotNull PsiElement element, @NotNull PsiFile hostFile, final int hostOffset, @NotNull final PsiDocumentManager documentManager) {
    final Ref<PsiElement> out = new Ref<PsiElement>();
    enumerate(element, hostFile, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        for (PsiLanguageInjectionHost.Shred place : places) {
          TextRange hostRange = place.host.getTextRange();
          if (hostRange.cutOut(place.getRangeInsideHost()).grown(1).contains(hostOffset)) {
            DocumentWindowImpl document = (DocumentWindowImpl)documentManager.getCachedDocument(injectedPsi);
            int injectedOffset = document.hostToInjected(hostOffset);
            PsiElement injElement = injectedPsi.findElementAt(injectedOffset);
            out.set(injElement == null ? injectedPsi : injElement);
          }
        }
      }
    }, true);
    return out.get();
  }

  private static final Key<List<DocumentWindow>> INJECTED_DOCS_KEY = Key.create("INJECTED_DOCS_KEY");
  private static final Key<List<RangeMarker>> INJECTED_REGIONS_KEY = Key.create("INJECTED_REGIONS_KEY");
  @NotNull
  public static List<DocumentWindow> getCachedInjectedDocuments(@NotNull PsiFile hostPsiFile) {
    List<DocumentWindow> injected = hostPsiFile.getUserData(INJECTED_DOCS_KEY);
    if (injected == null) {
      injected = ((UserDataHolderEx)hostPsiFile).putUserDataIfAbsent(INJECTED_DOCS_KEY, ContainerUtil.<DocumentWindow>createEmptyCOWList());
    }
    return injected;
  }

  public static void commitAllInjectedDocuments(Document hostDocument, Project project) {
    List<RangeMarker> injected = getCachedInjectedRegions(hostDocument);
    if (injected.isEmpty()) return;

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    PsiFile hostPsiFile = documentManager.getPsiFile(hostDocument);
    assert hostPsiFile != null;
    for (RangeMarker rangeMarker : injected) {
      PsiElement element = rangeMarker.isValid() ? hostPsiFile.findElementAt(rangeMarker.getStartOffset()) : null;
      if (element == null) {
        injected.remove(rangeMarker);
        continue;
      }
      // it is here reparse happens and old file contents replaced
      enumerate(element, hostPsiFile, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          PsiDocumentManagerImpl.checkConsistency(injectedPsi, injectedPsi.getViewProvider().getDocument());
        }
      }, true);
    }
    EditorWindow.disposeInvalidEditors();  // in write action

    PsiDocumentManagerImpl.checkConsistency(hostPsiFile, hostDocument);
  }

  public static void clearCaches(PsiFile injected) {
    VirtualFileWindow virtualFile = (VirtualFileWindow)injected.getVirtualFile();
    PsiManagerEx psiManagerEx = (PsiManagerEx)injected.getManager();
    psiManagerEx.getFileManager().setViewProvider((VirtualFile)virtualFile, null);
  }


  static List<RangeMarker> getCachedInjectedRegions(Document hostDocument) {
    List<RangeMarker> injectedRegions = hostDocument.getUserData(INJECTED_REGIONS_KEY);
    if (injectedRegions == null) {
      injectedRegions = ((UserDataHolderEx)hostDocument).putUserDataIfAbsent(INJECTED_REGIONS_KEY,
                                                                             ContainerUtil.<RangeMarker>createEmptyCOWList());
    }
    return injectedRegions;
  }

  public static Editor openEditorFor(PsiFile file, Project project) {
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
    if (editor == null || editor instanceof EditorWindow) return editor;
    if (document instanceof DocumentWindowImpl) {
      return EditorWindow.create((DocumentWindowImpl)document, (EditorImpl)editor, file);
    }
    return editor;
  }

  public static PsiFile getTopLevelFile(PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    Document document = PsiDocumentManager.getInstance(element.getProject()).getCachedDocument(containingFile);
    if (document instanceof DocumentWindow) {
      PsiElement host = containingFile.getContext();
      if (host != null) containingFile = host.getContainingFile();
    }
    return containingFile;
  }
  public static Editor getTopLevelEditor(Editor editor) {
    return editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
  }
  public static boolean isInInjectedLanguagePrefixSuffix(final PsiElement element) {
    PsiFile injectedFile = element.getContainingFile();
    if (injectedFile == null) return false;
    Project project = injectedFile.getProject();
    InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(project);
    if (!languageManager.isInjectedFragment(injectedFile)) return false;
    TextRange elementRange = element.getTextRange();
    List<TextRange> editables = languageManager.intersectWithAllEditableFragments(injectedFile, elementRange);
    int combinedEdiablesLength = 0;
    for (TextRange editable : editables) {
      combinedEdiablesLength += editable.getLength();
    }

    return combinedEdiablesLength != elementRange.getLength();
  }

  public static boolean isSelectionIsAboutToOverflowInjectedFragment(EditorWindow injectedEditor) {
    int selStart = injectedEditor.getSelectionModel().getSelectionStart();
    int selEnd = injectedEditor.getSelectionModel().getSelectionEnd();

    DocumentWindow document = injectedEditor.getDocument();

    boolean isStartOverflows = selStart == 0;
    if (!isStartOverflows) {
      int hostPrev = document.injectedToHost(selStart - 1);
      isStartOverflows = document.hostToInjected(hostPrev) == selStart;
    }

    boolean isEndOverflows = selEnd == document.getTextLength();
    if (!isEndOverflows) {
      int hostNext = document.injectedToHost(selEnd + 1);
      isEndOverflows = document.hostToInjected(hostNext) == selEnd;
    }

    return isStartOverflows && isEndOverflows;
  }

  public static boolean hasInjections(@NotNull PsiLanguageInjectionHost host) {
    final Ref<Boolean> result = Ref.create(false);
    host.processInjectedPsi(new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull final PsiFile injectedPsi, @NotNull final List<PsiLanguageInjectionHost.Shred> places) {
        result.set(true);
      }
    });
    return result.get().booleanValue();
  }
}
