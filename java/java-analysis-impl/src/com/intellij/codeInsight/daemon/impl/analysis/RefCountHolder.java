// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.codeInsight.daemon.impl.GlobalUsageHelper;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class RefCountHolder {
  private final PsiFile myFile;
  // resolved elements -> list of their references in this file
  private final @NotNull MultiMap<PsiElement, PsiReference> myLocalRefsMap;

  private final Set<PsiAnchor> myDclsUsedMap;
  // reference -> import statement the reference has come from
  private final Map<PsiReference, PsiImportStatementBase> myImportStatements;

  private static final Key<Reference<RefCountHolder>> REF_COUNT_HOLDER_IN_FILE_KEY = Key.create("REF_COUNT_HOLDER_IN_FILE_KEY");
  private volatile boolean ready; // true when analysis completed and inner maps can be queried

  static RefCountHolder get(@NotNull PsiFile file, @NotNull TextRange dirtyScope) {
    Reference<RefCountHolder> ref = file.getUserData(REF_COUNT_HOLDER_IN_FILE_KEY);
    RefCountHolder storedHolder = com.intellij.reference.SoftReference.dereference(ref);
    boolean wholeFile = dirtyScope.equals(file.getTextRange());
    if (storedHolder == null && !wholeFile) {
      // RefCountHolder was GCed and queried for subrange of the file, can't return anything meaningful
      return null;
    }
    return storedHolder == null || wholeFile ?
           new RefCountHolder(file, MultiMap.createConcurrentSet(), ConcurrentCollectionFactory.createConcurrentSet(HashingStrategy.canonical()), ConcurrentCollectionFactory.createConcurrentMap())
           : storedHolder.removeInvalidRefs();
  }

  void storeReadyHolder(@NotNull PsiFile file) {
    ready = true;
    file.putUserData(REF_COUNT_HOLDER_IN_FILE_KEY, new SoftReference<>(this));
  }

  private RefCountHolder(@NotNull PsiFile file,
                         @NotNull MultiMap<PsiElement, PsiReference> myLocalRefsMap,
                         @NotNull Set<PsiAnchor> myDclsUsedMap,
                         @NotNull Map<PsiReference, PsiImportStatementBase> myImportStatements) {
    myFile = file;
    this.myLocalRefsMap = myLocalRefsMap;
    this.myDclsUsedMap = myDclsUsedMap;
    this.myImportStatements = myImportStatements;
    log("c: created for ", file);
  }

  @NotNull
  GlobalUsageHelper getGlobalUsageHelper(@NotNull PsiFile file,
                                         @Nullable UnusedDeclarationInspectionBase deadCodeInspection,
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
    assert ready;
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

  @NotNull
  private RefCountHolder removeInvalidRefs() {
    assert ready;
    boolean changed = false;
    MultiMap<PsiElement, PsiReference> newLocalRefsMap = MultiMap.createConcurrentSet();
    for (Map.Entry<PsiElement, Collection<PsiReference>> entry : myLocalRefsMap.entrySet()) {
      PsiElement element = entry.getKey();
      for (PsiReference ref : entry.getValue()) {
        if (ref.getElement().isValid()) {
          newLocalRefsMap.putValue(element, ref);
        }
        else {
          changed = true;
        }
      }
    }
    Set<PsiAnchor> newDclsUsedMap = ConcurrentCollectionFactory.createConcurrentSet(HashingStrategy.canonical());
    for (PsiAnchor element : myDclsUsedMap) {
      if (element.retrieve() != null) {
        newDclsUsedMap.add(element);
      }
      else {
        changed = true;
      }
    }
    Map<PsiReference, PsiImportStatementBase> newImportStatements = ConcurrentCollectionFactory.createConcurrentMap();
    for (Map.Entry<PsiReference, PsiImportStatementBase> entry : myImportStatements.entrySet()) {
      PsiReference key = entry.getKey();
      PsiImportStatementBase value = entry.getValue();
      if (value.isValid() && key.getElement().isValid()) {
        newImportStatements.put(key, value);
      }
      else {
        changed = true;
      }
    }
    return changed ? new RefCountHolder(myFile, newLocalRefsMap, newDclsUsedMap, newImportStatements) : this;
  }

  boolean isReferenced(@NotNull PsiElement element) {
    assert ready;
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
    assert ready;
    if (!(element instanceof PsiClass)) return false;

    Set<PsiImportStatementBase> imports = new HashSet<>();
    for (PsiReference classReference : array) {
      PsiImportStatementBase importStmt = PsiTreeUtil.getParentOfType(classReference.getElement(), PsiImportStatementBase.class);
      if (importStmt == null) return false;
      imports.add(importStmt);
    }

    return ContainerUtil.all(imports, importStmt -> {
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
    assert ready;
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
    assert ready;
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
