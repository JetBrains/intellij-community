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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.psi.util.PsiMatchers;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class RefCountHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.RefCountHolder");

  private final PsiFile myFile;
  private final BidirectionalMap<PsiReference,PsiElement> myLocalRefsMap = new BidirectionalMap<PsiReference, PsiElement>();

  private final Map<PsiNamedElement, Boolean> myDclsUsedMap = new ConcurrentHashMap<PsiNamedElement, Boolean>();
  private final Map<PsiReference, PsiImportStatementBase> myImportStatements = new ConcurrentHashMap<PsiReference, PsiImportStatementBase>();
  private final Map<PsiElement,Boolean> myPossiblyDuplicateElements = new ConcurrentHashMap<PsiElement, Boolean>();
  private final AtomicReference<State> myState = new AtomicReference<State>(State.VIRGIN);

  private enum State {
    VIRGIN,                       // just created or cleared
    BEING_WRITTEN_BY_GHP,         // general highlighting pass is storing references during analysis
    READY,                        // may be used for highlighting unused stuff
    BEING_USED_BY_PHP,            // post highlighting pass is retrieving info
  }

  private static final Key<RefCountHolder> REF_COUNT_HOLDER_IN_FILE_KEY = Key.create("REF_COUNT_HOLDER_IN_FILE_KEY");
  public static RefCountHolder getInstance(PsiFile file) {
    RefCountHolder refCountHolder = file.getUserData(REF_COUNT_HOLDER_IN_FILE_KEY);
    if (refCountHolder == null) {
      refCountHolder = ((UserDataHolderEx)file).putUserDataIfAbsent(REF_COUNT_HOLDER_IN_FILE_KEY, new RefCountHolder(file));
    }
    return refCountHolder;
  }

  private RefCountHolder(@NotNull PsiFile file) {
    myFile = file;
    LOG.debug("RefCountHolder created for '"+ StringUtil.first(file.getText(), 30, true));
  }

  private void clear() {
    assertIsAnalyzing();
    myLocalRefsMap.clear();
    myImportStatements.clear();
    myDclsUsedMap.clear();
    myPossiblyDuplicateElements.clear();
  }

  public void registerLocallyReferenced(@NotNull PsiNamedElement result) {
    assertIsAnalyzing();
    myDclsUsedMap.put(result,Boolean.TRUE);
  }

  public void registerReference(@NotNull PsiJavaReference ref, JavaResolveResult resolveResult) {
    assertIsAnalyzing();
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

  private void registerImportStatement(@NotNull PsiReference ref, PsiImportStatementBase importStatement) {
    myImportStatements.put(ref, importStatement);
  }

  public boolean isRedundant(PsiImportStatementBase importStatement) {
    assertIsRetrieving();
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
    assertIsAnalyzing();
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
    removeInvalidFrom(myPossiblyDuplicateElements.keySet());
  }
  private static void removeInvalidFrom(Iterable<? extends PsiElement> collection) {
    for (Iterator<? extends PsiElement> it = collection.iterator(); it.hasNext();) {
      PsiElement element = it.next();
      if (!element.isValid()) it.remove();
    }
  }

  public boolean isReferenced(PsiNamedElement element) {
    assertIsRetrieving();
    List<PsiReference> array = myLocalRefsMap.getKeysByValue(element);
    if (array != null && !array.isEmpty() && !isParameterUsedRecursively(element, array)) return true;

    Boolean usedStatus = myDclsUsedMap.get(element);
    return usedStatus == Boolean.TRUE;
  }

  private static boolean isParameterUsedRecursively(final PsiElement element, final List<PsiReference> array) {
    if (!(element instanceof PsiParameter)) return false;
    PsiParameter parameter = (PsiParameter)element;
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)scope;
    int paramIndex = ArrayUtil.find(method.getParameterList().getParameters(), parameter);

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
      int argumentIndex = ArrayUtil.find(arguments, argument);
      if (paramIndex != argumentIndex) return false;
    }

    return true;
  }

  public boolean isReferencedForRead(PsiElement element) {
    assertIsRetrieving();
    LOG.assertTrue(element instanceof PsiVariable);
    List<PsiReference> array = myLocalRefsMap.getKeysByValue(element);
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

  public boolean isReferencedForWrite(PsiElement element) {
    assertIsRetrieving();
    LOG.assertTrue(element instanceof PsiVariable);
    List<PsiReference> array = myLocalRefsMap.getKeysByValue(element);
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

  public boolean analyze(Runnable analyze, final TextRange dirtyScope, final PsiFile file) {
    myState.compareAndSet(State.READY, State.VIRGIN);
    if (!myState.compareAndSet(State.VIRGIN, State.BEING_WRITTEN_BY_GHP)) {
      return false;
    }

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
    }
    finally {
      boolean set = myState.compareAndSet(State.BEING_WRITTEN_BY_GHP, State.READY);
      assert set : myState.get();
    }
    return true;
  }

  public boolean retrieveUnusedReferencesInfo(Runnable analyze) {
    if (!myState.compareAndSet(State.READY, State.BEING_USED_BY_PHP)) {
      return false;
    }
    try {
      analyze.run();
    }
    finally {
      boolean set = myState.compareAndSet(State.BEING_USED_BY_PHP, State.READY);
      assert set : myState.get();
    }
    return  true;
  }

  private void assertIsAnalyzing() {
    assert myState.get() == State.BEING_WRITTEN_BY_GHP : myState.get();
  }
  private void assertIsRetrieving() {
    assert myState.get() == State.BEING_USED_BY_PHP : myState.get();
  }
}
