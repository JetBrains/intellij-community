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
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class SingleRootFileViewProvider extends AbstractFileViewProvider implements FileViewProvider {
  private static final Key<Boolean> OUR_NO_SIZE_LIMIT_KEY = Key.create("no.size.limit");
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.SingleRootFileViewProvider");
  private final AtomicReference<PsiFile> myPsiFile = Atomics.newReference();
  @NotNull private final Language myBaseLanguage;

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
    super(manager, virtualFile, eventSystemEnabled, type);
    myBaseLanguage = language;
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
  @NotNull
  public List<PsiFile> getAllFiles() {
    return ContainerUtil.createMaybeSingletonList(getPsi(getBaseLanguage()));
  }

  @Override
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
  public final PsiFile getCachedPsi(@NotNull Language target) {
    if (target != getBaseLanguage()) return null;
    PsiFile file = myPsiFile.get();
    return file == PsiUtilCore.NULL_PSI_FILE ? null : file;
  }

  @NotNull
  @Override
  public final List<PsiFile> getCachedPsiFiles() {
    return ContainerUtil.createMaybeSingletonList(getCachedPsi(getBaseLanguage()));
  }

  @Override
  @NotNull
  public final List<FileElement> getKnownTreeRoots() {
    PsiFile psiFile = getCachedPsi(getBaseLanguage());
    if (!(psiFile instanceof PsiFileImpl)) return Collections.emptyList();
    FileElement element = ((PsiFileImpl)psiFile).getTreeElement();
    return ContainerUtil.createMaybeSingletonList(element);
  }

  private PsiFile createFile() {
    try {
      final VirtualFile vFile = getVirtualFile();
      if (vFile.isDirectory()) return null;
      if (isIgnored()) return null;

      Project project = getManager().getProject();
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

      return createFile(project, vFile, getFileType());
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }
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

  @NotNull
  @Override
  public SingleRootFileViewProvider createCopy(@NotNull final VirtualFile copy) {
    return new SingleRootFileViewProvider(getManager(), copy, false, getBaseLanguage());
  }

  @Override
  public PsiReference findReferenceAt(final int offset) {
    final PsiFile psiFile = getPsi(getBaseLanguage());
    return findReferenceAt(psiFile, offset);
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

  public final void forceCachedPsi(@NotNull PsiFile psiFile) {
    PsiFile prev = myPsiFile.getAndSet(psiFile);
    if (prev != null && prev != psiFile && prev instanceof PsiFileEx) {
      ((PsiFileEx)prev).markInvalidated();
    }
    getManager().getFileManager().setViewProvider(getVirtualFile(), this);
  }

  @Override
  public final void markInvalidated() {
    PsiFile psiFile = getCachedPsi(getBaseLanguage());
    if (psiFile instanceof PsiFileEx) {
      ((PsiFileEx)psiFile).markInvalidated();
    }
    super.markInvalidated();
  }

}
