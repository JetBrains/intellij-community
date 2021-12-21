// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.*;

public class RefMethodImpl extends RefJavaElementImpl implements RefMethod {
  private static final RefParameter[] EMPTY_PARAMS_ARRAY = new RefParameter[0];

  private static final int IS_APPMAIN_MASK = 0b1_00000000_00000000;
  private static final int IS_LIBRARY_OVERRIDE_MASK = 0b10_00000000_00000000;
  private static final int IS_CONSTRUCTOR_MASK = 0b100_00000000_00000000;
  private static final int IS_ABSTRACT_MASK = 0b1000_00000000_00000000;
  private static final int IS_BODY_EMPTY_MASK = 0b10000_00000000_00000000;
  private static final int IS_ONLY_CALLS_SUPER_MASK = 0b100000_00000000_00000000;
  private static final int IS_RETURN_VALUE_USED_MASK = 0b1000000_00000000_00000000;

  private static final int IS_TEST_METHOD_MASK = 0b100_00000000_00000000_00000000;
  private static final int IS_CALLED_ON_SUBCLASS_MASK = 0b1000_00000000_00000000_00000000;

  private static final String RETURN_VALUE_UNDEFINED = "#";

  private List<RefMethod> mySuperMethods; // guarded by this
  private List<RefOverridable> myDerivedReferences; // guarded by this
  private List<String> myUnThrownExceptions; // guarded by this

  private RefParameter[] myParameters; // guarded by this
  private volatile String myReturnValueTemplate = RETURN_VALUE_UNDEFINED; // guarded by this

  RefMethodImpl(UMethod method, PsiElement psi, RefManager manager) {
    super(method, psi, manager);

    setConstructor(method.isConstructor());
    PsiType returnType = method.getReturnType();
    setFlag(returnType == null || PsiType.VOID.equals(returnType) ||
            returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID), IS_RETURN_VALUE_USED_MASK);
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

    List<UParameter> paramList = method.getUastParameters();
    if (!paramList.isEmpty()) {
      List<RefParameter> newParameters = new ArrayList<>(paramList.size());
      final RefJavaUtil refUtil = RefJavaUtil.getInstance();
      for (int i = 0; i < paramList.size(); i++) {
        UParameter param = paramList.get(i);
        if (param.getSourcePsi() != null) {
          final RefParameter refParameter = getRefJavaManager().getParameterReference(param, i, this);
          if (refParameter != null) {
            refUtil.setIsFinal(refParameter, param.isFinal());
            newParameters.add(refParameter);
          }
        }
      }
      synchronized (this) {
        myParameters = newParameters.toArray(EMPTY_PARAMS_ARRAY);
      }
    }

    RefElement parentRef = findParentRef(sourcePsi, method, myManager);
    if (parentRef == null) return;
    this.setOwner((WritableRefEntity)parentRef);
    if (!myManager.isDeclarationsFound()) return;

    PsiMethod javaPsi = method.getJavaPsi();
    RefClass ownerClass = ObjectUtils.tryCast(parentRef, RefClass.class);
    if (!isConstructor()) {
      if (ownerClass != null && ownerClass.isInterface()) {
        setAbstract(false);
      }
      else {
        setAbstract(javaPsi.hasModifierProperty(PsiModifier.ABSTRACT));
      }

      boolean isNative = javaPsi.hasModifierProperty(PsiModifier.NATIVE);
      setLibraryOverride(isNative);
      if (javaPsi.hasModifierProperty(PsiModifier.PUBLIC)) {
        setAppMain(isAppMain(javaPsi, this));

        @NonNls final String name = method.getName();
        if (ownerClass != null && ownerClass.isTestCase() && name.startsWith("test")) {
          setTestMethod(true);
        }
      }
      if (!javaPsi.hasModifierProperty(PsiModifier.PRIVATE)) {
        initializeSuperMethods(javaPsi);
        if (ownerClass != null && isExternalOverride()) {
          ((RefClassImpl)ownerClass).addLibraryOverrideMethod(this);
        }
      }
    }

