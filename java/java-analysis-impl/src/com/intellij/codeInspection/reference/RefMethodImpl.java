/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 * Date: Oct 21, 2001
 */
public class RefMethodImpl extends RefJavaElementImpl implements RefMethod {
  private static final List<RefMethod> EMPTY_METHOD_LIST = Collections.emptyList();
  private static final RefParameter[] EMPTY_PARAMS_ARRAY = new RefParameter[0];

  private static final int IS_APPMAIN_MASK = 0x10000;
  private static final int IS_LIBRARY_OVERRIDE_MASK = 0x20000;
  private static final int IS_CONSTRUCTOR_MASK = 0x40000;
  private static final int IS_ABSTRACT_MASK = 0x80000;
  private static final int IS_BODY_EMPTY_MASK = 0x100000;
  private static final int IS_ONLY_CALLS_SUPER_MASK = 0x200000;
  private static final int IS_RETURN_VALUE_USED_MASK = 0x400000;

  private static final int IS_TEST_METHOD_MASK = 0x4000000;
  private static final int IS_CALLED_ON_SUBCLASS_MASK = 0x8000000;

  private static final String RETURN_VALUE_UNDEFINED = "#";

  private List<RefMethod> mySuperMethods;
  private List<RefMethod> myDerivedMethods;
  private List<String> myUnThrownExceptions;

  private RefParameter[] myParameters;
  private String myReturnValueTemplate;
  protected final RefClass myOwnerClass;

  RefMethodImpl(@NotNull RefClass ownerClass, PsiMethod method, RefManager manager) {
    super(method, manager);

    ((RefClassImpl)ownerClass).add(this);

    myOwnerClass = ownerClass;
  }

  // To be used only from RefImplicitConstructor.
  protected RefMethodImpl(@NotNull String name, @NotNull RefClass ownerClass) {
    super(name, ownerClass);
    myOwnerClass = ownerClass;
    ((RefClassImpl)ownerClass).add(this);

    addOutReference(getOwnerClass());
    ((RefClassImpl)getOwnerClass()).addInReference(this);

    setConstructor(true);
  }

  @Override
  public void add(@NotNull RefEntity child) {
    if (child instanceof RefParameter) {
      return;
    }
    super.add(child);
  }

  @Override
  public List<RefEntity> getChildren() {
    List<RefEntity> superChildren = super.getChildren();
    if (myParameters == null) return superChildren;
    if (superChildren == null || superChildren.isEmpty()) return Arrays.<RefEntity>asList(myParameters);
    
    List<RefEntity> allChildren = new ArrayList<>(superChildren.size() + myParameters.length);
    allChildren.addAll(superChildren);
    Collections.addAll(allChildren, myParameters);
    return allChildren;
  }

  @Override
  protected void initialize() {
    final PsiMethod method = (PsiMethod)getElement();
    LOG.assertTrue(method != null);
    setConstructor(method.isConstructor());
    final PsiType returnType = method.getReturnType();
    setFlag(returnType == null || 
            PsiType.VOID.equals(returnType) || 
            returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID), IS_RETURN_VALUE_USED_MASK);

    if (!isReturnValueUsed()) {
      myReturnValueTemplate = RETURN_VALUE_UNDEFINED;
    }

    if (isConstructor()) {
      addReference(getOwnerClass(), getOwnerClass().getElement(), method, false, true, null);
    }

    if (getOwnerClass().isInterface()) {
      setAbstract(false);
    } else {
      setAbstract(method.hasModifierProperty(PsiModifier.ABSTRACT));
    }


    setAppMain(isAppMain(method, this));
    setLibraryOverride(method.hasModifierProperty(PsiModifier.NATIVE));

    initializeSuperMethods(method);
    if (isExternalOverride()) {
      ((RefClassImpl)getOwnerClass()).addLibraryOverrideMethod(this);
    }

    @NonNls final String name = method.getName();
    if (getOwnerClass().isTestCase() && name.startsWith("test")) {
      setTestMethod(true);
    }

    PsiParameter[] paramList = method.getParameterList().getParameters();
    if (paramList.length > 0){
      myParameters = new RefParameterImpl[paramList.length];
      for (int i = 0; i < paramList.length; i++) {
        PsiParameter parameter = paramList[i];
        myParameters[i] = getRefJavaManager().getParameterReference(parameter, i);
      }
    }

