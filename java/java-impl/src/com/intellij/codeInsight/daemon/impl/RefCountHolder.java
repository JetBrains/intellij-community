/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.psi.util.PsiMatchers;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RefCountHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.RefCountHolder");

  private final PsiFile myFile;
  private final BidirectionalMap<PsiReference,PsiElement> myLocalRefsMap = new BidirectionalMap<PsiReference, PsiElement>();

  private final Map<PsiNamedElement, Boolean> myDclsUsedMap = new ConcurrentHashMap<PsiNamedElement, Boolean>();
  private final Map<PsiReference, PsiImportStatementBase> myImportStatements = new ConcurrentHashMap<PsiReference, PsiImportStatementBase>();
  private final AtomicReference<ProgressIndicator> myState = new AtomicReference<ProgressIndicator>(VIRGIN);
  private static final ProgressIndicator VIRGIN = new DaemonProgressIndicator(); // just created or cleared
  private static final ProgressIndicator READY = new DaemonProgressIndicator();
  private volatile ProgressIndicator analyzedUnder;

  private static class HolderReference extends SoftReference<RefCountHolder> {
    @SuppressWarnings("UnusedDeclaration")
    private volatile RefCountHolder myHardRef; // to prevent gc
    // number of live references to RefCountHolder. Once it reaches zero, hard ref is cleared
    // the counter is used instead of a flag because multiple passes can be running simultaneously (one actual and several canceled winding down)
    // and there is a chance they overlap the usage of RCH
    private final AtomicInteger myRefCount = new AtomicInteger();

    public HolderReference(@NotNull RefCountHolder holder) {
      super(holder);
      myHardRef = holder;
    }

    private void changeLivenessBy(int delta) {
      if (myRefCount.addAndGet(delta) == 0) {
        myHardRef = null;
      }
      else if (myHardRef == null) {
        myHardRef = get();
      }
    }
  }

  private static final Key<HolderReference> REF_COUNT_HOLDER_IN_FILE_KEY = Key.create("REF_COUNT_HOLDER_IN_FILE_KEY");
  @NotNull
  private static Pair<RefCountHolder, HolderReference> getInstance(@NotNull PsiFile file, boolean create) {
    HolderReference ref = file.getUserData(REF_COUNT_HOLDER_IN_FILE_KEY);
    RefCountHolder holder = ref == null ? null : ref.get();
    if (holder == null && create) {
      holder = new RefCountHolder(file);
      HolderReference newRef = new HolderReference(holder);
      while (true) {
        boolean replaced = ((UserDataHolderEx)file).replace(REF_COUNT_HOLDER_IN_FILE_KEY, ref, newRef);
        if (replaced) {
          ref = newRef;
          break;
        }
        ref = file.getUserData(REF_COUNT_HOLDER_IN_FILE_KEY);
        RefCountHolder newHolder = ref == null ? null : ref.get();
        if (newHolder != null) {
          holder = newHolder;
          break;
        }
      }
    }
    return Pair.create(holder, ref);
  }

  @NotNull
  public static RefCountHolder startUsing(@NotNull PsiFile file) {
    Pair<RefCountHolder, HolderReference> pair = getInstance(file, true);
    HolderReference reference = pair.second;
    reference.changeLivenessBy(1); // make sure RefCountHolder won't be gced during highlighting
    log("startUsing: " + pair.first.myState+" for "+file);
    return pair.first;
  }

  @Nullable("might be gced")
  public static RefCountHolder endUsing(@NotNull PsiFile file) {
    Pair<RefCountHolder, HolderReference> pair = getInstance(file, false);
    HolderReference reference = pair.second;
    reference.changeLivenessBy(-1); // no longer needed, can be cleared
    RefCountHolder holder = pair.first;
    log("endUsing: " + (holder == null ? null : holder.myState)+" for "+file);
    return holder;
  }

  private RefCountHolder(@NotNull PsiFile file) {
    myFile = file;
    log("c: created: " + myState.get()+" for "+file);
  }

  private void clear() {
    synchronized (myLocalRefsMap) {
      myLocalRefsMap.clear();
    }
    myImportStatements.clear();
    myDclsUsedMap.clear();
  }

  public void registerLocallyReferenced(@NotNull PsiNamedElement result) {
    myDclsUsedMap.put(result,Boolean.TRUE);
  }

  public void registerReference(@NotNull PsiJavaReference ref, @NotNull JavaResolveResult resolveResult) {
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

  public boolean isRedundant(@NotNull PsiImportStatementBase importStatement) {
    return !myImportStatements.containsValue(importStatement);
  }

  private void registerLocalRef(@NotNull PsiReference ref, PsiElement refElement) {
    if (refElement instanceof PsiMethod && PsiTreeUtil.isAncestor(refElement, ref.getElement(), true)) return; // filter self-recursive calls
    if (refElement instanceof PsiClass && PsiTreeUtil.isAncestor(refElement, ref.getElement(), true)) return; // filter inner use of itself
    synchronized (myLocalRefsMap) {
      myLocalRefsMap.put(ref, refElement);
    }
  }

  private void removeInvalidRefs() {
    synchronized (myLocalRefsMap) {
      for(Iterator<PsiReference> iterator = myLocalRefsMap.keySet().iterator(); iterator.hasNext();){
        PsiReference ref = iterator.next();
        if (!ref.getElement().isValid()){
          PsiElement value = myLocalRefsMap.get(ref);
          iterator.remove();
          List<PsiReference> array = myLocalRefsMap.getKeysByValue(value);
          LOG.assertTrue(array != null);
          array.remove(ref);
        }
      }
    }
    for (Iterator<PsiReference> iterator = myImportStatements.keySet().iterator(); iterator.hasNext();) {
      PsiReference ref = iterator.next();
      if (!ref.getElement().isValid()) {
        iterator.remove();
      }
    }
    removeInvalidFrom(myDclsUsedMap.keySet());
  }

  private static void removeInvalidFrom(@NotNull Collection<? extends PsiElement> collection) {
    for (Iterator<? extends PsiElement> it = collection.iterator(); it.hasNext();) {
      PsiElement element = it.next();
      if (!element.isValid()) it.remove();
    }
  }

  public boolean isReferenced(@NotNull PsiNamedElement element) {
    List<PsiReference> array;
    synchronized (myLocalRefsMap) {
      array = myLocalRefsMap.getKeysByValue(element);
    }
    if (array != null && !array.isEmpty() && !isParameterUsedRecursively(element, array)) return true;

    Boolean usedStatus = myDclsUsedMap.get(element);
    return usedStatus == Boolean.TRUE;
  }

  private static boolean isParameterUsedRecursively(@NotNull PsiElement element, @NotNull List<PsiReference> array) {
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

  public boolean isReferencedForRead(@NotNull PsiElement element) {
    LOG.assertTrue(element instanceof PsiVariable);
    List<PsiReference> array;
    synchronized (myLocalRefsMap) {
      array = myLocalRefsMap.getKeysByValue(element);
    }
    if (array == null) return false;
    for (PsiReference ref : array) {
      PsiElement refElement = ref.getElement();
      if (!(refElement instanceof PsiExpression)) { // possible with incomplete code
        return true;
      }
      if (PsiUtil.isAccessedForReading((PsiExpression)refElement)) {
        if (refElement.getParent() instanceof PsiExpression &&
            refElement.getParent().getParent() instanceof PsiExpressionStatement &&
            PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
          continue; // "var++;"
        }
        return true;
      }
    }
    return false;
  }

  public boolean isReferencedForWrite(@NotNull PsiElement element) {
    LOG.assertTrue(element instanceof PsiVariable);
    List<PsiReference> array;
    synchronized (myLocalRefsMap) {
      array = myLocalRefsMap.getKeysByValue(element);
    }
    if (array == null) return false;
    for (PsiReference ref : array) {
      final PsiElement refElement = ref.getElement();
      if (!(refElement instanceof PsiExpression)) { // possible with incomplete code
        return true;
      }
      if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
        return true;
      }
    }
    return false;
  }

  public boolean analyze(@NotNull PsiFile file, TextRange dirtyScope, @NotNull Runnable analyze, @NotNull ProgressIndicator indicator) {
    ProgressIndicator old = myState.get();
    if (old != VIRGIN && old != READY) return false;
    if (!myState.compareAndSet(old, indicator)) {
      log("a: failed to change " + old + "->" + indicator);
      return false;
    }
    log("a: changed " + old + "->" + indicator);
    analyzedUnder = null;
    boolean completed = false;
    try {
      if (dirtyScope != null) {
        if (dirtyScope.equals(file.getTextRange())) {
          clear();
        }
        else {
          removeInvalidRefs();
        }
      }

      analyze.run();
      analyzedUnder = indicator;
      completed = true;
    }
    finally {
      ProgressIndicator resultState = completed ? READY : VIRGIN;
      boolean set = myState.compareAndSet(indicator, resultState);
      assert set : myState.get();
      log("a: changed after analyze" + indicator + "->" + resultState);
    }
    return true;
  }

  private static void log(@NonNls String s) {
    //System.err.println("RFC: "+s);
  }

  public boolean retrieveUnusedReferencesInfo(@NotNull ProgressIndicator indicator, @NotNull Runnable analyze) {
    ProgressIndicator old = myState.get();
    if (!myState.compareAndSet(READY, indicator)) {
      log("r: failed to change " + old + "->" + indicator);
      return false;
    }
    log("r: changed " + old + "->" + indicator);
    try {
      if (analyzedUnder != indicator) {
        return false;
      }
      analyze.run();
    }
    finally {
      boolean set = myState.compareAndSet(indicator, READY);
      assert set : myState.get();
      log("r: changed back " + indicator + "->" + READY);
    }
    return  true;
  }
}
