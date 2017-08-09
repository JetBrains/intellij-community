/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi;

import com.google.common.util.concurrent.Atomics;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import com.intellij.psi.impl.file.PsiLargeBinaryFileImpl;
import com.intellij.psi.impl.file.PsiLargeTextFileImpl;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class SingleRootFileViewProvider extends UserDataHolderBase implements FileViewProvider {
  private static final Key<Boolean> OUR_NO_SIZE_LIMIT_KEY = Key.create("no.size.limit");
  private static final Logger LOG = Logger.getInstance("#" + SingleRootFileViewProvider.class.getCanonicalName());
  public static final Key<Object> FREE_THREADED = Key.create("FREE_THREADED");
  @NotNull private final PsiManager myManager;
  @NotNull private final VirtualFile myVirtualFile;
  private final boolean myEventSystemEnabled;
  private final boolean myPhysical;
  private final AtomicReference<PsiFile> myPsiFile = Atomics.newReference();
  private volatile Content myContent;
  private volatile Reference<Document> myDocument;
  @NotNull private final Language myBaseLanguage;
  @NotNull private final FileType myFileType;

  public SingleRootFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file) {
    this(manager, file, true);
  }

  public SingleRootFileViewProvider(@NotNull PsiManager manager,
                                    @NotNull VirtualFile virtualFile,
                                    final boolean eventSystemEnabled) {
    this(manager, virtualFile, eventSystemEnabled, virtualFile.getFileType());
  }

  public SingleRootFileViewProvider(@NotNull PsiManager manager,
                                    @NotNull VirtualFile virtualFile,
                                    final boolean eventSystemEnabled,
                                    @NotNull final FileType fileType) {
    this(manager, virtualFile, eventSystemEnabled, calcBaseLanguage(virtualFile, manager.getProject(), fileType), fileType);
  }

  protected SingleRootFileViewProvider(@NotNull PsiManager manager,
                                       @NotNull VirtualFile virtualFile,
                                       final boolean eventSystemEnabled,
                                       @NotNull Language language) {
    this(manager, virtualFile, eventSystemEnabled, language, virtualFile.getFileType());
  }

  protected SingleRootFileViewProvider(@NotNull PsiManager manager,
                                       @NotNull VirtualFile virtualFile,
                                       final boolean eventSystemEnabled,
                                       @NotNull Language language,
                                       @NotNull FileType type) {
    myManager = manager;
    myVirtualFile = virtualFile;
    myEventSystemEnabled = eventSystemEnabled;
    myBaseLanguage = language;
    setContent(new VirtualFileContent());
    myPhysical = isEventSystemEnabled() &&
                 !(virtualFile instanceof LightVirtualFile) &&
                 !(virtualFile.getFileSystem() instanceof NonPhysicalFileSystem);
    virtualFile.putUserData(FREE_THREADED, isFreeThreaded(this));
    myFileType = type;
  }

  public static boolean isFreeThreaded(@NotNull FileViewProvider provider) {
    return provider.getVirtualFile() instanceof LightVirtualFile && !provider.isEventSystemEnabled();
  }

  @Override
  @NotNull
  public Language getBaseLanguage() {
    return myBaseLanguage;
  }

  private static Language calcBaseLanguage(@NotNull VirtualFile file, @NotNull Project project, @NotNull final FileType fileType) {
    if (fileType.isBinary()) return Language.ANY;
    if (isTooLargeForIntelligence(file)) return PlainTextLanguage.INSTANCE;

    Language language = LanguageUtil.getLanguageForPsi(project, file);

    return language != null ? language : PlainTextLanguage.INSTANCE;
  }

  @Override
  @NotNull
  public Set<Language> getLanguages() {
    return Collections.singleton(getBaseLanguage());
  }

  @Override
  @Nullable
  public final PsiFile getPsi(@NotNull Language target) {
    if (!isPhysical()) {
      FileManager fileManager = ((PsiManagerEx)myManager).getFileManager();
      VirtualFile virtualFile = getVirtualFile();
      if (myPsiFile.get() == null && fileManager.findCachedViewProvider(virtualFile) == null) {
        fileManager.setViewProvider(virtualFile, this);
      }
    }
    return getPsiInner(target);
  }

  @Override
  @NotNull
  public List<PsiFile> getAllFiles() {
    return ContainerUtil.createMaybeSingletonList(getPsi(getBaseLanguage()));
  }

  @Nullable
  protected PsiFile getPsiInner(@NotNull Language target) {
    if (target != getBaseLanguage()) {
      return null;
    }
    PsiFile psiFile = myPsiFile.get();
    if (psiFile == null) {
      psiFile = createFile();
      if (psiFile == null) {
        psiFile = PsiUtilCore.NULL_PSI_FILE;
      }
      boolean set = myPsiFile.compareAndSet(null, psiFile);
      if (!set && psiFile != PsiUtilCore.NULL_PSI_FILE) {
        PsiFile alreadyCreated = myPsiFile.get();
        if (alreadyCreated == psiFile) {
          LOG.error(this + ".createFile() must create new file instance but got the same: " + psiFile);
        }
        if (psiFile instanceof PsiFileEx) {
          DebugUtil.startPsiModification("invalidating throw-away copy");
          try {
            ((PsiFileEx)psiFile).markInvalidated();
          }
          finally {
            DebugUtil.finishPsiModification();
          }
        }
        psiFile = alreadyCreated;
      }
    }
    return psiFile == PsiUtilCore.NULL_PSI_FILE ? null : psiFile;
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

  public void beforeDocumentChanged(@Nullable PsiFile psiCause) {
    PsiFile psiFile = psiCause != null ? psiCause : getPsi(getBaseLanguage());
    if (psiFile instanceof PsiFileImpl && myContent instanceof VirtualFileContent) {
      setContent(new PsiFileContent((PsiFileImpl)psiFile, psiCause == null ? getModificationStamp() : LocalTimeCounter.currentTime()));
      checkLengthConsistency();
    }
  }

  public final void onContentReload() {
    List<PsiFile> files = getCachedPsiFiles();
    List<PsiTreeChangeEventImpl> events = ContainerUtil.newArrayList();
    List<PsiTreeChangeEventImpl> genericEvents = ContainerUtil.newArrayList();
    for (PsiFile file : files) {
      genericEvents.add(createChildrenChangeEvent(file, true));
      events.add(createChildrenChangeEvent(file, false));
    }

    beforeContentsSynchronized();

    for (PsiTreeChangeEventImpl event : genericEvents) {
      ((PsiManagerImpl)getManager()).beforeChildrenChange(event);
    }
    for (PsiTreeChangeEventImpl event : events) {
      ((PsiManagerImpl)getManager()).beforeChildrenChange(event);
    }

    for (PsiFile psiFile : files) {
      if (psiFile instanceof PsiFileEx) {
        ((PsiFileEx)psiFile).onContentReload();
      }
    }

    for (PsiTreeChangeEventImpl event : events) {
      ((PsiManagerImpl)getManager()).childrenChanged(event);
    }
    for (PsiTreeChangeEventImpl event : genericEvents) {
      ((PsiManagerImpl)getManager()).childrenChanged(event);
    }

    contentsSynchronized();
  }

  private PsiTreeChangeEventImpl createChildrenChangeEvent(PsiFile file, boolean generic) {
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setParent(file);
    event.setFile(file);
    event.setGenericChange(generic);
    if (file instanceof PsiFileImpl && ((PsiFileImpl)file).isContentsLoaded()) {
      event.setOffset(0);
      event.setOldLength(file.getTextLength());
    }
    return event;
  }

  @Override
  public void rootChanged(@NotNull PsiFile psiFile) {
    if (psiFile instanceof PsiFileImpl && ((PsiFileImpl)psiFile).isContentsLoaded()) {
      setContent(new PsiFileContent((PsiFileImpl)psiFile, LocalTimeCounter.currentTime()));
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
  public boolean supportsIncrementalReparse(@NotNull final Language rootLanguage) {
    return true;
  }


  public PsiFile getCachedPsi(@NotNull Language target) {
    PsiFile file = myPsiFile.get();
    return file == PsiUtilCore.NULL_PSI_FILE ? null : file;
  }

  public List<PsiFile> getCachedPsiFiles() {
    return ContainerUtil.createMaybeSingletonList(getCachedPsi(myBaseLanguage));
  }

  @NotNull
  public List<FileElement> getKnownTreeRoots() {
    PsiFile psiFile = getCachedPsi(myBaseLanguage);
    if (!(psiFile instanceof PsiFileImpl)) return Collections.emptyList();
    FileElement element = ((PsiFileImpl)psiFile).getTreeElement();
    return ContainerUtil.createMaybeSingletonList(element);
  }

  private PsiFile createFile() {
    try {
      final VirtualFile vFile = getVirtualFile();
      if (vFile.isDirectory()) return null;
      if (isIgnored()) return null;

      final Project project = myManager.getProject();
      if (isPhysical() && vFile.isInLocalFileSystem()) { // check directories consistency
        final VirtualFile parent = vFile.getParent();
        if (parent == null) return null;
        final PsiDirectory psiDir = getManager().findDirectory(parent);
        if (psiDir == null) {
          FileIndexFacade indexFacade = FileIndexFacade.getInstance(project);
          if (!indexFacade.isInLibrarySource(vFile) && !indexFacade.isInLibraryClasses(vFile)) {
            return null;
          }
        }
      }

      return createFile(project, vFile, myFileType);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }
  }

  protected boolean isIgnored() {
    final VirtualFile file = getVirtualFile();
    return !(file instanceof LightVirtualFile) && FileTypeRegistry.getInstance().isFileIgnored(file);
  }

  @Nullable
  protected PsiFile createFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull FileType fileType) {
    if (fileType.isBinary() || file.is(VFileProperty.SPECIAL)) {
      return isTooLargeForContentLoading(file) ?
             new PsiLargeBinaryFileImpl(((PsiManagerImpl)getManager()), this) :
             new PsiBinaryFileImpl((PsiManagerImpl)getManager(), this);
    }
    if (!isTooLargeForIntelligence(file)) {
      final PsiFile psiFile = createFile(getBaseLanguage());
      if (psiFile != null) return psiFile;
    }

    if (isTooLargeForContentLoading(file)) {
      return new PsiLargeTextFileImpl(this);
    }

    return new PsiPlainTextFileImpl(this);
  }

  @Deprecated
  public static boolean isTooLarge(@NotNull VirtualFile vFile) {
    return isTooLargeForIntelligence(vFile);
  }

  public static boolean isTooLargeForIntelligence(@NotNull VirtualFile vFile) {
    if (!checkFileSizeLimit(vFile)) return false;
    return fileSizeIsGreaterThan(vFile, PersistentFSConstants.getMaxIntellisenseFileSize());
  }

  public static boolean isTooLargeForContentLoading(@NotNull VirtualFile vFile) {
    return fileSizeIsGreaterThan(vFile, PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD);
  }

  private static boolean checkFileSizeLimit(@NotNull VirtualFile vFile) {
    return !Boolean.TRUE.equals(vFile.getUserData(OUR_NO_SIZE_LIMIT_KEY));
  }
  public static void doNotCheckFileSizeLimit(@NotNull VirtualFile vFile) {
    vFile.putUserData(OUR_NO_SIZE_LIMIT_KEY, Boolean.TRUE);
  }

  public static boolean isTooLargeForIntelligence(@NotNull VirtualFile vFile, final long contentSize) {
    if (!checkFileSizeLimit(vFile)) return false;
    return contentSize > PersistentFSConstants.getMaxIntellisenseFileSize();
  }

  @SuppressWarnings("UnusedParameters")
  public static boolean isTooLargeForContentLoading(@NotNull VirtualFile vFile, final long contentSize) {
    return contentSize > PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD;
  }

  public static boolean fileSizeIsGreaterThan(@NotNull VirtualFile vFile, final long maxBytes) {
    if (vFile instanceof LightVirtualFile) {
      // This is optimization in order to avoid conversion of [large] file contents to bytes
      final int lengthInChars = ((LightVirtualFile)vFile).getContent().length();
      if (lengthInChars < maxBytes / 2) return false;
      if (lengthInChars > maxBytes ) return true;
    }

    return vFile.getLength() > maxBytes;
  }

  @Nullable
  protected PsiFile createFile(@NotNull Language lang) {
    if (lang != getBaseLanguage()) return null;
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    if (parserDefinition != null) {
      return parserDefinition.createFile(this);
    }
    return null;
  }

  @Override
  @NotNull
  public PsiManager getManager() {
    return myManager;
  }

  @Override
  @NotNull
  public CharSequence getContents() {
    return getContent().getText();
  }

  @Override
  @NotNull
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Nullable
  private Document getCachedDocument() {
    final Document document = com.intellij.reference.SoftReference.dereference(myDocument);
    if (document != null) return document;
    return FileDocumentManager.getInstance().getCachedDocument(getVirtualFile());
  }

  @Override
  public Document getDocument() {
    Document document = com.intellij.reference.SoftReference.dereference(myDocument);
    if (document == null/* TODO[ik] make this change && isEventSystemEnabled()*/) {
      document = FileDocumentManager.getInstance().getDocument(getVirtualFile());
      myDocument = document == null ? null : new SoftReference<>(document);
    }
    return document;
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public FileViewProvider clone() {
    final VirtualFile origFile = getVirtualFile();
    LightVirtualFile copy = new LightVirtualFile(origFile.getName(), myFileType, getContents(), origFile.getCharset(), getModificationStamp());
    copy.setOriginalFile(origFile);
    copy.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    copy.setCharset(origFile.getCharset());
    return createCopy(copy);
  }

  @NotNull
  @Override
  public SingleRootFileViewProvider createCopy(@NotNull final VirtualFile copy) {
    return new SingleRootFileViewProvider(getManager(), copy, false, myBaseLanguage);
  }

  @Override
  public PsiReference findReferenceAt(final int offset) {
    final PsiFile psiFile = getPsi(getBaseLanguage());
    return findReferenceAt(psiFile, offset);
  }

  @Override
  public PsiElement findElementAt(final int offset, @NotNull final Language language) {
    final PsiFile psiFile = getPsi(language);
    return psiFile != null ? findElementAt(psiFile, offset) : null;
  }

  @Override
  @Nullable
  public PsiReference findReferenceAt(final int offset, @NotNull final Language language) {
    final PsiFile psiFile = getPsi(language);
    return psiFile != null ? findReferenceAt(psiFile, offset) : null;
  }

  @Nullable
  protected static PsiReference findReferenceAt(@Nullable final PsiFile psiFile, final int offset) {
    if (psiFile == null) return null;
    int offsetInElement = offset;
    PsiElement child = psiFile.getFirstChild();
    while (child != null) {
      final int length = child.getTextLength();
      if (length <= offsetInElement) {
        offsetInElement -= length;
        child = child.getNextSibling();
        continue;
      }
      return child.findReferenceAt(offsetInElement);
    }
    return null;
  }

  @Override
  public PsiElement findElementAt(final int offset) {
    return findElementAt(getPsi(getBaseLanguage()), offset);
  }


  @Override
  public PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang) {
    if (!ReflectionUtil.isAssignable(lang, getBaseLanguage().getClass())) return null;
    return findElementAt(offset);
  }

  @Nullable
  public static PsiElement findElementAt(@Nullable PsiElement psiFile, final int offset) {
    ASTNode node = psiFile == null ? null : psiFile.getNode();
    return node == null ? null : SourceTreeToPsiMap.treeElementToPsi(node.findLeafElementAt(offset));
  }

  public void forceCachedPsi(@NotNull PsiFile psiFile) {
    PsiFile prev = myPsiFile.getAndSet(psiFile);
    if (prev != null && prev != psiFile && prev instanceof PsiFileEx) {
      ((PsiFileEx)prev).markInvalidated();
    }
    ((PsiManagerEx)myManager).getFileManager().setViewProvider(getVirtualFile(), this);
  }

  @NotNull
  private Content getContent() {
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

    List<FileElement> knownTreeRoots = getKnownTreeRoots();
    if (knownTreeRoots.isEmpty()) return;

    int fileLength = myContent.getTextLength();
    for (FileElement fileElement : knownTreeRoots) {
      int nodeLength = fileElement.getTextLength();
      if (nodeLength != fileLength) {
        PsiUtilCore.ensureValid(fileElement.getPsi());
        List<Attachment> attachments = ContainerUtil.newArrayList(new Attachment(myVirtualFile.getNameWithoutExtension() + ".tree.txt", fileElement.getText()),
                                                                  new Attachment(myVirtualFile.getNameWithoutExtension() + ".file.txt", myContent.toString()));
        if (document != null) {
          attachments.add(new Attachment(myVirtualFile.getNameWithoutExtension() + ".document.txt", document.getText()));
        }
        // exceptions here should be assigned to peter
        LOG.error("Inconsistent " + fileElement.getElementType() + " tree in " + this + "; nodeLength=" + nodeLength + "; fileLength=" + fileLength,
                  attachments.toArray(Attachment.EMPTY_ARRAY));
      }
    }
  }

  @NonNls
  @Override
  public String toString() {
    return getClass().getSimpleName() + "{myVirtualFile=" + myVirtualFile + ", content=" + getContent() + '}';
  }

  public void markInvalidated() {
    PsiFile psiFile = getCachedPsi(myBaseLanguage);
    if (psiFile instanceof PsiFileEx) {
      ((PsiFileEx)psiFile).markInvalidated();
    }
  }

  private interface Content {
    CharSequence getText();
    int getTextLength();

    long getModificationStamp();
  }

  private class VirtualFileContent implements Content {
    @Override
    public CharSequence getText() {
      final VirtualFile virtualFile = getVirtualFile();
      if (virtualFile instanceof LightVirtualFile) {
        Document doc = getCachedDocument();
        if (doc != null) return getLastCommittedText(doc);
        return ((LightVirtualFile)virtualFile).getContent();
      }

      final Document document = getDocument();
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
      final Document document = getCachedDocument();
      if (document == null) {
        return getVirtualFile().getModificationStamp();
      }
      return getLastCommittedStamp(document);
    }

    @NonNls
    @Override
    public String toString() {
      return "VirtualFileContent{size=" + getVirtualFile().getLength() + "}";
    }
  }

  private CharSequence getLastCommittedText(Document document) {
    return PsiDocumentManager.getInstance(myManager.getProject()).getLastCommittedText(document);
  }
  private long getLastCommittedStamp(Document document) {
    return PsiDocumentManager.getInstance(myManager.getProject()).getLastCommittedStamp(document);
  }

  private class PsiFileContent implements Content {
    private final PsiFileImpl myFile;
    private volatile String myContent;
    private final long myModificationStamp;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<FileElement> myFileElementHardRefs = new SmartList<>();

    private PsiFileContent(final PsiFileImpl file, final long modificationStamp) {
      myFile = file;
      myModificationStamp = modificationStamp;
      for (PsiFile aFile : getAllFiles()) {
        if (aFile instanceof PsiFileImpl) {
          myFileElementHardRefs.add(((PsiFileImpl)aFile).calcTreeElement());
        }
      }
    }

    @Override
    public CharSequence getText() {
      String content = myContent;
      if (content == null) {
        myContent = content = ReadAction.compute(() -> myFile.calcTreeElement().getText());
      }
      return content;
    }

    @Override
    public int getTextLength() {
      String content = myContent;
      if (content != null) {
        return content.length();
      }
      return myFile.calcTreeElement().getTextLength();
    }

    @Override
    public long getModificationStamp() {
      return myModificationStamp;
    }
  }

  @NotNull
  @Override
  public PsiFile getStubBindingRoot() {
    final PsiFile psi = getPsi(getBaseLanguage());
    assert psi != null;
    return psi;
  }

  @NotNull
  @Override
  public final FileType getFileType() {
    return myFileType;
  }
}
