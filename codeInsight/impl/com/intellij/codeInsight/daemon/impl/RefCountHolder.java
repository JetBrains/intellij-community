
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.BidirectionalMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RefCountHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.RefCountHolder");

  private final PsiFile myFile;
  private final BidirectionalMap<PsiReference,PsiElement> myLocalRefsMap = new BidirectionalMap<PsiReference, PsiElement>();

  private final Map<PsiNamedElement, Boolean> myDclsUsedMap = new THashMap<PsiNamedElement, Boolean>();
  private final Map<String, XmlAttribute> myXmlId2AttributeMap = new THashMap<String, XmlAttribute>();
  private final Map<PsiReference, PsiImportStatementBase> myImportStatements = new THashMap<PsiReference, PsiImportStatementBase>();
  private final Set<PsiNamedElement> myUsedElements = new THashSet<PsiNamedElement>();
  private final AtomicInteger myState = new AtomicInteger(State.VIRGIN);
  private interface State {
    int VIRGIN = 0;                   // just created or cleared
    int BEING_WRITTEN_BY_GHP = 1;     // general highlighting pass is storing references during analysis
    int READY = 2;                    // may be used for higlighting unused stuff
    int BEING_USED_BY_PHP = 3;        // post highlighting pass is retrieving info
  }

  public RefCountHolder(@NotNull PsiFile file) {
    myFile = file;
    LOG.debug("RefCountHolder created for '"+ StringUtil.first(file.getText(), 30, true));
  }

  public void clear() {
    myLocalRefsMap.clear();
    myImportStatements.clear();
    myDclsUsedMap.clear();
    myXmlId2AttributeMap.clear();
    myUsedElements.clear();
  }

  public void registerLocallyReferenced(@NotNull PsiNamedElement result) {
    assertIsAnalyzing();
    myDclsUsedMap.put(result,Boolean.TRUE);
  }

  private static void addStatistics(final PsiNamedElement dcl) {
    final PsiType typeByPsiElement = PsiUtil.getTypeByPsiElement(dcl);
    final StatisticsManager.NameContext context = StatisticsManager.getContext(dcl);
    if(typeByPsiElement != null && context != null) {
      StatisticsManager.getInstance().incNameUseCount(typeByPsiElement, context, dcl.getName());
    }
  }

  public void registerAttributeWithId(@NotNull String id, XmlAttribute attr) {
    assertIsAnalyzing();
    myXmlId2AttributeMap.put(id,attr);
  }

  public XmlAttribute getAttributeById(String id) {
    /* TODO[cdr, maxim.mossienko]
    LOG.assertTrue(myState);
    */
    return myXmlId2AttributeMap.get(id);
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
    myLocalRefsMap.put(ref, refElement);
    if(refElement instanceof PsiNamedElement) {
      PsiNamedElement namedElement = (PsiNamedElement)refElement;
      if(myUsedElements.add(namedElement)) {
        addStatistics(namedElement);
      }
    }
  }

  public void removeInvalidRefs() {
    assertIsAnalyzing();
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
    for (Iterator<PsiReference> iterator = myImportStatements.keySet().iterator(); iterator.hasNext();) {
      PsiReference ref = iterator.next();
      if (!ref.getElement().isValid()) {
        iterator.remove();
      }
    }
    for(Iterator<PsiNamedElement> iterator = myDclsUsedMap.keySet().iterator(); iterator.hasNext();) {
      PsiNamedElement element = iterator.next();
      if (!element.isValid()) iterator.remove();
    }
    for(Iterator<PsiNamedElement> iterator = myUsedElements.iterator(); iterator.hasNext();) {
      PsiNamedElement element = iterator.next();
      if (!element.isValid()) iterator.remove();
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
        .dot(PsiMatcherImpl.hasClass(PsiReferenceExpression.class))
        .parent(PsiMatcherImpl.hasClass(PsiExpressionList.class))
        .parent(PsiMatcherImpl.hasClass(PsiMethodCallExpression.class))
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
      if (!(refElement instanceof PsiExpression)) { // possible with uncomplete code
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
      if (!(refElement instanceof PsiExpression)) { // possible with uncomplete code
        return true;
      }
      if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
        return true;
      }
    }
    return false;
  }

  public List<PsiNamedElement> getUnusedDcls() {
    assertIsRetrieving();
    List<PsiNamedElement> result = new ArrayList<PsiNamedElement>();
    Set<Map.Entry<PsiNamedElement, Boolean>> entries = myDclsUsedMap.entrySet();

    for (final Map.Entry<PsiNamedElement, Boolean> entry : entries) {
      if (entry.getValue() == Boolean.FALSE) result.add(entry.getKey());
    }

    return result;
  }

  public void analyzeAndStoreReferences(Runnable analyze, ProgressIndicator progress) {
    myState.compareAndSet(State.READY, State.VIRGIN);
    if (!myState.compareAndSet(State.VIRGIN, State.BEING_WRITTEN_BY_GHP)) {
      progress.cancel();
      return;
    }
    int newState;
    try {
      analyze.run();
      newState = State.READY;
    }
    catch (Exception e) {
      newState = State.VIRGIN;
    }
    boolean set = myState.compareAndSet(State.BEING_WRITTEN_BY_GHP, newState);
    assert set : myState.get();
  }

  public void retrieveUnusedReferencesInfo(Runnable analyze, ProgressIndicator progress) {
    if (!myState.compareAndSet(State.READY, State.BEING_USED_BY_PHP)) {
      progress.cancel();
      return;
    }
    try {
      analyze.run();
    }
    finally {
      boolean set = myState.compareAndSet(State.BEING_USED_BY_PHP, State.READY);
      assert set : myState.get();
    }
  }
  private void assertIsAnalyzing() {
    assert myState.get() == State.BEING_WRITTEN_BY_GHP : myState.get();
  }
  private void assertIsRetrieving() {
    assert myState.get() == State.BEING_USED_BY_PHP : myState.get();
  }
}
