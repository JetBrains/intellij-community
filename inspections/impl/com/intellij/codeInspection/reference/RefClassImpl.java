/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:29:19 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.TestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RefClassImpl extends RefElementImpl implements RefClass {
  private static final HashSet<RefElement> EMPTY_SET = new HashSet<RefElement>(0);
  private static final HashSet<RefClass> EMPTY_CLASS_SET = new HashSet<RefClass>(0);
  private static final ArrayList<RefMethod> EMPTY_METHOD_LIST = new ArrayList<RefMethod>(0);
  private static final int IS_ANONYMOUS_MASK = 0x10000;
  private static final int IS_INTERFACE_MASK = 0x20000;
  private static final int IS_UTILITY_MASK   = 0x40000;
  private static final int IS_ABSTRACT_MASK  = 0x80000;

  private static final int IS_APPLET_MASK    = 0x200000;
  private static final int IS_SERVLET_MASK   = 0x400000;
  private static final int IS_TESTCASE_MASK  = 0x800000;
  private static final int IS_LOCAL_MASK     = 0x1000000;

  private HashSet<RefClass> myBases;
  private HashSet<RefClass> mySubClasses;
  private ArrayList<RefMethod> myConstructors;
  private RefMethodImpl myDefaultConstructor;
  private ArrayList<RefMethod> myOverridingMethods;
  private THashSet<RefElement> myInTypeReferences;
  private THashSet<RefElement> myInstanceReferences;
  private ArrayList<RefElement> myClassExporters;

  RefClassImpl(PsiClass psiClass, RefManager manager) {
    super(psiClass, manager);
  }

  protected void initialize() {
    myDefaultConstructor = null;

    final PsiClass psiClass = getElement();

    LOG.assertTrue(psiClass != null);

    PsiElement psiParent = psiClass.getParent();
    if (psiParent instanceof PsiFile) {      
      if (isSyntheticJSP()) {
        final RefFileImpl refFile = (RefFileImpl)getRefManager().getReference(PsiUtil.getJspFile(psiClass));
        LOG.assertTrue(refFile != null);
        refFile.add(this);
      } else {
        PsiJavaFile psiFile = (PsiJavaFile) psiParent;
        String packageName = psiFile.getPackageName();
        if (!"".equals(packageName)) {
          ((RefPackageImpl)getRefManager().getPackage(packageName)).add(this);
        } else {
          ((RefPackageImpl)getRefManager().getRefProject().getDefaultPackage()).add(this);
        }
      }
      final Module module = ModuleUtil.findModuleForPsiElement(psiClass);
      LOG.assertTrue(module != null);
      final RefModuleImpl refModule = ((RefModuleImpl)getRefManager().getRefModule(module));
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
      ((RefManagerImpl)getRefManager()).getFieldReference(this, psiField);
    }

    if (!isApplet()) {
      final PsiClass servlet = ((RefManagerImpl)getRefManager()).getServlet();
      setServlet(servlet != null && psiClass.isInheritor(servlet, true));
    }
    if (!isApplet() && !isServlet()) {
      setTestCase(TestUtil.isTestClass(psiClass));
      for (RefClass refBase : getBaseClasses()) {
        ((RefClassImpl)refBase).setTestCase(true);
      }
    }

    for (PsiMethod psiMethod : psiMethods) {
      RefMethod refMethod = ((RefManagerImpl)getRefManager()).getMethodReference(this, psiMethod);

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

    if (getConstructors().size() == 0 && !isInterface() && !isAnonymous()) {
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


    final PsiClass applet = ((RefManagerImpl)getRefManager()).getApplet();
    setApplet(applet != null && psiClass.isInheritor(applet, true));
    ((RefManagerImpl)getRefManager()).fireNodeInitialized(this);
  }

  private void initializeSuperReferences(PsiClass psiClass) {
    if (!isSelfInheritor(psiClass)) {
      for (PsiClass psiSuperClass : psiClass.getSupers()) {
        if (RefUtil.getInstance().belongsToScope(psiSuperClass, getRefManager())) {
          RefClassImpl refClass = (RefClassImpl)getRefManager().getReference(psiSuperClass);
          if (refClass != null) {
            addBaseClass(refClass);
            refClass.addSubClass(this);
          }
        }
      }
    }
  }

  public boolean isSelfInheritor(PsiClass psiClass) {
    return isSelfInheritor(psiClass, new ArrayList<PsiClass>());
  }

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

  public void buildReferences() {
    PsiClass psiClass = getElement();

    if (psiClass != null) {
      for (PsiClassInitializer classInitializer : psiClass.getInitializers()) {
        ((RefUtilImpl)RefUtil.getInstance()).addReferences(psiClass, this, classInitializer.getBody());
      }

      PsiField[] psiFields = psiClass.getFields();
      for (PsiField psiField : psiFields) {
        ((RefManagerImpl)getRefManager()).getFieldReference(this, psiField);
        final PsiExpression initializer = psiField.getInitializer();
        if (initializer != null) {
          ((RefUtilImpl)RefUtil.getInstance()).addReferences(psiClass, this, initializer);
        }        
      }

      PsiMethod[] psiMethods = psiClass.getMethods();
      for (PsiMethod psiMethod : psiMethods) {
        ((RefManagerImpl)getRefManager()).getMethodReference(this, psiMethod);
      }
      ((RefManagerImpl)getRefManager()).fireBuildReferences(this);
    }
  }

  public void accept(final RefVisitor visitor) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        visitor.visitClass(RefClassImpl.this);
      }
    });
  }

  @NotNull
  public HashSet<RefClass> getBaseClasses() {
    if (myBases == null) return EMPTY_CLASS_SET;
    return myBases;
  }

  private void addBaseClass(RefClass refClass){
    if (myBases == null){
      myBases = new HashSet<RefClass>(1);
    }
    myBases.add(refClass);
  }

  @NotNull
  public HashSet<RefClass> getSubClasses() {
    if (mySubClasses == null) return EMPTY_CLASS_SET;
    return mySubClasses;
  }

  private void addSubClass(RefClass refClass){
    if (mySubClasses == null){
      mySubClasses = new HashSet<RefClass>(1);
    }
    mySubClasses.add(refClass);
  }

  @NotNull
  public ArrayList<RefMethod> getConstructors() {
    if (myConstructors == null) return EMPTY_METHOD_LIST;
    return myConstructors;
  }

  @NotNull
  public Set<RefElement> getInTypeReferences() {
    if (myInTypeReferences == null) return EMPTY_SET;
    return myInTypeReferences;
  }

  public void addTypeReference(RefElement from) {
    if (from != null) {
      if (myInTypeReferences == null){
        myInTypeReferences = new THashSet<RefElement>(1);
      }
      myInTypeReferences.add(from);
      ((RefElementImpl)from).addOutTypeRefernce(this);
      ((RefManagerImpl)getRefManager()).fireNodeMarkedReferenced(this, from, false, false, false);
    }
  }

  @NotNull
  public Set<RefElement> getInstanceReferences() {
    if (myInstanceReferences == null) return EMPTY_SET;
    return myInstanceReferences;
  }

  public void addInstanceReference(RefElement from) {
    if (myInstanceReferences == null){
      myInstanceReferences = new THashSet<RefElement>(1);
    }
    myInstanceReferences.add(from);
  }

  public RefMethod getDefaultConstructor() {
    return myDefaultConstructor;
  }

  private void addConstructor(RefMethod refConstructor) {
    if (myConstructors == null){
      myConstructors = new ArrayList<RefMethod>(1);
    }
    myConstructors.add(refConstructor);
  }

  public void addLibraryOverrideMethod(RefMethod refMethod) {
    if (myOverridingMethods == null){
      myOverridingMethods = new ArrayList<RefMethod>(2);
    }
    myOverridingMethods.add(refMethod);
  }

  @NotNull
  public List<RefMethod> getLibraryMethods() {
    if (myOverridingMethods == null) return EMPTY_METHOD_LIST;
    return myOverridingMethods;
  }

  public boolean isAnonymous() {
    return checkFlag(IS_ANONYMOUS_MASK);
  }

  public boolean isInterface() {
    return checkFlag(IS_INTERFACE_MASK);
  }

  public boolean isSuspicious() {
    return !(isUtilityClass() && getOutReferences().isEmpty()) && super.isSuspicious();
  }

  public boolean isUtilityClass() {
    return checkFlag(IS_UTILITY_MASK);
  }

  public String getExternalName() {
    final String[] result = new String[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final PsiClass psiClass = getElement();
        LOG.assertTrue(psiClass != null);
        final StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          formatClassName(psiClass, builder);
          result[0] = builder.toString();
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
    });
    return result[0];
  }

  private static void formatClassName(@NotNull final PsiClass aClass, final StringBuilder buf) {
    final String qName = aClass.getQualifiedName();
    if (qName != null) {
      buf.append(qName);
    }
    else {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
      if (parentClass != null) {
        formatClassName(parentClass, buf);
        buf.append("$");
        buf.append(getNonQualifiedClassIdx(aClass));
        final String name = aClass.getName();
        if (name != null) {
          buf.append(name);
        }
      }
    }
  }

  private static int getNonQualifiedClassIdx(@NotNull final PsiClass psiClass) {
    final int [] result = new int[] {-1};
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
    LOG.assertTrue(containingClass != null);
    containingClass.accept(new PsiRecursiveElementVisitor(){
      private int myCurrentIdx = 0;

      public void visitElement(PsiElement element) {
        if (result[0] == -1) {
          super.visitElement(element);
        }
      }

      public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);
        if (aClass.getQualifiedName() == null) {
          myCurrentIdx++;
          if (psiClass == aClass) {
            result[0] = myCurrentIdx;
          }
        }        
      }     
    });
    return result[0];
  }

  private static PsiClass findNonQualifiedClassByIndex(final String indexName, @NotNull final PsiClass contaningClass) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      for (int i = 0; i < indexName.length(); i++) {
        final char c = indexName.charAt(i);
        if (Character.isDigit(c)) {
          builder.append(c);
        }
        else {
          break;
        }
      }
      final int idx = Integer.parseInt(builder.toString());
      final String name = builder.length() < indexName.length() ? indexName.substring(builder.length()) : null;
      final PsiClass[] result = new PsiClass[1];
      contaningClass.accept(new PsiRecursiveElementVisitor() {
        private int myCurrentIdx = 0;

        public void visitElement(PsiElement element) {
          if (result[0] == null) {
            super.visitElement(element);
          }
        }

        public void visitClass(PsiClass aClass) {
          super.visitClass(aClass);
          if (aClass.getQualifiedName() == null) {
            myCurrentIdx++;
            if (myCurrentIdx == idx && Comparing.strEqual(name, aClass.getName())) {
              result[0] = aClass;
            }
          }
        }
      });
      return result[0];
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  public static RefClass classFromExternalName(RefManager manager, String externalName) {
    return (RefClass) manager.getReference(findPsiClass(PsiManager.getInstance(manager.getProject()), externalName));
  }

  @Nullable
  public static PsiClass findPsiClass(final PsiManager psiManager, String externalName){
    return findPsiClass(psiManager, externalName, null);
  } 

  @Nullable
  private static PsiClass findPsiClass(final PsiManager psiManager, String externalName, PsiClass psiClass) {
    final int topIdx = externalName.indexOf('$');
    if (topIdx > -1) {
      if (psiClass == null) {
        psiClass = psiManager.findClass(externalName.substring(0, topIdx), GlobalSearchScope.allScope(psiManager.getProject()));
      }
      if (psiClass == null) return null;
      externalName = externalName.substring(topIdx + 1);
      final int nextIdx = externalName.indexOf("$");
      if (nextIdx > -1) {
        return findPsiClass(psiManager, externalName.substring(nextIdx), findNonQualifiedClassByIndex(externalName.substring(0, nextIdx), psiClass));
      } else {
        return findNonQualifiedClassByIndex(externalName, psiClass);
      }
    } else {
      return psiManager.findClass(externalName, GlobalSearchScope.allScope(psiManager.getProject()));
    }
  }

  public void referenceRemoved() {
    super.referenceRemoved();

    for (RefClass subClass : getSubClasses()) {
      ((RefClassImpl)subClass).removeBase(this);
    }

    for (RefClass superClass : getBaseClasses()) {
      superClass.getSubClasses().remove(this);
    }
  }

  private void removeBase(RefClass superClass) {
    getBaseClasses().remove(superClass);
  }

  protected void methodRemoved(RefMethod method) {
    getConstructors().remove(method);
    getLibraryMethods().remove(method);

    if (getDefaultConstructor() == method) {
      setDefaultConstructor(null);
    }
  }

  public boolean isAbstract() {
    return checkFlag(IS_ABSTRACT_MASK);
  }

  public boolean isApplet() {
    return checkFlag(IS_APPLET_MASK);
  }

  public boolean isServlet() {
    return checkFlag(IS_SERVLET_MASK);
  }

  public boolean isTestCase() {
    return checkFlag(IS_TESTCASE_MASK);
  }

  public boolean isLocalClass() {
    return checkFlag(IS_LOCAL_MASK);
  }

 
  public boolean isReferenced() {
    if (super.isReferenced()) return true;

    if (isInterface() || isAbstract()) {
      if (getSubClasses().size() > 0) return true;
    }

    return false;
  }

  public boolean hasSuspiciousCallers() {
    if (super.hasSuspiciousCallers()) return true;

    if (isInterface() || isAbstract()) {
      if (getSubClasses().size() > 0) return true;
    }

    return false;
  }

  public void addClassExporter(RefElement exporter) {
    if (myClassExporters == null) myClassExporters = new ArrayList<RefElement>(1);
    if (myClassExporters.contains(exporter)) return;
    myClassExporters.add(exporter);
  }

  public List<RefElement> getClassExporters() {
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
}

