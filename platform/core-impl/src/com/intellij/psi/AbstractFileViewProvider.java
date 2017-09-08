/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
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

public abstract class AbstractFileViewProvider extends UserDataHolderBase implements FileViewProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.AbstractFileViewProvider");
  public static final Key<Object> FREE_THREADED = Key.create("FREE_THREADED");
  private static final Key<Set<AbstractFileViewProvider>> KNOWN_COPIES = Key.create("KNOWN_COPIES");
  @NotNull private final PsiManagerEx myManager;
  @NotNull private final VirtualFile myVirtualFile;
  private final boolean myEventSystemEnabled;
  private final boolean myPhysical;
  private boolean myInvalidated;
  private volatile Content myContent;
  private volatile Reference<Document> myDocument;
  @NotNull private final FileType myFileType;
  private final PsiLock myPsiLock = new PsiLock();

  protected AbstractFileViewProvider(@NotNull PsiManager manager,
                                     @NotNull VirtualFile virtualFile,
                                     boolean eventSystemEnabled,
                                     @NotNull FileType type) {
    myManager = (PsiManagerEx)manager;
    myVirtualFile = virtualFile;
    myEventSystemEnabled = eventSystemEnabled;
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

  @NotNull public PsiLock getFilePsiLock() {
    return myPsiLock;
  }

  protected final boolean isIgnored() {
    final VirtualFile file = getVirtualFile();
    return !(file instanceof LightVirtualFile) && FileTypeRegistry.getInstance().isFileIgnored(file);
  }

  @Nullable
  protected PsiFile createFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull FileType fileType) {
    if (fileType.isBinary() || file.is(VFileProperty.SPECIAL)) {
      return SingleRootFileViewProvider.isTooLargeForContentLoading(file) ?
             new PsiLargeBinaryFileImpl(((PsiManagerImpl)getManager()), this) :
             new PsiBinaryFileImpl((PsiManagerImpl)getManager(), this);
    }
    if (!SingleRootFileViewProvider.isTooLargeForIntelligence(file)) {
      final PsiFile psiFile = createFile(getBaseLanguage());
      if (psiFile != null) return psiFile;
    }

    if (SingleRootFileViewProvider.isTooLargeForContentLoading(file)) {
      return new PsiLargeTextFileImpl(this);
    }

    return new PsiPlainTextFileImpl(this);
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
  public final PsiManagerEx getManager() {
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
    if (document == null) {
      document = FileDocumentManager.getInstance().getDocument(getVirtualFile());
      myDocument = document == null ? null : new SoftReference<>(document);
    }
    return document;
  }

  @Override
  @Nullable
  public final PsiFile getPsi(@NotNull Language target) {
    if (!isPhysical()) {
      FileManager fileManager = getManager().getFileManager();
      VirtualFile virtualFile = getVirtualFile();
      if (fileManager.findCachedViewProvider(virtualFile) == null && getCachedPsiFiles().isEmpty()) {
        fileManager.setViewProvider(virtualFile, this);
      }
    }
    return getPsiInner(target);
  }

  @Nullable
  protected abstract PsiFile getPsiInner(Language target);

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public FileViewProvider clone() {
    VirtualFile origFile = getVirtualFile();
    LightVirtualFile copy = new LightVirtualFile(origFile.getName(), myFileType, getContents(), origFile.getCharset(), getModificationStamp());
    copy.setOriginalFile(origFile);
    copy.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    copy.setCharset(origFile.getCharset());

    return createCopy(copy);
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

  @Nullable
  public static PsiElement findElementAt(@Nullable PsiElement psiFile, final int offset) {
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

  public final void beforeDocumentChanged(@Nullable PsiFile psiCause) {
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
      if (!isDocumentConsistentWithPsi(fileLength, fileElement, nodeLength)) {
        PsiUtilCore.ensureValid(fileElement.getPsi());
        List<Attachment> attachments = ContainerUtil.newArrayList(new Attachment(myVirtualFile.getName(), myContent.getText().toString()),
                                                                  new Attachment(myVirtualFile.getNameWithoutExtension() + ".tree.txt", fileElement.getText()));
        if (document != null) {
          attachments.add(new Attachment(myVirtualFile.getNameWithoutExtension() + ".document.txt", document.getText()));
        }
        // exceptions here should be assigned to peter
        LOG.error("Inconsistent " + fileElement.getElementType() + " tree in " + this + "; nodeLength=" + nodeLength + "; fileLength=" + fileLength,
                  attachments.toArray(Attachment.EMPTY_ARRAY));
      }
    }
  }

  private boolean isDocumentConsistentWithPsi(int fileLength, FileElement fileElement, int nodeLength) {
    if (nodeLength != fileLength) return false;

    if (ApplicationManager.getApplication().isUnitTestMode() && !ApplicationInfoImpl.isInStressTest()) {
      return fileElement.textMatches(myContent.getText());
    }

    return true;
  }


  @NonNls
  @Override
  public String toString() {
    return getClass().getSimpleName() + "{myVirtualFile=" + myVirtualFile + ", content=" + getContent() + '}';
  }

  public abstract PsiFile getCachedPsi(@NotNull Language target);

  public abstract List<PsiFile> getCachedPsiFiles();

  @NotNull
  public abstract List<FileElement> getKnownTreeRoots();

  public void markInvalidated() {
    if (myInvalidated) return;

    myInvalidated = true;
    invalidateCopies();
  }

  private void invalidateCopies() {
    Set<AbstractFileViewProvider> knownCopies = getUserData(KNOWN_COPIES);
    if (knownCopies != null) {
      for (AbstractFileViewProvider copy : knownCopies) {
        if (copy.getCachedPsiFiles().stream().anyMatch(f -> f.getOriginalFile().getViewProvider() == this)) {
          myManager.getFileManager().setViewProvider(copy.getVirtualFile(), null);
        }
      }
    }
  }

  public final void registerAsCopy(@NotNull AbstractFileViewProvider copy) {
    Set<AbstractFileViewProvider> copies = getUserData(KNOWN_COPIES);
    if (copies == null) {
      copies = putUserDataIfAbsent(KNOWN_COPIES, Collections.newSetFromMap(ContainerUtil.createConcurrentWeakMap()));
    }
    copies.add(copy);
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
