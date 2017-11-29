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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.codeInsight.daemon.impl.GlobalUsageHelper;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.psi.util.PsiMatchers;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Predicate;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class RefCountHolder {
  private final PsiFile myFile;
  // resolved elements -> list of their references
  private final MultiMap<PsiElement,PsiReference> myLocalRefsMap = MultiMap.createSet();

  private final Map<PsiAnchor, Boolean> myDclsUsedMap = new THashMap<>();
  private final Map<PsiReference, PsiImportStatementBase> myImportStatements = new THashMap<>();
  private final AtomicReference<ProgressIndicator> myState = new AtomicReference<>(EMPTY);
  // contains useful information
  private static final ProgressIndicator READY = new DaemonProgressIndicator() {
    {
      cancel();
    }
    @Override
    public String toString() {
      return "READY";
    }
  };
  // contains no information, must be rebuilt before use
  private static final ProgressIndicator EMPTY = new DaemonProgressIndicator() {
    {
      cancel();
    }
    @Override
    public String toString() {
      return "EMPTY";
    }
  };

  private static final Key<Reference<RefCountHolder>> REF_COUNT_HOLDER_IN_FILE_KEY = Key.create("REF_COUNT_HOLDER_IN_FILE_KEY");

  @NotNull
  static RefCountHolder get(@NotNull PsiFile file) {
    Reference<RefCountHolder> ref = file.getUserData(REF_COUNT_HOLDER_IN_FILE_KEY);
    RefCountHolder holder = com.intellij.reference.SoftReference.dereference(ref);
    if (holder == null) {
      holder = new RefCountHolder(file);
      Reference<RefCountHolder> newRef = new SoftReference<>(holder);
      while (true) {
        boolean replaced = ((UserDataHolderEx)file).replace(REF_COUNT_HOLDER_IN_FILE_KEY, ref, newRef);
        if (replaced) {
          break;
        }
        ref = file.getUserData(REF_COUNT_HOLDER_IN_FILE_KEY);
        RefCountHolder newHolder = com.intellij.reference.SoftReference.dereference(ref);
        if (newHolder != null) {
          holder = newHolder;
          break;
        }
      }
    }
    return holder;
  }

  private RefCountHolder(@NotNull PsiFile file) {
    myFile = file;
    log("c: created for ", file);
  }

  @NotNull
  GlobalUsageHelper getGlobalUsageHelper(@NotNull PsiFile file,
                                         @Nullable final UnusedDeclarationInspectionBase deadCodeInspection,
                                         boolean isUnusedToolEnabled) {
    FileViewProvider viewProvider = file.getViewProvider();
    Project project = file.getProject();

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    boolean inLibrary = fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInLibrarySource(virtualFile);

    boolean myDeadCodeEnabled = deadCodeInspection != null && isUnusedToolEnabled && deadCodeInspection.isGlobalEnabledInEditor();
    Predicate<PsiElement> myIsEntryPointPredicate = (@NotNull PsiElement member) -> !myDeadCodeEnabled || deadCodeInspection.isEntryPoint(member);

    return new GlobalUsageHelper() {
      @Override
      public boolean shouldCheckUsages(@NotNull PsiMember member) {
        return !inLibrary && !myIsEntryPointPredicate.apply(member);
      }

      @Override
      public boolean isCurrentFileAlreadyChecked() {
        return true;
      }

      @Override
      public boolean isLocallyUsed(@NotNull PsiNamedElement member) {
        return isReferenced(member);
      }
    };
  }

  private void clear() {
    synchronized (myLocalRefsMap) {
      myLocalRefsMap.clear();
    }
    myImportStatements.clear();
    myDclsUsedMap.clear();
  }

  void registerLocallyReferenced(@NotNull PsiNamedElement result) {
    myDclsUsedMap.put(PsiAnchor.create(result), Boolean.TRUE);
  }

  void registerReference(@NotNull PsiReference ref, @NotNull JavaResolveResult resolveResult) {
    PsiElement refElement = resolveResult.getElement();
    PsiFile psiFile = refElement == null ? null : refElement.getContainingFile();
    if (psiFile != null) psiFile = (PsiFile)psiFile.getNavigationElement(); // look at navigation elements because all references resolve into Cls elements when highlighting library source
    if (refElement != null && psiFile != null && myFile.getViewProvider().equals(psiFile.getViewProvider())) {
      registerLocalRef(ref, refElement.getNavigationElement());
    }

    PsiElement resolveScope = resolveResult.getCurrentFileResolveScope();
    if (resolveScope instanceof PsiImportStatementBase) {
      registerImportStatement(ref, (PsiImportStatementBase)resolveScope);
    }
  }

  private void registerImportStatement(@NotNull PsiReference ref, @NotNull PsiImportStatementBase importStatement) {
    myImportStatements.put(ref, importStatement);
  }

  boolean isRedundant(@NotNull PsiImportStatementBase importStatement) {
    return !myImportStatements.containsValue(importStatement);
  }

  private void registerLocalRef(@NotNull PsiReference ref, PsiElement refElement) {
    if (refElement instanceof PsiMethod && PsiTreeUtil.isAncestor(refElement, ref.getElement(), true)) return; // filter self-recursive calls
    if (refElement instanceof PsiClass && PsiTreeUtil.isAncestor(refElement, ref.getElement(), true)) return; // filter inner use of itself
    synchronized (myLocalRefsMap) {
      myLocalRefsMap.putValue(refElement, ref);
    }
  }

  private void removeInvalidRefs() {
    synchronized (myLocalRefsMap) {
      List<Pair<PsiElement, PsiReference>> toRemove = new ArrayList<>();
      for (Map.Entry<PsiElement, Collection<PsiReference>> entry : myLocalRefsMap.entrySet()) {
        PsiElement element = entry.getKey();
        for (PsiReference ref : entry.getValue()) {
          if (!ref.getElement().isValid()) {
            toRemove.add(Pair.create(element, ref));
          }
        }
      }
      for (Pair<PsiElement, PsiReference> pair : toRemove) {
        myLocalRefsMap.remove(pair.first, pair.second);
      }
    }
    myImportStatements.keySet().removeIf(ref -> !ref.getElement().isValid());
    removeInvalidFrom(myDclsUsedMap.keySet());
  }

  private static void removeInvalidFrom(@NotNull Collection<? extends PsiAnchor> collection) {
    collection.removeIf(element -> element.retrieve() == null);
  }

  boolean isReferenced(@NotNull PsiElement element) {
    Collection<PsiReference> array;
    synchronized (myLocalRefsMap) {
      array = myLocalRefsMap.get(element);
    }
    if (!array.isEmpty() && !isParameterUsedRecursively(element, array)) {
      for (PsiReference reference : array) {
        if (reference.isReferenceTo(element)) return true;
      }
    }

    Boolean usedStatus = myDclsUsedMap.get(PsiAnchor.create(element));
    return usedStatus == Boolean.TRUE;
  }

  private static boolean isParameterUsedRecursively(@NotNull PsiElement element, @NotNull Collection<PsiReference> array) {
    if (!(element instanceof PsiParameter)) return false;
    PsiParameter parameter = (PsiParameter)element;
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)scope;
    int paramIndex = ArrayUtilRt.find(method.getParameterList().getParameters(), parameter);

    for (PsiReference reference : array) {
      if (!(reference instanceof PsiElement)) return false;
      PsiElement argument = (PsiElement)reference;

      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)new PsiMatcherImpl(argument)
        .dot(PsiMatchers.hasClass(PsiReferenceExpression.class))
        .parent(PsiMatchers.hasClass(PsiExpressionList.class))
        .parent(PsiMatchers.hasClass(PsiMethodCallExpression.class))
        .getElement();
      if (methodCallExpression == null) return false;
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      if (method != methodExpression.resolve()) return false;
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      int argumentIndex = ArrayUtilRt.find(arguments, argument);
      if (paramIndex != argumentIndex) return false;
    }

    return true;
  }

  boolean isReferencedForRead(@NotNull PsiVariable variable) {
    Collection<PsiReference> array;
    synchronized (myLocalRefsMap) {
      array = myLocalRefsMap.get(variable);
    }
    if (array.isEmpty()) return false;
    for (PsiReference ref : array) {
      PsiElement refElement = ref.getElement();
      PsiElement resolved = ref.resolve();
      if (resolved != null) {
        ReadWriteAccessDetector.Access access = getAccess(ref, resolved);
        if (access == ReadWriteAccessDetector.Access.Read || access == ReadWriteAccessDetector.Access.ReadWrite) {
          if (isJustIncremented(access, refElement)) continue;
          return true;
        }
      }
    }
    return false;
  }

  private static ReadWriteAccessDetector.Access getAccess(@NotNull PsiReference ref, @NotNull PsiElement resolved) {
    PsiElement start = resolved.getLanguage() == ref.getElement().getLanguage() ? resolved : ref.getElement();
    ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(start);
    if (detector != null) {
      return detector.getReferenceAccess(resolved, ref);
    }
    return null;
  }

  // "var++;"
  private static boolean isJustIncremented(@NotNull ReadWriteAccessDetector.Access access, @NotNull PsiElement refElement) {
    return access == ReadWriteAccessDetector.Access.ReadWrite &&
           refElement instanceof PsiExpression &&
           refElement.getParent() instanceof PsiExpression &&
           refElement.getParent().getParent() instanceof PsiExpressionStatement;
  }

  boolean isReferencedForWrite(@NotNull PsiVariable variable) {
    Collection<PsiReference> array;
    synchronized (myLocalRefsMap) {
      array = myLocalRefsMap.get(variable);
    }
    if (array.isEmpty()) return false;
    for (PsiReference ref : array) {
      PsiElement resolved = ref.resolve();
      if (resolved != null) {
        ReadWriteAccessDetector.Access access = getAccess(ref, resolved);
        if (access == ReadWriteAccessDetector.Access.Write || access == ReadWriteAccessDetector.Access.ReadWrite) {
          return true;
        }
      }
    }
    return false;
  }

  boolean analyze(@NotNull PsiFile file,
                  TextRange dirtyScope,
                  @NotNull ProgressIndicator indicator,
                  @NotNull Runnable analyze) {
    ProgressIndicator result;
    if (myState.compareAndSet(EMPTY, indicator)) {
      if (!file.getTextRange().equals(dirtyScope)) {
        log(" RefCountHolder: invalid scope " + dirtyScope);
        // empty holder needs filling before it can be used, so restart daemon to re-analyze the whole file
        myState.set(EMPTY);
        return false;
      }
      result = EMPTY;
    }
    else if (myState.compareAndSet(READY, indicator)) {
      result = READY;
    }
    else {
      log("a: failed to change ", myState, "->", indicator);
      return false;
    }
    try {
      log("a: changed ", myState, "->", indicator);
      if (dirtyScope != null) {
        if (dirtyScope.equals(file.getTextRange())) {
          clear();
        }
        else {
          removeInvalidRefs();
        }
      }

      analyze.run();
      result = READY;
      return true;
    }
    finally {
      boolean set = myState.compareAndSet(indicator, result);
      assert set : myState.get();
      log("a: changed after analyze", indicator, "->", result);
    }
  }

  private static void log(@NonNls @NotNull Object... info) {
    FileStatusMap.log(info);
  }
}
