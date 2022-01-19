// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmMetaLanguage;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.util.JvmInheritanceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.*;

public final class RefClassImpl extends RefJavaElementImpl implements RefClass {
  private static final Set<RefElement> EMPTY_SET = Collections.emptySet();
  private static final Set<RefClass> EMPTY_CLASS_SET = Collections.emptySet();
  private static final List<RefMethod> EMPTY_METHOD_LIST = ContainerUtil.emptyList();
  private static final int IS_ANONYMOUS_MASK = 0b1_00000000_00000000;
  private static final int IS_INTERFACE_MASK = 0b10_00000000_00000000;
  private static final int IS_UTILITY_MASK   = 0b100_00000000_00000000;
  private static final int IS_ABSTRACT_MASK  = 0b1000_00000000_00000000;

  private static final int IS_APPLET_MASK    = 0b100000_00000000_00000000;
  private static final int IS_SERVLET_MASK   = 0b1000000_00000000_00000000;
  private static final int IS_TESTCASE_MASK  = 0b10000000_00000000_00000000;
  private static final int IS_LOCAL_MASK     = 0b1_00000000_00000000_00000000;

  private Set<RefClass> myBases; // singleton (to conserve memory) or HashSet. guarded by this
  private Set<RefOverridable> myDerivedReferences; // singleton (to conserve memory) or HashSet. guarded by this
  private List<RefMethod> myConstructors; // guarded by this
  private RefMethodImpl myDefaultConstructor; //guarded by this
  private List<RefMethod> myOverridingMethods; //guarded by this
  private Set<RefElement> myInTypeReferences; //guarded by this
  private List<RefJavaElement> myClassExporters; //guarded by this
  private final RefModule myRefModule;

  RefClassImpl(UClass uClass, PsiElement psi, RefManager manager) {
    super(uClass, psi, manager);
    myRefModule = manager.getRefModule(ModuleUtilCore.findModuleForPsiElement(psi));

    setInterface(uClass.isInterface());
    setAbstract(uClass.getJavaPsi().hasModifier(JvmModifier.ABSTRACT));
    setAnonymous(uClass.getName() == null);
  }

