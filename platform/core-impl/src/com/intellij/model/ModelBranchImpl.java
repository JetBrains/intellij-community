// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

@ApiStatus.Experimental
@ApiStatus.Internal
public final class ModelBranchImpl implements ModelBranch {
  private final Map<VirtualFile, VirtualFile> myVFileCopies = new HashMap<>();
  private final Map<Document, List<DocumentEvent>> myDocumentChanges = new HashMap<>();
  private final List<Runnable> myAfterMerge = new ArrayList<>();
  private final SimpleModificationTracker myFileSetChanges = new SimpleModificationTracker();
  private final Project myProject;
  private boolean myMerged;

  private ModelBranchImpl(@NotNull Project project) {
    myProject = project;
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

  @NotNull
  private VirtualFile obtainFileCopy(@NotNull VirtualFile original) {
    return myVFileCopies.computeIfAbsent(original, __ -> {
      assert !(original instanceof VirtualFileWindow);
      Document origDoc = FileDocumentManager.getInstance().getDocument(original);
      assert origDoc != null;
      assert PsiDocumentManager.getInstance(myProject).isCommitted(origDoc);

      VirtualFile copy = new BranchedVirtualFile(original, origDoc.getImmutableCharSequence(), this);
      copy.putUserData(AbstractFileViewProvider.FREE_THREADED, true);
      myFileSetChanges.incModificationCount();

      Document copyDoc = FileDocumentManager.getInstance().getDocument(copy);
      assert copyDoc != null;

      List<DocumentEvent> events = new ArrayList<>();
      myDocumentChanges.put(copyDoc, events);
      copyDoc.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          events.add(event);
        }
      });

      return copy;
    });
  }

  @Nullable
  private PsiFile findSameLanguageRoot(@Nullable PsiFile original, @Nullable VirtualFile vFileCopy) {
    if (original == null || vFileCopy == null) return null;
    FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(vFileCopy);
    return viewProvider == null ? null : viewProvider.getPsi(original.getLanguage());
  }

  @Override
  @Nullable
  public VirtualFile findFileCopy(@NotNull VirtualFile file) {
    assert ModelBranch.getFileBranch(file) != this;
    return myVFileCopies.get(file);
  }

  @Override
  @NotNull
  public <T extends PsiElement> T obtainPsiCopy(@NotNull T original) {
    PsiFile file = original.getContainingFile();
    assert file != null : original;
    VirtualFile vFileCopy = obtainFileCopy(file.getViewProvider().getVirtualFile());
    PsiFile fileCopy = Objects.requireNonNull(findSameLanguageRoot(file, vFileCopy));
    return Objects.requireNonNull(PsiTreeUtil.findSameElementInCopy(original, fileCopy));
  }

  @Override
  @Nullable
  public <T extends PsiElement> T findPsiCopy(@NotNull T original) {
    PsiFile file = original.getContainingFile();
    if (file == null) return null;
    PsiFile fileCopy = findSameLanguageRoot(file, findFileCopy(file.getViewProvider().getVirtualFile()));
    return fileCopy == null ? null : PsiTreeUtil.findSameElementInCopy(original, fileCopy);
  }

  @Override
  @Nullable
  public <T extends PsiSymbolReference> T findReferenceCopy(@NotNull T original) {
    PsiElement psiCopy = findPsiCopy(original.getElement());
    if (psiCopy == null) return null;

    TextRange range = original.getRangeInElement();
    //noinspection unchecked
    return (T)ContainerUtil.find(psiCopy.getReferences(), r -> r.getClass() == original.getClass() && range.equals(r.getRangeInElement()));
  }

  @Override
  @Nullable
  public <T extends PsiElement> T findOriginalPsi(@NotNull T branched) {
    assert myMerged;
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
    return myFileSetChanges.getModificationCount() +
           myVFileCopies.values().stream()
             .map(PsiManager.getInstance(myProject)::findFile)
             .filter(Objects::nonNull)
             .mapToLong(PsiFile::getModificationStamp)
             .sum();
  }

  private void mergeBack() {
    assert !myMerged;
    myMerged = true;

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
        if (fileBranch == null) {
          return findFileCopy(file) == null && super.contains(file);
        }
        return false;
      }

      @Override
      public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
        return Collections.singleton(ModelBranchImpl.this);
      }
    };
  }

  public static boolean hasBranchedFilesInScope(@NotNull GlobalSearchScope scope) {
    return !processBranchedFilesInScope(scope, __ -> false);
  }

  public static boolean processBranchedFilesInScope(@NotNull GlobalSearchScope scope, @NotNull Processor<? super VirtualFile> processor) {
    Collection<ModelBranch> branches = scope.getModelBranchesAffectingScope();
    return branches.isEmpty() || processBranchedFilesInScope(scope, processor, branches);
  }

  private static boolean processBranchedFilesInScope(GlobalSearchScope scope,
                                                     Processor<? super VirtualFile> processor,
                                                     Collection<ModelBranch> branches) {
    for (ModelBranch branch : branches) {
      for (VirtualFile file : ((ModelBranchImpl)branch).myVFileCopies.values()) {
        if (scope.contains(file) && !processor.process(file)) {
          return false;
        }
      }
    }
    return true;
  }
}