    if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      updateReturnValueTemplate(null);
      updateThrowsList(null);
    }
    collectUncaughtExceptions(method);
  }

  private static boolean isAppMain(PsiMethod psiMethod, RefMethod refMethod) {
    if (!refMethod.isStatic()) return false;
    if (!PsiType.VOID.equals(psiMethod.getReturnType())) return false;

    PsiMethod appMainPattern = ((RefMethodImpl)refMethod).getRefJavaManager().getAppMainPattern();
    if (MethodSignatureUtil.areSignaturesEqual(psiMethod, appMainPattern)) return true;

    PsiMethod appPremainPattern = ((RefMethodImpl)refMethod).getRefJavaManager().getAppPremainPattern();
    if (MethodSignatureUtil.areSignaturesEqual(psiMethod, appPremainPattern)) return true;

    PsiMethod appAgentmainPattern = ((RefMethodImpl)refMethod).getRefJavaManager().getAppAgentmainPattern();
    return MethodSignatureUtil.areSignaturesEqual(psiMethod, appAgentmainPattern);
  }

  private void checkForSuperCall(PsiMethod method) {
    if (isConstructor()) {
      PsiCodeBlock body = method.getBody();
      if (body == null) return;
      PsiStatement[] statements = body.getStatements();
      boolean isBaseExplicitlyCalled = false;
      if (statements.length > 0) {
        PsiStatement first = statements[0];
        if (first instanceof PsiExpressionStatement) {
          PsiExpression firstExpression = ((PsiExpressionStatement) first).getExpression();
          if (firstExpression instanceof PsiMethodCallExpression) {
            PsiExpression qualifierExpression = ((PsiMethodCallExpression)firstExpression).getMethodExpression().getQualifierExpression();
            if (qualifierExpression instanceof PsiReferenceExpression) {
              @NonNls String text = qualifierExpression.getText();
              if ("super".equals(text) || text.equals("this")) {
                isBaseExplicitlyCalled = true;
              }
            }
          }
        }
      }

      if (!isBaseExplicitlyCalled) {
        for (RefClass superClass : getOwnerClass().getBaseClasses()) {
          RefMethodImpl superDefaultConstructor = (RefMethodImpl)superClass.getDefaultConstructor();

          if (superDefaultConstructor != null) {
            superDefaultConstructor.addInReference(this);
            addOutReference(superDefaultConstructor);
          }
        }
      }
    }
  }

  @Override
  @NotNull
  public Collection<RefMethod> getSuperMethods() {
    if (mySuperMethods == null) return EMPTY_METHOD_LIST;
    if (mySuperMethods.size() > 10) {
      LOG.info("method: " + getName() + " owner:" + getOwnerClass().getQualifiedName());
    }
    if (getRefManager().isOfflineView()) {
      LOG.debug("Should not traverse graph offline");
    }
    return mySuperMethods;
  }

  @Override
  @NotNull
  public Collection<RefMethod> getDerivedMethods() {
    if (myDerivedMethods == null) return EMPTY_METHOD_LIST;
    return myDerivedMethods;
  }

  @Override
  public boolean isBodyEmpty() {
    return checkFlag(IS_BODY_EMPTY_MASK);
  }

  @Override
  public boolean isOnlyCallsSuper() {
    return checkFlag(IS_ONLY_CALLS_SUPER_MASK);
  }

  @Override
  public boolean hasBody() {
    return !isAbstract() && !getOwnerClass().isInterface() || !isBodyEmpty();
  }

  private void initializeSuperMethods(PsiMethod method) {
    if (getRefManager().isOfflineView()) return;
    for (PsiMethod psiSuperMethod : method.findSuperMethods()) {
      if (getRefManager().belongsToScope(psiSuperMethod)) {
        RefMethodImpl refSuperMethod = (RefMethodImpl)getRefManager().getReference(psiSuperMethod);
        if (refSuperMethod != null) {
          addSuperMethod(refSuperMethod);
          refSuperMethod.markExtended(this);
        }
      }
      else {
        setLibraryOverride(true);
      }
    }
  }

  public void addSuperMethod(RefMethodImpl refSuperMethod) {
    if (!getSuperMethods().contains(refSuperMethod) && !refSuperMethod.getSuperMethods().contains(this)) {
      if (mySuperMethods == null){
        mySuperMethods = new ArrayList<>(1);
      }
      mySuperMethods.add(refSuperMethod);
    }
  }

  public void markExtended(RefMethodImpl method) {
    if (!getDerivedMethods().contains(method) && !method.getDerivedMethods().contains(this)) {
      if (myDerivedMethods == null) {
        myDerivedMethods = new ArrayList<>(1);
      }
      myDerivedMethods.add(method);
    }
  }

  @Override
  @NotNull
  public RefParameter[] getParameters() {
    if (myParameters == null) return EMPTY_PARAMS_ARRAY;
    return myParameters;
  }

  @Override
  public void buildReferences() {
    // Work on code block to find what we're referencing...
    PsiMethod method = (PsiMethod) getElement();
    if (method == null) return;
    PsiCodeBlock body = method.getBody();
    final RefJavaUtil refUtil = RefJavaUtil.getInstance();
    refUtil.addReferences(method, this, body);
    refUtil.addReferences(method, this, method.getModifierList());
    checkForSuperCall(method);
    setOnlyCallsSuper(refUtil.isMethodOnlyCallsSuper(method));

    setBodyEmpty(isOnlyCallsSuper() || !isExternalOverride() && (body == null || body.getStatements().length == 0));

    refUtil.addTypeReference(method, method.getReturnType(), getRefManager(), this);

    for (RefParameter parameter : getParameters()) {
      refUtil.setIsFinal(parameter, parameter.getElement().hasModifierProperty(PsiModifier.FINAL));
    }

    getRefManager().fireBuildReferences(this);
  }

  private void collectUncaughtExceptions(@NotNull PsiMethod method) {
    if (isExternalOverride()) return;
    if (getRefManager().isOfflineView()) return;
    @NonNls final String name = method.getName();
    if (getOwnerClass().isTestCase() && name.startsWith("test")) return;

    if (getSuperMethods().isEmpty()) {
      PsiClassType[] throwsList = method.getThrowsList().getReferencedTypes();
      if (throwsList.length > 0) {
        myUnThrownExceptions = throwsList.length == 1 ? new SmartList<>() : new ArrayList<>(throwsList.length);
        for (final PsiClassType type : throwsList) {
          PsiClass aClass = type.resolve();
          String fqn = aClass == null ? null : aClass.getQualifiedName();
          if (fqn != null) {
            myUnThrownExceptions.add(fqn);
          }
        }
      }
    }

    final PsiCodeBlock body = method.getBody();
    if (body == null) return;

    final Collection<PsiClassType> exceptionTypes = ExceptionUtil.collectUnhandledExceptions(body, method, false);
    for (final PsiClassType exceptionType : exceptionTypes) {
      updateThrowsList(exceptionType);
    }
  }

  public void removeUnThrownExceptions(PsiClass unThrownException) {
    if (myUnThrownExceptions != null) {
      myUnThrownExceptions.remove(unThrownException.getQualifiedName());
    }
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> ((RefJavaVisitor)visitor).visitMethod(this));
    } else {
      super.accept(visitor);
    }
  }

  @Override
  public boolean isExternalOverride() {
    return isLibraryOverride(new HashSet<>());
  }

  private boolean isLibraryOverride(@NotNull Collection<RefMethod> processed) {
    if (!processed.add(this)) return false;

    if (checkFlag(IS_LIBRARY_OVERRIDE_MASK)) return true;
    for (RefMethod superMethod : getSuperMethods()) {
      if (((RefMethodImpl)superMethod).isLibraryOverride(processed)) {
        setFlag(true, IS_LIBRARY_OVERRIDE_MASK);
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean isAppMain() {
    return checkFlag(IS_APPMAIN_MASK);
  }

  @Override
  public boolean isAbstract() {
    return checkFlag(IS_ABSTRACT_MASK);
  }

  @Override
  public boolean hasSuperMethods() {
    return !getSuperMethods().isEmpty() || isExternalOverride();
  }

  @Override
  public boolean isReferenced() {
    // Directly called from somewhere..
    for (RefElement refCaller : getInReferences()) {
      if (!getDerivedMethods().contains(refCaller)) return true;
    }

    // Library override probably called from library code.
    return isExternalOverride();
  }

  @Override
  public boolean hasSuspiciousCallers() {
    // Directly called from somewhere..
    for (RefElement refCaller : getInReferences()) {
      if (((RefElementImpl)refCaller).isSuspicious() && !getDerivedMethods().contains(refCaller)) return true;
    }

    // Library override probably called from library code.
    if (isExternalOverride()) return true;

    // Class isn't instantiated. Most probably we have problem with class, not method.
    if (!isStatic() && !isConstructor()) {
      if (((RefClassImpl)getOwnerClass()).isSuspicious()) return true;

      // Is an override. Probably called via reference to base class.
      for (RefMethod refSuper : getSuperMethods()) {
        if (((RefMethodImpl)refSuper).isSuspicious()) return true;
      }
    }

    return false;
  }

  @Override
  public boolean isConstructor() {
    return checkFlag(IS_CONSTRUCTOR_MASK);
  }

  @Override
  public RefClass getOwnerClass() {
    return (RefClass) getOwner();
  }

  @NotNull
  @Override
  public String getName() {
    if (isValid()) {
      return ReadAction.compute(() -> {
        PsiMethod psiMethod = (PsiMethod) getElement();
        if (psiMethod instanceof SyntheticElement) {
          return psiMethod.getName();
        }
        else {
          return PsiFormatUtil.formatMethod(psiMethod,
                                                 PsiSubstitutor.EMPTY,
                                                 PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                                 PsiFormatUtilBase.SHOW_TYPE
          );
        }
      });
    } else {
      return super.getName();
    }
  }

  @Override
  public String getExternalName() {
    return ReadAction.compute(() -> {
      final PsiMethod psiMethod = (PsiMethod)getElement();
      LOG.assertTrue(psiMethod != null);
      return PsiFormatUtil.getExternalName(psiMethod, true, Integer.MAX_VALUE);
    });
  }

  @Nullable
  public static RefMethod methodFromExternalName(RefManager manager, String externalName) {
    return (RefMethod) manager.getReference(findPsiMethod(PsiManager.getInstance(manager.getProject()), externalName));
  }

  @Nullable
  public static PsiMethod findPsiMethod(PsiManager manager, String externalName) {
    final int spaceIdx = externalName.indexOf(' ');
    final String className = externalName.substring(0, spaceIdx);
    final PsiClass psiClass = ClassUtil.findPsiClass(manager, className);
    if (psiClass == null) return null;
    try {
      PsiElementFactory factory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
      String methodSignature = externalName.substring(spaceIdx + 1);
      PsiMethod patternMethod = factory.createMethodFromText(methodSignature, psiClass);
      return psiClass.findMethodBySignature(patternMethod, false);
    } catch (IncorrectOperationException e) {
      // Do nothing. Returning null is acceptable in this case.
      return null;
    }
  }

  @Override
  public void referenceRemoved() {
    if (getOwnerClass() != null) {
      ((RefClassImpl)getOwnerClass()).methodRemoved(this);
    }

    super.referenceRemoved();

    for (RefMethod superMethod : getSuperMethods()) {
      superMethod.getDerivedMethods().remove(this);
    }

    for (RefMethod subMethod : getDerivedMethods()) {
      subMethod.getSuperMethods().remove(this);
    }

    ArrayList<RefElement> deletedRefs = new ArrayList<>();
    for (RefParameter parameter : getParameters()) {
      getRefManager().removeRefElement(parameter, deletedRefs);
    }
  }

  @Override
  public boolean isSuspicious() {
    if (isConstructor() && PsiModifier.PRIVATE.equals(getAccessModifier()) && getParameters().length == 0 && getOwnerClass().getConstructors().size() == 1) return false;
    return super.isSuspicious();
  }

  public void setReturnValueUsed(boolean value) {
    if (checkFlag(IS_RETURN_VALUE_USED_MASK) == value) return;
    setFlag(value, IS_RETURN_VALUE_USED_MASK);
    for (RefMethod refSuper : getSuperMethods()) {
      ((RefMethodImpl)refSuper).setReturnValueUsed(value);
    }
  }

  @Override
  public boolean isReturnValueUsed() {
    return checkFlag(IS_RETURN_VALUE_USED_MASK);
  }

  public void updateReturnValueTemplate(PsiExpression expression) {
    if (myReturnValueTemplate == null) return;

    if (!getSuperMethods().isEmpty()) {
      for (final RefMethod refMethod : getSuperMethods()) {
        RefMethodImpl refSuper = (RefMethodImpl)refMethod;
        refSuper.updateReturnValueTemplate(expression);
      }
    }else {
      String newTemplate = null;
      final RefJavaUtil refUtil = RefJavaUtil.getInstance();
      if (expression instanceof PsiLiteralExpression) {
        PsiLiteralExpression psiLiteralExpression = (PsiLiteralExpression) expression;
        newTemplate = psiLiteralExpression.getText();
      } else if (expression instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiField) {
          PsiField psiField = (PsiField) resolved;
          if (psiField.hasModifierProperty(PsiModifier.STATIC) &&
              psiField.hasModifierProperty(PsiModifier.FINAL) &&
              refUtil.compareAccess(refUtil.getAccessModifier(psiField), getAccessModifier()) >= 0) {
            newTemplate = PsiFormatUtil.formatVariable(psiField, PsiFormatUtilBase.SHOW_NAME |
                                                                 PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                 PsiFormatUtilBase.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
          }
        }
      } else if (refUtil.isCallToSuperMethod(expression, (PsiMethod) getElement())) return;

      //noinspection StringEquality
      if (myReturnValueTemplate == RETURN_VALUE_UNDEFINED) {
        myReturnValueTemplate = newTemplate;
      } else if (!Comparing.equal(myReturnValueTemplate, newTemplate)) {
        myReturnValueTemplate = null;
      }
    }
  }

  public void updateParameterValues(PsiExpression[] args) {
    if (isExternalOverride()) return;

    if (!getSuperMethods().isEmpty()) {
      for (RefMethod refSuper : getSuperMethods()) {
        ((RefMethodImpl)refSuper).updateParameterValues(args);
      }
    } else {
      final RefParameter[] params = getParameters();
      if (params.length <= args.length && params.length > 0) {
        for (int i = 0; i < args.length; i++) {
          RefParameter refParameter;
          if (params.length <= i){
            refParameter = params[params.length - 1];
          } else {
            refParameter = params[i];
          }
          ((RefParameterImpl)refParameter).updateTemplateValue(args[i]);
        }
      }
    }
  }

  @Override
  public String getReturnValueIfSame() {
    //noinspection StringEquality
    if (myReturnValueTemplate == RETURN_VALUE_UNDEFINED) return null;
    return myReturnValueTemplate;
  }

  public void updateThrowsList(PsiClassType exceptionType) {
    if (!getSuperMethods().isEmpty()) {
      for (RefMethod refSuper : getSuperMethods()) {
        ((RefMethodImpl)refSuper).updateThrowsList(exceptionType);
      }
    }
    else if (myUnThrownExceptions != null) {
      if (exceptionType == null) {
        myUnThrownExceptions = null;
        return;
      }
      PsiClass exceptionClass = exceptionType.resolve();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myManager.getProject());
      for (int i = myUnThrownExceptions.size() - 1; i >= 0; i--) {
        String exceptionFqn = myUnThrownExceptions.get(i);
        PsiClass classType = facade.findClass(exceptionFqn, GlobalSearchScope.allScope(getRefManager().getProject()));
        if (InheritanceUtil.isInheritorOrSelf(exceptionClass, classType, true) ||
            InheritanceUtil.isInheritorOrSelf(classType, exceptionClass, true)) {
          myUnThrownExceptions.remove(i);
        }
      }

      if (myUnThrownExceptions.isEmpty()) myUnThrownExceptions = null;
    }
  }

  @Override
  @Nullable
  public PsiClass[] getUnThrownExceptions() {
    if (getRefManager().isOfflineView()) {
      LOG.debug("Should not traverse graph offline");
    }
    if (myUnThrownExceptions == null) return null;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myManager.getProject());
    List<PsiClass> result = new ArrayList<>(myUnThrownExceptions.size());
    for (String exception : myUnThrownExceptions) {
      PsiClass element = facade.findClass(exception, GlobalSearchScope.allScope(myManager.getProject()));
      if (element != null) result.add(element);
    }
    return result.toArray(new PsiClass[result.size()]);
  }


  public void setLibraryOverride(boolean libraryOverride) {
    setFlag(libraryOverride, IS_LIBRARY_OVERRIDE_MASK);
  }

  private void setAppMain(boolean appMain) {
    setFlag(appMain, IS_APPMAIN_MASK);
  }

  private void setAbstract(boolean anAbstract) {
    setFlag(anAbstract, IS_ABSTRACT_MASK);
  }

  public void setBodyEmpty(boolean bodyEmpty) {
    setFlag(bodyEmpty, IS_BODY_EMPTY_MASK);
  }

  private void setOnlyCallsSuper(boolean onlyCallsSuper) {
    setFlag(onlyCallsSuper, IS_ONLY_CALLS_SUPER_MASK);
  }



  private void setConstructor(boolean constructor) {
    setFlag(constructor, IS_CONSTRUCTOR_MASK);
  }

  @Override
  public boolean isTestMethod() {
    return checkFlag(IS_TEST_METHOD_MASK);
  }

  private void setTestMethod(boolean testMethod){
    setFlag(testMethod, IS_TEST_METHOD_MASK);
  }

  @Override
  public PsiModifierListOwner getElement() {
    return (PsiModifierListOwner)super.getElement();
  }

  @Override
  public boolean isCalledOnSubClass() {
    return checkFlag(IS_CALLED_ON_SUBCLASS_MASK);
  }

  public void setCalledOnSubClass(boolean isCalledOnSubClass){
    setFlag(isCalledOnSubClass, IS_CALLED_ON_SUBCLASS_MASK);
  }

  private static String extractMethodName(String methodSignature) {
    final String returnTypeAndName = methodSignature.substring(0, methodSignature.indexOf('('));
    return returnTypeAndName.substring(returnTypeAndName.indexOf(' ') + 1);
  }
}