  @Override
  protected void initialize() {
    setDefaultConstructor(null);

    UClass uClass = getUastElement();
    LOG.assertTrue(uClass != null);

    UDeclaration parent = UDeclarationKt.getContainingDeclaration(uClass);
    while (parent != null) {
      if (parent instanceof UMethod || parent instanceof UClass || parent instanceof UField) {
        break;
      }
      parent = UDeclarationKt.getContainingDeclaration(parent);
    }
    RefElement refParent = parent != null ? getRefManager().getReference(parent.getSourcePsi()) : null;
    if (refParent != null) {
      ((RefElementImpl)refParent).add(this);
    }
    else {
      PsiFile containingFile = getContainingFile();
      if (isSyntheticJSP()) {
        final PsiFile psiFile = PsiUtilCore.getTemplateLanguageFile(getPsiElement());
        final RefFileImpl refFile = (RefFileImpl)getRefManager().getReference(psiFile);
        LOG.assertTrue(refFile != null);
        refFile.add(this);
      }
      else if (isKindOfJvmLanguage(containingFile.getLanguage())) {
        String packageName = UastContextKt.toUElement(containingFile, UFile.class).getPackageName();
        if (!packageName.isEmpty()) {
          ((RefPackageImpl)getRefJavaManager().getPackage(packageName)).add(this);
        }
        else {
          ((RefPackageImpl)getRefJavaManager().getDefaultPackage()).add(this);
        }
      }
      else {
        ((WritableRefEntity)myRefModule).add(this);
      }
    }

    if (!myManager.isDeclarationsFound()) return;

    setIsLocal(!isAnonymous() && parent != null && !(parent instanceof UClass));

    initializeSuperReferences(uClass);

    UMethod[] uMethods = uClass.getMethods();
    UField[] uFields = uClass.getFields();

    boolean utilityClass = uMethods.length > 0 || uFields.length > 0;

    for (UField uField : uFields) {
      final RefField field = ObjectUtils.tryCast(getRefManager().getReference(uField.getSourcePsi()), RefField.class);
      if (field != null) addChild(field);
    }
    RefMethod varargConstructor = null;
    for (UMethod uMethod : uMethods) {
      RefMethod refMethod = ObjectUtils.tryCast(getRefManager().getReference(uMethod.getSourcePsi()), RefMethod.class);

      if (refMethod != null) {
        addChild(refMethod);
        if (uMethod.isConstructor()) {
          final List<UParameter> parameters = uMethod.getUastParameters();
          if (!parameters.isEmpty()|| uMethod.getVisibility() != UastVisibility.PRIVATE) {
            utilityClass = false;
          }

          addConstructor(refMethod);
          if (parameters.isEmpty()) {
            setDefaultConstructor((RefMethodImpl)refMethod);
          }
          else if (parameters.size() == 1) {
            PsiElement parameterPsi = parameters.get(0).getJavaPsi();
            if (parameterPsi instanceof PsiParameter && ((PsiParameter)parameterPsi).isVarArgs()) {
              varargConstructor = refMethod;
            }
          }
        }
        else {
          if (!uMethod.isStatic()) {
            utilityClass = false;
          }
        }
      }
    }
    if (!isInterface()) {
      for (int i = 0; i < uFields.length && utilityClass; i++) {
        if (!uFields[i].isStatic()) {
          utilityClass = false;
        }
      }
    }

    if (!utilityClass) {
      utilityClass = ClassUtils.isSingleton(uClass.getJavaPsi());
    }

    synchronized (this) {
      if (varargConstructor != null && myDefaultConstructor == null) {
        setDefaultConstructor((RefMethodImpl)varargConstructor);
      }
    }

    if (getConstructors().isEmpty() && !isInterface() && !isAnonymous()) {
      RefImplicitConstructorImpl refImplicitConstructor = new RefImplicitConstructorImpl(this);
      setDefaultConstructor(refImplicitConstructor);
      addConstructor(refImplicitConstructor);
    }

    setUtilityClass(utilityClass);

    if (!utilityClass) {
      setApplet(getRefJavaManager().getApplet() != null && JvmInheritanceUtil.isInheritor(uClass, getRefJavaManager().getAppletQName()));
      if (!isApplet()) {
        setServlet(JvmInheritanceUtil.isInheritor(uClass.getJavaPsi(), getRefJavaManager().getServletQName()));
      }
      if (!isApplet() && !isServlet()) {
        PsiElement psi = uClass.getSourcePsi();
        if (psi instanceof PsiClass) {
          final boolean isTestClass = TestFrameworks.getInstance().isTestClass((PsiClass)psi);
          setTestCase(isTestClass);
          if (isTestClass) {
            for (RefClass refBase : getBaseClasses()) {
              ((RefClassImpl)refBase).setTestCase(true);
            }
          }
        }
      }
    }
  }

  private void initializeSuperReferences(UClass uClass) {
    if (!isSelfInheritor(uClass)) {
        uClass.getUastSuperTypes().stream()
        .map(t -> PsiUtil.resolveClassInType(t.getType()))
        .filter(Objects::nonNull)
        .filter(c -> getRefJavaManager().belongsToScope(c))
        .forEach(c -> {
          RefClassImpl refClass = (RefClassImpl)getRefManager().getReference(c);
          if (refClass != null) {
            addBaseClass(refClass);
            refClass.addDerivedReference(this);
          }
        });
    }
  }

  @Override
  public boolean isSelfInheritor(@NotNull UClass uClass) {
    return isSelfInheritor(uClass, new ArrayList<>());
  }

  @Override
  public @Nullable RefModule getModule() {
    return myRefModule;
  }

