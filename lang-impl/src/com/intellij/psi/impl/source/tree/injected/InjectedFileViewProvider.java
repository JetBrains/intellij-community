package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.injected.editor.VirtualFileWindowImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
*/
class InjectedFileViewProvider extends SingleRootFileViewProvider {
  private List<PsiLanguageInjectionHost.Shred> myShreds;
  private Project myProject;
  private final Object myLock = new Object();

  InjectedFileViewProvider(@NotNull PsiManager psiManager, @NotNull VirtualFileWindow virtualFile, List<PsiLanguageInjectionHost.Shred> shreds) {
    super(psiManager, (VirtualFile)virtualFile);
    synchronized (myLock) {
      myShreds = new ArrayList<PsiLanguageInjectionHost.Shred>(shreds);
      myProject = myShreds.get(0).host.getProject();
    }
  }

  public void rootChanged(PsiFile psiFile) {
    super.rootChanged(psiFile);
    List<PsiLanguageInjectionHost.Shred> shreds;
    Project project;
    synchronized (myLock) {
      shreds = myShreds;
      project = myProject;
    }
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)documentManager.getDocument(psiFile);
    assert documentWindow.getHostRanges().length == shreds.size();
    String[] changes = documentWindow.calculateMinEditSequence(psiFile.getText());
    //RangeMarker[] hostRanges = documentWindow.getHostRanges();
    assert changes.length == shreds.size();
    for (int i = 0; i < changes.length; i++) {
      String change = changes[i];
      if (change != null) {
        PsiLanguageInjectionHost.Shred shred = shreds.get(i);
        PsiLanguageInjectionHost host = shred.host;
        //RangeMarker hostRange = hostRanges[i];
        //TextRange hostTextRange = host.getTextRange();
        TextRange rangeInsideHost = shred.getRangeInsideHost();
        //TextRange rangeInsideHost = hostTextRange.intersection(toTextRange(hostRange)).shiftRight(-hostTextRange.getStartOffset());
        String newHostText = StringUtil.replaceSubstring(host.getText(), rangeInsideHost, change);
        host.fixText(newHostText);
      }
    }
  }

  public FileViewProvider clone() {

    /*
    final FileViewProvider copy = super.clone();
    final PsiFile psi = copy.getPsi(getBaseLanguage());
    psi.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, getPsi(getBaseLanguage()).getUserData(FileContextUtil.INJECTED_IN_ELEMENT));
    return copy;
    */

    final DocumentWindow oldDocumentWindow = ((VirtualFileWindow)getVirtualFile()).getDocumentWindow();
    Document hostDocument = oldDocumentWindow.getDelegate();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getManager().getProject());
    PsiFile hostFile = documentManager.getPsiFile(hostDocument);
    PsiFile hostPsiFileCopy = (PsiFile)hostFile.copy();
    RangeMarker firstTextRange = oldDocumentWindow.getHostRanges()[0];
    PsiElement elementCopy = hostPsiFileCopy.findElementAt(firstTextRange.getStartOffset());
    assert elementCopy != null;
    final Ref<FileViewProvider> provider = new Ref<FileViewProvider>();
    InjectedLanguageUtil.enumerate(elementCopy, hostPsiFileCopy, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        Document document = documentManager.getCachedDocument(injectedPsi);
        if (document instanceof DocumentWindowImpl && oldDocumentWindow.areRangesEqual((DocumentWindowImpl)document)) {
          provider.set(injectedPsi.getViewProvider());
        }
      }
    }, true);
    FileViewProvider copy = provider.get();
    return copy;
  }

  @Nullable
  protected PsiFile getPsiInner(Language target) {
    // when FileManager rebuilds file map, all files temporarily become invalid, so this check is doomed
    PsiFile file = super.getPsiInner(target);
    //if (file == null || file.getContext() == null) return null;
    return file;
  }

  void replace(VirtualFileWindowImpl virtualFile, List<PsiLanguageInjectionHost.Shred> shreds) {
    synchronized (myLock) {
      setVirtualFile(virtualFile);
      myShreds = new ArrayList<PsiLanguageInjectionHost.Shred>(shreds);
      myProject = shreds.get(0).host.getProject();
    }
  }

  boolean isValid() {
    synchronized (myLock) {
      return !myProject.isDisposed();
    }
  }

  List<PsiLanguageInjectionHost.Shred> getShreds() {
    synchronized (myLock) {
      return myShreds;
    }
  }
}
