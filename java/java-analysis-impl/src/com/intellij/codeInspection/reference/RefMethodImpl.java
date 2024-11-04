// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;

import java.util.*;
import java.util.function.Predicate;

public sealed class RefMethodImpl extends RefJavaElementImpl implements RefMethod permits RefImplicitConstructorImpl {
  private static final int IS_APPMAIN_MASK            = 0b1_00000000_00000000; // 17th bit
  private static final int IS_LIBRARY_OVERRIDE_MASK   = 0b10_00000000_00000000; // 18th bit
  private static final int IS_CONSTRUCTOR_MASK        = 0b100_00000000_00000000; // 19th bit
  private static final int IS_ABSTRACT_MASK           = 0b1000_00000000_00000000; // 20th bit
  public static final int IS_BODY_EMPTY_MASK          = 0b10000_00000000_00000000; // 21st bit
  private static final int IS_ONLY_CALLS_SUPER_MASK   = 0b100000_00000000_00000000; // 22nd bit
  private static final int IS_RETURN_VALUE_USED_MASK  = 0b1000000_00000000_00000000; // 23rd bit
  private static final int IS_RECORD_ACCESSOR_MASK    = 0b10000000_00000000_00000000; // 24rd bit

  private static final int IS_CALLED_ON_SUBCLASS_MASK = 0b1000_00000000_00000000_00000000; // 28th bit

  private static final String RETURN_VALUE_UNDEFINED = "#";

  private Object mySuperMethods; // guarded by this
  private List<RefOverridable> myDerivedReferences; // guarded by this
  private List<String> myUnThrownExceptions; // guarded by this
  private volatile String myReturnValueTemplate = RETURN_VALUE_UNDEFINED; // guarded by this