  private static boolean isSelfInheritor(UClass uClass, List<? super UClass> visited) {
    if (visited.contains(uClass)) return true;
    visited.add(uClass);
    if (uClass.getUastSuperTypes().stream()
              .map(t -> PsiUtil.resolveClassInType(t.getType()))
              .filter(Objects::nonNull)
              .map(c -> UastContextKt.toUElement(c, UClass.class))
              .filter(Objects::nonNull)
              .anyMatch(c -> isSelfInheritor(c, visited))) {
      return true;
    }

    visited.remove(uClass);
    return false;
  }

  private synchronized void setDefaultConstructor(RefMethodImpl defaultConstructor) {
    myDefaultConstructor = defaultConstructor;
  }

  @Override
  public UClass getUastElement() {
    return UastContextKt.toUElement(getPsiElement(), UClass.class);
  }

  @Override
  public @NotNull String getQualifiedName() {
    final UClass uClass = getUastElement();
    if (uClass == null) return super.getQualifiedName();
    final String qName = uClass.getQualifiedName();
    if (qName == null) return super.getQualifiedName();
    return qName;
  }

  @Override
  public void buildReferences() {
    UClass uClass = getUastElement();
    if (uClass != null) {
      if (uClass instanceof UAnonymousClass) {
        UObjectLiteralExpression objectAccess = UastUtils.getParentOfType(uClass, UObjectLiteralExpression.class);
        if (objectAccess != null && objectAccess.getDeclaration().getSourcePsi() == uClass.getSourcePsi()) {
          RefJavaUtil.getInstance().addReferencesTo(uClass, this, objectAccess.getValueArguments().toArray(UElementKt.EMPTY_ARRAY));
        }
      }

      for (UClassInitializer classInitializer : uClass.getInitializers()) {
        RefJavaUtil.getInstance().addReferencesTo(uClass, this, classInitializer.getUastBody());
      }

      RefJavaUtil.getInstance().addReferencesTo(uClass, this, uClass.getUAnnotations().toArray(UElementKt.EMPTY_ARRAY));

      for (PsiTypeParameter parameter : uClass.getJavaPsi().getTypeParameters()) {
        UElement uTypeParameter = UastContextKt.toUElement(parameter);
        if (uTypeParameter != null) {
          RefJavaUtil.getInstance().addReferencesTo(uClass, this, uTypeParameter);
        }
      }

      UField[] uFields = uClass.getFields();
      for (UField uField : uFields) {
        final UExpression initializer = uField.getUastInitializer();
        if (initializer != null) {
          RefJavaUtil.getInstance().addReferencesTo(uClass, this, initializer);
        }
      }

      waitForInitialized();
      final RefMethodImpl defaultConstructor = (RefMethodImpl)getDefaultConstructor();
      if (defaultConstructor != null) {
        for (RefClass superClass : getBaseClasses()) {
          superClass.waitForInitialized();
          WritableRefElement superDefaultConstructor = (WritableRefElement)superClass.getDefaultConstructor();

          if (superDefaultConstructor != null) {
            superDefaultConstructor.addInReference(defaultConstructor);
            defaultConstructor.addOutReference(superDefaultConstructor);
          }
        }
      }

      RefJavaUtil.getInstance().addReferencesTo(uClass, this, uClass.getUastSuperTypes().toArray(UElementKt.EMPTY_ARRAY));

      getRefManager().fireBuildReferences(this);
    }
  }

