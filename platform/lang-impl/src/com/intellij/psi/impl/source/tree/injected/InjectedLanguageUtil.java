/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.injected.editor.*;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiParameterizedCachedValue;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
 */
public class InjectedLanguageUtil {
  static final Key<List<Trinity<IElementType, SmartPsiElementPointer<PsiLanguageInjectionHost>, TextRange>>> HIGHLIGHT_TOKENS = Key.create("HIGHLIGHT_TOKENS");
  public static Key<Boolean> FRANKENSTEIN_INJECTION = Key.create("FRANKENSTEIN_INJECTION"); // meaning: injected file text is probably incorrect

  public static void forceInjectionOnElement(@NotNull PsiElement host) {
    enumerate(host, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      @Override
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
      }
    });
  }

  @NotNull
  static PsiElement loadTree(@NotNull PsiElement host, @NotNull PsiFile containingFile) {
    if (containingFile instanceof DummyHolder) {
      PsiElement context = containingFile.getContext();
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

  public static List<Trinity<IElementType, SmartPsiElementPointer<PsiLanguageInjectionHost>, TextRange>> getHighlightTokens(@NotNull PsiFile file) {
    return file.getUserData(HIGHLIGHT_TOKENS);
  }

  public static Place getShreds(@NotNull PsiFile injectedFile) {
    FileViewProvider viewProvider = injectedFile.getViewProvider();
    return getShreds(viewProvider);
  }

  public static Place getShreds(@NotNull FileViewProvider viewProvider) {
    if (!(viewProvider instanceof InjectedFileViewProvider)) return null;
    InjectedFileViewProvider myFileViewProvider = (InjectedFileViewProvider)viewProvider;
    return ((DocumentWindowImpl)myFileViewProvider.getDocument()).getShreds();
  }

  public static void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    PsiFile containingFile = host.getContainingFile();
    enumerate(host, containingFile, true, visitor);
  }

  public static void enumerate(@NotNull PsiElement host,
                               @NotNull PsiFile containingFile,
                               boolean probeUp,
                               @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    //do not inject into nonphysical files except during completion
    if (!containingFile.isPhysical() && containingFile.getOriginalFile() == containingFile) {
      final PsiElement context = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
      if (context == null) return;

      final PsiFile file = context.getContainingFile();
      if (file == null || !file.isPhysical() && file.getOriginalFile() == file) return;
    }

    if (containingFile.getViewProvider() instanceof InjectedFileViewProvider) return; // no injection inside injection

    PsiElement inTree = loadTree(host, containingFile);
    if (inTree != host) {
      host = inTree;
      containingFile = host.getContainingFile();
    }

    MultiHostRegistrarImpl registrar = probeElementsUp(host, containingFile, probeUp);
    if (registrar == null) {
      return;
    }
    List<Pair<Place, PsiFile>> places = registrar.getResult();
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
  public static Editor getInjectedEditorForInjectedFile(@NotNull Editor hostEditor, @Nullable final PsiFile injectedFile) {
    if (injectedFile == null || hostEditor instanceof EditorWindow || hostEditor.isDisposed()) return hostEditor;
    Project project = hostEditor.getProject();
    if (project == null) project = injectedFile.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(injectedFile);
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

  @Nullable
  public static PsiFile findInjectedPsiNoCommit(@NotNull PsiFile host, int offset) {
    PsiElement injected = findInjectedElementNoCommit(host, offset);
    return injected == null ? null : injected.getContainingFile();
  }

  // consider injected elements
  public static PsiElement findElementAtNoCommit(@NotNull PsiFile file, int offset) {
    FileViewProvider viewProvider = file.getViewProvider();
    Trinity<PsiElement, PsiElement, Language> result = null;
    if (!(viewProvider instanceof InjectedFileViewProvider)) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
      result = tryOffset(file, offset, documentManager);
      PsiElement injected = result.first;
      if (injected != null) {
        return injected;
      }
    }
    Language baseLanguage = viewProvider.getBaseLanguage();
    if (result != null && baseLanguage == result.third) {
      return result.second; // already queried
    }
    return viewProvider.findElementAt(offset, baseLanguage);
  }

  private static final InjectedPsiCachedValueProvider INJECTED_PSI_PROVIDER = new InjectedPsiCachedValueProvider();
  private static final Key<ParameterizedCachedValue<MultiHostRegistrarImpl, PsiElement>> INJECTED_PSI = Key.create("INJECTED_PSI");

  private static MultiHostRegistrarImpl probeElementsUp(@NotNull PsiElement element, @NotNull PsiFile hostPsiFile, boolean probeUp) {
    PsiManager psiManager = hostPsiFile.getManager();
    final Project project = psiManager.getProject();
    InjectedLanguageManagerImpl injectedManager = InjectedLanguageManagerImpl.getInstanceImpl(project);
    if (injectedManager == null) {
      return null; //for tests
    }
    MultiHostRegistrarImpl registrar = null;
    PsiElement current = element;
    nextParent:
    while (current != null && current != hostPsiFile) {
      ProgressManager.checkCanceled();
      if ("EL".equals(current.getLanguage().getID())) break;
      ParameterizedCachedValue<MultiHostRegistrarImpl, PsiElement> data = current.getUserData(INJECTED_PSI);
      if (data == null) {
        registrar = InjectedPsiCachedValueProvider.doCompute(current, injectedManager, project, hostPsiFile);
      }
      else {
        registrar = data.getValue(current);
      }

      current = current.getParent(); // cache no injection for current

      if (registrar != null) {
        List<Pair<Place, PsiFile>> places = registrar.getResult();
        // check that injections found intersect with queried element
        TextRange elementRange = element.getTextRange();
        for (Pair<Place, PsiFile> pair : places) {
          Place place = pair.first;
          for (PsiLanguageInjectionHost.Shred shred : place) {
            if (shred.getHost().getTextRange().intersects(elementRange)) {
              if (place.isValid()) break nextParent;
            }
          }
        }
      }
      if (!probeUp) {
        break;
      }
    }

    if (probeUp) {
      // cache only if we walked all parents
      for (PsiElement e = element; e != current && e != null && e != hostPsiFile; e = e.getParent()) {
        ProgressManager.checkCanceled();
        if (registrar == null) {
          e.putUserData(INJECTED_PSI, null);
        }
        else {
          ParameterizedCachedValue<MultiHostRegistrarImpl, PsiElement> cachedValue =
            CachedValuesManager.getManager(project).createParameterizedCachedValue(INJECTED_PSI_PROVIDER, false);

          CachedValueProvider.Result<MultiHostRegistrarImpl> result = CachedValueProvider.Result.create(registrar, PsiModificationTracker.MODIFICATION_COUNT, registrar);
          ((PsiParameterizedCachedValue<MultiHostRegistrarImpl, PsiElement>)cachedValue).setValue(result);

          e.putUserData(INJECTED_PSI, cachedValue);
        }
      }
    }
    return registrar;
  }

  public static PsiElement findInjectedElementNoCommit(@NotNull PsiFile hostFile, final int offset) {
    if (hostFile instanceof PsiCompiledElement) return null;
    Project project = hostFile.getProject();
    if (InjectedLanguageManager.getInstance(project).isInjectedFragment(hostFile)) return null;
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Trinity<PsiElement, PsiElement, Language> result = tryOffset(hostFile, offset, documentManager);
    PsiElement injected = result.first;
    return injected;
  }

  // returns (injected psi, leaf element at the offset, language of the leaf element)
  // since findElementAt() is expensive, we trying to reuse its result
  @NotNull
  private static Trinity<PsiElement,PsiElement,Language> tryOffset(@NotNull PsiFile hostFile, final int offset, @NotNull PsiDocumentManager documentManager) {
    FileViewProvider provider = hostFile.getViewProvider();
    Language leafLanguage = null;
    PsiElement leafElement = null;
    for (Language language : provider.getLanguages()) {
      PsiElement element = provider.findElementAt(offset, language);
      if (element != null) {
        if (leafLanguage == null) {
          leafLanguage = language;
          leafElement = element;
        }
        PsiElement injected = findInside(element, hostFile, offset, documentManager);
        if (injected != null) return Trinity.create(injected,element, language);
      }
      // maybe we are at the border between two psi elements, then try to find injection at the end of the left element
      if (offset != 0 && (element == null || element.getTextRange().getStartOffset() == offset)) {
        PsiElement leftElement = provider.findElementAt(offset-1, language);
        if (leftElement != null && leftElement.getTextRange().getEndOffset() == offset) {
          PsiElement injected = findInside(leftElement, hostFile, offset, documentManager);
          if (injected != null) return Trinity.create(injected, element, language);
        }
      }
    }

    return Trinity.create(null, leafElement, leafLanguage);
  }

  private static PsiElement findInside(@NotNull PsiElement element, @NotNull PsiFile hostFile, final int hostOffset, @NotNull final PsiDocumentManager documentManager) {
    final Ref<PsiElement> out = new Ref<PsiElement>();
    enumerate(element, hostFile, true, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      @Override
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        for (PsiLanguageInjectionHost.Shred place : places) {
          TextRange hostRange = place.getHost().getTextRange();
          if (hostRange.cutOut(place.getRangeInsideHost()).grown(1).contains(hostOffset)) {
            DocumentWindowImpl document = (DocumentWindowImpl)documentManager.getCachedDocument(injectedPsi);
            if (document == null) return;
            int injectedOffset = document.hostToInjected(hostOffset);
            PsiElement injElement = injectedPsi.findElementAt(injectedOffset);
            out.set(injElement == null ? injectedPsi : injElement);
          }
        }
      }
    });
    return out.get();
  }

  private static final Key<List<DocumentWindow>> INJECTED_DOCS_KEY = Key.create("INJECTED_DOCS_KEY");

  @NotNull
  public static List<DocumentWindow> getCachedInjectedDocuments(@NotNull PsiFile hostPsiFile) {
    // modification of cachedInjectedDocuments must be under PsiLock only
    List<DocumentWindow> injected = hostPsiFile.getUserData(INJECTED_DOCS_KEY);
    if (injected == null) {
      injected = ((UserDataHolderEx)hostPsiFile).putUserDataIfAbsent(INJECTED_DOCS_KEY, ContainerUtil.<DocumentWindow>createEmptyCOWList());
    }
    return injected;
  }
  public static void clearCachedInjectedFragmentsForFile(@NotNull PsiFile file) {
    file.putUserData(INJECTED_DOCS_KEY, null);
  }

  public static void clearCaches(@NotNull PsiFile injected, @NotNull DocumentWindowImpl documentWindow) {
    VirtualFileWindowImpl virtualFile = (VirtualFileWindowImpl)injected.getVirtualFile();
    PsiManagerEx psiManagerEx = (PsiManagerEx)injected.getManager();
    if (psiManagerEx.getProject().isDisposed()) return;
    psiManagerEx.getFileManager().setViewProvider(virtualFile, null);
    PsiElement context = InjectedLanguageManager.getInstance(injected.getProject()).getInjectionHost(injected);
    PsiFile hostFile;
    if (context != null) {
      hostFile = context.getContainingFile();
    }
    else {
      VirtualFile delegate = virtualFile.getDelegate();
      hostFile = delegate.isValid() ? psiManagerEx.findFile(delegate) : null;
    }
    if (hostFile != null) {
      // modification of cachedInjectedDocuments must be under PsiLock
      synchronized (PsiLock.LOCK) {
        List<DocumentWindow> cachedInjectedDocuments = getCachedInjectedDocuments(hostFile);
        for (int i = cachedInjectedDocuments.size() - 1; i >= 0; i--) {
          DocumentWindow cachedInjectedDocument = cachedInjectedDocuments.get(i);
          if (cachedInjectedDocument == documentWindow) {
            cachedInjectedDocuments.remove(i);
          }
        }
      }
    }
    //FileDocumentManagerImpl.registerDocument(null, virtualFile);
    //FileDocumentManagerImpl.registerDocument(documentWindow, null);
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
      return EditorWindow.create((DocumentWindowImpl)document, (EditorImpl)editor, file);
    }
    return editor;
  }

  public static PsiFile getTopLevelFile(@NotNull PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;
    Document document = PsiDocumentManager.getInstance(element.getProject()).getCachedDocument(containingFile);
    if (document instanceof DocumentWindow) {
      PsiElement host = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
      if (host != null) containingFile = host.getContainingFile();
    }
    return containingFile;
  }
  @NotNull
  public static Editor getTopLevelEditor(@NotNull Editor editor) {
    return editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
  }
  public static boolean isInInjectedLanguagePrefixSuffix(@NotNull final PsiElement element) {
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

  public static boolean isSelectionIsAboutToOverflowInjectedFragment(@NotNull EditorWindow injectedEditor) {
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
    if (!host.isPhysical()) return false;
    final Ref<Boolean> result = Ref.create(false);
    enumerate(host, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      @Override
      public void visit(@NotNull final PsiFile injectedPsi, @NotNull final List<PsiLanguageInjectionHost.Shred> places) {
        result.set(true);
      }
    });
    return result.get().booleanValue();
  }

  public static String getUnescapedText(PsiFile file, @Nullable final PsiElement startElement, @Nullable final PsiElement endElement) {
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
    if (manager.getInjectionHost(file) == null) {
      return file.getText().substring(startElement == null? 0 : startElement.getTextRange().getStartOffset(),
                                      endElement == null? file.getTextLength() : endElement.getTextRange().getStartOffset());
    }
    final StringBuilder sb = new StringBuilder();
    file.accept(new PsiRecursiveElementWalkingVisitor() {

      Boolean myState = startElement == null? Boolean.TRUE : null;

      @Override
      public void visitElement(PsiElement element) {
        if (element == startElement) myState = Boolean.TRUE;
        if (element == endElement) myState = Boolean.FALSE;
        if (Boolean.FALSE == myState) return;
        if (Boolean.TRUE == myState && element.getFirstChild() == null) {
          sb.append(manager.getUnescapedText(element));
        }
        else {
          super.visitElement(element);
        }
      }
    });
    return sb.toString();
  }
}