  RefMethodImpl(UMethod method, PsiElement psi, RefManager manager) {
    super(method, psi, manager);

    setConstructor(method.isConstructor());
    PsiType returnType = method.getReturnType();
    setFlag(returnType == null || PsiTypes.voidType().equals(returnType) ||
            returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID), IS_RETURN_VALUE_USED_MASK);
  }

  // To be used only from RefImplicitConstructor!
  RefMethodImpl(@NotNull String name, @NotNull RefClass ownerClass) {
    super(name, ownerClass);

    // fair enough to add parent here
    ((RefClassImpl)ownerClass).add(this);

    addOutReference(ownerClass);
    ((WritableRefElement)ownerClass).addInReference(this);

    setConstructor(true);
  }

  @Override
  protected synchronized void initialize() {
    UMethod method = getUastElement();
    LOG.assertTrue(method != null);
    PsiElement sourcePsi = method.getSourcePsi();
    LOG.assertTrue(sourcePsi != null);

    List<UParameter> paramList = method.getUastParameters();
    if (!paramList.isEmpty()) {
      // Empty constructor body signals Java record header or Kotlin primary constructor (or red code): uses all parameters implicitly
      // TODO create an extension point for this kind of thing.
      boolean parameterUsed = method.isConstructor() && method.getUastBody() == null;
      for (int i = 0; i < paramList.size(); i++) {
        UParameter param = paramList.get(i);
        if (param.getSourcePsi() != null) {
          RefParameterImpl parameter = (RefParameterImpl)getRefJavaManager().getParameterReference(param, i, this);
          if (parameterUsed && parameter != null) {
            parameter.setUsedForReading();
          }
        }
      }
    }

    WritableRefEntity parentRef = (WritableRefEntity)findParentRef(sourcePsi, method, myManager);
    if (parentRef == null) return;
    if (!myManager.isDeclarationsFound()) {
      parentRef.add(this);
      return;
    }
    setOwner(parentRef);

    PsiMethod javaPsi = method.getJavaPsi();
    if (!method.isConstructor()) {
      setAbstract(javaPsi.hasModifierProperty(PsiModifier.ABSTRACT));

      setLibraryOverride(javaPsi.hasModifierProperty(PsiModifier.NATIVE));
      setAppMain(isAppMain(javaPsi, this));
      if (!PsiModifier.PRIVATE.equals(getAccessModifier()) && !isStatic()) {
        initializeSuperMethods(javaPsi);
      }
    }

    if (sourcePsi instanceof PsiMethod && sourcePsi.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      if (!method.isConstructor() && JavaPsiRecordUtil.getRecordComponentForAccessor((PsiMethod)sourcePsi) != null) {
        setRecordAccessor(true);
      }
      collectUncaughtExceptions((PsiMethod)sourcePsi);
    }
  }

  public void setParametersAreUnknown() {
    for (RefParameter parameter : getParameters()) {
      parameter.initializeIfNeeded();
      ((RefParameterImpl)parameter).clearTemplateValue();
    }
    for (RefMethod method : getSuperMethods()) {
      method.initializeIfNeeded();
      ((RefMethodImpl)method).setParametersAreUnknown();
    }
  }

  private static boolean isAppMain(PsiMethod psiMethod, RefMethod refMethod) {
    if ("main".equals(psiMethod.getName()) && PsiMethodUtil.isMainMethod(psiMethod)) return true;

    if (!refMethod.isStatic()) return false;
    if (!PsiTypes.voidType().equals(psiMethod.getReturnType())) return false;

    if ("main".equals(psiMethod.getName()) && psiMethod.getParameterList().isEmpty() &&
        psiMethod.getLanguage().isKindOf("kotlin")) return true;

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
        body instanceof UBlockExpression blockExpression ? blockExpression.getExpressions() : Collections.singletonList(body);
      boolean isBaseExplicitlyCalled = false;
      if (!statements.isEmpty()) {
        UExpression first = statements.get(0);
        if (first instanceof UCallExpression callExpression && callExpression.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
          isBaseExplicitlyCalled = true;
        }
      }

      if (!isBaseExplicitlyCalled) {
        RefClass ownerClass = getOwnerClass();
        if (ownerClass != null) {
          for (RefClass superClass : ownerClass.getBaseClasses()) {
            superClass.initializeIfNeeded();
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
    if (mySuperMethods instanceof Collection) {
      //noinspection unchecked
      return (Collection<RefMethod>)mySuperMethods;
    }
    return mySuperMethods != null ? List.of((RefMethod)mySuperMethods) : List.of();
  }

  @Override
  @NotNull
  public synchronized Collection<RefMethod> getDerivedMethods() {
    if (myDerivedReferences == null) return Collections.emptyList();
    return ContainerUtil.filterIsInstance(myDerivedReferences, RefMethod.class);
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
      if (ownerClass == null || !ownerClass.isInterface()) {
        return true;
      }
    }
    return !isBodyEmpty();
  }

  private void initializeSuperMethods(PsiMethod method) {
    final RefManagerImpl refManager = getRefManager();
    if (refManager.isOfflineView()) return;
    for (PsiMethod psiSuperMethod : method.findSuperMethods()) {
      if (refManager.belongsToScope(psiSuperMethod)) {
        PsiElement sourceElement = RefJavaUtilImpl.returnToPhysical(psiSuperMethod);
        RefElement refElement = refManager.getReference(sourceElement);
        if (refElement instanceof RefMethodImpl refSuperMethod) {
          addSuperMethod(refSuperMethod);
          refManager.executeTask(() -> refSuperMethod.markExtended(this));
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

  public synchronized void addSuperMethod(RefMethodImpl refSuperMethod) {
    if (refSuperMethod.checkFlag(IS_LIBRARY_OVERRIDE_MASK)) setLibraryOverride(true);
    if (mySuperMethods == null) {
      mySuperMethods = refSuperMethod;
    }
    else if (mySuperMethods instanceof RefMethod refMethod) {
      ArrayList<RefMethod> list = new ArrayList<>(2);
      list.add(refMethod);
      list.add(refSuperMethod);
      mySuperMethods = list;
    }
    else {
      //noinspection unchecked
      ((List<RefMethod>)mySuperMethods).add(refSuperMethod);
    }
  }

  public void markExtended(RefMethodImpl method) {
    addDerivedReference(method);
  }

  @Override
  public synchronized RefParameter @NotNull [] getParameters() {
    LOG.assertTrue(isInitialized());
    return ContainerUtil.filterIsInstance(getChildren(), RefParameter.class).toArray(RefParameter.EMPTY_ARRAY);
  }

  @Override
  public void buildReferences() {
    initializeIfNeeded();

    // Work on code block to find what we're referencing...
    UMethod method = getUastElement();
    if (method == null) return;
    if (isConstructor()) {
      final RefClass ownerClass = getOwnerClass();
      assert ownerClass != null;
      ownerClass.initializeIfNeeded();
      addReference(ownerClass, ownerClass.getPsiElement(), method, false, true, null);
    }
    final RefJavaUtil refUtil = RefJavaUtil.getInstance();
    refUtil.addReferencesTo(method, this, method);
    checkForSuperCall(method);
    setOnlyCallsSuper(refUtil.isMethodOnlyCallsSuper(method));

    setBodyEmpty(isOnlyCallsSuper() || !isExternalOverride() && isEmptyExpression(method.getUastBody()));
    refUtil.addTypeReference(method, method.getReturnType(), getRefManager(), this);
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
    if (visitor instanceof RefJavaVisitor javaVisitor) {
      ReadAction.run(() -> javaVisitor.visitMethod(this));
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
        setLibraryOverride(true);
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
      final UMethod uMethod = getUastElement();
      LOG.assertTrue(uMethod != null);
      PsiMethod javaMethod = uMethod.getJavaPsi();
      return PsiFormatUtil.getExternalName(javaMethod, true, Integer.MAX_VALUE);
    });
  }

  @Nullable
  static RefJavaElement methodFromExternalName(RefManager manager, String externalName) {
    PsiElement method = RefJavaUtilImpl.returnToPhysical(findPsiMethod(PsiManager.getInstance(manager.getProject()), externalName));
    RefElement reference = manager.getReference(method);
    if (!(reference instanceof RefJavaElement) && reference != null) {
      LOG.error("Expected refMethod but found: " + reference.getClass().getName() + "; for externalName: " +externalName );
      return null;
    }
    return (RefJavaElement)reference;
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
      refSuper.initializeIfNeeded();
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
        refSuper.initializeIfNeeded();
        refSuper.updateReturnValueTemplate(expression);
      }
    }
    else {
      if (RefJavaUtil.getInstance().isCallToSuperMethod(expression, getUastElement())) return;
      String newTemplate = createReturnValueTemplate(expression, this);

      synchronized (this) {
        if (Strings.areSameInstance(myReturnValueTemplate, RETURN_VALUE_UNDEFINED)) {
          myReturnValueTemplate = newTemplate;
        }
        else if (!Objects.equals(myReturnValueTemplate, newTemplate)) {
          myReturnValueTemplate = null;
        }
      }
    }
  }

  void updateParameterValues(@NotNull UCallExpression call, @Nullable PsiElement elementPlace) {
    LOG.assertTrue(isInitialized());
    if (call.getValueArguments().isEmpty()) return;
    if (isExternalOverride()) return;

    if (!getSuperMethods().isEmpty()) {
      for (RefMethod refSuper : getSuperMethods()) {
        refSuper.initializeIfNeeded();
        ((RefMethodImpl)refSuper).updateParameterValues(call, null);
      }
    }
    else {
      final RefParameter[] params = getParameters();
      for (int i = 0; i < params.length; i++) {
        ((RefParameterImpl)params[i]).updateTemplateValue(call.getArgumentForParameter(i), elementPlace);
      }
    }
  }

  @Override
  public synchronized String getReturnValueIfSame() {
    if (Strings.areSameInstance(myReturnValueTemplate, RETURN_VALUE_UNDEFINED)) return null;
    return myReturnValueTemplate;
  }

  public void updateThrowsList(PsiClassType exceptionType) {
    LOG.assertTrue(isInitialized());
    myManager.executeTask(() -> {
      for (RefMethod refSuper : getSuperMethods()) {
        refSuper.initializeIfNeeded();
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

  private void setRecordAccessor(boolean accessor) {
    setFlag(accessor, IS_RECORD_ACCESSOR_MASK);
  }

  @Override
  public boolean isTestMethod() {
    return TestFrameworks.getInstance().isTestMethod(getUastElement().getJavaPsi());
  }

  @Override
  public boolean isRecordAccessor() {
    return checkFlag(IS_RECORD_ACCESSOR_MASK);
  }

  @Override
  public UMethod getUastElement() {
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
    return expression instanceof UBlockExpression blockExpression && blockExpression.getExpressions().isEmpty();
  }

  @Nullable
  static RefElement findParentRef(@NotNull PsiElement psiElement, @NotNull UElement uElement, @NotNull RefManagerImpl refManager) {
    UDeclaration containingUDecl = UDeclarationKt.getContainingDeclaration(uElement);
    PsiElement containingDeclaration = RefJavaUtilImpl.returnToPhysical(containingUDecl == null ? null : containingUDecl.getSourcePsi());
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

  /**
   * Returns the string representation of the {@code UExpression} argument if the
   * expression is either a {@code ULiteralExpression} or a {@code UResolvable}
   * that resolves to a constant.
   *
   * @param expression an {@code UExpression}.
   * @return the string representation of a literal expression value if the
   * argument is a {@code UExpression}, or the fully qualified name of a
   * constant if the argument is a {@code UExpression} and resolves to a
   * constant, or {@code null} in all other cases.
   */
  @Contract("null -> null")
  public static @Nullable String createReturnValueTemplate(@Nullable UExpression expression) {
    return createReturnValueTemplate(expression, Predicates.alwaysTrue());
  }

  private static @Nullable String createReturnValueTemplate(UExpression expression, @NotNull RefMethodImpl refMethod) {
    RefJavaUtil refUtil = RefJavaUtil.getInstance();
    return createReturnValueTemplate(expression,
                                     field -> refUtil.compareAccess(refUtil.getAccessModifier(field), refMethod.getAccessModifier()) >= 0);
  }

  private static @Nullable String createReturnValueTemplate(UExpression expression, @NotNull Predicate<PsiField> predicate) {
    if (expression instanceof ULiteralExpression literalExpression) {
      return String.valueOf(literalExpression.getValue());
    }
    else if (expression instanceof UInjectionHost injectionHost) {
      return injectionHost.evaluateToString();
    }
    else if (expression instanceof UResolvable resolvable) {
      UElement resolved = UResolvableKt.resolveToUElement(resolvable);
      if (resolved instanceof UField uField && uField.isStatic() && uField.isFinal()) {
        PsiField psi = (PsiField)uField.getJavaPsi();
        if (psi != null && predicate.test(psi)) {
          return PsiFormatUtil.formatVariable(psi, PsiFormatUtilBase.SHOW_NAME |
                                                   PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                   PsiFormatUtilBase.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
        }
      }
    }
    return null;
  }
}