  @Override
  public void accept(final @NotNull RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> ((RefJavaVisitor)visitor).visitClass(this));
    } else {
      super.accept(visitor);
    }
  }

  @Override
  public synchronized @NotNull Set<RefClass> getBaseClasses() {
    if (myBases == null) return EMPTY_CLASS_SET;
    return myBases;
  }

  private synchronized void addBaseClass(RefClass refClass){
    if (myBases == null) {
      myBases = Collections.singleton(refClass);
      return;
    }
    if (myBases.size() == 1) {
      // convert from singleton
      myBases = new HashSet<>(myBases);
    }
    myBases.add(refClass);
  }

  @Override
  public synchronized @NotNull Set<RefClass> getSubClasses() {
    if (myDerivedReferences == null) return EMPTY_CLASS_SET;
    return StreamEx.of(myDerivedReferences).select(RefClass.class).toSet();
  }

  @Override
  public synchronized @NotNull Collection<? extends RefOverridable> getDerivedReferences() {
    return ObjectUtils.notNull(myDerivedReferences, EMPTY_CLASS_SET);
  }

  @Override
  public synchronized void addDerivedReference(@NotNull RefOverridable reference) {
    if (myDerivedReferences == null) {
      myDerivedReferences = Collections.singleton(reference);
      return;
    }
    if (myDerivedReferences.size() == 1) {
      // convert from singleton
      myDerivedReferences = new HashSet<>(myDerivedReferences);
    }
    myDerivedReferences.add(reference);
  }

  private synchronized void removeSubClass(RefClass refClass) {
    if (myDerivedReferences == null) return;
    if (myDerivedReferences.size() == 1) {
      myDerivedReferences = null;
    }
    else {
      myDerivedReferences.remove(refClass);
    }
  }

  @Override
  public synchronized @NotNull List<RefMethod> getConstructors() {
    if (myConstructors == null) return EMPTY_METHOD_LIST;
    return myConstructors;
  }

  @Override
  public synchronized @NotNull Set<RefElement> getInTypeReferences() {
    if (myInTypeReferences == null) return EMPTY_SET;
    return myInTypeReferences;
  }

  void addTypeReference(RefJavaElement from) {
    if (from != null) {
      synchronized (this) {
        if (myInTypeReferences == null){
          myInTypeReferences = new HashSet<>(1);
        }
        myInTypeReferences.add(from);
      }
      ((RefJavaElementImpl)from).addOutTypeReference(this);
      getRefManager().fireNodeMarkedReferenced(this, from, false, false, false);
    }
  }

  @Override
  public synchronized RefMethod getDefaultConstructor() {
    return myDefaultConstructor;
  }

  private synchronized void addConstructor(RefMethod refConstructor) {
    if (myConstructors == null){
      myConstructors = new ArrayList<>(1);
    }
    myConstructors.add(refConstructor);
  }

  synchronized void addLibraryOverrideMethod(RefMethod refMethod) {
    if (myOverridingMethods == null){
      myOverridingMethods = new ArrayList<>(2);
    }
    myOverridingMethods.add(refMethod);
  }

  @Override
  public synchronized @NotNull List<RefMethod> getLibraryMethods() {
    if (myOverridingMethods == null) return EMPTY_METHOD_LIST;
    return myOverridingMethods;
  }

  @Override
  public boolean isAnonymous() {
    return checkFlag(IS_ANONYMOUS_MASK);
  }

  @Override
  public boolean isInterface() {
    return checkFlag(IS_INTERFACE_MASK);
  }

  @Override
  public boolean isSuspicious() {
    return !(isUtilityClass() && getOutReferences().isEmpty()) && super.isSuspicious();
  }

  @Override
  public boolean isUtilityClass() {
    return checkFlag(IS_UTILITY_MASK);
  }

  @Override
  public String getExternalName() {
    return ReadAction.compute(() -> {//todo synthetic JSP
      final UClass psiClass = getUastElement();
      LOG.assertTrue(psiClass != null, "No uast class found for psi: " + getPsiElement());
      return PsiFormatUtil.getExternalName(psiClass.getJavaPsi());
    });
  }


  static @Nullable RefClass classFromExternalName(RefManager manager, String externalName) {
    return (RefClass) manager.getReference(ClassUtil.findPsiClass(PsiManager.getInstance(manager.getProject()), externalName));
  }

  @Override
  public void referenceRemoved() {
    super.referenceRemoved();

    for (RefClass subClass : getSubClasses()) {
      ((RefClassImpl)subClass).removeBase(this);
    }

    for (RefClass superClass : getBaseClasses()) {
      ((RefClassImpl)superClass).removeSubClass(this);
    }
  }

  private synchronized void removeBase(RefClass superClass) {
    final Set<RefClass> baseClasses = getBaseClasses();
    if (baseClasses.contains(superClass)) {
      if (baseClasses.size() == 1) {
        myBases = null;
        return;
      }
      baseClasses.remove(superClass);
    }
  }

  void methodRemoved(RefMethod method) {
    getConstructors().remove(method);
    getLibraryMethods().remove(method);

    if (getDefaultConstructor() == method) {
      setDefaultConstructor(null);
    }
  }

  @Override
  public boolean isAbstract() {
    return checkFlag(IS_ABSTRACT_MASK);
  }

  @Override
  public boolean isApplet() {
    return checkFlag(IS_APPLET_MASK);
  }

  @Override
  public boolean isServlet() {
    return checkFlag(IS_SERVLET_MASK);
  }

  @Override
  public boolean isTestCase() {
    return checkFlag(IS_TESTCASE_MASK);
  }

  @Override
  public boolean isLocalClass() {
    return checkFlag(IS_LOCAL_MASK);
  }


  @Override
  public boolean isReferenced() {
    if (super.isReferenced()) return true;

    if (isInterface()) {
      if (!getDerivedReferences().isEmpty()) return true;
    }
    else if (isAbstract()) {
      if (!getSubClasses().isEmpty()) return true;
    }

    return false;
  }

  @Override
  public boolean hasSuspiciousCallers() {
    if (super.hasSuspiciousCallers()) return true;

    if (isInterface()) {
      if (!getDerivedReferences().isEmpty()) return true;
    }
    else if (isAbstract()) {
      if (!getSubClasses().isEmpty()) return true;
    }

    return false;
  }

  synchronized void addClassExporter(RefJavaElement exporter) {
    if (myClassExporters == null) myClassExporters = new ArrayList<>(1);
    if (myClassExporters.contains(exporter)) return;
    myClassExporters.add(exporter);
  }

  public synchronized List<RefJavaElement> getClassExporters() {
    return myClassExporters;
  }

  private void setAnonymous(boolean anonymous) {
    setFlag(anonymous, IS_ANONYMOUS_MASK);
  }

  private void setInterface(boolean anInterface) {
    setFlag(anInterface, IS_INTERFACE_MASK);
  }

  /**
   * typical jvm utility class has only static method or fields. But for example in kotlin utility classes (Objects) follow singleton pattern.
   */
  private void setUtilityClass(boolean utilityClass) {
    setFlag(utilityClass, IS_UTILITY_MASK);
  }

  private void setAbstract(boolean anAbstract) {
    setFlag(anAbstract, IS_ABSTRACT_MASK);
  }

  private void setApplet(boolean applet) {
    setFlag(applet, IS_APPLET_MASK);
  }

  private void setServlet(boolean servlet) {
    setFlag(servlet, IS_SERVLET_MASK);
  }

  private void setTestCase(boolean testCase) {
    setFlag(testCase, IS_TESTCASE_MASK);
  }

  private void setIsLocal(boolean isLocal) {
    setFlag(isLocal, IS_LOCAL_MASK);
  }

  @Override
  public @NotNull RefElement getContainingEntry() {
    RefElement defaultConstructor = getDefaultConstructor();
    if (defaultConstructor != null) return defaultConstructor;
    return super.getContainingEntry();
  }

  private static boolean isKindOfJvmLanguage(@NotNull Language language) {
    return Language.findInstance(JvmMetaLanguage.class).getMatchingLanguages().stream().anyMatch(language::is);
  }
}

