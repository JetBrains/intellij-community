
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.WeakHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FileStatusMap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.FileStatusMap");
  private final Project myProject;
  private final Map<Document,FileStatus> myDocumentToStatusMap = new WeakHashMap<Document, FileStatus>(); // all dirty if absent
  private final AtomicInteger myClearModificationCount = new AtomicInteger();

  public FileStatusMap(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  static TextRange getDirtyTextRange(Editor editor, int part) {
    Document document = editor.getDocument();
    TextRange documentRange = TextRange.from(0, document.getTextLength());

    TextRange dirtyScope = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(editor.getProject())).getFileStatusMap().getFileDirtyScope(document, part);
    if (dirtyScope == null || !documentRange.intersects(dirtyScope)) {
      return null;
    }
    return documentRange.intersection(dirtyScope);
  }

  public void setErrorFoundFlag(Document document, boolean errorFound) {
    //GHP has found error. Flag is used by ExternalToolPass to decide whether to run itself or not
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        if (!errorFound) return;
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        status = new FileStatus(file);
        myDocumentToStatusMap.put(document, status);
      }
      status.errorFound = errorFound;
    }
  }
  
  public boolean wasErrorFound(Document document) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && status.errorFound;
    }
  }

  private static class FileStatus {
    private TextRange dirtyScope;
    private TextRange localInspectionsDirtyScope;
    public boolean defensivelyMarked; // file marked dirty without knowledge of specific dirty region. Subsequent markScopeDirty can refine dirty scope, not extend it
    private boolean wolfPassFinfished;
    private TextRange externalDirtyScope;
    private final TIntObjectHashMap<TextRange> customPassDirtyScopes = new TIntObjectHashMap<TextRange>();
    private boolean errorFound;

    private FileStatus(PsiFile file) {
      TextRange range = file.getTextRange();
      dirtyScope = range;
      localInspectionsDirtyScope = range;
      externalDirtyScope = range;
      TextEditorHighlightingPassRegistrarImpl registrar = (TextEditorHighlightingPassRegistrarImpl) TextEditorHighlightingPassRegistrar.getInstance(file.getProject());
      for(DirtyScopeTrackingHighlightingPassFactory factory: registrar.getDirtyScopeTrackingFactories()) {
        customPassDirtyScopes.put(factory.getPassId(), range);
      }
    }

    public boolean allCustomDirtyScopesAreNull() {
      for (Object o : customPassDirtyScopes.getValues()) {
        if (o != null) return false;
      }
      return true;
    }
  }

  public void markAllFilesDirty() {
    LOG.debug("********************************* Mark all dirty");
    synchronized(myDocumentToStatusMap){
      myDocumentToStatusMap.clear();
    }
    myClearModificationCount.incrementAndGet();
  }

  public void markFileUpToDate(@NotNull Document document, int passId) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      if (status == null){
        status = new FileStatus(file);
        myDocumentToStatusMap.put(document, status);
      }

      status.defensivelyMarked=false;
      switch (passId) {
        case Pass.UPDATE_ALL:
        case Pass.POST_UPDATE_ALL:
          status.dirtyScope = null;
          break;
        case Pass.LOCAL_INSPECTIONS:
          status.localInspectionsDirtyScope = null;
          break;
        case WolfPassFactory.PASS_ID:
          status.wolfPassFinfished = true;
          break;
        case Pass.EXTERNAL_TOOLS:
          status.externalDirtyScope = null;
          break;
        default:
          if (status.customPassDirtyScopes.containsKey(passId)) {
            status.customPassDirtyScopes.put(passId, null);
          }
          break;
      }
    }
  }

  /**
   * @param document
   * @param passId
   * @return null for processed file, whole file for untouched or entirely dirty file, range(usually code block) for dirty region (optimization)
   */
  @Nullable
  public TextRange getFileDirtyScope(@NotNull Document document, int passId) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        return file == null ? null : file.getTextRange();
      }
      if (status.defensivelyMarked) {
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        status.dirtyScope = status.localInspectionsDirtyScope = file == null ? null : file.getTextRange();
        status.defensivelyMarked = false;
      }
      switch (passId) {
        case Pass.UPDATE_ALL:
          return status.dirtyScope;
        case Pass.LOCAL_INSPECTIONS:
          return status.localInspectionsDirtyScope;
        case Pass.EXTERNAL_TOOLS:
          return status.externalDirtyScope;
        default:
          if (status.customPassDirtyScopes.containsKey(passId)) {
            return status.customPassDirtyScopes.get(passId);
          }
          LOG.error("Unknown pass " + passId);
          return null;
      }
    }
  }

  public void markFileScopeDirtyDefensively(@NotNull PsiFile file) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("********************************* Mark dirty file defensively: "+file.getName());
    }
    // mark whole file dirty in case no subsequent PSI events will come, but file requires rehighlighting nevertheless
    // e.g. in the case of quick typing/backspacing char
    synchronized(myDocumentToStatusMap){
      Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
      if (document == null) return;
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      status.defensivelyMarked = true;
    }
  }

  public void markFileScopeDirty(@NotNull Document document, @NotNull TextRange scope) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("********************************* Mark dirty: "+scope);
    }
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      if (status.defensivelyMarked) {
        status.defensivelyMarked = false;
      }
      status.dirtyScope = combineScopes(status.dirtyScope, scope, document);
      status.localInspectionsDirtyScope = combineScopes(status.localInspectionsDirtyScope, scope, document);
      status.externalDirtyScope = combineScopes(status.externalDirtyScope, scope, document);
    }
  }

  private static TextRange combineScopes(TextRange scope1, TextRange scope2, Document document) {
    if (scope1 == null) return scope2;
    if (scope2 == null) return scope1;
    TextRange documentRange = TextRange.from(0, document.getTextLength());
    if (!documentRange.contains(scope1) || !documentRange.contains(scope2)) return documentRange;
    return scope1.union(scope2);
  }

  public boolean allDirtyScopesAreNull(final Document document) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null
             && !status.defensivelyMarked
             && status.dirtyScope == null
             && status.allCustomDirtyScopesAreNull()
             && status.localInspectionsDirtyScope == null
             && status.externalDirtyScope == null
             && status.wolfPassFinfished
        ;
    }
  }
}
