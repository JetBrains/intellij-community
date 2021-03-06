// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class SingleRootFileViewProvider extends AbstractFileViewProvider implements FileViewProvider {
  private static final Key<Boolean> OUR_NO_SIZE_LIMIT_KEY = Key.create("no.size.limit");
  private static final Logger LOG = Logger.getInstance(SingleRootFileViewProvider.class);
  @SuppressWarnings("unused")
  private volatile PsiFile myPsiFile;
  private static final AtomicReferenceFieldUpdater<SingleRootFileViewProvider, PsiFile>
    myPsiFileUpdater = AtomicReferenceFieldUpdater.newUpdater(SingleRootFileViewProvider.class, PsiFile.class, "myPsiFile");
  @NotNull private final Language myBaseLanguage;

  public SingleRootFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file) {
    this(manager, file, true);
  }

  public SingleRootFileViewProvider(@NotNull PsiManager manager,
                                    @NotNull VirtualFile virtualFile,
                                    final boolean eventSystemEnabled) {
    this(manager, virtualFile, eventSystemEnabled, calcBaseLanguage(virtualFile, manager.getProject(), virtualFile.getFileType()));
  }

  public SingleRootFileViewProvider(@NotNull PsiManager manager,
                                    @NotNull VirtualFile virtualFile,
                                    final boolean eventSystemEnabled,
                                    @NotNull final FileType fileType) {
    this(manager, virtualFile, eventSystemEnabled, calcBaseLanguage(virtualFile, manager.getProject(), fileType));
  }

  protected SingleRootFileViewProvider(@NotNull PsiManager manager,
                                       @NotNull VirtualFile virtualFile,
                                       final boolean eventSystemEnabled,
                                       @NotNull Language language) {
    super(manager, virtualFile, eventSystemEnabled);
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

    Language language = LanguageUtil.getLanguageForPsi(project, file, fileType);

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
    PsiFile psiFile = myPsiFile;
    if (psiFile == null) {
      psiFile = createFile();
      if (psiFile == null) {
        psiFile = PsiUtilCore.NULL_PSI_FILE;
      }
      boolean set = myPsiFileUpdater.compareAndSet(this, null, psiFile);
      if (!set && psiFile != PsiUtilCore.NULL_PSI_FILE) {
        PsiFile alreadyCreated = myPsiFile;
        if (alreadyCreated == psiFile) {
          LOG.error(this + ".createFile() must create new file instance but got the same: " + psiFile);
        }
        if (psiFile instanceof PsiFileEx) {
          PsiFile finalPsiFile = psiFile;
          DebugUtil.performPsiModification("invalidating throw-away copy", () ->
            ((PsiFileEx)finalPsiFile).markInvalidated()
          );
        }
        psiFile = alreadyCreated;
      }
    }
    return psiFile == PsiUtilCore.NULL_PSI_FILE ? null : psiFile;
  }

  @Override
  public final PsiFile getCachedPsi(@NotNull Language target) {
    if (target != getBaseLanguage()) return null;
    PsiFile file = myPsiFile;
    return file == PsiUtilCore.NULL_PSI_FILE ? null : file;
  }

  @NotNull
  @Override
  public final List<PsiFile> getCachedPsiFiles() {
    return ContainerUtil.createMaybeSingletonList(getCachedPsi(getBaseLanguage()));
  }

  @Override
  public final @NotNull List<FileASTNode> getKnownTreeRoots() {
    PsiFile psiFile = getCachedPsi(getBaseLanguage());
    if (!(psiFile instanceof PsiFileImpl)) return Collections.emptyList();
    FileASTNode element = ((PsiFileImpl)psiFile).getNodeIfLoaded();
    return ContainerUtil.createMaybeSingletonList(element);
  }

  private PsiFile createFile() {
    try {
      return shouldCreatePsi() ? createFile(getManager().getProject(), getVirtualFile(), getFileType()) : null;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }
  }

  public static boolean isTooLargeForIntelligence(@NotNull VirtualFile vFile) {
    return isTooLargeForIntelligence(vFile, null);
  }

  public static boolean isTooLargeForIntelligence(@NotNull VirtualFile file,
                                                  @Nullable("if content size should be retrieved from a file") Long contentSize) {
    if (!checkFileSizeLimit(file)) {
      return false;
    }
    if (file instanceof LightVirtualFile && ((LightVirtualFile)file).isTooLargeForIntelligence() == ThreeState.YES) {
      return false;
    }
    int maxSize = PersistentFSConstants.getMaxIntellisenseFileSize();
    return contentSize == null
           ? fileSizeIsGreaterThan(file, maxSize)
           : contentSize > maxSize;
  }

  public static boolean isTooLargeForContentLoading(@NotNull VirtualFile vFile) {
    return fileSizeIsGreaterThan(vFile, PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD);
  }

  public static boolean isTooLargeForContentLoading(@NotNull VirtualFile vFile,
                                                    @Nullable("if content size should be retrieved from a file") Long contentSize) {
    long maxLength = PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD;
    return contentSize == null
           ? fileSizeIsGreaterThan(vFile, maxLength)
           : contentSize > maxLength;
  }
  private static boolean checkFileSizeLimit(@NotNull VirtualFile vFile) {
    if (Boolean.TRUE.equals(vFile.getCopyableUserData(OUR_NO_SIZE_LIMIT_KEY))) {
      return false;
    }
    if (vFile instanceof LightVirtualFile) {
      VirtualFile original = ((LightVirtualFile)vFile).getOriginalFile();
      if (original != null) return checkFileSizeLimit(original);
    }
    return true;
  }

  public static void doNotCheckFileSizeLimit(@NotNull VirtualFile vFile) {
    vFile.putCopyableUserData(OUR_NO_SIZE_LIMIT_KEY, Boolean.TRUE);
  }

  public static boolean fileSizeIsGreaterThan(@NotNull VirtualFile vFile, final long maxBytes) {
    if (vFile instanceof LightVirtualFile && !vFile.getFileType().isBinary()) {
      // This is optimization in order to avoid conversion of [large] file contents to bytes
      int lengthInChars = ((LightVirtualFile)vFile).getContent().length();
      if (lengthInChars < maxBytes / 2) {
        return false;
      }
      if (lengthInChars > maxBytes ) {
        return true;
      }
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
    while (true) {
      PsiFile prev = myPsiFile;
      // jdk 6 doesn't have getAndSet()
      if (myPsiFileUpdater.compareAndSet(this, prev, psiFile)) {
        if (prev != psiFile && prev instanceof PsiFileEx) {
          DebugUtil.performPsiModification(getClass().getName() + " PSI change", () -> ((PsiFileEx)prev).markInvalidated());
        }
        break;
      }
    }
    getManager().getFileManager().setViewProvider(getVirtualFile(), this);
  }
}
