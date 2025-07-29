// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInsight.multiverse.FileViewProviderUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import com.intellij.psi.impl.file.PsiLargeBinaryFileImpl;
import com.intellij.psi.impl.file.PsiLargeTextFileImpl;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class AbstractFileViewProvider extends UserDataHolderBase implements FileViewProvider {
  private static final Logger LOG = Logger.getInstance(AbstractFileViewProvider.class);
  public static final Key<Object> FREE_THREADED = Key.create("FREE_THREADED");
  private static final Key<Set<AbstractFileViewProvider>> KNOWN_COPIES = Key.create("KNOWN_COPIES");

  private final @NotNull PsiManagerEx myManager;
  private final @NotNull VirtualFile myVirtualFile;
  private final boolean myEventSystemEnabled;
  private final boolean myPhysical;
  private volatile Content myContent;
  private volatile Reference<Document> myDocument;
  @SuppressWarnings("FieldMayBeStatic") private final PsiLock myPsiLock = new PsiLock();

  protected AbstractFileViewProvider(@NotNull PsiManager manager,
                                     @NotNull VirtualFile virtualFile,
                                     boolean eventSystemEnabled) {
    myManager = (PsiManagerEx)manager;
    myVirtualFile = virtualFile;
    myEventSystemEnabled = eventSystemEnabled;
    setContent(new VirtualFileContent());
    myPhysical = isEventSystemEnabled() &&
                 !(virtualFile instanceof LightVirtualFile) &&
                 !(virtualFile.getFileSystem() instanceof NonPhysicalFileSystem);
    virtualFile.putUserData(FREE_THREADED, isFreeThreaded(this));
    if (virtualFile instanceof VirtualFileWindow && !(this instanceof FreeThreadedFileViewProvider) && !isFreeThreaded(this)) {
      throw new IllegalArgumentException("Must not create "+getClass()+" for injected file "+virtualFile+"; InjectedFileViewProvider must be used instead");
    }
  }

  protected boolean shouldCreatePsi() {
    if (isIgnored()) return false;

    VirtualFile vFile = getVirtualFile();
    if (isPhysical() && vFile.isInLocalFileSystem()) { // check directories consistency
      VirtualFile parent = vFile.getParent();
      if (parent == null) return false;

      PsiDirectory psiDir = getManager().findDirectory(parent);
      if (psiDir == null) {
        FileIndexFacade indexFacade = FileIndexFacade.getInstance(getManager().getProject());
        if (!indexFacade.isInLibrarySource(vFile) && !indexFacade.isInLibraryClasses(vFile)) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean isFreeThreaded(@NotNull FileViewProvider provider) {
    return provider.getVirtualFile() instanceof LightVirtualFile && !provider.isEventSystemEnabled();
  }

  public @NotNull PsiLock getFilePsiLock() {
    return myPsiLock;
  }

  protected final boolean isIgnored() {
    VirtualFile file = getVirtualFile();
    return !(file instanceof LightVirtualFile) && FileTypeRegistry.getInstance().isFileIgnored(file);
  }

  protected @Nullable PsiFile createFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull FileType fileType) {
    return createFile(file, fileType, getBaseLanguage());
  }

  protected @NotNull PsiFile createFile(@NotNull VirtualFile file, @NotNull FileType fileType, @NotNull Language language) {
    if (fileType.isBinary() || file.is(VFileProperty.SPECIAL)) {
      return SingleRootFileViewProvider.isTooLargeForContentLoading(file) ?
             new PsiLargeBinaryFileImpl(getManager(), this) :
             new PsiBinaryFileImpl(getManager(), this);
    }
    if (!SingleRootFileViewProvider.isTooLargeForIntelligence(file)) {
      PsiFile psiFile = createFile(language);
      if (psiFile != null) return psiFile;
    }

    if (SingleRootFileViewProvider.isTooLargeForContentLoading(file)) {
      return new PsiLargeTextFileImpl(this);
    }

    return new PsiPlainTextFileImpl(this);
  }

  protected @Nullable PsiFile createFile(@NotNull Language lang) {
    if (lang != getBaseLanguage()) return null;
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    if (parserDefinition != null) {
      return parserDefinition.createFile(this);
    }
    return null;
  }

  @Override
  public final @NotNull PsiManagerEx getManager() {
    return myManager;
  }

  @Override
  public @NotNull CharSequence getContents() {
    return getContent().getText();
  }

  @Override
  public @NotNull VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  private @Nullable Document getCachedDocument() {
    Document document = com.intellij.reference.SoftReference.dereference(myDocument);
    if (document != null) return document;
    return FileDocumentManager.getInstance().getCachedDocument(getVirtualFile());
  }

  @Override
  public Document getDocument() {
    Document document = com.intellij.reference.SoftReference.dereference(myDocument);
    if (document == null) {
      VirtualFile file = getVirtualFile();
      document = FileDocumentManager.getInstance().getDocument(file, myManager.getProject());
      myDocument = document == null ? null : new SoftReference<>(document);
    }
    return document;
  }

  @Override
  public final @Nullable PsiFile getPsi(@NotNull Language target) {
    if (!isPhysical()) {
      FileManager fileManager = getManager().getFileManager();
      VirtualFile virtualFile = getVirtualFile();
      // todo IJPL-339 check no real context is used here???
      if (fileManager.findCachedViewProvider(virtualFile) == null && getCachedPsiFiles().isEmpty()) {
        fileManager.setViewProvider(virtualFile, this);
      }
    }
    return getPsiInner(target);
  }

  protected abstract @Nullable PsiFile getPsiInner(@NotNull Language target);

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public FileViewProvider clone() {
    VirtualFile origFile = getVirtualFile();
    LightVirtualFile copy = new LightVirtualFile(origFile.getName(), origFile.getFileType(), getContents(), origFile.getCharset(), getModificationStamp());
    origFile.copyCopyableDataTo(copy);
    copy.setOriginalFile(origFile);
    UndoUtil.disableUndoFor(copy);
    copy.setCharset(origFile.getCharset());

    return createCopy(copy);
  }

  @Override
  public PsiElement findElementAt(int offset, @NotNull Language language) {
    PsiFile psiFile = getPsi(language);
    return psiFile != null ? findElementAt(psiFile, offset) : null;
  }

  @Override
  public @Nullable PsiReference findReferenceAt(int offset, @NotNull Language language) {
    PsiFile psiFile = getPsi(language);
    return psiFile != null ? findReferenceAt(psiFile, offset) : null;
  }

  protected static @Nullable PsiReference findReferenceAt(@Nullable PsiFile psiFile, int offset) {
    if (psiFile == null) return null;
    int offsetInElement = offset;
    PsiElement child = psiFile.getFirstChild();
    while (child != null) {
      int length = child.getTextLength();
      if (length <= offsetInElement) {
        offsetInElement -= length;
        child = child.getNextSibling();
        continue;
      }
      return child.findReferenceAt(offsetInElement);
    }
    return null;
  }

  public static @Nullable PsiElement findElementAt(@Nullable PsiElement psiFile, int offset) {
    ASTNode node = psiFile == null ? null : psiFile.getNode();
    return node == null ? null : SourceTreeToPsiMap.treeElementToPsi(node.findLeafElementAt(offset));
  }

    @Override
  public void beforeContentsSynchronized() {
  }

  @Override
  public void contentsSynchronized() {
    if (myContent instanceof PsiFileContent) {
      setContent(new VirtualFileContent());
    }
    checkLengthConsistency();
  }

  public final void onContentReload() {
    List<PsiFile> psiFiles = getCachedPsiFiles();
    List<PsiTreeChangeEventImpl> events = new ArrayList<>(psiFiles.size());
    List<PsiTreeChangeEventImpl> genericEvents = new ArrayList<>(psiFiles.size());
    for (PsiFile psiFile : psiFiles) {
      genericEvents.add(createChildrenChangeEvent(psiFile, true));
      events.add(createChildrenChangeEvent(psiFile, false));
    }

    beforeContentsSynchronized();

    for (PsiTreeChangeEventImpl event : genericEvents) {
      getManager().beforeChildrenChange(event);
    }
    for (PsiTreeChangeEventImpl event : events) {
      getManager().beforeChildrenChange(event);
    }

    for (PsiFile psiFile : psiFiles) {
      if (psiFile instanceof PsiFileEx) {
        ((PsiFileEx)psiFile).onContentReload();
      }
    }

    contentsSynchronized();

    for (PsiTreeChangeEventImpl event : events) {
      getManager().childrenChanged(event);
    }
    for (PsiTreeChangeEventImpl event : genericEvents) {
      getManager().childrenChanged(event);
    }
  }

  private @NotNull PsiTreeChangeEventImpl createChildrenChangeEvent(@NotNull PsiFile psiFile, boolean generic) {
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setParent(psiFile);
    event.setFile(psiFile);
    event.setGenericChange(generic);
    if (psiFile instanceof PsiFileImpl && ((PsiFileImpl)psiFile).isContentsLoaded()) {
      event.setOffset(0);
      event.setOldLength(psiFile.getTextLength());
    }
    return event;
  }

  @Override
  public void rootChanged(@NotNull PsiFile psiFile) {
    if (psiFile instanceof PsiFileImpl && ((PsiFileImpl)psiFile).isContentsLoaded() && psiFile.isValid()) {
      setContent(new PsiFileContent(((PsiFileImpl)psiFile).calcTreeElement(), LocalTimeCounter.currentTime()));
    }
  }

  @Override
  public boolean isEventSystemEnabled() {
    return myEventSystemEnabled;
  }

  @Override
  public boolean isPhysical() {
    return myPhysical;
  }

  @Override
  public long getModificationStamp() {
    return getContent().getModificationStamp();
  }

  @Override
  public boolean supportsIncrementalReparse(@NotNull Language rootLanguage) {
    return true;
  }

  private @NotNull Content getContent() {
    return myContent;
  }

  private void setContent(@NotNull Content content) {
    myContent = content;
  }

  private void checkLengthConsistency() {
    Document document = getCachedDocument();
    if (document instanceof DocumentWindow) {
      return;
    }
    if (document != null &&
        ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(myManager.getProject())).getSynchronizer().isInSynchronization(document)) {
      return;
    }

    List<FileASTNode> knownTreeRoots = getKnownTreeRoots();
    if (knownTreeRoots.isEmpty()) return;

    int fileLength = myContent.getTextLength();
    for (FileASTNode fileElement : knownTreeRoots) {
      int nodeLength = fileElement.getTextLength();
      if (!isDocumentConsistentWithPsi(fileLength, fileElement, nodeLength)) {
        PsiUtilCore.ensureValid(fileElement.getPsi());
        Attachment vfContent = new Attachment(myVirtualFile.getName(), myContent.getText().toString());
        Attachment astContent = new Attachment(myVirtualFile.getNameWithoutExtension() + ".tree.txt", fileElement.getText());
        Attachment[] attachments = document == null ? new Attachment[]{vfContent, astContent} :
          new Attachment[]{vfContent, astContent, new Attachment(myVirtualFile.getNameWithoutExtension() + ".document.txt", document.getText())};

        String message =
          "Inconsistent " + fileElement.getElementType() + " tree in " + this + "; nodeLength=" + nodeLength + "; fileLength=" + fileLength;

        if (ApplicationManager.getApplication().isUnitTestMode()
            && !ApplicationManagerEx.isInStressTest() &&
            CodeInsightContexts.isSharedSourceSupportEnabled(getManager().getProject())
        ) {
          message += "; context: " + FileViewProviderUtil.getCodeInsightContext(this);

          FileManager fileManager = PsiManagerEx.getInstanceEx(getManager().getProject()).getFileManager();
          List<FileViewProvider> providers = fileManager.findCachedViewProviders(myVirtualFile);

          message += "; known view providers: " + providers.size();

        }

        LOG.error(message, attachments);
      }
    }
  }

  private boolean isDocumentConsistentWithPsi(int fileLength, @NotNull FileASTNode fileElement, int nodeLength) {
    if (nodeLength != fileLength) return false;

    if (ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManagerEx.isInStressTest()) {
      return fileElement.getPsi().textMatches(myContent.getText());
    }

    return true;
  }


  @Override
  public @NonNls String toString() {
    return getClass().getName() + "{vFile=" + myVirtualFile
           + (myVirtualFile instanceof VirtualFileWithId ? ", vFileId=" + ((VirtualFileWithId)myVirtualFile).getId() : "")
           + ", content=" + getContent() + ", eventSystemEnabled=" + isEventSystemEnabled() + '}';
  }

  public abstract PsiFile getCachedPsi(@NotNull Language target);

  public abstract @Unmodifiable @NotNull List<PsiFile> getCachedPsiFiles();

  public abstract @Unmodifiable @NotNull List<FileASTNode> getKnownTreeRoots();

  public final void markInvalidated() {
    invalidateCachedPsi();
    for (AbstractFileViewProvider copy : getKnownCopies()) {
      myManager.getFileManager().setViewProvider(copy.getVirtualFile(), null);
    }
  }

  public final void markPossiblyInvalidated() {
    invalidateCachedPsi();
    for (AbstractFileViewProvider copy : getKnownCopies()) {
      FileManagerImpl.markPossiblyInvalidated(copy);
    }
  }

  private void invalidateCachedPsi() {
    for (PsiFile file : getCachedPsiFiles()) {
      if (file instanceof PsiFileEx) {
        ((PsiFileEx)file).markInvalidated();
      }
    }
  }

  private @NotNull @Unmodifiable Iterable<AbstractFileViewProvider> getKnownCopies() {
    Set<AbstractFileViewProvider> copies = getUserData(KNOWN_COPIES);
    if (copies != null) {
      return ContainerUtil.filter(copies, copy -> ContainerUtil.exists(copy.getCachedPsiFiles(), f -> f.getOriginalFile().getViewProvider() == this));
    }
    return Collections.emptySet();
  }

  public final void registerAsCopy(@NotNull AbstractFileViewProvider copy) {
    if (copy instanceof FreeThreadedFileViewProvider) {
      LOG.assertTrue(this instanceof FreeThreadedFileViewProvider, "Injected file can't have non-injected original file");
    }
    Set<AbstractFileViewProvider> copies = ConcurrencyUtil.computeIfAbsent(this, KNOWN_COPIES, () -> Collections.newSetFromMap(CollectionFactory.createConcurrentWeakMap()));
    if (copy.getUserData(KNOWN_COPIES) != null) {
      List<AbstractFileViewProvider> derivations = JBTreeTraverser.from(AbstractFileViewProvider::getKnownCopies).withRoot(copy).toList();
      if (derivations.contains(this)) {
        throw new IllegalStateException("An attempted cycle in view provider copy graph involving " + this + " and " + copy);
      }
    }
    copies.add(copy);
  }

  private interface Content {
    @NotNull
    CharSequence getText();
    int getTextLength();

    long getModificationStamp();
  }

  private class VirtualFileContent implements Content {
    @Override
    public @NotNull CharSequence getText() {
      VirtualFile virtualFile = getVirtualFile();
      if (virtualFile instanceof LightVirtualFile) {
        Document doc = getCachedDocument();
        if (doc != null) return getLastCommittedText(doc);
        return ((LightVirtualFile)virtualFile).getContent();
      }

      Document document = getDocument();
      if (document == null) {
        return LoadTextUtil.loadText(virtualFile);
      }
      return getLastCommittedText(document);
    }

    @Override
    public int getTextLength() {
      return getText().length();
    }

    @Override
    public long getModificationStamp() {
      Document document = getCachedDocument();
      if (document == null) {
        return getVirtualFile().getModificationStamp();
      }
      return getLastCommittedStamp(document);
    }

    @Override
    public @NonNls String toString() {
      return "VirtualFileContent{size=" + getVirtualFile().getLength() + "}";
    }
  }

  private @NotNull CharSequence getLastCommittedText(@NotNull Document document) {
    return PsiDocumentManager.getInstance(myManager.getProject()).getLastCommittedText(document);
  }
  private long getLastCommittedStamp(@NotNull Document document) {
    return PsiDocumentManager.getInstance(myManager.getProject()).getLastCommittedStamp(document);
  }

  private static class PsiFileContent implements Content {
    private final long myModificationStamp;
    private final FileElement myFileElement;

    PsiFileContent(@NotNull FileElement fileElement, long modificationStamp) {
      myModificationStamp = modificationStamp;
      myFileElement = fileElement;
    }

    @Override
    public @NotNull CharSequence getText() {
      return myFileElement.getText();
    }

    @Override
    public int getTextLength() {
      return myFileElement.getTextLength();
    }

    @Override
    public long getModificationStamp() {
      return myModificationStamp;
    }
  }

  @Override
  public @NotNull PsiFile getStubBindingRoot() {
    PsiFile psi = getPsi(getBaseLanguage());
    assert psi != null;
    return psi;
  }

  @Override
  public final @NotNull FileType getFileType() {
    return myVirtualFile.getFileType();
  }
}