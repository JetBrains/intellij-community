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

package com.intellij.psi.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

//todo listen & notifyListeners readonly events?

public class PsiDocumentManagerImpl extends PsiDocumentManager implements ProjectComponent, DocumentListener, SettingsSavingComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiDocumentManagerImpl");
  private static final Key<PsiFile> HARD_REF_TO_PSI = new Key<PsiFile>("HARD_REFERENCE_TO_PSI");
  private static final Key<Boolean> KEY_COMMITING = new Key<Boolean>("Commiting");
  private static final Key<List<Runnable>> ACTION_AFTER_COMMIT = Key.create("ACTION_AFTER_COMMIT");

  private final Project myProject;
  private final PsiManager myPsiManager;
  private final Key<TextBlock> KEY_TEXT_BLOCK = Key.create("KEY_TEXT_BLOCK");
  private final Set<Document> myUncommittedDocuments = Collections.synchronizedSet(new HashSet<Document>());

  private final BlockSupportImpl myBlockSupport;
  private volatile boolean myIsCommitInProgress;
  private final PsiToDocumentSynchronizer mySynchronizer;

  private final List<Listener> myListeners = new ArrayList<Listener>();
  private Listener[] myCachedListeners = null; //guarded by mylisteners
  private final SmartPointerManagerImpl mySmartPointerManager;

  public PsiDocumentManagerImpl(Project project,
                                PsiManager psiManager,
                                SmartPointerManager smartPointerManager,
                                BlockSupport blockSupport,
                                EditorFactory editorFactory,
                                MessageBus bus) {
    myProject = project;
    myPsiManager = psiManager;
    mySmartPointerManager = (SmartPointerManagerImpl)smartPointerManager;
    myBlockSupport = (BlockSupportImpl)blockSupport;
    mySynchronizer = new PsiToDocumentSynchronizer(this, bus);
    myPsiManager.addPsiTreeChangeListener(mySynchronizer);
    editorFactory.getEventMulticaster().addDocumentListener(this, myProject);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "PsiDocumentManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  @Nullable
  public PsiFile getPsiFile(@NotNull Document document) {
    final PsiFile userData = document.getUserData(HARD_REF_TO_PSI);
    if(userData != null) return userData;

    PsiFile psiFile = getCachedPsiFile(document);
    if (psiFile == null){
      final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      if (virtualFile == null || !virtualFile.isValid()) return null;
      psiFile = getPsiFile(virtualFile);
      if (psiFile == null) return null;

      //psiFile.setModificationStamp(document.getModificationStamp());
      fireFileCreated(document, psiFile);
    }

    return psiFile;
  }


  public static void cachePsi(@NotNull Document document, @NotNull PsiFile file) {
    document.putUserData(HARD_REF_TO_PSI, file);
  }
  public PsiFile getCachedPsiFile(@NotNull Document document) {
    final PsiFile userData = document.getUserData(HARD_REF_TO_PSI);
    if(userData != null) return userData;

    final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !virtualFile.isValid()) return null;
    return getCachedPsiFile(virtualFile);
  }

  @Nullable
  public FileViewProvider getCachedViewProvider(Document document) {
    final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !virtualFile.isValid()) return null;
    return ((PsiManagerEx)myPsiManager).getFileManager().findCachedViewProvider(virtualFile);
  }

  @Nullable
  protected PsiFile getCachedPsiFile(VirtualFile virtualFile) {
    return ((PsiManagerEx)myPsiManager).getFileManager().getCachedPsiFile(virtualFile);
  }

  @Nullable
  protected PsiFile getPsiFile(VirtualFile virtualFile) {
    return ((PsiManagerEx)myPsiManager).getFileManager().findFile(virtualFile);
  }

  public Document getDocument(@NotNull PsiFile file) {
    if (file instanceof PsiBinaryFile) return null;

    Document document = getCachedDocument(file);
    if (document != null) {
      if (!file.getViewProvider().isPhysical() &&
          document.getUserData(HARD_REF_TO_PSI) == null) {
        cachePsi(document, file);
      }
      return document;
    }

    if (!file.getViewProvider().isEventSystemEnabled()) return null;
    document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

    if (document != null && !file.getViewProvider().isPhysical()) {
      cachePsi(document, file);
    }

    fireDocumentCreated(document, file);

    return document;
  }

  public Document getCachedDocument(@NotNull PsiFile file) {
    if(!file.isPhysical()) return null;
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    return FileDocumentManager.getInstance().getCachedDocument(vFile);
  }

  public void commitAllDocuments() {
    if (myUncommittedDocuments.isEmpty()) return;

    //long time1 = System.currentTimeMillis();

    final Document[] documents = getUncommittedDocuments();
    for (Document document : documents) {
      commitDocument(document);
    }

    //long time2 = System.currentTimeMillis();
    //Statistics.commitTime += (time2 - time1);
  }

  @Override
  public void performForCommittedDocument(@NotNull final Document doc, @NotNull final Runnable action) {
    final Document document = doc instanceof DocumentWindow ? ((DocumentWindow)doc).getDelegate() : doc;
    if (isUncommited(document)) {
      addRunOnCommit(document, action);
    }
    else {
      action.run();
    }
  }

  public void addRunOnCommit(Document document, Runnable action) {
    synchronized (ACTION_AFTER_COMMIT) {
      List<Runnable> list = document.getUserData(ACTION_AFTER_COMMIT);
      if (list == null) {
        document.putUserData(ACTION_AFTER_COMMIT, list = new SmartList<Runnable>());
      }
      list.add(action);
    }
  }

  public void commitDocument(@NotNull final Document doc) {
    final Document document = doc instanceof DocumentWindow ? ((DocumentWindow)doc).getDelegate() : doc;
    if (isUncommited(document)) {
      doCommit(document, null);
    }
  }

  private void doCommit(final Document document, final PsiFile excludeFile) {
    assert !(document instanceof DocumentWindow);
    ApplicationManager.getApplication().runWriteAction(new CommitToPsiFileAction(document,myProject) {
      public void run() {
        if (isCommittingDocument(document)) return;
        document.putUserData(KEY_COMMITING, Boolean.TRUE);

        try {
          boolean hasCommits = false;
          try {
            final FileViewProvider viewProvider = getCachedViewProvider(document);
            if (viewProvider != null) {
              final List<PsiFile> psiFiles = viewProvider.getAllFiles();
              for (PsiFile file : psiFiles) {
                if (file.isValid() && file != excludeFile) {
                  hasCommits |= commit(document, file);
                }
              }
              viewProvider.contentsSynchronized();
            }
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
          finally {
            myUncommittedDocuments.remove(document);
          }

          if (hasCommits) {
            InjectedLanguageUtil.commitAllInjectedDocuments(document, myProject);
          }
        }
        finally {
          document.putUserData(KEY_COMMITING, null);
        }
      }
    });

    List<Runnable> list;
    synchronized (ACTION_AFTER_COMMIT) {
      list = document.getUserData(ACTION_AFTER_COMMIT);
      if (list != null) {
        list = new ArrayList<Runnable>(list);
        document.putUserData(ACTION_AFTER_COMMIT, null);
      }
    }
    if (list != null) {
      for (final Runnable runnable : list) {
        runnable.run();
      }
    }
  }

  public void commitOtherFilesAssociatedWithDocument(final Document document, final PsiFile psiFile) {
    final FileViewProvider viewProvider = getCachedViewProvider(document);
    if (viewProvider != null && viewProvider.getAllFiles().size() > 1) {
      PostprocessReformattingAspect.getInstance(myProject).disablePostprocessFormattingInside(new Runnable() {
        public void run() {
          doCommit(document, psiFile);
        }
      });
    }
  }  

  public <T> T commitAndRunReadAction(@NotNull final Computable<T> computation) {
    final Ref<T> ref = Ref.create(null);
    commitAndRunReadAction(new Runnable() {
      public void run() {
        ref.set(computation.compute());
      }
    });
    return ref.get();
  }

  public void commitAndRunReadAction(@NotNull final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (SwingUtilities.isEventDispatchThread()){
      commitAllDocuments();
      runnable.run();
    }
    else{
      LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed(), "Don't call commitAndRunReadAction inside ReadAction it may cause a deadlock.");

      final Semaphore s1 = new Semaphore();
      final Semaphore s2 = new Semaphore();
      final boolean[] committed = {false};

      application.runReadAction(
        new Runnable() {
          public void run() {
            if (myUncommittedDocuments.isEmpty()){
              runnable.run();
              committed[0] = true;
            }
            else{
              s1.down();
              s2.down();
              final Runnable commitRunnable = new Runnable() {
                public void run() {
                  commitAllDocuments();
                  s1.up();
                  s2.waitFor();
                }
              };
              final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
              if (progressIndicator == null) {
                ApplicationManager.getApplication().invokeLater(commitRunnable);
              }
              else {
                ApplicationManager.getApplication().invokeLater(commitRunnable, progressIndicator.getModalityState());
              }
            }
          }
        }
      );

      if (!committed[0]){
        s1.waitFor();
        application.runReadAction(
          new Runnable() {
            public void run() {
              s2.up();
              runnable.run();
            }
          }
        );
      }
    }
  }

  private Listener[] getCachedListeners() {
    synchronized (myListeners) {
      if (myCachedListeners == null) {
        myCachedListeners = myListeners.toArray(new Listener[myListeners.size()]);
      }
      return myCachedListeners;
    }
  }

  public void addListener(@NotNull Listener listener) {
    synchronized (myListeners) {
      myListeners.add(listener);
      myCachedListeners = null;
    }
  }

  public void removeListener(@NotNull Listener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener);
      myCachedListeners = null;
    }
  }

  public boolean isDocumentBlockedByPsi(@NotNull Document doc) {
    final FileViewProvider viewProvider = getCachedViewProvider(doc);
    return viewProvider != null && viewProvider.isLockedByPsiOperations();
  }

  public void doPostponedOperationsAndUnblockDocument(@NotNull Document doc) {
    if (doc instanceof DocumentWindow) doc = ((DocumentWindow)doc).getDelegate();
    final PostprocessReformattingAspect component = myProject.getComponent(PostprocessReformattingAspect.class);
    final FileViewProvider viewProvider = getCachedViewProvider(doc);
    if(viewProvider != null) component.doPostponedFormatting(viewProvider);
  }

  private void fireDocumentCreated(Document document, PsiFile file) {
    Listener[] listeners = getCachedListeners();
    for (Listener listener : listeners) {
      listener.documentCreated(document, file);
    }
  }

  private void fireFileCreated(Document document, PsiFile file) {
    Listener[] listeners = getCachedListeners();
    for (Listener listener : listeners) {
      listener.fileCreated(file, document);
    }
  }

  @SuppressWarnings({"ALL"})
  private ASTNode myTreeElementBeingReparsedSoItWontBeCollected;

  protected boolean commit(final Document document, final PsiFile file) {
    document.putUserData(TEMP_TREE_IN_DOCUMENT_KEY, null);

    TextBlock textBlock = getTextBlock(document, file);
    if (textBlock.isEmpty()) return false;

    myIsCommitInProgress = true;
    try{
      if (mySmartPointerManager != null) { // mock tests
        SmartPointerManagerImpl.synchronizePointers(file);
      }

      myTreeElementBeingReparsedSoItWontBeCollected = ((PsiFileImpl)file).calcTreeElement();

      if (textBlock.isEmpty()) return false ; // if tree was just loaded above textBlock will be cleared by contentsLoaded

      textBlock.lock();
      final CharSequence chars = document.getCharsSequence();
      final Boolean data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY);
      if (data != null) {
        document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
        file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data);
      }

      int startOffset;
      int endOffset;
      int lengthShift;
      if (file.getViewProvider().supportsIncrementalReparse(file.getLanguage())) {
        startOffset = textBlock.getStartOffset();
        int psiEndOffset = textBlock.getPsiEndOffset();
        endOffset = psiEndOffset;
        lengthShift = textBlock.getTextEndOffset() - psiEndOffset;
      }
      else {
        startOffset = 0;
        endOffset = document.getTextLength();
        lengthShift = document.getTextLength() - file.getTextLength();
      }
      myBlockSupport.reparseRange(file, startOffset, endOffset, lengthShift, chars);

      textBlock.unlock();
      textBlock.clear();
    }
    finally {
      myTreeElementBeingReparsedSoItWontBeCollected = null;
      myIsCommitInProgress = false;
    }
    return true;
  }

  @NotNull
  public Document[] getUncommittedDocuments() {
    return myUncommittedDocuments.toArray(new Document[myUncommittedDocuments.size()]);
  }

  public boolean isUncommited(@NotNull Document document) {
    if(getSynchronizer().isInSynchronization(document)) return false;
    return ((DocumentEx)document).isInEventsHandling() || myUncommittedDocuments.contains(document);
  }

  public boolean hasUncommitedDocuments() {
    return !myIsCommitInProgress && !myUncommittedDocuments.isEmpty();
  }

  private final Key<ASTNode> TEMP_TREE_IN_DOCUMENT_KEY = Key.create("TEMP_TREE_IN_DOCUMENT_KEY");

  public void beforeDocumentChange(DocumentEvent event) {
    final Document document = event.getDocument();

    final FileViewProvider provider = getCachedViewProvider(document);
    if (provider == null) return;

    if (provider.getVirtualFile().getFileType().isBinary()) return;

    final List<PsiFile> files = provider.getAllFiles();
    boolean hasLockedBlocks = false;                                                      
    for (PsiFile file : files) {
      if (file == null) continue;
      final TextBlock textBlock = getTextBlock(document, file);
      if (textBlock.isLocked()) {
        hasLockedBlocks = true;
        continue;
      }

      if (file instanceof PsiFileImpl){
        myIsCommitInProgress = true;
        try{
          PsiFileImpl psiFile = (PsiFileImpl)file;
          // tree should be initialized and be kept until commit
          document.putUserData(TEMP_TREE_IN_DOCUMENT_KEY, psiFile.calcTreeElement());
        }
        finally{
          myIsCommitInProgress = false;
        }
      }

      if (file.isPhysical()) {
        if (mySmartPointerManager != null) { // mock tests
          SmartPointerManagerImpl.fastenBelts(file);
        }
      }
    }

    if (!hasLockedBlocks) {
      ((SingleRootFileViewProvider)provider).beforeDocumentChanged();
    }
  }

  public void documentChanged(DocumentEvent event) {
    final Document document = event.getDocument();
    final FileViewProvider viewProvider = getCachedViewProvider(document);
    if (viewProvider == null) return;
    if (viewProvider.getVirtualFile().getFileType().isBinary()) return;

    final List<PsiFile> files = viewProvider.getAllFiles();
    boolean commitNecessary = false;
    for (PsiFile file : files) {
      if (file == null || file instanceof PsiFileImpl && ((PsiFileImpl)file).getTreeElement() == null) continue;
      final TextBlock textBlock = getTextBlock(document, file);
      if (textBlock.isLocked()) continue;

      if (mySmartPointerManager != null) { // mock tests
        SmartPointerManagerImpl.unfastenBelts(file);
      }

      textBlock.documentChanged(event);
      assert file instanceof PsiFileImpl : event + "; file="+file+"; allFiles="+files+"; viewProvider="+viewProvider;
      myUncommittedDocuments.add(document);
      commitNecessary = true;
    }

    if (commitNecessary && ApplicationManager.getApplication().getCurrentWriteAction(ExternalChangeAction.class) != null){
      commitDocument(document);
    }
  }

  public TextBlock getTextBlock(Document document, PsiFile file) {
    TextBlock textBlock = file.getUserData(KEY_TEXT_BLOCK);
    if (textBlock == null){
      textBlock = new TextBlock();
      file.putUserData(KEY_TEXT_BLOCK, textBlock);
    }

    return textBlock;
  }

  public static boolean checkConsistency(PsiFile psiFile, Document document) {
    //todo hack
    if (psiFile.getVirtualFile() == null) return true;

    CharSequence editorText = document.getCharsSequence();
    int documentLength = document.getTextLength();
    if (psiFile.textMatches(editorText)) {
      LOG.assertTrue(psiFile.getTextLength() == documentLength);
      return true;
    }

    char[] fileText = psiFile.textToCharArray();
    @NonNls String error = "File '" + psiFile.getName() + "' text mismatch after reparse. " +
                           "File length=" + fileText.length + "; Doc length=" + documentLength + "\n";
    int i = 0;
    for(; i < documentLength; i++){
      if (i >= fileText.length){
        error += "editorText.length > psiText.length i=" + i + "\n";
        break;
      }
      if (i >= editorText.length()){
        error += "editorText.length > psiText.length i=" + i + "\n";
        break;
      }
      if (editorText.charAt(i) != fileText[i]){
        error += "first unequal char i=" + i + "\n";
        break;
      }
    }
    //error += "*********************************************" + "\n";
    //if (i <= 500){
    //  error += "Equal part:" + editorText.subSequence(0, i) + "\n";
    //}
    //else{
    //  error += "Equal part start:\n" + editorText.subSequence(0, 200) + "\n";
    //  error += "................................................" + "\n";
    //  error += "................................................" + "\n";
    //  error += "................................................" + "\n";
    //  error += "Equal part end:\n" + editorText.subSequence(i - 200, i) + "\n";
    //}
    error += "*********************************************" + "\n";
    error += "Editor Text tail:(" + (documentLength - i) + ")\n";// + editorText.subSequence(i, Math.min(i + 300, documentLength)) + "\n";
    error += "*********************************************" + "\n";
    error += "Psi Text tail:(" + (fileText.length - i) + ")\n";// + new String(fileText, i, Math.min(i + 300, fileText.length) - i) + "\n";
    error += "*********************************************" + "\n";

    if (document instanceof DocumentWindow) {
      error += "doc: '" + document.getText() + "'\n";
      error += "psi: '" +  psiFile.getText() + "'\n";
      error += "ast: '" + psiFile.getNode().getText() + "'\n";
      error += psiFile.getLanguage()+"\n";
      PsiElement context = psiFile.getContext();
      if (context != null) {
        error += "context: " + context +"; text: '" + context.getText() + "'\n";
        error += "context file: " + context.getContainingFile() + "\n";
      }
      error += "document window ranges: " + Arrays.asList(((DocumentWindow)document).getHostRanges())+"\n";
    }
    LOG.error(error);
    //document.replaceString(0, documentLength, psiFile.getText());
    return false;
  }

  public void contentsLoaded(PsiFileImpl file) {
    final Document document = getCachedDocument(file);
    if (document != null) getTextBlock(document, file).clear();
  }

  @TestOnly
  public void clearUncommitedDocuments() {
    myUncommittedDocuments.clear();
  }

  public PsiToDocumentSynchronizer getSynchronizer() {
    return mySynchronizer;
  }

  public boolean isCommittingDocument(final Document doc) {
    return doc.getUserData(KEY_COMMITING) == Boolean.TRUE;
  }

  public void save() {
    // Ensure all documents are commited on save so file content dependent indicies, that use PSI to build have consistent content.
    commitAllDocuments();
  }
}
