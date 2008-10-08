package com.intellij.psi.impl.source.tree.injected;
  
import com.intellij.injected.editor.*;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.ParameterizedCachedValueImpl;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author cdr
 */
public class InjectedLanguageUtil {
  private static final Key<ParameterizedCachedValue<Places, PsiElement>> INJECTED_PSI_KEY = Key.create("INJECTED_PSI");
  static final Key<List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>>> HIGHLIGHT_TOKENS = Key.create("HIGHLIGHT_TOKENS");

  public static void forceInjectionOnElement(@NotNull final PsiElement host) {
    enumerate(host, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
      }
    });
  }

  @Nullable
  public static List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull final PsiElement host) {
    final List<Pair<PsiElement, TextRange>> result = new SmartList<Pair<PsiElement, TextRange>>();
    enumerate(host, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        for (PsiLanguageInjectionHost.Shred place : places) {
          if (place.host == host) {
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

  static boolean isInjectedFragment(final PsiFile file) {
    return file.getViewProvider() instanceof InjectedFileViewProvider;
  }
  public static List<PsiLanguageInjectionHost.Shred> getShreds(PsiFile injectedFile) {
    FileViewProvider viewProvider = injectedFile.getViewProvider();
    if (!(viewProvider instanceof InjectedFileViewProvider)) return null;
    InjectedFileViewProvider myFileViewProvider = (InjectedFileViewProvider)viewProvider;
    return myFileViewProvider.getShreds();
  }

  static class Place {
    private final PsiFile myInjectedPsi;
    private final List<PsiLanguageInjectionHost.Shred> myShreds;

    public Place(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> shreds) {
      myShreds = shreds;
      myInjectedPsi = injectedPsi;
    }
  }


  public static void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    PsiFile containingFile = host.getContainingFile();
    enumerate(host, containingFile, visitor, true);
  }

  public static void enumerate(@NotNull PsiElement host, @NotNull PsiFile containingFile, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor, boolean probeUp) {
    //do not inject into nonphysical files except during completion
    if (!containingFile.isPhysical() && containingFile.getOriginalFile() == null) {
      final PsiElement context = containingFile.getContext();
      if (context == null) return;

      final PsiFile file = context.getContainingFile();
      if (file == null || !file.isPhysical() && file.getOriginalFile() == null) return;
    }
    Places places = probeElementsUp(host, containingFile, probeUp);
    if (places == null) return;
    for (Place place : places) {
      PsiFile injectedPsi = place.myInjectedPsi;
      List<PsiLanguageInjectionHost.Shred> pairs = place.myShreds;

      visitor.visit(injectedPsi, pairs);
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
  public static Editor getInjectedEditorForInjectedFile(@NotNull Editor editor, final PsiFile injectedFile) {
    if (injectedFile == null || editor instanceof EditorWindow) return editor;
    Document document = PsiDocumentManager.getInstance(editor.getProject()).getDocument(injectedFile);
    if (!(document instanceof DocumentWindowImpl)) return editor;
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)document;
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      int selstart = selectionModel.getSelectionStart();
      int selend = selectionModel.getSelectionEnd();
      if (!documentWindow.containsRange(selstart, selend)) {
        // selection spreads out the injected editor range
        return editor;
      }
    }
    return EditorWindow.create(documentWindow, (EditorImpl)editor, injectedFile);
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
    if (!isInjectedFragment(file)) {
      PsiElement injected = findInjectedElementNoCommit(file, offset);
      if (injected != null) {
        return injected;
      }
    }
    //PsiElement at = file.findElementAt(offset);
    FileViewProvider viewProvider = file.getViewProvider();
    return viewProvider.findElementAt(offset, viewProvider.getBaseLanguage());
  }

  private static final InjectedPsiProvider INJECTED_PSI_PROVIDER = new InjectedPsiProvider();
  private static Places probeElementsUp(@NotNull PsiElement element, @NotNull PsiFile hostPsiFile, boolean probeUp) {
    PsiManager psiManager = hostPsiFile.getManager();
    final Project project = psiManager.getProject();
    InjectedLanguageManagerImpl injectedManager = InjectedLanguageManagerImpl.getInstanceImpl(project);
    if (injectedManager == null) return null; //for tests

    for (PsiElement current = element; current != null && current != hostPsiFile; current = current.getParent()) {
      if ("EL".equals(current.getLanguage().getID())) break;
      ParameterizedCachedValue<Places,PsiElement> data = current.getUserData(INJECTED_PSI_KEY);
      Places places;
      if (data == null) {
        places = InjectedPsiProvider.doCompute(current, injectedManager, project, hostPsiFile);
        if (places != null) {
          ParameterizedCachedValue<Places, PsiElement> cachedValue =
              psiManager.getCachedValuesManager().createParameterizedCachedValue(INJECTED_PSI_PROVIDER, false);
          Document hostDocument = hostPsiFile.getViewProvider().getDocument();
          CachedValueProvider.Result<Places> result =
              new CachedValueProvider.Result<Places>(places, PsiModificationTracker.MODIFICATION_COUNT, hostDocument);
          ((ParameterizedCachedValueImpl<Places, PsiElement>)cachedValue).setValue(result);
          current.putUserData(INJECTED_PSI_KEY, cachedValue);
        }
      }
      else {
        places = data.getValue(current);
      }
      if (places != null) {
        // check that injections found intersect with queried element
        TextRange elementRange = element.getTextRange();
        for (Place place : places) {
          for (PsiLanguageInjectionHost.Shred shred : place.myShreds) {
            if (shred.host.getTextRange().intersects(elementRange)) {
              return places;
            }
          }
        }
      }
      if (!probeUp) break;
    }
    return null;
  }

  public static PsiElement findInjectedElementNoCommitWithOffset(@NotNull PsiFile file, final int offset) {
    if (isInjectedFragment(file)) return null;
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());

    PsiElement element = file.getViewProvider().findElementAt(offset, file.getLanguage());
    return element == null ? null : findInside(element, file, offset, documentManager);
  }

  public static PsiElement findInjectedElementNoCommit(@NotNull PsiFile file, final int offset) {
    PsiElement inj = findInjectedElementNoCommitWithOffset(file, offset);
    if (inj != null) return inj;
    if (offset != 0) {
      inj = findInjectedElementNoCommitWithOffset(file, offset - 1);
    }
    return inj;
  }

  private static PsiElement findInside(@NotNull PsiElement element, @NotNull PsiFile file, final int offset, @NotNull final PsiDocumentManager documentManager) {
    final Ref<PsiElement> out = new Ref<PsiElement>();
    enumerate(element, file, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        for (PsiLanguageInjectionHost.Shred place : places) {
          TextRange hostRange = place.host.getTextRange();
          if (hostRange.cutOut(place.getRangeInsideHost()).grown(1).contains(offset)) {
            DocumentWindowImpl document = (DocumentWindowImpl)documentManager.getCachedDocument(injectedPsi);
            int injectedOffset = document.hostToInjected(offset);
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
      injected = ((UserDataHolderEx)hostPsiFile).putUserDataIfAbsent(INJECTED_DOCS_KEY, new CopyOnWriteArrayList<DocumentWindow>());
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
    PsiDocumentManagerImpl.checkConsistency(hostPsiFile, hostDocument);
  }

  public static void clearCaches(PsiFile injected) {
    VirtualFileWindow virtualFile = (VirtualFileWindow)injected.getVirtualFile();
    PsiManagerEx psiManagerEx = (PsiManagerEx)injected.getManager();
    psiManagerEx.getFileManager().setViewProvider((VirtualFile)virtualFile, null);
    Project project = psiManagerEx.getProject();
    if (!project.isDisposed()) {
      InjectedLanguageManagerImpl.getInstanceImpl(project).clearCaches(virtualFile);
    }
  }


  static List<RangeMarker> getCachedInjectedRegions(Document hostDocument) {
    List<RangeMarker> injectedRegions = hostDocument.getUserData(INJECTED_REGIONS_KEY);
    if (injectedRegions == null) {
      injectedRegions = ((UserDataHolderEx)hostDocument).putUserDataIfAbsent(INJECTED_REGIONS_KEY, new CopyOnWriteArrayList<RangeMarker>());
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
  public static boolean isInInjectedLanguagePrefixSuffix(final PsiElement element) {
    PsiFile injectedFile = element.getContainingFile();
    if (injectedFile == null || !isInjectedFragment(injectedFile)) return false;
    TextRange elementRange = element.getTextRange();
    List<TextRange> editables = InjectedLanguageManager.getInstance(injectedFile.getProject())
        .intersectWithAllEditableFragments(injectedFile, elementRange);
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
}
