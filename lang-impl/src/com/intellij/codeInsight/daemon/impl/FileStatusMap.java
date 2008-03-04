
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
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
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

    PsiElement dirtyScope = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(editor.getProject())).getFileStatusMap().getFileDirtyScope(document, part);
    if (dirtyScope == null || !dirtyScope.isValid()) {
      return null;
    }
    PsiFile file = dirtyScope.getContainingFile();
    if (file.getTextLength() != document.getTextLength()) {
      LOG.error("Length wrong! dirtyScope:" + dirtyScope,
                "file length:" + file.getTextLength(),
                "document length:" + document.getTextLength(),
                "file stamp:" + file.getModificationStamp(),
                "document stamp:" + document.getModificationStamp()
                );
    }
    return dirtyScope.getTextRange();
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
    private PsiElement dirtyScope; //Q: use WeakReference?
    private PsiElement localInspectionsDirtyScope;
    public boolean defensivelyMarked; // file marked dirty without knowlesdge of specific dirty region. Subsequent markScopeDirty can refine dirty scope, not extend it
    private boolean wolfPassFinfished;
    private PsiElement externalDirtyScope;
    private TIntObjectHashMap<PsiElement> customPassDirtyScopes = new TIntObjectHashMap<PsiElement>();
    private boolean errorFound;

    private FileStatus(PsiElement dirtyScope) {
      this.dirtyScope = dirtyScope;
      localInspectionsDirtyScope = dirtyScope;
      externalDirtyScope = dirtyScope;
      TextEditorHighlightingPassRegistrarImpl registrar = (TextEditorHighlightingPassRegistrarImpl) TextEditorHighlightingPassRegistrar.getInstance(dirtyScope.getProject());
      for(DirtyScopeTrackingHighlightingPassFactory factory: registrar.getDirtyScopeTrackingFactories()) {
        customPassDirtyScopes.put(factory.getPassId(), dirtyScope);
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
      if (LOG.isDebugEnabled()) {
        LOG.debug("********************************* Mark file uptodate "+file.getName());
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
   * @return null for processed file, whole file for untouched or entirely dirty file, PsiElement(usually code block) for dirty region (optimization)
   */
  @Nullable
  public PsiElement getFileDirtyScope(@NotNull Document document, int passId) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        return PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      }
      if (status.defensivelyMarked) {
        status.dirtyScope = status.localInspectionsDirtyScope = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
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
          LOG.assertTrue(false);
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

  public void markFileScopeDirty(@NotNull Document document, @NotNull PsiElement scope) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("********************************* Mark dirty: "+scope);
    }
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      if (status.defensivelyMarked) {
        status.defensivelyMarked = false;
      }
      status.dirtyScope = combineScopes(status.dirtyScope, scope, document, myProject);
      status.localInspectionsDirtyScope = combineScopes(status.localInspectionsDirtyScope, scope, document, myProject);
      status.externalDirtyScope = combineScopes(status.externalDirtyScope, scope, document, myProject);
    }
  }

  @Nullable
  private static PsiElement combineScopes(PsiElement scope1, PsiElement scope2, Document document, Project project) {
    if (scope1 == null) return scope2;
    if (scope2 == null) return scope1;
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (!scope1.isValid() || !scope2.isValid()) return documentManager.getPsiFile(document);
    final PsiElement commonParent = PsiTreeUtil.findCommonParent(scope1, scope2);
    return commonParent == null || commonParent instanceof PsiDirectory ? documentManager.getPsiFile(document) : commonParent;
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
