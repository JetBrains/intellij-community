// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

@ApiStatus.Experimental
@ApiStatus.Internal
public final class ModelBranchImpl implements ModelBranch {
  private final Map<VirtualFile, VirtualFile> myVFileCopies = new HashMap<>();
  private final Set<BranchedVirtualFile> myRenamedFiles = new LinkedHashSet<>();
  private final Map<Document, List<DocumentEvent>> myDocumentChanges = new HashMap<>();
  private final List<Runnable> myAfterMerge = new ArrayList<>();
  private final SimpleModificationTracker myVfsChanges = new SimpleModificationTracker();
  private final Project myProject;
  private boolean myMerged;

  private ModelBranchImpl(@NotNull Project project) {
    myProject = project;
    if (PsiDocumentManager.getInstance(project).hasEventSystemEnabledUncommittedDocuments()) {
      throw new IllegalStateException("Model branches may only be created on committed PSI");
    }
  }

  @NotNull
  static ModelPatch performInBranch(@NotNull Project project, @NotNull Consumer<ModelBranch> action) {
    ModelBranchImpl branch = new ModelBranchImpl(project);
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
          result.put(original, document.getImmutableCharSequence());
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
  @NotNull
  public VirtualFile findFileCopy(@NotNull VirtualFile original) {
    assert ModelBranch.getFileBranch(original) != this;
    return myVFileCopies.computeIfAbsent(original, __ -> {
      assert !(original instanceof VirtualFileWindow);
      BranchedVirtualFile copy = new BranchedVirtualFile(original, this) {

        @Override
        public @NotNull CharSequence getContent() {
          if (!getFileType().isBinary()) {
            FileViewProvider vp = PsiManagerEx.getInstanceEx(myProject).getFileManager().findViewProvider(original);
            if (vp != null) {
              Document document = FileDocumentManager.getInstance().getCachedDocument(original);
              if (document != null && PsiDocumentManager.getInstance(myProject).isUncommited(document)) {
                throw new IllegalStateException("Content loading is only allowed for committed original files");
              }
              return vp.getContents().toString();
            }
          }
          throw new UnsupportedOperationException("No string content for binary file " + this);
        }

        @Override
        public byte @NotNull [] contentsToByteArray() throws IOException {
          return original.contentsToByteArray();
        }

        @Override
        public void rename(Object requestor, @NotNull String newName) throws IOException {
          super.rename(requestor, newName);
          myVfsChanges.incModificationCount();
          myRenamedFiles.add(this);
        }

        @Override
        public @Nullable VirtualFile findChild(@NotNull String name) {
          BranchedVirtualFile renamed = ContainerUtil.find(myRenamedFiles, f -> name.equals(f.getName()) && equals(f.getParent()));
          if (renamed != null) return renamed;

          VirtualFile child = original.findChild(name);
          return child == null ? null : branch.findFileCopy(child);
        }

      };

      copy.putUserData(AbstractFileViewProvider.FREE_THREADED, true);

      return copy;
    });
  }

  void registerDocumentChange(Document document, DocumentEvent event) {
    myDocumentChanges.computeIfAbsent(document, __ -> new ArrayList<>()).add(event);
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

    PsiFile file = original.getContainingFile();
    assert file != null : original;
    VirtualFile vFileCopy = findFileCopy(file.getViewProvider().getVirtualFile());
    PsiFile fileCopy = Objects.requireNonNull(findSameLanguageRoot(file, vFileCopy));
    return Objects.requireNonNull(PsiTreeUtil.findSameElementInCopy(original, fileCopy));
  }

  @Override
  @NotNull
  public <T extends PsiSymbolReference> T obtainReferenceCopy(@NotNull T original) {
    PsiElement psiCopy = obtainPsiCopy(original.getElement());
    TextRange range = original.getRangeInElement();
    PsiReference[] refs = psiCopy.getReferences();
    T found = findSimilarReference(original, range, refs);
    if (found == null) throw new AssertionError("Cannot find " + original +
                                                " of " + original.getClass() +
                                                " at " + range +
                                                " in the copy, where references are " + Arrays.toString(refs));
    return found;
  }

  @Nullable
  private static <T> T findSimilarReference(@NotNull T original, TextRange range, PsiReference[] references) {
    //noinspection unchecked
    return (T)ContainerUtil.find(references, r -> r.getClass() == original.getClass() && range.equals(r.getRangeInElement()));
  }

  @Override
  @Nullable
  public <T extends PsiElement> T findOriginalPsi(@NotNull T branched) {
    assert myMerged;
    if (branched instanceof PsiDirectory) {
      //noinspection unchecked
      return (T)Objects.requireNonNull(PsiManager.getInstance(myProject).findDirectory(findOriginalFile(((PsiDirectory)branched).getVirtualFile())));
    }

    PsiFile branchedFile = branched.getContainingFile();
    PsiFile originalFile = findSameLanguageRoot(branchedFile, findOriginalFile(branchedFile.getViewProvider().getVirtualFile()));
    return originalFile == null ? null : PsiTreeUtil.findSameElementInCopy(branched, originalFile);
  }

  @Override
  @NotNull
  public VirtualFile findOriginalFile(@NotNull VirtualFile file) {
    BranchedVirtualFile branched = (BranchedVirtualFile)file;
    assert branched.branch == this;
    return branched.original;
  }

  @Override
  public long getBranchedPsiModificationCount() {
    return myVfsChanges.getModificationCount() +
           myDocumentChanges.keySet().stream()
             .map(PsiDocumentManager.getInstance(myProject)::getPsiFile)
             .filter(Objects::nonNull)
             .mapToLong(PsiFile::getModificationStamp)
             .sum();
  }

  private void mergeBack() {
    assert !myMerged;
    myMerged = true;

    for (BranchedVirtualFile file : myRenamedFiles) {
      VirtualFile original = file.original;
      String copyName = file.getName();
      if (!original.getName().equals(copyName)) {
        PsiFileImplUtil.saveDocumentIfFileWillBecomeBinary(original, copyName);
        try {
          original.rename(this, copyName);
        }
        catch (IOException e) {
          throw new IncorrectOperationException(e);
        }
      }
    }

    for (Document document : myDocumentChanges.keySet()) {
      VirtualFile file = Objects.requireNonNull(FileDocumentManager.getInstance().getFile(document));
      DocumentImpl original = (DocumentImpl) FileDocumentManager.getInstance().getDocument(findOriginalFile(file));
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

  @NotNull
  public GlobalSearchScope modifyScope(@NotNull GlobalSearchScope scope) {
    return new DelegatingGlobalSearchScope(scope, this, getBranchedPsiModificationCount()) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        ModelBranch fileBranch = ModelBranch.getFileBranch(file);
        if (fileBranch == ModelBranchImpl.this) {
          return super.contains(findOriginalFile(file));
        }
        return false;
      }

      @Override
      public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
        return Collections.singleton(ModelBranchImpl.this);
      }
    };
  }

  public boolean hasModifications(@NotNull VirtualFile branchFile) {
    assert ModelBranch.getFileBranch(branchFile) == this;
    return myRenamedFiles.contains(branchFile) ||
           myDocumentChanges.containsKey(FileDocumentManager.getInstance().getCachedDocument(branchFile));
  }

  public static boolean processModifiedFilesInScope(@NotNull GlobalSearchScope scope, @NotNull Processor<? super VirtualFile> processor) {
    Collection<ModelBranch> branches = scope.getModelBranchesAffectingScope();
    return branches.isEmpty() || processModifiedFilesInScope(scope, processor, branches);
  }

  private static boolean processModifiedFilesInScope(GlobalSearchScope scope,
                                                     Processor<? super VirtualFile> processor,
                                                     Collection<ModelBranch> branches) {
    for (ModelBranch branch : branches) {
      for (VirtualFile file : ((ModelBranchImpl)branch).myRenamedFiles) {
        if (scope.contains(file) && !processor.process(file)) {
          return false;
        }
      }
      for (Document document : ((ModelBranchImpl)branch).myDocumentChanges.keySet()) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file != null && scope.contains(file) && !processor.process(file)) {
          return false;
        }
      }
    }
    return true;
  }
}
