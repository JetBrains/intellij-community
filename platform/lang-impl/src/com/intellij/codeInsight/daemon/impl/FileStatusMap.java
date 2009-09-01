
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.WeakHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
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
  public static TextRange getDirtyTextRange(@NotNull Editor editor, int passId) {
    Document document = editor.getDocument();
    TextRange documentRange = TextRange.from(0, document.getTextLength());

    FileStatusMap me = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(editor.getProject())).getFileStatusMap();
    TextRange dirtyScope = me.getFileDirtyScope(document, passId);
    if (dirtyScope == null || !documentRange.intersects(dirtyScope)) {
      return null;
    }
    return documentRange.intersection(dirtyScope);
  }

  public void setErrorFoundFlag(@NotNull Document document, boolean errorFound) {
    //GHP has found error. Flag is used by ExternalToolPass to decide whether to run or not
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        if (!errorFound) return;
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        assert file != null : document;
        status = new FileStatus(file,document);
        myDocumentToStatusMap.put(document, status);
      }
      status.errorFound = errorFound;
    }
  }

  public boolean wasErrorFound(@NotNull Document document) {
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && status.errorFound;
    }
  }

  private static class FileStatus {
    public boolean defensivelyMarked; // file marked dirty without knowledge of specific dirty region. Subsequent markScopeDirty can refine dirty scope, not extend it
    private boolean wolfPassFinfished;
    private final TIntObjectHashMap<RangeMarker> dirtyScopes = new TIntObjectHashMap<RangeMarker>();
    private boolean errorFound;

    private FileStatus(@NotNull PsiFile file, @NotNull Document document) {
      markWholeFile(file, document, file.getProject());
    }

    private void markWholeFile(PsiFile file, Document document, Project project) {
      dirtyScopes.put(Pass.UPDATE_ALL, createWholeFileMarker(file, document));
      dirtyScopes.put(Pass.EXTERNAL_TOOLS, createWholeFileMarker(file, document));
      dirtyScopes.put(Pass.LOCAL_INSPECTIONS, createWholeFileMarker(file, document));
      TextEditorHighlightingPassRegistrarImpl registrar = (TextEditorHighlightingPassRegistrarImpl) TextEditorHighlightingPassRegistrar.getInstance(project);
      for(DirtyScopeTrackingHighlightingPassFactory factory: registrar.getDirtyScopeTrackingFactories()) {
        dirtyScopes.put(factory.getPassId(), createWholeFileMarker(file, document));
      }
    }

    private static RangeMarker createWholeFileMarker(PsiFile file, Document document) {
      int length = file == null ? -1 : Math.min(file.getTextLength(), document.getTextLength());
      return length == -1 ? null : document.createRangeMarker(0, length);
    }

    public boolean allDirtyScopesAreNull() {
      for (Object o : dirtyScopes.getValues()) {
        if (o != null) return false;
      }
      return true;
    }

    public void combineScopesWith(final TextRange scope, final int fileLength, final Document document) {
      dirtyScopes.forEachEntry(new TIntObjectProcedure<RangeMarker>() {
        public boolean execute(int id, RangeMarker oldScope) {
          RangeMarker newScope = combineScopes(oldScope, scope, fileLength, document);
          if (newScope != oldScope) {
            dirtyScopes.put(id, newScope);
          }
          return true;
        }
      });
    }
  }

  public void markAllFilesDirty() {
    LOG.debug("********************************* Mark all dirty");
    synchronized(myDocumentToStatusMap){
      myDocumentToStatusMap.clear();
    }
    myClearModificationCount.incrementAndGet();
  }

  public void markFileUpToDate(@NotNull Document document, @NotNull PsiFile file, int passId) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        status = new FileStatus(file,document);
        myDocumentToStatusMap.put(document, status);
      }
      status.defensivelyMarked=false;
      if (passId == Pass.WOLF) {
        status.wolfPassFinfished = true;
      }
      else if (status.dirtyScopes.containsKey(passId)) {
        RangeMarker marker = status.dirtyScopes.get(passId);
        if (marker != null) {
          ((DocumentEx)document).removeRangeMarker((RangeMarkerEx)marker);
          status.dirtyScopes.put(passId, null);
        }
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
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      if (CollectHighlightsUtil.isOutOfSourceRootJavaFile(file)) return null;
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        return file == null ? null : file.getTextRange();
      }
      if (status.defensivelyMarked) {
        //PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        status.markWholeFile(file, document, myProject);
        status.defensivelyMarked = false;
      }
      LOG.assertTrue(status.dirtyScopes.containsKey(passId), "Unknown pass " + passId);
      RangeMarker marker = status.dirtyScopes.get(passId);
      return marker == null ? null : marker.isValid() ? new TextRange(marker.getStartOffset(), marker.getEndOffset()) : new TextRange(0, document.getTextLength());
    }
  }
  
  public void markFileScopeDirty(@NotNull Document document, int passId) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        return;
      }
      if (passId == Pass.WOLF) {
        status.wolfPassFinfished = false;
      }
      else {
        LOG.assertTrue(status.dirtyScopes.containsKey(passId));
        RangeMarker marker = status.dirtyScopes.get(passId);
        if (marker != null) {
          ((DocumentEx)document).removeRangeMarker((RangeMarkerEx)marker);
        }
        marker = document.createRangeMarker(0, document.getTextLength());
        status.dirtyScopes.put(passId, marker);
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

  public void markFileScopeDirty(@NotNull Document document, @NotNull TextRange scope, int fileLength) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("********************************* Mark dirty: "+scope);
    }
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      if (status.defensivelyMarked) {
        status.defensivelyMarked = false;
      }
      status.combineScopesWith(scope, fileLength,document);
    }
  }

  private static RangeMarker combineScopes(RangeMarker old, TextRange scope, int textLength, @NotNull Document document) {
    if (scope == null) return old;
    if (old == null) {
      return document.createRangeMarker(scope);
    }
    TextRange oldRange = !old.isValid() || scope.getEndOffset() >= textLength ? new TextRange(0, textLength) : new TextRange(old.getStartOffset(), old.getEndOffset());
    TextRange union = scope.union(oldRange);
    if (old.isValid() && union.equals(oldRange)) {
      return old;
    }
    else {
      ((DocumentEx)document).removeRangeMarker((RangeMarkerEx)old);
      return document.createRangeMarker(union);
    }
  }

  public boolean allDirtyScopesAreNull(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      if (CollectHighlightsUtil.isOutOfSourceRootJavaFile(file)) return true;

      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && !status.defensivelyMarked && status.wolfPassFinfished && status.allDirtyScopesAreNull();
    }
  }
}
