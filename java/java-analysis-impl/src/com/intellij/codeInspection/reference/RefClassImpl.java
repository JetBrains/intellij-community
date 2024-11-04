// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmMetaLanguage;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.util.JvmInheritanceUtil;
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
  private static final int IS_ANONYMOUS_MASK  = 0b1_00000000_00000000; // 17th bit
  private static final int IS_INTERFACE_MASK  = 0b10_00000000_00000000; // 18th bit
  private static final int IS_UTILITY_MASK    = 0b100_00000000_00000000; // 19th bit
  private static final int IS_ABSTRACT_MASK   = 0b1000_00000000_00000000; // 20th bit
  private static final int IS_RECORD_MASK     = 0b10000_00000000_00000000; // 21st bit
  private static final int IS_APPLET_MASK     = 0b100000_00000000_00000000; // 22nd bit
  private static final int IS_SERVLET_MASK    = 0b1000000_00000000_00000000; // 23rd bit
  private static final int IS_TESTCASE_MASK   = 0b10000000_00000000_00000000; // 24th bit
  private static final int IS_LOCAL_MASK      = 0b1_00000000_00000000_00000000; // 25th bit
  private static final int IS_ENUM_MASK       = 0b10_00000000_00000000_00000000; // 26th bit
  private static final int IS_ANNOTATION_MASK = 0b100_00000000_00000000_00000000; // 27th bit

  private Object myBases; // singleton (to conserve memory) or HashSet. guarded by this
  private Set<RefOverridable> myDerivedReferences; // singleton (to conserve memory) or HashSet. guarded by this
  private RefMethodImpl myDefaultConstructor; // guarded by this
  private Set<RefElement> myInTypeReferences; // guarded by this
  private List<RefJavaElement> myClassExporters; // guarded by this
  private Set<RefClass> myOutTypeReferences; // guarded by this
  private final RefModule myRefModule;

  RefClassImpl(UClass uClass, PsiElement psi, RefManager manager) {
    super(uClass, psi, manager);
    myRefModule = manager.getRefModule(ModuleUtilCore.findModuleForPsiElement(psi));
  }

  @Override
  protected synchronized void initialize() {
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
    final RefManagerImpl manager = getRefManager();
    boolean utilityClass = true;
    final UClass[] innerClasses = uClass.getInnerClasses();
    for (UClass innerClass : innerClasses) {
      final PsiElement psi = innerClass.getSourcePsi();
      if (psi != null) {
        final RefElement refInnerClass = manager.getReference(psi);
        if (refInnerClass !=  null) {
          addChild(refInnerClass);
          if (!innerClass.isStatic()) utilityClass = false;
        }
      }
    }
    WritableRefEntity refParent = parent != null ? (WritableRefEntity)manager.getReference(parent.getSourcePsi()) : null;
    if (refParent != null) {
      if (!myManager.isDeclarationsFound()) {
        refParent.add(this);
      }
      else {
        setOwner(refParent);
      }
    }
    else {
      PsiFile containingFile = getContainingFile();
      if (isSyntheticJSP()) {
        final PsiFile psiFile = PsiUtilCore.getTemplateLanguageFile(getPsiElement());
        final RefFileImpl refFile = (RefFileImpl)manager.getReference(psiFile);
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

    setInterface(uClass.isInterface());
    setAnnotationType(uClass.isAnnotationType());
    setAnonymous(uClass instanceof UAnonymousClass);
    final PsiClass psiClass = uClass.getJavaPsi();
    if (!isAnonymous()) {
      setRecord(psiClass.isRecord());
      setEnum(psiClass.isEnum());
      setAbstract(psiClass.hasModifier(JvmModifier.ABSTRACT));
      setLocal(parent != null && !(parent instanceof UClass));
    }

    initializeSuperReferences(uClass);

    UMethod[] uMethods = uClass.getMethods();
    UField[] uFields = uClass.getFields();

    boolean memberSeen = false;
    for (UField uField : uFields) {
      final RefField field = ObjectUtils.tryCast(manager.getReference(uField.getSourcePsi()), RefField.class);
      if (field != null) {
        memberSeen = true;
        addChild(field);
        if (!uField.isStatic() || uField instanceof UEnumConstant) utilityClass = false;
      }
    }
    RefMethodImpl varargConstructor = null;
    boolean constructorSeen = false;
    for (UMethod uMethod : uMethods) {
      RefMethodImpl refMethod = ObjectUtils.tryCast(manager.getReference(uMethod.getSourcePsi()), RefMethodImpl.class);

      if (refMethod != null) {
        addChild(refMethod);
        if (uMethod.isConstructor()) {
          constructorSeen = true;
          final List<UParameter> parameters = uMethod.getUastParameters();
          if (!parameters.isEmpty() || uMethod.getVisibility() != UastVisibility.PRIVATE) {
            utilityClass = false;
          }

          if (parameters.isEmpty()) {
            setDefaultConstructor(refMethod);
          }
          else if (parameters.size() == 1) {
            PsiElement parameterPsi = parameters.get(0).getJavaPsi();
            if (parameterPsi instanceof PsiParameter && ((PsiParameter)parameterPsi).isVarArgs()) {
              varargConstructor = refMethod;
            }
          }
        }
        else {
          memberSeen = true;
          if (!uMethod.isStatic()) utilityClass = false;
        }
      }
    }

    if (!memberSeen || isInterface() || isRecord() || isAnonymous()) {
      utilityClass = false;
    }
    if (!utilityClass && psiClass.getLanguage().isKindOf("kotlin")) {
      // Kotlin companion object is singleton
      utilityClass = ClassUtils.isSingleton(psiClass);
    }

    if (myDefaultConstructor == null && varargConstructor != null) {
      setDefaultConstructor(varargConstructor);
    }
    if (!constructorSeen && !isInterface() && !isAnonymous()) {
      // implicit constructor is generated by javac when the class has no other constructor
      setDefaultConstructor(new RefImplicitConstructorImpl(this));
    }

    setUtilityClass(utilityClass);

    if (!utilityClass) {
      setApplet(getRefJavaManager().getApplet() != null && JvmInheritanceUtil.isInheritor(uClass, getRefJavaManager().getAppletQName()));
      if (!isApplet()) {
        setServlet(JvmInheritanceUtil.isInheritor(psiClass, getRefJavaManager().getServletQName()));
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
    uClass.getUastSuperTypes().stream()
      .map(t -> PsiUtil.resolveClassInClassTypeOnly(t.getType()))
      .filter(Objects::nonNull)
      .filter(c -> getRefJavaManager().belongsToScope(c))
      .forEach(c -> {
        RefClassImpl refClass = (RefClassImpl)getRefManager().getReference(c);
        if (refClass != null) {
          addBaseClass(refClass);
          getRefManager().executeTask(() -> refClass.addDerivedReference(this));
        }
      });
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
      .map(t -> PsiUtil.resolveClassInClassTypeOnly(t.getType()))
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
      final PsiElement classSourcePsi = uClass.getSourcePsi();
      final RefJavaUtil refUtil = RefJavaUtil.getInstance();
      if (uClass instanceof UAnonymousClass) {
        UObjectLiteralExpression objectAccess = UastUtils.getParentOfType(uClass, UObjectLiteralExpression.class);
        if (objectAccess != null && objectAccess.getDeclaration().getSourcePsi() == classSourcePsi) {
          refUtil.addReferencesTo(uClass, this, objectAccess.getValueArguments().toArray(UElementKt.EMPTY_ARRAY));
        }
      }

      for (UClassInitializer classInitializer : uClass.getInitializers()) {
        refUtil.addReferencesTo(uClass, this, classInitializer.getUastBody());
      }

      refUtil.addReferencesTo(uClass, this, uClass.getUAnnotations().toArray(UElementKt.EMPTY_ARRAY));

      for (PsiTypeParameter parameter : uClass.getJavaPsi().getTypeParameters()) {
        UElement uTypeParameter = UastContextKt.toUElement(parameter);
        if (uTypeParameter != null) {
          refUtil.addReferencesTo(uClass, this, uTypeParameter);
        }
      }

      final RefMethodImpl defaultConstructor = (RefMethodImpl)getDefaultConstructor();
      if (defaultConstructor != null) {
        for (RefClass superClass : getBaseClasses()) {
          superClass.initializeIfNeeded();
          WritableRefElement superDefaultConstructor = (WritableRefElement)superClass.getDefaultConstructor();

          if (superDefaultConstructor != null) {
            superDefaultConstructor.addInReference(defaultConstructor);
            defaultConstructor.addOutReference(superDefaultConstructor);
          }
        }
      }

      UMethod[] uMethods = uClass.getMethods();
      for (UMethod uMethod : uMethods) {
        if (uMethod.getSourcePsi() == classSourcePsi) {
          // Kotlin implicit constructor
          refUtil.addReferencesTo(uClass, this, uMethod.getUastBody());
        }
      }
      refUtil.addReferencesTo(uClass, this, uClass.getUastSuperTypes().toArray(UElementKt.EMPTY_ARRAY));
    }
  }

  @Override
  public void accept(final @NotNull RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor javaVisitor) {
      ReadAction.run(() -> javaVisitor.visitClass(this));
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public synchronized @NotNull Set<RefClass> getBaseClasses() {
    if (myBases == null) return EMPTY_CLASS_SET;
    //noinspection unchecked
    return myBases instanceof Set ? (Set<RefClass>)myBases : Collections.singleton((RefClass)myBases);
  }

  private synchronized void addBaseClass(RefClass refClass) {
    if (myBases == null) {
      myBases = refClass;
    }
    else if (myBases instanceof RefClass) {
      HashSet<RefClass> set = new HashSet<>();
      set.add((RefClass)myBases);
      set.add(refClass);
      myBases = set;
    }
    else {
      //noinspection unchecked
      ((Set<RefClass>)myBases).add(refClass);
    }
  }

  @Override
  public synchronized @NotNull Set<RefClass> getSubClasses() {
    LOG.assertTrue(isInitialized());
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
    LOG.assertTrue(isInitialized());
    return StreamEx.of(getChildren()).select(RefMethod.class).filter(RefMethod::isConstructor).toList();
  }

  @Override
  public List<RefField> getFields() {
    LOG.assertTrue(isInitialized());
    return ContainerUtil.filterIsInstance(getChildren(), RefField.class);
  }

  @Override
  public synchronized @NotNull Set<RefElement> getInTypeReferences() {
    if (myInTypeReferences == null) return EMPTY_SET;
    return myInTypeReferences;
  }

  void addTypeReference(RefJavaElement from) {
    if (from != null) {
      synchronized (this) {
        if (myInTypeReferences == null) {
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

  @Override
  public synchronized @NotNull List<RefMethod> getLibraryMethods() {
    return StreamEx.of(getChildren()).select(RefMethod.class).filter(RefMethod::isExternalOverride).toList();
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
  public boolean isRecord() {
    return checkFlag(IS_RECORD_MASK);
  }

  @Override
  public boolean isAnnotationType() {
    return checkFlag(IS_ANNOTATION_MASK);
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
    return (RefClass)manager.getReference(ClassUtil.findPsiClass(PsiManager.getInstance(manager.getProject()), externalName));
  }

  @Override
  public void referenceRemoved() {
    super.referenceRemoved();

    for (RefClass subClass : getSubClasses()) {
      ((RefClassImpl)subClass).removeBaseClass(this);
    }

    for (RefClass superClass : getBaseClasses()) {
      ((RefClassImpl)superClass).removeSubClass(this);
    }
  }

  private synchronized void removeBaseClass(RefClass superClass) {
    if (myBases instanceof RefClass) {
      if (myBases == superClass) myBases = null;
    }
    else if (myBases != null) {
      //noinspection unchecked
      ((Set<RefClass>)myBases).remove(superClass);
    }
  }

  void methodRemoved(RefMethod method) {
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
  public boolean isEnum() {
    return checkFlag(IS_ENUM_MASK);
  }

  @Override
  public synchronized boolean isReferenced() {
    if (super.isReferenced()) return true;

    if (isInterface()) {
      return !getDerivedReferences().isEmpty();
    }
    else if (isAbstract()) {
      return !getSubClasses().isEmpty();
    }

    return false;
  }

  @Override
  public boolean hasSuspiciousCallers() {
    if (super.hasSuspiciousCallers()) return true;

    if (isInterface()) {
      return !getDerivedReferences().isEmpty();
    }
    else if (isAbstract()) {
      return !getSubClasses().isEmpty();
    }

    return false;
  }

  synchronized void addClassExporter(RefJavaElement exporter) {
    if (myClassExporters == null) myClassExporters = new ArrayList<>(1);
    if (myClassExporters.contains(exporter)) return;
    myClassExporters.add(exporter);
  }

  @Override
  synchronized void addOutTypeReference(RefClass refClass) {
    if (myOutTypeReferences == null) {
      myOutTypeReferences = new HashSet<>();
    }
    myOutTypeReferences.add(refClass);
  }

  public synchronized List<RefJavaElement> getClassExporters() {
    return myClassExporters;
  }

  @Override
  public synchronized @NotNull Collection<RefClass> getOutTypeReferences() {
    return ObjectUtils.notNull(myOutTypeReferences, Collections.emptySet());
  }

  private void setAnonymous(boolean anonymous) {
    setFlag(anonymous, IS_ANONYMOUS_MASK);
  }

  private void setInterface(boolean isInterface) {
    setFlag(isInterface, IS_INTERFACE_MASK);
  }

  private void setRecord(boolean record) {
    setFlag(record, IS_RECORD_MASK);
  }

  private void setAnnotationType(boolean annotationType) {
    setFlag(annotationType, IS_ANNOTATION_MASK);
  }

  /**
   * A typical Java utility class has only static methods or fields.
   * However in Kotlin utility classes (Objects) follow singleton pattern.
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

  private void setLocal(boolean isLocal) {
    setFlag(isLocal, IS_LOCAL_MASK);
  }

  private void setEnum(boolean isEnum) {
    setFlag(isEnum, IS_ENUM_MASK);
  }

  @Override
  public @NotNull RefElement getContainingEntry() {
    RefElement defaultConstructor = getDefaultConstructor();
    return defaultConstructor == null ? super.getContainingEntry() : defaultConstructor;
  }

  private static boolean isKindOfJvmLanguage(@NotNull Language language) {
    for (Language t : Language.findInstance(JvmMetaLanguage.class).getMatchingLanguages()) {
      if (language.is(t)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isSuppressed(String @NotNull ... toolIds) {
    if (super.isSuppressed(toolIds)) {
      return true;
    }
    RefElement fileRef = getRefManager().getReference(getContainingFile());
    return fileRef instanceof RefJavaFileImpl file && file.isSuppressed(toolIds);
  }
}
