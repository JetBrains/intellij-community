// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

@ApiStatus.Experimental
@ApiStatus.Internal
public abstract class ModelBranchImpl extends UserDataHolderBase implements ModelBranch {
  private static final Logger LOG = Logger.getInstance(ModelBranchImpl.class);
  private final Map<VirtualFile, BranchedVirtualFileImpl> myVFileCopies = new HashMap<>();
  private final Set<BranchedVirtualFileImpl> myVfsStructureChanges = new LinkedHashSet<>();
  private final Set<BranchedVirtualFileImpl> myAffectedFiles = new HashSet<>();
  private final Map<Document, List<DocumentEvent>> myDocumentChanges = new HashMap<>();
  private final List<Runnable> myAfterMerge = new ArrayList<>();
  private final SimpleModificationTracker myVfsChanges = new SimpleModificationTracker();
  private final Project myProject;
  private final @NotNull Throwable myCreationTrace;
  private boolean myMerged;

  ModelBranchImpl(@NotNull Project project) {
    myProject = project;
    myCreationTrace = ThrowableInterner.intern(new Throwable());
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (PsiDocumentManager.getInstance(project).hasEventSystemEnabledUncommittedDocuments()) {
      throw new IllegalStateException("Model branches may only be created on committed PSI");
    }
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  void addVfsStructureChange(BranchedVirtualFileImpl file) {
    myVfsChanges.incModificationCount();

    PsiManagerImpl psiManager = (PsiManagerImpl)PsiManager.getInstance(myProject);
    psiManager.beforeChange(false);
    psiManager.afterChange(false);

    myVfsStructureChanges.add(file);

    VfsUtilCore.processFilesRecursively(file, each -> {
      myAffectedFiles.add((BranchedVirtualFileImpl)each);
      return true;
    });
  }

  @NotNull
  static ModelPatch performInBranch(@NotNull Consumer<? super ModelBranch> action, @NotNull ModelBranchImpl branch) {
    action.accept(branch);
    return new ModelPatch() {
      @Override
      public void applyBranchChanges() {
        branch.mergeBack();
      }

      @Override
      public @NotNull Map<VirtualFile, CharSequence> getBranchChanges() {
        Map<VirtualFile, CharSequence> result = new HashMap<>();
        for (Document document : branch.myDocumentChanges.keySet()) {
          VirtualFile file = Objects.requireNonNull(FileDocumentManager.getInstance().getFile(document));
          VirtualFile original = branch.findOriginalFile(file);
          if (original != null) {
            result.put(original, document.getImmutableCharSequence());
          }
        }
        return result;
      }
    };
  }

  @Override
  public void runAfterMerge(@NotNull Runnable action) {
    myAfterMerge.add(action);
  }

  @Override
  public @Nullable VirtualFile findFileByUrl(@NotNull String url) {
    int prefixEnd = url.length();
    while (prefixEnd > 0) {
      VirtualFile someParent = VirtualFileManager.getInstance().findFileByUrl(url.substring(0, prefixEnd));
      if (someParent != null) {
        return findFileByUrl(url, findPhysicalFileCopy(someParent));
      }
      prefixEnd = url.lastIndexOf('/', prefixEnd - 1);
    }
    return null;
  }

  @Nullable
  private VirtualFile findFileByUrl(@NotNull String url, @NotNull BranchedVirtualFileImpl someCopyFromSameFS) {
    BranchedVirtualFileImpl topmostChange =
      JBIterable.generate(someCopyFromSameFS, BranchedVirtualFileImpl::getParent).filter(myVfsStructureChanges::contains).last();
    BranchedVirtualFileImpl stableAncestor = topmostChange != null ? topmostChange.getParent() : someCopyFromSameFS;
    String stableUrl = Objects.requireNonNull(findOriginalFile(stableAncestor)).getUrl();

    if (url.equals(stableUrl)) {
      return stableAncestor;
    }

    if (!url.startsWith(stableUrl)) {
      LOG.error("Inconsistent branch copies, please include attachment with paths",
                new Attachment("urls.txt", "url=" + url + "\nstableUrl=" + stableUrl));
      return null;
    }
    return stableAncestor.findFileByRelativePath(url.substring(stableUrl.length() + 1));
  }

  @Override
  @NotNull
  public VirtualFile findFileCopy(@NotNull VirtualFile original) {
    return original instanceof VirtualFileWindow ? findInjectedFileCopy((VirtualFileWindow)original) : findPhysicalFileCopy(original);
  }

  @NotNull
  private VirtualFile findInjectedFileCopy(VirtualFileWindow original) {
    VirtualFile hostCopy = findPhysicalFileCopy(original.getDelegate());
    DocumentWindow injectedDoc = original.getDocumentWindow();
    PsiFile hostPsi = PsiManager.getInstance(myProject).findFile(hostCopy);
    assert hostPsi != null;
    PsiElement leaf =
      InjectedLanguageManager.getInstance(myProject).findInjectedElementAt(hostPsi, injectedDoc.getHostRanges()[0].getStartOffset());
    assert leaf != null;
    PsiFile injectedCopy = leaf.getContainingFile();
    return injectedCopy.getViewProvider().getVirtualFile();
  }

  @NotNull
  BranchedVirtualFileImpl findPhysicalFileCopy(@NotNull VirtualFile original) {
    assert ModelBranch.getFileBranch(original) != this;
    return myVFileCopies.computeIfAbsent(original, __ -> {
      assert original instanceof VirtualFileWithId;
      return new BranchedVirtualFileImpl(this, original, original.getName(), original.isDirectory(), null);
    });
  }

  void registerDocumentChange(Document document, DocumentEvent event, BranchedVirtualFileImpl file) {
    myDocumentChanges.computeIfAbsent(document, __ -> new ArrayList<>()).add(event);
    myAffectedFiles.add(file);
  }

  @Nullable
  private PsiFile findSameLanguageRoot(@Nullable PsiFile original, @Nullable VirtualFile vFileCopy) {
    if (original == null || vFileCopy == null) return null;
    FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(vFileCopy);
    return viewProvider == null ? null : viewProvider.getPsi(original.getLanguage());
  }

  @Override
  @NotNull
  public <T extends PsiElement> T obtainPsiCopy(@NotNull T original) {
    if (original instanceof PsiDirectory) {
      //noinspection unchecked
      return (T)Objects.requireNonNull(PsiManager.getInstance(myProject).findDirectory(findFileCopy(((PsiDirectory)original).getVirtualFile())));
    }
    if (original instanceof BranchableSyntheticPsiElement) {
      //noinspection unchecked
      return (T)((BranchableSyntheticPsiElement)original).obtainBranchCopy(this);
    }

    PsiFile file = original.getContainingFile();
    assert file != null : original;
    VirtualFile vFileCopy = findFileCopy(file.getViewProvider().getVirtualFile());
    PsiFile fileCopy = Objects.requireNonNull(findSameLanguageRoot(file, vFileCopy));
    return Objects.requireNonNull(PsiTreeUtil.findSameElementInCopy(original, fileCopy));
  }

  @Override
  @NotNull
  public <T extends PsiReference> T obtainReferenceCopy(@NotNull T original) {
    PsiElement psiCopy = obtainPsiCopy(original.getElement());
    TextRange range = original.getRangeInElement();
    PsiReference[] refs = psiCopy.getReferences();

    T found = findSimilarReference(original, range, refs);
    if (found == null) {
      throw new AssertionError("Cannot find " + original +
                                                  " of " + original.getClass() +
                                                  " at " + range +
                                                  " in the copy, where references are " + Arrays.toString(refs));
    }
    return found;
  }

  private int findIndex(@NotNull PsiReference original) {
    PsiElement element = original.getElement();
    PsiReference[] references = element.getReferences();
    return ArrayUtil.indexOf(references, original);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private <T extends PsiReference> T findSimilarReference(@NotNull T original, TextRange range, PsiReference[] references) {
    Condition<PsiReference> isSimilar = r -> r.getClass() == original.getClass() && range.equals(r.getRangeInElement());

    //try to get ref of the same index
    int index = findIndex(original);
    if (index >= 0 && index < references.length && isSimilar.value(references[index])) {
      return (T)references[index];
    }

    //ok, try any similar reference
    return (T)ContainerUtil.find(references, isSimilar);
  }

  @Override
  @Nullable
  public <T extends PsiElement> T findOriginalPsi(@NotNull T branched) {
    if (branched instanceof PsiDirectory) {
      VirtualFile originalDir = findOriginalFile(((PsiDirectory) branched).getVirtualFile());
      if (originalDir == null) return null;
      //noinspection unchecked
      return (T)Objects.requireNonNull(PsiManager.getInstance(myProject).findDirectory(originalDir));
    }

    PsiFile branchedFile = branched.getContainingFile();
    PsiFile originalFile = findSameLanguageRoot(branchedFile, findOriginalFile(branchedFile.getViewProvider().getVirtualFile()));
    return originalFile == null ? null : PsiTreeUtil.findSameElementInCopy(branched, originalFile);
  }

  @Override
  @Nullable
  public VirtualFile findOriginalFile(@NotNull VirtualFile file) {
    BranchedVirtualFileImpl branched = (BranchedVirtualFileImpl)file;
    assert branched.getBranch() == this;
    return branched.getOriginal();
  }

  @Override
  public long getBranchedPsiModificationCount() {
    return getBranchedVfsStructureModificationCount() +
           myDocumentChanges.keySet().stream()
             .map(PsiDocumentManager.getInstance(myProject)::getPsiFile)
             .filter(Objects::nonNull)
             .mapToLong(PsiFile::getModificationStamp)
             .sum();
  }

  @Override
  public long getBranchedVfsStructureModificationCount() {
    return myVfsChanges.getModificationCount();
  }

  private void mergeBack() {
    checkBranchIsAlive();

    try {
      try {
        for (BranchedVirtualFileImpl file : myVfsStructureChanges) {
          VirtualFile original = file.getOrCreateOriginal();
          String copyName = file.getName();
          if (!original.getName().equals(copyName)) {
            PsiFileImplUtil.saveDocumentIfFileWillBecomeBinary(original, copyName);
            original.rename(this, copyName);
          }
          VirtualFile newParent = findOriginalFile(file.getParent());
          if (!original.getParent().equals(newParent)) {
            assert newParent != null;
            original.move(this, newParent);
          }
        }
      }
      catch (IOException e) {
        throw new IncorrectOperationException(e);
      }
  
      for (Document document : myDocumentChanges.keySet()) {
        VirtualFile file = Objects.requireNonNull(FileDocumentManager.getInstance().getFile(document));
        DocumentImpl original = (DocumentImpl) FileDocumentManager.getInstance().getDocument(Objects.requireNonNull(findOriginalFile(file)));
        assert original != null;
  
        for (DocumentEvent event : myDocumentChanges.get(document)) {
          original.replaceString(event.getOffset(), event.getOffset() + event.getOldLength(), event.getMoveOffset(),
                                 event.getNewFragment(), LocalTimeCounter.currentTime(), false);
        }
      }
  
      for (Runnable runnable : myAfterMerge) {
        runnable.run();
      }
    }
    finally {
      myMerged = true;
    }
  }

  @NotNull
  public GlobalSearchScope modifyScope(@NotNull GlobalSearchScope scope) {
    return new DelegatingGlobalSearchScope(scope, this, getBranchedPsiModificationCount()) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return ModelBranch.getFileBranch(file) == ModelBranchImpl.this && super.contains(file);
      }

      @Override
      public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
        if (myMerged) {
          return Collections.emptyList();
        }
        return Collections.singleton(ModelBranchImpl.this);
      }
    };
  }

  public boolean hasModifications(@NotNull VirtualFile branchFile) {
    assert ModelBranch.getFileBranch(branchFile) == this;
    return myAffectedFiles.contains(branchFile);
  }

  public static boolean processModifiedFilesInScope(@NotNull GlobalSearchScope scope, @NotNull Processor<? super VirtualFile> processor) {
    Collection<ModelBranch> branches = scope.getModelBranchesAffectingScope();
    return branches.isEmpty() || processModifiedFilesInScope(scope, processor, branches);
  }

  private static boolean processModifiedFilesInScope(GlobalSearchScope scope,
                                                     Processor<? super VirtualFile> processor,
                                                     Collection<? extends ModelBranch> branches) {
    for (ModelBranch branch : branches) {
      for (VirtualFile file : ((ModelBranchImpl)branch).myAffectedFiles) {
        if (scope.contains(file) && !processor.process(file)) {
          return false;
        }
      }
    }
    return true;
  }

  protected abstract void assertAllChildrenLoaded(@NotNull VirtualFile file);
  
  void checkBranchIsAlive() {
    if (myMerged) {
      LOG.error("Attempting to access merged branch [" + hashCode() + "]", 
        new Attachment("creation.trace", myCreationTrace));
    }
  }
}
