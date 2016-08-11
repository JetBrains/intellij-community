/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInspection.BatchSuppressManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


/**
 * @author anna
 * Date: 20-Dec-2007
 */
public class RefJavaManagerImpl extends RefJavaManager {
  private static final Logger LOG = Logger.getInstance("#" + RefJavaManagerImpl.class.getName());
  private PsiMethod myAppMainPattern;
  private PsiMethod myAppPremainPattern;
  private PsiMethod myAppAgentmainPattern;
  private PsiClass myApplet;
  private PsiClass myServlet;
  private RefPackage myDefaultPackage;
  private THashMap<String, RefPackage> myPackages;
  private final RefManagerImpl myRefManager;
  private PsiElementVisitor myProjectIterator;
  private EntryPointsManager myEntryPointsManager;

  public RefJavaManagerImpl(@NotNull RefManagerImpl manager) {
    myRefManager = manager;
    final Project project = manager.getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    try {
      myAppMainPattern = factory.createMethodFromText("void main(String[] args);", null);
      myAppPremainPattern = factory.createMethodFromText("void premain(String[] args, java.lang.instrument.Instrumentation i);", null);
      myAppAgentmainPattern = factory.createMethodFromText("void agentmain(String[] args, java.lang.instrument.Instrumentation i);", null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    myApplet = JavaPsiFacade.getInstance(psiManager.getProject()).findClass("java.applet.Applet", GlobalSearchScope.allScope(project));
    myServlet = JavaPsiFacade.getInstance(psiManager.getProject()).findClass("javax.servlet.Servlet", GlobalSearchScope.allScope(project));

  }

  @Override
  public RefImplicitConstructor getImplicitConstructor(String classFQName) {
    final RefEntity entity = getReference(CLASS, classFQName);
    if (entity == null) return null;
    final RefClass refClass = (RefClass)entity;
    return (RefImplicitConstructor)refClass.getDefaultConstructor();
  }

  @Override
  public RefPackage getPackage(String packageName) {
    if (myPackages == null) {
      myPackages = new THashMap<>();
    }

    RefPackage refPackage = myPackages.get(packageName);
    if (refPackage == null) {
      refPackage = new RefPackageImpl(packageName, myRefManager);
      myPackages.put(packageName, refPackage);

      int dotIndex = packageName.lastIndexOf('.');
      if (dotIndex >= 0) {
        ((RefPackageImpl)getPackage(packageName.substring(0, dotIndex))).add(refPackage);
      }
      else {
        ((RefProjectImpl)myRefManager.getRefProject()).add(refPackage);
      }
    }

    return refPackage;
  }


  public boolean isEntryPoint(final RefElement element) {
    UnusedDeclarationInspectionBase tool = getDeadCodeTool(element);
    return tool != null && tool.isEntryPoint(element);
  }

  @Nullable
  private UnusedDeclarationInspectionBase getDeadCodeTool(RefElement element) {
    PsiFile file = ((RefElementImpl)element).getContainingFile();
    if (file == null) return null;

    return getDeadCodeTool(file.getContainingFile());
  }

  private UnusedDeclarationInspectionBase getDeadCodeTool(PsiFile file) {
    Tools tools = ((GlobalInspectionContextBase)myRefManager.getContext()).getTools().get(UnusedDeclarationInspectionBase.SHORT_NAME);
    InspectionToolWrapper toolWrapper = tools == null ? null : tools.getEnabledTool(file);
    InspectionProfileEntry tool = toolWrapper == null ? null : toolWrapper.getTool();
    return tool instanceof UnusedDeclarationInspectionBase ? (UnusedDeclarationInspectionBase)tool : null;
  }

  @Override
  public RefPackage getDefaultPackage() {
    if (myDefaultPackage == null) {
      myDefaultPackage = getPackage(InspectionsBundle.message("inspection.reference.default.package"));
    }
    return myDefaultPackage;
  }

  @Override
  public PsiMethod getAppMainPattern() {
    return myAppMainPattern;
  }

  @Override
  public PsiMethod getAppPremainPattern() {
    return myAppPremainPattern;
  }

  @Override
  public PsiMethod getAppAgentmainPattern() {
    return myAppAgentmainPattern;
  }

  @Override
  public PsiClass getApplet() {
    return myApplet;
  }

  @Override
  public PsiClass getServlet() {
    return myServlet;
  }

  @Override
  public RefParameter getParameterReference(final PsiParameter param, final int index) {
    LOG.assertTrue(myRefManager.isValidPointForReference(), "References may become invalid after process is finished");
    
    return myRefManager.getFromRefTableOrCache(param, () -> {
      RefParameter ref = new RefParameterImpl(param, index, myRefManager);
      ((RefParameterImpl)ref).initialize();
      return ref;
    });
  }

  @Override
  public void iterate(@NotNull final RefVisitor visitor) {
    if (myPackages != null) {
      for (RefPackage refPackage : myPackages.values()) {
        refPackage.accept(visitor);
      }
    }
    for (RefElement refElement : myRefManager.getSortedElements()) {
      if (refElement instanceof RefClass) {
        RefClass refClass = (RefClass)refElement;
        RefMethod refDefaultConstructor = refClass.getDefaultConstructor();
        if (refDefaultConstructor instanceof RefImplicitConstructor) {
          refClass.getDefaultConstructor().accept(visitor);
        }
      }
    }
  }

  @Override
  public void cleanup() {
    if (myEntryPointsManager != null) {
      Disposer.dispose(myEntryPointsManager);
      myEntryPointsManager = null;
    }
    myPackages = null;
    myApplet = null;
    myAppMainPattern = null;
    myAppPremainPattern = null;
    myAppAgentmainPattern = null;
    myServlet = null;
    myDefaultPackage = null;
    myProjectIterator = null;
  }

  @Override
  public void removeReference(@NotNull final RefElement refElement) {
    if (refElement instanceof RefMethod) {
      RefMethod refMethod = (RefMethod)refElement;
      RefParameter[] params = refMethod.getParameters();
      for (RefParameter param : params) {
        myRefManager.removeReference(param);
      }
    }
  }

  @Override
  @Nullable
  public RefElement createRefElement(final PsiElement elem) {
    if (elem instanceof PsiClass) {
      return new RefClassImpl((PsiClass)elem, myRefManager);
    }
    else if (elem instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)elem;
      final RefElement ref = myRefManager.getReference(method.getContainingClass(), true);
      if (ref instanceof RefClass) {
        return new RefMethodImpl((RefClass)ref, method, myRefManager);
      }
    }
    else if (elem instanceof PsiField) {
      final PsiField field = (PsiField)elem;
      final RefElement ref = myRefManager.getReference(field.getContainingClass(), true);
      if (ref instanceof RefClass) {
        return new RefFieldImpl((RefClass)ref, field, myRefManager);
      }
    }
    else if (elem instanceof PsiJavaFile) {
      return new RefJavaFileImpl((PsiJavaFile)elem, myRefManager);
    }
    return null;
  }

  @Override
  @Nullable
  public RefEntity getReference(final String type, final String fqName) {
    if (IMPLICIT_CONSTRUCTOR.equals(type)) {
      return getImplicitConstructor(fqName);
    }
    if (METHOD.equals(type)) {
      return RefMethodImpl.methodFromExternalName(myRefManager, fqName);
    }
    else if (CLASS.equals(type)) {
      return RefClassImpl.classFromExternalName(myRefManager, fqName);
    }
    else if (FIELD.equals(type)) {
      return RefFieldImpl.fieldFromExternalName(myRefManager, fqName);
    }
    else if (PARAMETER.equals(type)) {
      return RefParameterImpl.parameterFromExternalName(myRefManager, fqName);
    }
    else if (PACKAGE.equals(type)) {
      return RefPackageImpl.packageFromFQName(myRefManager, fqName);
    }
    return null;
  }

  @Override
  @Nullable
  public String getType(final RefEntity ref) {
    if (ref instanceof RefImplicitConstructor) {
      return IMPLICIT_CONSTRUCTOR;
    }
    else if (ref instanceof RefMethod) {
      return METHOD;
    }
    else if (ref instanceof RefClass) {
      return CLASS;
    }
    else if (ref instanceof RefField) {
      return FIELD;
    }
    else if (ref instanceof RefParameter) {
      return PARAMETER;
    }
    else if (ref instanceof RefPackage) {
      return PACKAGE;
    }
    return null;
  }

  @NotNull
  @Override
  public RefEntity getRefinedElement(@NotNull final RefEntity ref) {
    if (ref instanceof RefImplicitConstructor) {
      return ((RefImplicitConstructor)ref).getOwnerClass();
    }
    return ref;
  }

  @Override
  public void visitElement(final PsiElement element) {
    if (myProjectIterator == null) {
      myProjectIterator = new MyJavaElementVisitor();
    }
    element.accept(myProjectIterator);
  }

  @Override
  @Nullable
  public String getGroupName(final RefEntity entity) {
    if (entity instanceof RefFile && !(entity instanceof RefJavaFileImpl)) return null;
    return RefJavaUtil.getInstance().getPackageName(entity);
  }

  @Override
  public boolean belongsToScope(final PsiElement psiElement) {
    return !(psiElement instanceof PsiTypeParameter);
  }

  @Override
  public void export(@NotNull final RefEntity refEntity, @NotNull final Element element) {
    if (refEntity instanceof RefElement) {
      final SmartPsiElementPointer pointer = ((RefElement)refEntity).getPointer();
      if (pointer != null) {
        final PsiFile psiFile = pointer.getContainingFile();
        if (psiFile instanceof PsiJavaFile) {
          appendPackageElement(element, ((PsiJavaFile)psiFile).getPackageName());
        }
      }
    }
  }

  @Override
  public void onEntityInitialized(RefElement refElement, PsiElement psiElement) {
    if (myRefManager.isOfflineView()) return;
    if (isEntryPoint(refElement)) {
      getEntryPointsManager().addEntryPoint(refElement, false);
    }

    /*if (psiElement instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)psiElement;

      EntryPointsManager entryPointsManager = getEntryPointsManager();
      if (psiClass.isAnnotationType()){
        entryPointsManager.addEntryPoint(refElement, false);
        for (PsiMethod psiMethod : psiClass.getMethods()) {
          entryPointsManager.addEntryPoint(myRefManager.getReference(psiMethod), false);
        }
      }
      else if (psiClass.isEnum()) {
        entryPointsManager.addEntryPoint(refElement, false);
      }
    }*/
  }

  private static void appendPackageElement(final Element element, final String packageName) {
    final Element packageElement = new Element("package");
    packageElement.addContent(packageName.isEmpty() ? InspectionsBundle.message("inspection.export.results.default") : packageName);
    element.addContent(packageElement);
  }

  @Override
  public EntryPointsManager getEntryPointsManager() {
    if (myEntryPointsManager == null) {
      final Project project = myRefManager.getProject();
      myEntryPointsManager = new EntryPointsManagerBase(project) {
        @Override
        public void configureAnnotations() {

        }

        @Override
        public void configureEntryClassPatterns() {}

        @Override
        public JButton createConfigureAnnotationsBtn() {
          return null;
        }
      };
      Disposer.register(project, myEntryPointsManager);

      ((EntryPointsManagerBase)myEntryPointsManager).addAllPersistentEntries(EntryPointsManagerBase.getInstance(project));
    }
    return myEntryPointsManager;
  }

  private class MyJavaElementVisitor extends JavaElementVisitor {
    private final RefJavaUtil myRefUtil;

    public MyJavaElementVisitor() {
      myRefUtil = RefJavaUtil.getInstance();
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitElement(expression);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    }


    @Override
    public void visitReferenceParameterList(final PsiReferenceParameterList list) {
      super.visitReferenceParameterList(list);
      final PsiMember member = PsiTreeUtil.getParentOfType(list, PsiMember.class);

      if (member instanceof PsiTypeParameter) {
        final PsiMember owner = ((PsiTypeParameter)member).getOwner();
        if (owner != null) {
          for (PsiClassType type : ((PsiTypeParameter)member).getExtendsListTypes()) {
            myRefUtil.addTypeReference(owner, type, myRefManager);
          }
        }
      }

      final PsiType[] typeArguments = list.getTypeArguments();
      for (PsiType type : typeArguments) {
        myRefUtil.addTypeReference(member, type, myRefManager);
      }
    }

    @Override
    public void visitClass(PsiClass aClass) {
      if (!(aClass instanceof PsiTypeParameter)) {
        super.visitClass(aClass);
        RefElement refClass = myRefManager.getReference(aClass);
        if (refClass != null) {
          ((RefClassImpl)refClass).buildReferences();
        }
      }
    }

    @Override
    public void visitMethod(final PsiMethod method) {
      super.visitMethod(method);
      final RefElement refElement = myRefManager.getReference(method);
      if (refElement instanceof RefMethodImpl) {
        ((RefMethodImpl)refElement).buildReferences();
      }
    }

    @Override
    public void visitField(final PsiField field) {
      super.visitField(field);
      final RefElement refElement = myRefManager.getReference(field);
      if (refElement instanceof RefFieldImpl) {
        ((RefFieldImpl)refElement).buildReferences();
      }
    }

    @Override
    public void visitDocComment(PsiDocComment comment) {
      super.visitDocComment(comment);
      final PsiDocTag[] tags = comment.getTags();
      for (PsiDocTag tag : tags) {
        if (Comparing.strEqual(tag.getName(), SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME)) {
          final PsiElement[] dataElements = tag.getDataElements();
          if (dataElements != null && dataElements.length > 0) {
            final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(comment, PsiModifierListOwner.class);
            if (listOwner != null) {
              final RefElementImpl element = (RefElementImpl)myRefManager.getReference(listOwner);
              if (element != null) {
                String suppression = "";
                for (PsiElement dataElement : dataElements) {
                  suppression += "," + dataElement.getText();
                }
                element.addSuppression(suppression);
              }
            }
          }
        }
      }
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      if (Comparing.strEqual(annotation.getQualifiedName(), BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME)) {
        final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
        if (listOwner != null) {
          final RefElementImpl element = (RefElementImpl)myRefManager.getReference(listOwner);
          if (element != null) {
            StringBuilder buf = new StringBuilder();
            final PsiNameValuePair[] nameValuePairs = annotation.getParameterList().getAttributes();
            for (PsiNameValuePair nameValuePair : nameValuePairs) {
              buf.append(",").append(nameValuePair.getText().replaceAll("[{}\"\"]", ""));
            }
            if (buf.length() > 0) {
              element.addSuppression(buf.substring(1));
            }
          }
        }
      }
    }

    @Override
    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      myRefUtil.addTypeReference(variable, variable.getType(), myRefManager);
      if (variable instanceof PsiParameter) {
        final RefElement reference = myRefManager.getReference(variable);
        if (reference instanceof RefParameterImpl) {
          ((RefParameterImpl)reference).buildReferences();
        }
      }
    }

    @Override
    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      super.visitInstanceOfExpression(expression);
      final PsiTypeElement typeElement = expression.getCheckType();
      if (typeElement != null) {
        myRefUtil.addTypeReference(expression, typeElement.getType(), myRefManager);
      }
    }

    @Override
    public void visitThisExpression(PsiThisExpression expression) {
      super.visitThisExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null) {
        myRefUtil.addTypeReference(expression, expression.getType(), myRefManager);
        RefClass ownerClass = myRefUtil.getOwnerClass(myRefManager, expression);
        if (ownerClass != null) {
          RefClassImpl refClass = (RefClassImpl)myRefManager.getReference(qualifier.resolve());
          if (refClass != null) {
            refClass.addInstanceReference(ownerClass);
          }
        }
      }
    }
  }
}
