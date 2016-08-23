/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:29:19 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RefClassImpl extends RefJavaElementImpl implements RefClass {
  private static final Set<RefElement> EMPTY_SET = Collections.emptySet();
  private static final Set<RefClass> EMPTY_CLASS_SET = Collections.emptySet();
  private static final List<RefMethod> EMPTY_METHOD_LIST = ContainerUtil.emptyList();
  private static final int IS_ANONYMOUS_MASK = 0x10000;
  private static final int IS_INTERFACE_MASK = 0x20000;
  private static final int IS_UTILITY_MASK   = 0x40000;
  private static final int IS_ABSTRACT_MASK  = 0x80000;

  private static final int IS_APPLET_MASK    = 0x200000;
  private static final int IS_SERVLET_MASK   = 0x400000;
  private static final int IS_TESTCASE_MASK  = 0x800000;
  private static final int IS_LOCAL_MASK     = 0x1000000;

  private Set<RefClass> myBases; // singleton (to conserve the memory) or THashSet
  private Set<RefClass> mySubClasses; // singleton (to conserve the memory) or THashSet
  private List<RefMethod> myConstructors;
  private RefMethodImpl myDefaultConstructor;
  private List<RefMethod> myOverridingMethods;
  private Set<RefElement> myInTypeReferences;
  private Set<RefElement> myInstanceReferences;
  private List<RefJavaElement> myClassExporters;

  RefClassImpl(PsiClass psiClass, RefManager manager) {
    super(psiClass, manager);
  }

  @Override
  protected void initialize() {
    myDefaultConstructor = null;

    final PsiClass psiClass = getElement();

    LOG.assertTrue(psiClass != null);

    PsiElement psiParent = psiClass.getParent();
    if (psiParent instanceof PsiFile) {
      if (isSyntheticJSP()) {
        final RefFileImpl refFile = (RefFileImpl)getRefManager().getReference(getJspFile(psiClass));
        LOG.assertTrue(refFile != null);
        refFile.add(this);
      } else if (psiParent instanceof PsiJavaFile) {
        PsiJavaFile psiFile = (PsiJavaFile) psiParent;
        String packageName = psiFile.getPackageName();
        if (!"".equals(packageName)) {
          ((RefPackageImpl)getRefJavaManager().getPackage(packageName)).add(this);
        } else {
          ((RefPackageImpl)getRefJavaManager().getDefaultPackage()).add(this);
        }
      }
      final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
      LOG.assertTrue(module != null);
      final RefModuleImpl refModule = (RefModuleImpl)getRefManager().getRefModule(module);
      LOG.assertTrue(refModule != null);
      refModule.add(this);
    } else {
      while (!(psiParent instanceof PsiClass || psiParent instanceof PsiMethod || psiParent instanceof PsiField)) {
        psiParent = psiParent.getParent();
      }
      RefElement refParent = getRefManager().getReference(psiParent);
      LOG.assertTrue (refParent != null);
      ((RefElementImpl)refParent).add(this);

    }

    setAbstract(psiClass.hasModifierProperty(PsiModifier.ABSTRACT));

    setAnonymous(psiClass instanceof PsiAnonymousClass);
    setIsLocal(!(isAnonymous() || psiParent instanceof PsiClass || psiParent instanceof PsiFile));
    setInterface(psiClass.isInterface());

    initializeSuperReferences(psiClass);

    PsiMethod[] psiMethods = psiClass.getMethods();
    PsiField[] psiFields = psiClass.getFields();

    setUtilityClass(psiMethods.length > 0 || psiFields.length > 0);

    for (PsiField psiField : psiFields) {
      getRefManager().getReference(psiField);
    }

    if (!isApplet()) {
      final PsiClass servlet = getRefJavaManager().getServlet();
      setServlet(servlet != null && psiClass.isInheritor(servlet, true));
    }
    if (!isApplet() && !isServlet()) {
      final boolean isTestClass = TestFrameworks.getInstance().isTestClass(psiClass);
      setTestCase(isTestClass);
      if (isTestClass) {
        for (RefClass refBase : getBaseClasses()) {
          ((RefClassImpl)refBase).setTestCase(true);
        }
      }
    }

    for (PsiMethod psiMethod : psiMethods) {
      RefMethod refMethod = (RefMethod)getRefManager().getReference(psiMethod);

      if (refMethod != null) {
        if (psiMethod.isConstructor()) {
          if (psiMethod.getParameterList().getParametersCount() > 0 || !psiMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
            setUtilityClass(false);
          }

          addConstructor(refMethod);
          if (psiMethod.getParameterList().getParametersCount() == 0) {
            setDefaultConstructor((RefMethodImpl)refMethod);
          }
        }
        else {
          if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
            setUtilityClass(false);
          }
        }
      }
    }

    if (getConstructors().isEmpty() && !isInterface() && !isAnonymous()) {
      RefImplicitConstructorImpl refImplicitConstructor = new RefImplicitConstructorImpl(this);
      setDefaultConstructor(refImplicitConstructor);
      addConstructor(refImplicitConstructor);
    }

    if (isInterface()) {
      for (int i = 0; i < psiFields.length && isUtilityClass(); i++) {
        PsiField psiField = psiFields[i];
        if (!psiField.hasModifierProperty(PsiModifier.STATIC)) {
          setUtilityClass(false);
        }
      }
    }


    final PsiClass applet = getRefJavaManager().getApplet();
    setApplet(applet != null && psiClass.isInheritor(applet, true));
    PsiManager psiManager = getRefManager().getPsiManager();
    psiManager.dropResolveCaches();
    PsiFile file = psiClass.getContainingFile();
    if (file != null) {
      InjectedLanguageManager.getInstance(file.getProject()).dropFileCaches(file);
    }
  }

  private static ServerPageFile getJspFile(PsiClass psiClass) {
    final PsiFile psiFile = PsiUtilCore.getTemplateLanguageFile(psiClass);
    return psiFile instanceof ServerPageFile ? (ServerPageFile)psiFile : null;
  }

  private void initializeSuperReferences(PsiClass psiClass) {
    if (!isSelfInheritor(psiClass)) {
      for (PsiClass psiSuperClass : psiClass.getSupers()) {
        if (getRefManager().belongsToScope(psiSuperClass)) {
          RefClassImpl refClass = (RefClassImpl)getRefManager().getReference(psiSuperClass);
          if (refClass != null) {
            addBaseClass(refClass);
            refClass.addSubClass(this);
          }
        }
      }
    }
  }

  @Override
  public boolean isSelfInheritor(PsiClass psiClass) {
    return isSelfInheritor(psiClass, new ArrayList<>());
  }

  @Nullable
  @Override
  public PsiClass getElement() {
    return (PsiClass)super.getElement();
  }

  private static boolean isSelfInheritor(PsiClass psiClass, ArrayList<PsiClass> visited) {
    if (visited.contains(psiClass)) return true;

    visited.add(psiClass);
    for (PsiClass aSuper : psiClass.getSupers()) {
      if (isSelfInheritor(aSuper, visited)) return true;
    }
    visited.remove(psiClass);

    return false;
  }

  private void setDefaultConstructor(RefMethodImpl defaultConstructor) {
    if (defaultConstructor != null) {
      for (RefClass superClass : getBaseClasses()) {
        RefMethodImpl superDefaultConstructor = (RefMethodImpl)superClass.getDefaultConstructor();

        if (superDefaultConstructor != null) {
          superDefaultConstructor.addInReference(defaultConstructor);
          defaultConstructor.addOutReference(superDefaultConstructor);
        }
      }
    }

    myDefaultConstructor = defaultConstructor;
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    final PsiClass psiClass = getElement();
    if (psiClass == null) return super.getQualifiedName();
    final String qName = psiClass.getQualifiedName();
    if (qName == null) return super.getQualifiedName();
    return qName;
  }

  @Override
  public void buildReferences() {
    PsiClass psiClass = getElement();

    if (psiClass != null) {
      for (PsiClassInitializer classInitializer : psiClass.getInitializers()) {
        RefJavaUtil.getInstance().addReferences(psiClass, this, classInitializer.getBody());
      }

      RefJavaUtil.getInstance().addReferences(psiClass, this, psiClass.getModifierList());

      PsiField[] psiFields = psiClass.getFields();
      for (PsiField psiField : psiFields) {
        getRefManager().getReference(psiField);
        final PsiExpression initializer = psiField.getInitializer();
        if (initializer != null) {
          RefJavaUtil.getInstance().addReferences(psiClass, this, initializer);
        }
      }

      PsiMethod[] psiMethods = psiClass.getMethods();
      for (PsiMethod psiMethod : psiMethods) {
        getRefManager().getReference(psiMethod);
      }

      RefJavaUtil.getInstance().addReferences(psiClass, this, psiClass.getExtendsList());
      RefJavaUtil.getInstance().addReferences(psiClass, this, psiClass.getImplementsList());

      getRefManager().fireBuildReferences(this);
    }
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> ((RefJavaVisitor)visitor).visitClass(this));
    } else {
      super.accept(visitor);
    }
  }

  @Override
  @NotNull
  public Set<RefClass> getBaseClasses() {
    if (myBases == null) return EMPTY_CLASS_SET;
    return myBases;
  }

  private void addBaseClass(RefClass refClass){
    if (myBases == null) {
      myBases = Collections.singleton(refClass);
      return;
    }
    if (myBases.size() == 1) {
      // convert from singleton
      myBases = new THashSet<>(myBases);
    }
    myBases.add(refClass);
  }

  @Override
  @NotNull
  public Set<RefClass> getSubClasses() {
    if (mySubClasses == null) return EMPTY_CLASS_SET;
    return mySubClasses;
  }

  private void addSubClass(@NotNull RefClass refClass){
    if (mySubClasses == null) {
      mySubClasses = Collections.singleton(refClass);
      return;
    }
    if (mySubClasses.size() == 1) {
      // convert from singleton
      mySubClasses = new THashSet<>(mySubClasses);
    }
    mySubClasses.add(refClass);
  }
  private void removeSubClass(RefClass refClass){
    if (mySubClasses == null) return;
    if (mySubClasses.size() == 1) {
      mySubClasses = null;
    }
    else {
      mySubClasses.remove(refClass);
    }
  }

  @Override
  @NotNull
  public List<RefMethod> getConstructors() {
    if (myConstructors == null) return EMPTY_METHOD_LIST;
    return myConstructors;
  }

  @Override
  @NotNull
  public Set<RefElement> getInTypeReferences() {
    if (myInTypeReferences == null) return EMPTY_SET;
    return myInTypeReferences;
  }

  public void addTypeReference(RefJavaElement from) {
    if (from != null) {
      if (myInTypeReferences == null){
        myInTypeReferences = new THashSet<>(1);
      }
      myInTypeReferences.add(from);
      ((RefJavaElementImpl)from).addOutTypeRefernce(this);
      getRefManager().fireNodeMarkedReferenced(this, from, false, false, false);
    }
  }

  @Override
  @NotNull
  public Set<RefElement> getInstanceReferences() {
    if (myInstanceReferences == null) return EMPTY_SET;
    return myInstanceReferences;
  }

  public void addInstanceReference(RefElement from) {
    if (myInstanceReferences == null){
      myInstanceReferences = new THashSet<>(1);
    }
    myInstanceReferences.add(from);
  }

  @Override
  public RefMethod getDefaultConstructor() {
    return myDefaultConstructor;
  }

  private void addConstructor(RefMethod refConstructor) {
    if (myConstructors == null){
      myConstructors = new ArrayList<>(1);
    }
    myConstructors.add(refConstructor);
  }

  public void addLibraryOverrideMethod(RefMethod refMethod) {
    if (myOverridingMethods == null){
      myOverridingMethods = new ArrayList<>(2);
    }
    myOverridingMethods.add(refMethod);
  }

  @Override
  @NotNull
  public List<RefMethod> getLibraryMethods() {
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
    final String[] result = new String[1];
    ApplicationManager.getApplication().runReadAction(() -> {//todo synthetic JSP
      final PsiClass psiClass = getElement();
      LOG.assertTrue(psiClass != null);
      result[0] = PsiFormatUtil.getExternalName(psiClass);
    });
    return result[0];
  }


  @Nullable
  public static RefClass classFromExternalName(RefManager manager, String externalName) {
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

  private void removeBase(RefClass superClass) {
    final Set<RefClass> baseClasses = getBaseClasses();
    if (baseClasses.contains(superClass)) {
      if (baseClasses.size() == 1) {
        myBases = null;
        return;
      }
      baseClasses.remove(superClass);
    }
  }

  protected void methodRemoved(RefMethod method) {
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

    if (isInterface() || isAbstract()) {
      if (!getSubClasses().isEmpty()) return true;
    }

    return false;
  }

  @Override
  public boolean hasSuspiciousCallers() {
    if (super.hasSuspiciousCallers()) return true;

    if (isInterface() || isAbstract()) {
      if (!getSubClasses().isEmpty()) return true;
    }

    return false;
  }

  public void addClassExporter(RefJavaElement exporter) {
    if (myClassExporters == null) myClassExporters = new ArrayList<>(1);
    if (myClassExporters.contains(exporter)) return;
    myClassExporters.add(exporter);
  }

  public List<RefJavaElement> getClassExporters() {
    return myClassExporters;
  }

  private void setAnonymous(boolean anonymous) {
    setFlag(anonymous, IS_ANONYMOUS_MASK);
  }

  private void setInterface(boolean anInterface) {
    setFlag(anInterface, IS_INTERFACE_MASK);
  }

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
  @NotNull
  public RefElement getContainingEntry() {
    RefElement defaultConstructor = getDefaultConstructor();
    if (defaultConstructor != null) return defaultConstructor;
    return super.getContainingEntry();
  }
}

