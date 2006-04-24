/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2001
 * Time: 8:21:36 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RefManagerImpl extends RefManager {

  private int myLastUsedMask = 256*256*256*4;

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.reference.RefManager");

  private final Project myProject;
  private AnalysisScope myScope;
  private RefProject myRefProject;
  private HashMap<PsiElement, RefElement> myRefTable;
  private HashMap<String, RefPackage> myPackages;
  private HashMap<Module, RefModule> myModules;
  private final ProjectIterator myProjectIterator;
  private boolean myDeclarationsFound;
  private PsiMethod myAppMainPattern;
  private PsiMethod myAppPremainPattern;
  private PsiClass myApplet;
  private PsiClass myServlet;
  private boolean myIsInProcess = false;

  private List<RefGraphAnnotator> myGraphAnnotators = new ArrayList<RefGraphAnnotator>();
  private GlobalInspectionContextImpl myContext;

  public RefManagerImpl(Project project, AnalysisScope scope, GlobalInspectionContextImpl context) {
    myDeclarationsFound = false;
    myProject = project;
    myScope = scope;
    myContext = context;
    myRefProject = new RefProjectImpl(this);
    myRefTable = new HashMap<PsiElement, RefElement>();


    myProjectIterator = new ProjectIterator();

    final PsiManager psiManager = PsiManager.getInstance(project);
    PsiElementFactory factory = psiManager.getElementFactory();
    try {
      myAppMainPattern = factory.createMethodFromText("void main(String[] args);", null);
      myAppPremainPattern = factory.createMethodFromText("void premain(String[] args, java.lang.instrument.Instrumentation i);", null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    myApplet = psiManager.findClass("java.applet.Applet", GlobalSearchScope.allScope(project));
    myServlet = psiManager.findClass("javax.servlet.Servlet", GlobalSearchScope.allScope(project));
  }

  public void iterate(RefVisitor visitor) {
    final HashMap<PsiElement, RefElement> refTable = getRefTable();
    for (RefElement refElement : refTable.values()) {
      refElement.accept(visitor);
      if (refElement instanceof RefClass) {
        RefClass refClass = (RefClass)refElement;
        RefMethod refDefaultConstructor = refClass.getDefaultConstructor();
        if (refDefaultConstructor instanceof RefImplicitConstructor) {
          refClass.getDefaultConstructor().accept(visitor);
        }
      }
    }
    if (myModules != null) {
      for (RefModule refModule : myModules.values()) {
        refModule.accept(visitor);
      }
    }
    if (myPackages != null) {
      for (RefPackage refPackage : myPackages.values()) {
        refPackage.accept(visitor);
      }
    }
  }

  public void cleanup() {
    myScope = null;
    myRefProject = null;
    myRefTable = null;
    myPackages = null;
    myModules = null;
    myContext = null;
    myGraphAnnotators.clear();
  }

  public AnalysisScope getScope() {
    return myScope;
  }


  public void fireNodeInitialized(RefElement refElement){
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onInitialize(refElement);
    }
  }

  public void fireNodeMarkedReferenced(RefElement refWhat,
                                       RefElement refFrom,
                                       boolean referencedFromClassInitializer){
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onMarkReferenced(refWhat, refFrom, referencedFromClassInitializer);
    }
  }

  public void fireBuildReferences(RefElement refElement){
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onReferencesBuild(refElement);
    }
  }

  public void registerGraphAnnotator(RefGraphAnnotator annotator){
    myGraphAnnotators.add(annotator);
  }

  public int getLastUsedMask() {
    myLastUsedMask *= 2;
    return myLastUsedMask;
  }

  public void findAllDeclarations() {
    if (!myDeclarationsFound) {
      long before = System.currentTimeMillis();
      getScope().accept(myProjectIterator);
      myDeclarationsFound = true;

      LOG.info("Total duration of processing project usages:" + (System.currentTimeMillis() - before));
    }
  }

  public void inspectionReadActionStarted() {
    myIsInProcess = true;
  }

  public void inspectionReadActionFinished() {
    myIsInProcess = false;
  }

  @Nullable public PsiElement getPsiAtOffset(VirtualFile vFile, int textOffset) {
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
    if (psiFile == null) return null;

    PsiElement psiElem = psiFile.findElementAt(textOffset);

    while (psiElem != null) {
      if (psiElem instanceof PsiClass ||
          psiElem instanceof PsiMethod ||
          psiElem instanceof PsiField ||
          psiElem instanceof PsiParameter) {
        return psiElem.getTextOffset() == textOffset ? psiElem : null;
      }

      psiElem = psiElem.getParent();
    }

    return null;
  }

  public Project getProject() {
    return myProject;
  }

  public RefProject getRefProject() {
    return myRefProject;
  }

  public HashMap<PsiElement, RefElement> getRefTable() {
    return myRefTable;
  }

  public RefPackage getPackage(String packageName) {
    if (myPackages == null) {
      myPackages = new HashMap<String, RefPackage>();
    }

    RefPackage refPackage = myPackages.get(packageName);
    if (refPackage == null) {
      refPackage = new RefPackageImpl(packageName);
      myPackages.put(packageName, refPackage);

      int dotIndex = packageName.lastIndexOf('.');
      if (dotIndex >= 0) {
        ((RefPackageImpl)getPackage(packageName.substring(0, dotIndex))).add(refPackage);
      }
      else {
        ((RefProjectImpl)getRefProject()).add(refPackage);
      }
    }

    return refPackage;
  }

  public void removeReference(RefElement refElem) {
    final HashMap<PsiElement, RefElement> refTable = getRefTable();

    if (refElem instanceof RefMethod) {
      RefMethod refMethod = (RefMethod)refElem;
      RefParameter[] params = refMethod.getParameters();
      for (RefParameter param : params) {
        removeReference(param);
      }
    }

    if (refTable.remove(refElem.getElement()) != null) return;

    //PsiElement may have been invalidated and new one returned by getElement() is different so we need to do this stuff.
    for (PsiElement psiElement : refTable.keySet()) {
      if (refTable.get(psiElement) == refElem) {
        refTable.remove(psiElement);
        return;
      }
    }
  }

  private class ProjectIterator extends PsiElementVisitor {
    private final RefUtilImpl REF_UTIL = (RefUtilImpl)RefUtil.getInstance();
    public void visitElement(PsiElement element) {
      for (PsiElement aChildren : element.getChildren()) {
        aChildren.accept(this);
      }
    }

    @Override
    public void visitFile(PsiFile file) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        myContext.incrementJobDoneAmount(GlobalInspectionContextImpl.BUILD_GRAPH, virtualFile.getPresentableUrl());
      }
      final FileViewProvider viewProvider = file.getViewProvider();
      final Set<Language> relevantLanguages = viewProvider.getRelevantLanguages();
      for (Language language : relevantLanguages) {
        visitElement(viewProvider.getPsi(language));
      }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitElement(expression);
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    }

    public void visitClass(PsiClass aClass) {
      if (!(aClass instanceof PsiTypeParameter)) {
        super.visitClass(aClass);
        RefElement refClass = RefManagerImpl.this.getReference(aClass);
        if (refClass != null) {
          ((RefClassImpl)refClass).buildReferences();
          List children = refClass.getChildren();
          if (children != null) {
            for (Object aChildren : children) {
              RefElementImpl refChild = (RefElementImpl)aChildren;
              refChild.buildReferences();
            }
          }
        }
      }
    }

    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      REF_UTIL.addTypeReference(variable, variable.getType(), RefManagerImpl.this);
    }

    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      super.visitInstanceOfExpression(expression);
      final PsiTypeElement typeElement = expression.getCheckType();
      if (typeElement != null) {
        REF_UTIL.addTypeReference(expression, typeElement.getType(), RefManagerImpl.this);
      }
    }

    public void visitThisExpression(PsiThisExpression expression) {
      super.visitThisExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null) {
        REF_UTIL.addTypeReference(expression, expression.getType(), RefManagerImpl.this);
        RefClass ownerClass = RefUtil.getInstance().getOwnerClass(RefManagerImpl.this, expression);
        if (ownerClass != null) {
          RefClassImpl refClass = (RefClassImpl)RefManagerImpl.this.getReference(qualifier.resolve());
          if (refClass != null) {
            refClass.addInstanceReference(ownerClass);
          }
        }
      }
    }

  }

  public PsiMethod getAppMainPattern() {
    return myAppMainPattern;
  }

  public PsiMethod getAppPremainPattern() {
    return myAppPremainPattern;
  }

  public PsiClass getApplet() {
    return myApplet;
  }

  public PsiClass getServlet() {
    return myServlet;
  }

  @Nullable public RefElement getReference(PsiElement elem) {
    if (elem != null && !(elem instanceof PsiPackage) && RefUtil.getInstance().belongsToScope(elem, this)) {
      if (!elem.isValid()) return null;

      RefElement ref = getRefTable().get(elem);
      if (ref == null) {
        if (!isValidPointForReference()){
          //LOG.assertTrue(true, "References may become invalid after process is finished");
          return null;
        }
        if (elem instanceof PsiClass) {
          ref = new RefClassImpl((PsiClass)elem, this);
        }
        else if (elem instanceof PsiMethod) {
          ref = new RefMethodImpl((PsiMethod)elem, this);
        }
        else if (elem instanceof PsiField) {
          ref = new RefFieldImpl((PsiField)elem, this);
        }
        else if (elem instanceof PsiFile) {
          ref = new RefFileImpl((PsiFile)elem, this);
        }
        else {
          return null;
        }

        getRefTable().put(elem, ref);
        ((RefElementImpl)ref).initialize();
      }

      return ref;
    }

    return null;
  }

  public RefMethod getMethodReference(RefClass refClass, PsiMethod psiMethod) {
    LOG.assertTrue(isValidPointForReference(), "References may become invalid after process is finished");

    RefMethodImpl ref = (RefMethodImpl)getRefTable().get(psiMethod);

    if (ref == null) {
      ref = new RefMethodImpl(refClass, psiMethod, this);
      ref.initialize();
      getRefTable().put(psiMethod, ref);
    }

    return ref;
  }

  public RefField getFieldReference(RefClass refClass, PsiField psiField) {
    LOG.assertTrue(isValidPointForReference(), "References may become invalid after process is finished");
    RefFieldImpl ref = (RefFieldImpl)getRefTable().get(psiField);

    if (ref == null) {
      ref = new RefFieldImpl(refClass, psiField, this);
      ref.initialize();
      getRefTable().put(psiField, ref);
    }

    return ref;
  }

  public RefParameter getParameterReference(PsiParameter param, int index) {
    LOG.assertTrue(isValidPointForReference(), "References may become invalid after process is finished");
    RefElement ref = getRefTable().get(param);

    if (ref == null) {
      ref = new RefParameterImpl(param, index, this);
      ((RefParameterImpl)ref).initialize();
      getRefTable().put(param, ref);
    }

    return (RefParameter)ref;
  }

  public RefModule getRefModule(Module module) {
    if (module == null){
      return null;
    }
    if (myModules == null){
      myModules = new HashMap<Module, RefModule>();
    }
    RefModule refModule = myModules.get(module);
    if (refModule == null){
      refModule = new RefModuleImpl(module);
      myModules.put(module, refModule);
    }
    return refModule;
  }

  private boolean isValidPointForReference() {
    return myIsInProcess || ApplicationManager.getApplication().isUnitTestMode();
  }
}