    if (sourcePsi instanceof PsiMethod && sourcePsi.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      collectUncaughtExceptions((PsiMethod)sourcePsi);
    }
  }

  public void setParametersAreUnknown() {
    for (RefParameter parameter : getParameters()) {
      parameter.waitForInitialized();
      ((RefParameterImpl)parameter).clearTemplateValue();
    }
    for (RefMethod method : getSuperMethods()) {
      method.waitForInitialized();
      ((RefMethodImpl)method).setParametersAreUnknown();
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

      List<UExpression> statements =
        body instanceof UBlockExpression ? ((UBlockExpression)body).getExpressions() : Collections.singletonList(body);
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
    return ObjectUtils.notNull(mySuperMethods, Collections.emptyList());
  }

  @Override
  @NotNull
  public synchronized Collection<RefMethod> getDerivedMethods() {
    if (myDerivedReferences == null) return Collections.emptyList();
    return StreamEx.of(myDerivedReferences).select(RefMethod.class).toList();
  }

  @Override
  public synchronized @NotNull Collection<? extends RefOverridable> getDerivedReferences() {
    return ObjectUtils.notNull(myDerivedReferences, Collections.emptyList());
  }

  @Override
  public void addDerivedReference(@NotNull RefOverridable reference) {
    if (reference.getDerivedReferences().contains(this)) return;
    synchronized (this) {
      if (myDerivedReferences == null) {
        myDerivedReferences = new ArrayList<>(1);
      }
      if (!myDerivedReferences.contains(reference)) {
        myDerivedReferences.add(reference);
      }
    }
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
    if (!isAbstract()) {
      RefClass ownerClass = getOwnerClass();
      if (ownerClass != null && !ownerClass.isInterface()) {
        return true;
      }
    }
    return !isBodyEmpty();
  }

  private void initializeSuperMethods(PsiMethod method) {
    if (getRefManager().isOfflineView()) return;
    for (PsiMethod psiSuperMethod : method.findSuperMethods()) {
      if (getRefManager().belongsToScope(psiSuperMethod)) {
        PsiElement sourceElement = psiSuperMethod instanceof LightElement ? psiSuperMethod.getNavigationElement() : psiSuperMethod;
        RefElement refElement = getRefManager().getReference(sourceElement);
        if (refElement instanceof RefMethodImpl) {
          RefMethodImpl refSuperMethod = (RefMethodImpl)refElement;
          addSuperMethod(refSuperMethod);
          refSuperMethod.markExtended(this);
        }
        else {
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
        if (mySuperMethods == null) {
          mySuperMethods = new ArrayList<>(1);
        }
        if (!mySuperMethods.contains(refSuperMethod)) {
          mySuperMethods.add(refSuperMethod);
        }
      }
    }
  }

  public void markExtended(RefMethodImpl method) {
    addDerivedReference(method);
  }

  @Override
  public synchronized RefParameter @NotNull [] getParameters() {
    LOG.assertTrue(isInitialized());
    return ObjectUtils.notNull(myParameters, EMPTY_PARAMS_ARRAY);
  }

  @Override
  public void buildReferences() {
    if (!isInitialized()) {
      // delay task until initialized.
      getRefManager().executeTask(() -> buildReferences());
      return;
    }

    // Work on code block to find what we're referencing...
    UMethod method = (UMethod)getUastElement();
    if (method == null) return;
    if (isConstructor()) {
      final RefClass ownerClass = getOwnerClass();
      assert ownerClass != null;
      ownerClass.waitForInitialized();
      addReference(ownerClass, ownerClass.getPsiElement(), method, false, true, null);
    }
    UExpression body = method.getUastBody();
    final RefJavaUtil refUtil = RefJavaUtil.getInstance();
    refUtil.addReferencesTo(method, this, method);
    checkForSuperCall(method);
    setOnlyCallsSuper(refUtil.isMethodOnlyCallsSuper(method));

    setBodyEmpty(isOnlyCallsSuper() || !isExternalOverride() && isEmptyExpression(body));
    refUtil.addTypeReference(method, method.getReturnType(), getRefManager(), this);

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
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public boolean isExternalOverride() {
    return isLibraryOverride(new HashSet<>());
  }

  private synchronized boolean isLibraryOverride(@NotNull Collection<? super RefMethod> processed) {
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
  public synchronized boolean isReferenced() {
    // Directly called from somewhere...
    for (RefElement refCaller : getInReferences()) {
      if (!getDerivedReferences().contains(refCaller)) return true;
    }

    // Library override probably called from library code.
    return isExternalOverride();
  }

  @Override
  public synchronized boolean hasSuspiciousCallers() {
    // Directly called from somewhere...
    for (RefElement refCaller : getInReferences()) {
      if (((RefElementImpl)refCaller).isSuspicious() && !getDerivedReferences().contains(refCaller)) return true;
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
    PsiElement method = findPsiMethod(PsiManager.getInstance(manager.getProject()), externalName);
    if (method instanceof LightElement) {
      method = method.getNavigationElement();
    }
    return (RefMethod)manager.getReference(method);
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
      return ContainerUtil.find(psiClass.findMethodsByName(patternSignature.getName(), false), m -> {
        MethodSignature s = m.getSignature(PsiSubstitutor.EMPTY);
        MethodSignature refinedPatternSignature = factory.createMethodFromText(methodSignature, m).getSignature(s.getSubstitutor());
        return MethodSignatureUtil.areErasedParametersEqual(s, refinedPatternSignature);
      });
    }
    catch (IncorrectOperationException e) {
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
      subMethod.getDerivedReferences().remove(this);
    }
  }

  @Override
  public boolean isSuspicious() {
    if (isConstructor() &&
        PsiModifier.PRIVATE.equals(getAccessModifier()) &&
        getParameters().length == 0 &&
        Objects.requireNonNull(getOwnerClass()).getConstructors().size() == 1) {
      return false;
    }
    return super.isSuspicious();
  }

  void setReturnValueUsed(boolean value) {
    LOG.assertTrue(isInitialized());
    if (checkFlag(IS_RETURN_VALUE_USED_MASK) == value) return;
    setFlag(value, IS_RETURN_VALUE_USED_MASK);
    for (RefMethod refSuper : getSuperMethods()) {
      refSuper.waitForInitialized();
      ((RefMethodImpl)refSuper).setReturnValueUsed(value);
    }
  }

  @Override
  public boolean isReturnValueUsed() {
    return checkFlag(IS_RETURN_VALUE_USED_MASK);
  }

  void updateReturnValueTemplate(UExpression expression) {
    LOG.assertTrue(isInitialized());
    if (expression == null) return;
    synchronized (this) {
      if (myReturnValueTemplate == null) return;
    }

    if (!getSuperMethods().isEmpty()) {
      for (final RefMethod refMethod : getSuperMethods()) {
        RefMethodImpl refSuper = (RefMethodImpl)refMethod;
        refSuper.waitForInitialized();
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
        else if (!Objects.equals(myReturnValueTemplate, newTemplate)) {
          myReturnValueTemplate = null;
        }
      }
    }
  }

  void updateParameterValues(List<UExpression> args, @Nullable PsiElement elementPlace) {
    LOG.assertTrue(isInitialized());
    if (isExternalOverride()) return;

    if (!getSuperMethods().isEmpty()) {
      for (RefMethod refSuper : getSuperMethods()) {
        refSuper.waitForInitialized();
        ((RefMethodImpl)refSuper).updateParameterValues(args, null);
      }
    }
    else {
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
    LOG.assertTrue(isInitialized());
    myManager.executeTask(() -> {
      for (RefMethod refSuper : getSuperMethods()) {
        refSuper.waitForInitialized();
        ((RefMethodImpl)refSuper).updateThrowsList(exceptionType);
      }
    });
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
  public synchronized PsiClass @Nullable [] getUnThrownExceptions() {
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

  private void setTestMethod(boolean testMethod) {
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

  void setCalledOnSubClass(boolean isCalledOnSubClass) {
    setFlag(isCalledOnSubClass, IS_CALLED_ON_SUBCLASS_MASK);
  }

  public static boolean isEmptyExpression(@Nullable UExpression expression) {
    if (expression == null) return true;
    if (expression instanceof UBlockExpression) return ((UBlockExpression)expression).getExpressions().isEmpty();
    return false;
  }

  @Nullable
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
