// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.*;

/**
 * @author max
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

  private List<RefMethod> mySuperMethods; //guarded by this
  private List<RefMethod> myDerivedMethods; //guarded by this
  private List<String> myUnThrownExceptions;//guarded by this

  private RefParameter[] myParameters; //guarded by this
  private volatile String myReturnValueTemplate = RETURN_VALUE_UNDEFINED; //guarded by this

  RefMethodImpl(UMethod method, PsiElement psi, RefManager manager) {
    super(method, psi, manager);
  }

  // To be used only from RefImplicitConstructor!
  protected RefMethodImpl(@NotNull String name, @NotNull RefClass ownerClass) {
    super(name, ownerClass);

    // fair enough to add parent here
    ((RefClassImpl)ownerClass).add(this);

    addOutReference(ownerClass);
    ((WritableRefElement)ownerClass).addInReference(this);

    setConstructor(true);
  }

  @Override
  public synchronized void add(@NotNull RefEntity child) {
    if (child instanceof RefParameter) {
      ((WritableRefEntity)child).setOwner(this);
      return;
    }
    super.add(child);
  }

  @NotNull
  @Override
  public synchronized List<RefEntity> getChildren() {
    List<RefEntity> superChildren = super.getChildren();
    if (myParameters == null) return superChildren;
    if (superChildren.isEmpty()) return Arrays.asList(myParameters);
    
    List<RefEntity> allChildren = new ArrayList<>(superChildren.size() + myParameters.length);
    allChildren.addAll(superChildren);
    Collections.addAll(allChildren, myParameters);
    return allChildren;
  }

  @Override
  protected void initialize() {
    UMethod method = (UMethod)getUastElement();
    LOG.assertTrue(method != null);
    PsiElement sourcePsi = method.getSourcePsi();
    LOG.assertTrue(sourcePsi != null);

    RefElement parentRef = findParentRef(sourcePsi, method, myManager);
    if (parentRef == null) return;
    ((WritableRefEntity)parentRef).add(this);
    if (!myManager.isDeclarationsFound()) return;

    PsiMethod javaPsi = method.getJavaPsi();
    setConstructor(method.isConstructor());
    final PsiType returnType = method.getReturnType();
    setFlag(returnType == null || 
            PsiType.VOID.equals(returnType) || 
            returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID), IS_RETURN_VALUE_USED_MASK);

    RefClass ownerClass = getOwnerClass();
    if (isConstructor()) {
      LOG.assertTrue(ownerClass != null);
      addReference(ownerClass, ownerClass.getPsiElement(), method, false, true, null);
    }

    if (ownerClass != null && ownerClass.isInterface()) {
      setAbstract(false);
    } else {
      setAbstract(javaPsi.hasModifierProperty(PsiModifier.ABSTRACT));
    }


    setAppMain(isAppMain(javaPsi, this));
    boolean isNative = javaPsi.hasModifierProperty(PsiModifier.NATIVE);
    setLibraryOverride(isNative);

    initializeSuperMethods(javaPsi);
    if (ownerClass != null && isExternalOverride()) {
      ((RefClassImpl)ownerClass).addLibraryOverrideMethod(this);
    }

    @NonNls final String name = method.getName();
    if (ownerClass != null && ownerClass.isTestCase() && name.startsWith("test")) {
      setTestMethod(true);
    }

    List<UParameter> paramList = method.getUastParameters();
    if (!paramList.isEmpty()){
      RefParameter[] newParameters = new RefParameterImpl[paramList.size()];
      for (int i = 0; i < paramList.size(); i++) {
        newParameters[i] = getRefJavaManager().getParameterReference(paramList.get(i), i, this);
      }
      synchronized (this) {
        myParameters = newParameters;
      }
    }

    if (isNative) {
      updateReturnValueTemplate(null);
      updateThrowsList(null);
    }

    if (sourcePsi.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      collectUncaughtExceptions((PsiMethod)sourcePsi);
    }
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

  private void checkForSuperCall(UMethod method) {
    if (isConstructor()) {
      UExpression body = method.getUastBody();
      if (body == null) return;

      List<UExpression> statements = body instanceof UBlockExpression ? ((UBlockExpression)body).getExpressions() : Collections.singletonList(body);
      boolean isBaseExplicitlyCalled = false;
      if (!statements.isEmpty()) {
        UExpression first = statements.get(0);
        if (first instanceof UCallExpression && ((UCallExpression)first).getKind() == UastCallKind.CONSTRUCTOR_CALL) {
          isBaseExplicitlyCalled = true;
        }
      }

      if (!isBaseExplicitlyCalled) {
        RefClass ownerClass = getOwnerClass();
        if (ownerClass != null) {
          for (RefClass superClass : ownerClass.getBaseClasses()) {
            WritableRefElement superDefaultConstructor = (WritableRefElement)superClass.getDefaultConstructor();
            if (superDefaultConstructor != null) {
              superDefaultConstructor.addInReference(this);
              addOutReference(superDefaultConstructor);
            }
          }
        }
      }
    }
  }

  @Override
  @NotNull
  public synchronized Collection<RefMethod> getSuperMethods() {
    return ObjectUtils.notNull(mySuperMethods, EMPTY_METHOD_LIST);
  }

  @Override
  @NotNull
  public synchronized Collection<RefMethod> getDerivedMethods() {
    return ObjectUtils.notNull(myDerivedMethods, EMPTY_METHOD_LIST);
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
        } else {
          setLibraryOverride(true);
        }
      }
      else {
        setLibraryOverride(true);
      }
    }
  }

  public void addSuperMethod(RefMethodImpl refSuperMethod) {
    if (!refSuperMethod.getSuperMethods().contains(this)) {
      synchronized (this) {
        List<RefMethod> superMethods = mySuperMethods;
        if (superMethods == null){
          mySuperMethods = superMethods = new ArrayList<>(1);
        }
        if (!superMethods.contains(refSuperMethod)) {
          superMethods.add(refSuperMethod);
        }
      }
    }
  }

  public void markExtended(RefMethodImpl method) {
    if (!method.getDerivedMethods().contains(this)) {
      synchronized (this) {
        List<RefMethod> derivedMethods = myDerivedMethods;
        if (derivedMethods == null) {
          myDerivedMethods = derivedMethods = new ArrayList<>(1);
        }
        if (!derivedMethods.contains(method)) {
          myDerivedMethods.add(method);
        }
      }
    }
  }

  @Override
  @NotNull
  public synchronized RefParameter[] getParameters() {
    return ObjectUtils.notNull(myParameters, EMPTY_PARAMS_ARRAY);
  }

  @Override
  public void buildReferences() {
    // Work on code block to find what we're referencing...
    UMethod method = (UMethod)getUastElement();
    if (method == null) return;
    UExpression body = method.getUastBody();
    final RefJavaUtil refUtil = RefJavaUtil.getInstance();
    refUtil.addReferencesTo(method, this, method);
    checkForSuperCall(method);
    setOnlyCallsSuper(refUtil.isMethodOnlyCallsSuper(method));

    setBodyEmpty(isOnlyCallsSuper() || !isExternalOverride() && isEmptyExpression(body));
    refUtil.addTypeReference((UElement)method, method.getReturnType(), getRefManager(), this);

    for (RefParameter parameter : getParameters()) {
      UParameter uParameter = parameter.getUastElement();
      if (uParameter != null) {
        refUtil.setIsFinal(parameter, uParameter.isFinal());
      }
    }

    getRefManager().fireBuildReferences(this);
  }

  private void collectUncaughtExceptions(@NotNull PsiMethod method) {
    if (getRefManager().isOfflineView()) return;

    PsiClassType[] throwsList = method.getThrowsList().getReferencedTypes();
    if (throwsList.length > 0) {
      List<String> unThrownExceptions = throwsList.length == 1 ? new SmartList<>() : new ArrayList<>(throwsList.length);
      for (final PsiClassType type : throwsList) {
        PsiClass aClass = type.resolve();
        String fqn = aClass == null ? null : aClass.getQualifiedName();
        if (fqn != null) {
          unThrownExceptions.add(fqn);
        }
      }
      synchronized (this) {
        myUnThrownExceptions = unThrownExceptions;
      }
    }
  }

  public synchronized void removeUnThrownExceptions(PsiClass unThrownException) {
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

  private boolean isLibraryOverride(@NotNull Collection<? super RefMethod> processed) {
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
      //noinspection SuspiciousMethodCalls
      if (!getDerivedMethods().contains(refCaller)) return true;
    }

    // Library override probably called from library code.
    return isExternalOverride();
  }

  @Override
  public boolean hasSuspiciousCallers() {
    // Directly called from somewhere..
    for (RefElement refCaller : getInReferences()) {
      //noinspection SuspiciousMethodCalls
      if (((RefElementImpl)refCaller).isSuspicious() && !getDerivedMethods().contains(refCaller)) return true;
    }

    // Library override probably called from library code.
    if (isExternalOverride()) return true;

    // Class isn't instantiated. Most probably we have problem with class, not method.
    if (!isStatic() && !isConstructor()) {
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

  @Nullable
  @Override
  public RefClass getOwnerClass() {
    return ObjectUtils.tryCast(getOwner(), RefClass.class);
  }

  @NotNull
  @Override
  public String getName() {
    if (isValid()) {
      return ReadAction.compute(() -> {
        UMethod uMethod = (UMethod)getUastElement();
        if (uMethod instanceof SyntheticElement) {
          return uMethod.getName();
        }
        return PsiFormatUtil.formatMethod(uMethod.getJavaPsi(),
                                          PsiSubstitutor.EMPTY,
                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                          PsiFormatUtilBase.SHOW_TYPE);
      });
    }
    return super.getName();
  }

  @Override
  public String getExternalName() {
    return ReadAction.compute(() -> {
      final UMethod uMethod = (UMethod)getUastElement();
      LOG.assertTrue(uMethod != null);
      PsiMethod javaMethod = uMethod.getJavaPsi();
      return PsiFormatUtil.getExternalName(javaMethod, true, Integer.MAX_VALUE);
    });
  }

  @Nullable
  static RefMethod methodFromExternalName(RefManager manager, String externalName) {
    return (RefMethod) manager.getReference(findPsiMethod(PsiManager.getInstance(manager.getProject()), externalName));
  }

  @Nullable
  public static PsiMethod findPsiMethod(PsiManager manager, String externalName) {
    final int spaceIdx = externalName.indexOf(' ');
    final String className = externalName.substring(0, spaceIdx);
    final PsiClass psiClass = ClassUtil.findPsiClass(manager, className);
    if (psiClass == null) return null;
    try {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      String methodSignature = externalName.substring(spaceIdx + 1);
      MethodSignature patternSignature = factory.createMethodFromText(methodSignature, psiClass).getSignature(PsiSubstitutor.EMPTY);
      return Arrays.stream(psiClass.findMethodsByName(patternSignature.getName(), false)).filter(m -> {
        MethodSignature s = m.getSignature(PsiSubstitutor.EMPTY);
        MethodSignature refinedPatternSignature = factory.createMethodFromText(methodSignature, m).getSignature(s.getSubstitutor());
        return MethodSignatureUtil.areErasedParametersEqual(s, refinedPatternSignature);
      }).findFirst().orElse(null);
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
  }

  @Override
  public boolean isSuspicious() {
    if (isConstructor() &&
        PsiModifier.PRIVATE.equals(getAccessModifier()) &&
        getParameters().length == 0 &&
        Objects.requireNonNull(getOwnerClass()).getConstructors().size() == 1) return false;
    return super.isSuspicious();
  }

  void setReturnValueUsed(boolean value) {
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

  void updateReturnValueTemplate(UExpression expression) {
    if (expression == null) return;
    synchronized (this) {
      if (myReturnValueTemplate == null) return;
    }

    if (!getSuperMethods().isEmpty()) {
      for (final RefMethod refMethod : getSuperMethods()) {
        RefMethodImpl refSuper = (RefMethodImpl)refMethod;
        refSuper.updateReturnValueTemplate(expression);
      }
    }
    else {
      String newTemplate = null;
      final RefJavaUtil refUtil = RefJavaUtil.getInstance();
      if (expression instanceof ULiteralExpression) {
        ULiteralExpression psiLiteralExpression = (ULiteralExpression)expression;
        newTemplate = String.valueOf(psiLiteralExpression.getValue());
      }
      else if (expression instanceof UResolvable) {
        UResolvable referenceExpression = (UResolvable)expression;
        UElement resolved = UResolvableKt.resolveToUElement(referenceExpression);
        if (resolved instanceof UField) {
          UField uField = (UField)resolved;
          PsiField psi = (PsiField)uField.getJavaPsi();
          if (uField.isStatic() &&
              uField.isFinal() &&
              refUtil.compareAccess(refUtil.getAccessModifier(psi), getAccessModifier()) >= 0) {
            newTemplate = PsiFormatUtil.formatVariable(psi, PsiFormatUtilBase.SHOW_NAME |
                                                                 PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                 PsiFormatUtilBase.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
          }
        }
      }
      else if (refUtil.isCallToSuperMethod(expression, (UMethod)getUastElement())) return;

      synchronized (this) {
        //noinspection StringEquality
        if (myReturnValueTemplate == RETURN_VALUE_UNDEFINED) {
          myReturnValueTemplate = newTemplate;
        }
        else if (!Comparing.equal(myReturnValueTemplate, newTemplate)) {
          myReturnValueTemplate = null;
        }
      }
    }
  }

  void updateParameterValues(List<UExpression> args, @Nullable PsiElement elementPlace) {
    if (isExternalOverride()) return;

    if (!getSuperMethods().isEmpty()) {
      for (RefMethod refSuper : getSuperMethods()) {
        ((RefMethodImpl)refSuper).updateParameterValues(args, null);
      }
    } else {
      final RefParameter[] params = getParameters();
      for (int i = 0; i < Math.min(params.length, args.size()); i++) {
        ((RefParameterImpl)params[i]).updateTemplateValue(args.get(i), elementPlace);
      }

      if (params.length != args.size() && params.length != 0) {
        ((RefParameterImpl)params[params.length - 1]).clearTemplateValue();
      }
    }
  }

  @Override
  public synchronized String getReturnValueIfSame() {
    //noinspection StringEquality
    if (myReturnValueTemplate == RETURN_VALUE_UNDEFINED) return null;
    return myReturnValueTemplate;
  }

  public void updateThrowsList(PsiClassType exceptionType) {
    for (RefMethod refSuper : getSuperMethods()) {
      ((RefMethodImpl)refSuper).updateThrowsList(exceptionType);
    }
    synchronized (this) {
      List<String> unThrownExceptions = myUnThrownExceptions;
      if (unThrownExceptions != null) {
        if (exceptionType == null) {
          myUnThrownExceptions = null;
        }
        else {
          PsiClass exceptionClass = exceptionType.resolve();
          JavaPsiFacade facade = JavaPsiFacade.getInstance(myManager.getProject());
          for (int i = unThrownExceptions.size() - 1; i >= 0; i--) {
            String exceptionFqn = unThrownExceptions.get(i);
            PsiClass classType = facade.findClass(exceptionFqn, GlobalSearchScope.allScope(getRefManager().getProject()));
            if (InheritanceUtil.isInheritorOrSelf(exceptionClass, classType, true) ||
                InheritanceUtil.isInheritorOrSelf(classType, exceptionClass, true)) {
              unThrownExceptions.remove(i);
            }
          }

          if (unThrownExceptions.isEmpty()) myUnThrownExceptions = null;
        }
      }
    }
  }

  @Override
  @Nullable
  public synchronized PsiClass[] getUnThrownExceptions() {
    if (getRefManager().isOfflineView()) {
      LOG.debug("Should not traverse graph offline");
    }
    List<String> unThrownExceptions = myUnThrownExceptions;
    if (unThrownExceptions == null) return null;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myManager.getProject());
    List<PsiClass> result = new ArrayList<>(unThrownExceptions.size());
    for (String exception : unThrownExceptions) {
      PsiClass element = facade.findClass(exception, GlobalSearchScope.allScope(myManager.getProject()));
      if (element != null) result.add(element);
    }
    return result.toArray(PsiClass.EMPTY_ARRAY);
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
  public UDeclaration getUastElement() {
    return UastContextKt.toUElement(getPsiElement(), UMethod.class);
  }

  @Override
  public boolean isCalledOnSubClass() {
    return checkFlag(IS_CALLED_ON_SUBCLASS_MASK);
  }

  void setCalledOnSubClass(boolean isCalledOnSubClass){
    setFlag(isCalledOnSubClass, IS_CALLED_ON_SUBCLASS_MASK);
  }

  public static boolean isEmptyExpression(@Nullable UExpression expression) {
    if (expression == null) return true;
    if (expression instanceof UBlockExpression) return ((UBlockExpression)expression).getExpressions().isEmpty();
    return false;
  }

  static RefElement findParentRef(@NotNull PsiElement psiElement, @NotNull UElement uElement, @NotNull RefManagerImpl refManager) {
    UDeclaration containingUDecl = UDeclarationKt.getContainingDeclaration(uElement);
    PsiElement containingDeclaration = containingUDecl == null ? null : containingUDecl.getSourcePsi();
    if (containingDeclaration instanceof LightElement) {
      containingDeclaration = containingDeclaration.getNavigationElement();
    }
    final RefElement parentRef;
    //TODO strange
    if (containingDeclaration == null || containingDeclaration instanceof LightElement) {
      parentRef = refManager.getReference(psiElement.getContainingFile(), true);
    }
    else {
      parentRef = refManager.getReference(containingDeclaration, true);
    }
    return parentRef;
  }
}
