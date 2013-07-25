/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.file.exclude.ProjectFileExclusionManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import com.intellij.psi.impl.file.PsiLargeFileImpl;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ReflectionCache;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class SingleRootFileViewProvider extends UserDataHolderBase implements FileViewProvider {
  private static final Key<Boolean> OUR_NO_SIZE_LIMIT_KEY = Key.create("no.size.limit");
  private static final Logger LOG = Logger.getInstance("#" + SingleRootFileViewProvider.class.getCanonicalName());
  @NotNull private final PsiManager myManager;
  @NotNull private final VirtualFile myVirtualFile;
  private final boolean myEventSystemEnabled;
  private final boolean myPhysical;
  private final AtomicReference<PsiFile> myPsiFile = new AtomicReference<PsiFile>();
  private volatile Content myContent;
  private volatile SoftReference<Document> myDocument;
  @NotNull private final Language myBaseLanguage;
  private final ProjectFileExclusionManager myExclusionManager;

  public SingleRootFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file) {
    this(manager, file, true);
  }

  public SingleRootFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile virtualFile, final boolean physical) {
    this(manager, virtualFile, physical, calcBaseLanguage(virtualFile, manager.getProject()));
  }

  protected SingleRootFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile virtualFile, final boolean physical, @NotNull Language language) {
    myManager = manager;
    myVirtualFile = virtualFile;
    myEventSystemEnabled = physical;
    myBaseLanguage = language;
    setContent(new VirtualFileContent());
    myPhysical = isEventSystemEnabled() &&
                 !(virtualFile instanceof LightVirtualFile) &&
                 !(virtualFile.getFileSystem() instanceof NonPhysicalFileSystem);
    myExclusionManager = ProjectFileExclusionManager.SERVICE.getInstance(manager.getProject());
  }

  @Override
  @NotNull
  public Language getBaseLanguage() {
    return myBaseLanguage;
  }

  private static Language calcBaseLanguage(@NotNull VirtualFile file, @NotNull Project project) {
    if (file instanceof LightVirtualFile) {
      final Language language = ((LightVirtualFile)file).getLanguage();
      if (language != null) {
        return language;
      }
    }

    FileType fileType = file.getFileType();
    // Do not load content
    if (fileType == UnknownFileType.INSTANCE) {
      fileType = FileTypeRegistry.getInstance().detectFileTypeFromContent(file);
    }
    if (fileType.isBinary()) return Language.ANY;
    if (isTooLargeForIntelligence(file)) return PlainTextLanguage.INSTANCE;

    if (fileType instanceof LanguageFileType) {
      return LanguageSubstitutors.INSTANCE.substituteLanguage(((LanguageFileType)fileType).getLanguage(), file, project);
    }

    final ContentBasedFileSubstitutor[] processors = Extensions.getExtensions(ContentBasedFileSubstitutor.EP_NAME);
    for (ContentBasedFileSubstitutor processor : processors) {
      Language language = processor.obtainLanguageForFile(file);
      if (language != null) return language;
    }

    return PlainTextLanguage.INSTANCE;
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
      if (fileManager.findCachedViewProvider(virtualFile) == null) {
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
      boolean set = myPsiFile.compareAndSet(null, psiFile);
      if (!set) {
        psiFile = myPsiFile.get();
      }
    }
    return psiFile;
  }

  @Override
  public void beforeContentsSynchronized() {
    unsetPsiContent();
  }

  @Override
  public void contentsSynchronized() {
    unsetPsiContent();
  }

  private void unsetPsiContent() {
    if (!(myContent instanceof PsiFileContent)) return;
    final Document cachedDocument = getCachedDocument();
    setContent(cachedDocument == null ? new VirtualFileContent() : new DocumentContent());
  }

  public void beforeDocumentChanged(@Nullable PsiFile psiCause) {
    PsiFile psiFile = psiCause != null ? psiCause : getPsi(getBaseLanguage());
    if (psiFile instanceof PsiFileImpl) {
      setContent(new PsiFileContent((PsiFileImpl)psiFile, psiCause == null ? getModificationStamp() : LocalTimeCounter.currentTime()));
    }
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


  public PsiFile getCachedPsi(Language target) {
    return myPsiFile.get();
  }

  public FileElement[] getKnownTreeRoots() {
    PsiFile psiFile = myPsiFile.get();
    if (psiFile == null || !(psiFile instanceof PsiFileImpl)) return new FileElement[0];
    if (((PsiFileImpl)psiFile).getTreeElement() == null) return new FileElement[0];
    return new FileElement[]{(FileElement)psiFile.getNode()};
  }

  private PsiFile createFile() {
    try {
      final VirtualFile vFile = getVirtualFile();
      if (vFile.isDirectory()) return null;
      if (isIgnored()) return null;

      final Project project = myManager.getProject();
      if (isPhysical()) { // check directories consistency
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

      FileType fileType = vFile.getFileType();
      return createFile(project, vFile, fileType);
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
    if (file instanceof LightVirtualFile) return false;
    if (myExclusionManager != null && myExclusionManager.isExcluded(file)) return true;
    return FileTypeRegistry.getInstance().isFileIgnored(file);
  }

  @Nullable
  protected PsiFile createFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull FileType fileType) {
    if (fileType.isBinary() || file.is(VFileProperty.SPECIAL)) {
      return new PsiBinaryFileImpl((PsiManagerImpl)getManager(), this);
    }
    if (!isTooLargeForIntelligence(file)) {
      final PsiFile psiFile = createFile(getBaseLanguage());
      if (psiFile != null) return psiFile;
    }

    if (isTooLargeForContentLoading(file)) {
      return new PsiLargeFileImpl((PsiManagerImpl)getManager(), this);
    }

    return new PsiPlainTextFileImpl(this);
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  public static boolean isTooLarge(@NotNull VirtualFile vFile) {
    return isTooLargeForIntelligence(vFile);
  }

  public static boolean isTooLargeForIntelligence(@NotNull VirtualFile vFile) {
    if (!checkFileSizeLimit(vFile)) return false;
    return fileSizeIsGreaterThan(vFile, PersistentFSConstants.getMaxIntellisenseFileSize());
  }

  public static boolean isTooLargeForContentLoading(@NotNull VirtualFile vFile) {
    if (!checkFileSizeLimit(vFile)) return false;
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

  private static boolean fileSizeIsGreaterThan(@NotNull VirtualFile vFile, final long maxBytes) {
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
    final Document document = myDocument != null ? myDocument.get() : null;
    if (document != null) return document;
    return FileDocumentManager.getInstance().getCachedDocument(getVirtualFile());
  }

  @Override
  public Document getDocument() {
    Document document = myDocument != null ? myDocument.get() : null;
    if (document == null/* TODO[ik] make this change && isEventSystemEnabled()*/) {
      document = FileDocumentManager.getInstance().getDocument(getVirtualFile());
      myDocument = new SoftReference<Document>(document);
    }
    if (document != null && getContent() instanceof VirtualFileContent) {
      setContent(new DocumentContent());
    }
    return document;
  }

  @Override
  public FileViewProvider clone() {
    final VirtualFile origFile = getVirtualFile();
    LightVirtualFile copy = new LightVirtualFile(origFile.getName(), origFile.getFileType(), getContents(), origFile.getCharset(), getModificationStamp());
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
    final PsiFileImpl psiFile = (PsiFileImpl)getPsi(getBaseLanguage());
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
  private static PsiReference findReferenceAt(@Nullable final PsiFile psiFile, final int offset) {
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
    if (!ReflectionCache.isAssignable(lang, getBaseLanguage().getClass())) return null;
    return findElementAt(offset);
  }

  @Nullable
  protected static PsiElement findElementAt(@Nullable final PsiElement psiFile, final int offset) {
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
      return child.findElementAt(offsetInElement);
    }
    return null;
  }

  public void forceCachedPsi(final PsiFile psiFile) {
    myPsiFile.set(psiFile);
    ((PsiManagerEx)myManager).getFileManager().setViewProvider(getVirtualFile(), this);
  }

  private Content getContent() {
    return myContent;
  }

  private void setContent(final Content content) {
    // temporarily commented
    //if (myPhysical) {
    //  final Content oldContent = myContent;
    //  if (oldContent != null && content.getModificationStamp() != oldContent.getModificationStamp()) {
    //    ApplicationManager.getApplication().assertWriteAccessAllowed();
    //  }
    //}
    myContent = content;
  }

  @NonNls
  @Override
  public String toString() {
    return getClass().getSimpleName() + "{myVirtualFile=" + myVirtualFile + ", content=" + getContent() + '}';
  }

  private interface Content {
    CharSequence getText();

    long getModificationStamp();
  }

  private class VirtualFileContent implements Content {
    @Override
    public CharSequence getText() {
      final VirtualFile virtualFile = getVirtualFile();
      if (virtualFile instanceof LightVirtualFile) {
        Document doc = getCachedDocument();
        if (doc != null) return doc.getCharsSequence();
        return ((LightVirtualFile)virtualFile).getContent();
      }

      final Document document = getDocument();
      if (document == null) {
        return LoadTextUtil.loadText(virtualFile);
      }
      else {
        return document.getCharsSequence();
      }
    }

    @Override
    public long getModificationStamp() {
      return getVirtualFile().getModificationStamp();
    }

    @NonNls
    @Override
    public String toString() {
      return "VirtualFileContent{size=" + getVirtualFile().getLength() + "}";
    }
  }

  private class DocumentContent implements Content {
    @NonNls
    @Override
    public String toString() {
      final Document document = getDocument();
      return "DocumentContent{size=" + (document == null ? null : document.getTextLength()) + "}";
    }

    @NotNull
    @Override
    public CharSequence getText() {
      final Document document = getDocument();
      assert document != null;
      return document.getCharsSequence();
    }

    @Override
    public long getModificationStamp() {
      Document document = myDocument == null ? null : myDocument.get();
      if (document != null) return document.getModificationStamp();
      return myVirtualFile.getModificationStamp();
    }
  }

  private class PsiFileContent implements Content {
    private final PsiFileImpl myFile;
    private volatile String myContent = null;
    private final long myModificationStamp;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<FileElement> myFileElementHardRefs = new SmartList<FileElement>();

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
        myContent = content = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
          @Override
          public String compute() {
            return myFile.calcTreeElement().getText();
          }
        });
      }
      return content;
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
}
