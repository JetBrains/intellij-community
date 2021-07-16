// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.codeInsight.daemon.impl.GlobalUsageHelper;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.concurrency.ConcurrentCollectionFactory;
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
import com.intellij.util.containers.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashSet;
import java.util.*;

final class RefCountHolder {
  private final PsiFile myFile;
  // resolved elements -> list of their references in this file
  private final MultiMap<PsiElement, PsiReference> myLocalRefsMap = MultiMap.createConcurrentSet();

  private final Set<PsiAnchor> myDclsUsedMap = ConcurrentCollectionFactory.createConcurrentSet(HashingStrategy.canonical());
  // reference -> import statement the reference has come from
  private final Map<PsiReference, PsiImportStatementBase> myImportStatements = ConcurrentCollectionFactory.createConcurrentMap();
  // There are two possible states of RefCountHolder:
  // - ready: RefCountHolder is finished updating, can be queried;
  // - not_ready: RefCountHolder is empty or being updated now, info can be inconsistent
  private volatile boolean myReady;

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
    boolean inLibrary = fileIndex.isInLibrary(virtualFile);

    boolean isDeadCodeEnabled = deadCodeInspection != null && isUnusedToolEnabled && deadCodeInspection.isGlobalEnabledInEditor();
    if (isDeadCodeEnabled && !inLibrary) {
      return new GlobalUsageHelperBase() {
        final Map<PsiMember, Boolean> myEntryPointCache = FactoryMap.create((PsiMember member) -> {
          if (deadCodeInspection.isEntryPoint(member)) return true;
          if (member instanceof PsiClass) {
            return !JBTreeTraverser
              .<PsiMember>from(m -> m instanceof PsiClass
                                    ? JBIterable.from(PsiTreeUtil.getStubChildrenOfTypeAsList(m, PsiMember.class))
                                    : JBIterable.empty())
              .withRoot(member)
              .traverse()
              .skip(1)
              .processEach(this::shouldCheckUsages);
          }
          return false;
        });

        @Override
        public boolean shouldCheckUsages(@NotNull PsiMember member) {
          return !myEntryPointCache.get(member);
        }
      };
    }
    return new GlobalUsageHelperBase();
  }

  private void clear() {
    myLocalRefsMap.clear();
    myImportStatements.clear();
    myDclsUsedMap.clear();
  }

  void registerLocallyReferenced(@NotNull PsiNamedElement result) {
    myDclsUsedMap.add(PsiAnchor.create(result));
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
    else if (refElement == null && ref instanceof PsiJavaReference) {
      for (JavaResolveResult result : ((PsiJavaReference)ref).multiResolve(true)) {
        resolveScope = result.getCurrentFileResolveScope();
        if (resolveScope instanceof PsiImportStatementBase) {
          registerImportStatement(ref, (PsiImportStatementBase)resolveScope);
          break;
        }
      }
    }
  }

  private void registerImportStatement(@NotNull PsiReference ref, @NotNull PsiImportStatementBase importStatement) {
    myImportStatements.put(ref, importStatement);
  }

  boolean isRedundant(@NotNull PsiImportStatementBase importStatement) {
    return !myImportStatements.containsValue(importStatement);
  }

  private void registerLocalRef(@NotNull PsiReference ref, PsiElement refElement) {
    PsiElement element = ref.getElement();
    if (refElement instanceof PsiMethod && PsiTreeUtil.isAncestor(refElement, element, true)) return; // filter self-recursive calls
    if (refElement instanceof PsiClass) {
      if (PsiTreeUtil.isAncestor(refElement, element, true)) {
        return; // filter inner use of itself
      }
    }
    myLocalRefsMap.putValue(refElement, ref);
  }

  private void removeInvalidRefs() {
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
    myImportStatements.entrySet().removeIf(e -> !e.getValue().isValid() || !e.getKey().getElement().isValid());
    removeInvalidFrom(myDclsUsedMap);
  }

  private static void removeInvalidFrom(@NotNull Collection<? extends PsiAnchor> collection) {
    collection.removeIf(element -> element.retrieve() == null);
  }

  boolean isReferenced(@NotNull PsiElement element) {
    Collection<PsiReference> array = myLocalRefsMap.get(element);
    if (!array.isEmpty() &&
        !isParameterUsedRecursively(element, array) &&
        !isClassUsedForInnerImports(element, array)) {
      for (PsiReference reference : array) {
        if (reference.isReferenceTo(element)) return true;
      }
    }

    return myDclsUsedMap.contains(PsiAnchor.create(element));
  }

  private boolean isClassUsedForInnerImports(@NotNull PsiElement element, @NotNull Collection<? extends PsiReference> array) {
    if (!(element instanceof PsiClass)) return false;

    Set<PsiImportStatementBase> imports = new HashSet<>();
    for (PsiReference classReference : array) {
      PsiImportStatementBase importStmt = PsiTreeUtil.getParentOfType(classReference.getElement(), PsiImportStatementBase.class);
      if (importStmt == null) return false;
      imports.add(importStmt);
    }

    return imports.stream().allMatch(importStmt -> {
      PsiElement importedMember = importStmt.resolve();
      if (importedMember != null && PsiTreeUtil.isAncestor(element, importedMember, false)) {
        for (PsiReference memberReference : myLocalRefsMap.get(importedMember)) {
          if (!PsiTreeUtil.isAncestor(element, memberReference.getElement(), false)) {
            return false;
          }
        }
        return true;
      }
      return false;
    });
  }

  private static boolean isParameterUsedRecursively(@NotNull PsiElement element, @NotNull Collection<? extends PsiReference> array) {
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
    Collection<PsiReference> array = myLocalRefsMap.get(variable);
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
    Collection<PsiReference> array = myLocalRefsMap.get(variable);
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
                  @NotNull TextRange dirtyScope,
                  @NotNull ProgressIndicator indicator,
                  @NotNull Runnable analyze) {
    boolean readyBefore = myReady;
    if (dirtyScope.equals(file.getTextRange())) {
      clear();
    }
    else {
      removeInvalidRefs();
    }
    analyze.run();
    myReady = true;
    log("a: ready changed ", readyBefore, "-> true", indicator);
    return true;
  }

  private static void log(@NonNls Object @NotNull ... info) {
    FileStatusMap.log(info);
  }

  private class GlobalUsageHelperBase extends GlobalUsageHelper {
    @Override
    public boolean shouldCheckUsages(@NotNull PsiMember member) {
      return false;
    }

    @Override
    public boolean isCurrentFileAlreadyChecked() {
      return true;
    }

    @Override
    public boolean isLocallyUsed(@NotNull PsiNamedElement member) {
      return isReferenced(member);
    }
  }
}
