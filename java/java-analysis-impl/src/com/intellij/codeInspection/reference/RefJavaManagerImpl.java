// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInspection.BatchSuppressManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;


/**
 * @author anna
 */
public class RefJavaManagerImpl extends RefJavaManager {
  private static final Condition<PsiElement> PROBLEM_ELEMENT_CONDITION = Conditions
    .and(Conditions.instanceOf(PsiFile.class, PsiClass.class, PsiMethod.class, PsiField.class, PsiJavaModule.class), Conditions.notInstanceOf(PsiTypeParameter.class));

  private static final Logger LOG = Logger.getInstance(RefJavaManagerImpl.class);
  private final PsiMethod myAppMainPattern;
  private final PsiMethod myAppPremainPattern;
  private final PsiMethod myAppAgentmainPattern;
  private final PsiClass myApplet;
  private final PsiClass myServlet;
  private volatile RefPackage myCachedDefaultPackage;  // cached value. benign race
  private Map<String, RefPackage> myPackages; // guarded by this
  private final RefManagerImpl myRefManager;
  private PsiElementVisitor myProjectIterator; // cached iterator. benign race
  private EntryPointsManager myEntryPointsManager; // cached manager. benign race

  public RefJavaManagerImpl(@NotNull RefManagerImpl manager) {
    myRefManager = manager;
    final Project project = manager.getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    myAppMainPattern = factory.createMethodFromText("void main(String[] args);", null);
    myAppPremainPattern = factory.createMethodFromText("void premain(String[] args, java.lang.instrument.Instrumentation i);", null);
    myAppAgentmainPattern = factory.createMethodFromText("void agentmain(String[] args, java.lang.instrument.Instrumentation i);", null);

    myApplet = JavaPsiFacade.getInstance(project).findClass("java.applet.Applet", GlobalSearchScope.allScope(project));
    myServlet = JavaPsiFacade.getInstance(project).findClass("javax.servlet.Servlet", GlobalSearchScope.allScope(project));
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
    RefPackage refPackage;
    synchronized (this) {
      if (myPackages == null) {
        myPackages = new THashMap<>();
      }

      refPackage = myPackages.get(packageName);
    }

    if (refPackage == null) {
      refPackage = new RefPackageImpl(packageName, myRefManager);
      boolean saved;
      synchronized (this) {
        RefPackage oldPackage = myPackages.get(packageName);
        if (oldPackage == null) {
          myPackages.put(packageName, refPackage);
          saved = true;
        }
        else {
          refPackage = oldPackage;
          saved = false;
        }
      }

      if (saved) {
        int dotIndex = packageName.lastIndexOf('.');
        if (dotIndex >= 0) {
          ((RefPackageImpl)getPackage(packageName.substring(0, dotIndex))).add(refPackage);
        }
        else {
          ((RefProjectImpl)myRefManager.getRefProject()).add(refPackage);
        }
      }
    }

    return refPackage;
  }

  private boolean isEntryPoint(final RefElement element) {
    UnusedDeclarationInspectionBase tool = getDeadCodeTool(element);
    return tool != null && tool.isEntryPoint(element) && isTestSource(tool, element);
  }

  private static boolean isTestSource(UnusedDeclarationInspectionBase tool, RefElement refElement) {
    if (tool.isTestEntryPoints()) return true;
    final PsiElement element = refElement.getElement();
    final VirtualFile file = PsiUtilCore.getVirtualFile(element);
    return file != null && !ProjectRootManager.getInstance(element.getProject()).getFileIndex().isInTestSourceContent(file);
  }

  @Nullable
  private UnusedDeclarationInspectionBase getDeadCodeTool(RefElement element) {
    PsiFile file = ((RefElementImpl)element).getContainingFile();
    if (file == null) return null;

    return getDeadCodeTool(file.getContainingFile());
  }

  private UnusedDeclarationInspectionBase getDeadCodeTool(PsiFile file) {
    GlobalInspectionContextBase contextBase = (GlobalInspectionContextBase)myRefManager.getContext();
    Tools tools = contextBase.getTools().get(UnusedDeclarationInspectionBase.SHORT_NAME);
    InspectionToolWrapper toolWrapper;
    if (tools != null) {
      toolWrapper = tools.getEnabledTool(file);
    }
    else  {
      String singleTool = contextBase.getCurrentProfile().getSingleTool();
      if (singleTool != null && !UnusedDeclarationInspectionBase.SHORT_NAME.equals(singleTool)) {
        InspectionProfileImpl currentProfile = InspectionProjectProfileManager.getInstance(myRefManager.getProject()).getCurrentProfile();
        tools = currentProfile.getTools(UnusedDeclarationInspectionBase.SHORT_NAME, myRefManager.getProject());
        toolWrapper = tools.getEnabledTool(file);
      }
      else {
        return null;
      }
    }
    InspectionProfileEntry tool = toolWrapper == null ? null : toolWrapper.getTool();
    return tool instanceof UnusedDeclarationInspectionBase ? (UnusedDeclarationInspectionBase)tool : null;
  }

  @Override
  public RefPackage getDefaultPackage() {
    RefPackage defaultPackage = myCachedDefaultPackage;
    if (defaultPackage == null) {
      myCachedDefaultPackage = defaultPackage = getPackage(InspectionsBundle.message("inspection.reference.default.package"));
    }
    return defaultPackage;
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
    Map<String, RefPackage> packages;
    synchronized (this) {
      packages = myPackages;
    }
    if (packages != null) {
      for (RefPackage refPackage : packages.values()) {
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
    synchronized (this) {
      myPackages = null;
      myCachedDefaultPackage = null;
    }
    myProjectIterator = null;
  }

  @Override
  public void removeReference(@NotNull final RefElement refElement) { }

  @Override
  @Nullable
  public RefElement createRefElement(@NotNull final PsiElement elem) {
    if (elem instanceof PsiClass) {
      return new RefClassImpl((PsiClass)elem, myRefManager);
    }
    if (elem instanceof PsiMethod) {
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
    else if (elem instanceof PsiJavaModule) {
      return new RefJavaModuleImpl((PsiJavaModule)elem, myRefManager);
    }
    return null;
  }

  @Nullable
  @Override
  public PsiNamedElement getElementContainer(@NotNull PsiElement psiElement) {
    return (PsiNamedElement)PsiTreeUtil.findFirstParent(psiElement, PROBLEM_ELEMENT_CONDITION);
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
    if (CLASS.equals(type)) {
      return RefClassImpl.classFromExternalName(myRefManager, fqName);
    }
    if (FIELD.equals(type)) {
      return RefFieldImpl.fieldFromExternalName(myRefManager, fqName);
    }
    if (PARAMETER.equals(type)) {
      return RefParameterImpl.parameterFromExternalName(myRefManager, fqName);
    }
    if (PACKAGE.equals(type)) {
      return RefPackageImpl.packageFromFQName(myRefManager, fqName);
    }
    if (JAVA_MODULE.equals(type)) {
      return RefJavaModuleImpl.moduleFromExternalName(myRefManager, fqName);
    }
    return null;
  }

  @Override
  @Nullable
  public String getType(@NotNull final RefEntity ref) {
    if (ref instanceof RefImplicitConstructor) {
      return IMPLICIT_CONSTRUCTOR;
    }
    if (ref instanceof RefMethod) {
      return METHOD;
    }
    if (ref instanceof RefClass) {
      return CLASS;
    }
    if (ref instanceof RefField) {
      return FIELD;
    }
    if (ref instanceof RefParameter) {
      return PARAMETER;
    }
    if (ref instanceof RefPackage) {
      return PACKAGE;
    }
    if (ref instanceof RefJavaModule) {
      return JAVA_MODULE;
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
  public void visitElement(@NotNull final PsiElement element) {
    PsiElementVisitor projectIterator = myProjectIterator;
    if (projectIterator == null) {
      myProjectIterator = projectIterator = new MyJavaElementVisitor();
    }
    element.accept(projectIterator);
  }

  @Override
  @Nullable
  public String getGroupName(@NotNull final RefEntity entity) {
    if (entity instanceof RefFile && !(entity instanceof RefJavaFileImpl)) return null;
    return RefJavaUtil.getInstance().getPackageName(entity);
  }

  @Override
  public boolean belongsToScope(@NotNull final PsiElement psiElement) {
    return !(psiElement instanceof PsiTypeParameter);
  }

  @Override
  public void export(@NotNull final RefEntity refEntity, @NotNull final Element element) {
    String packageName = RefJavaUtil.getInstance().getPackageName(refEntity);
    if (packageName != null) {
      final Element packageElement = new Element("package");
      packageElement.addContent(packageName.isEmpty() ? InspectionsBundle.message("inspection.reference.default.package") : packageName);
      element.addContent(packageElement);
    }
  }

  @Override
  public void onEntityInitialized(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    if (myRefManager.isOfflineView() || !myRefManager.isDeclarationsFound()) return;
    if (isEntryPoint(refElement)) {
      getEntryPointsManager().addEntryPoint(refElement, false);
    }
  }

  @Override
  public boolean shouldProcessExternalFile(@NotNull PsiFile file) {
    return file instanceof PsiClassOwner;
  }

  @NotNull
  @Override
  public Stream<? extends PsiElement> extractExternalFileImplicitReferences(@NotNull PsiFile psiFile) {
    return Arrays
      .stream(((PsiClassOwner)psiFile).getClasses())
      .flatMap(c -> Arrays.stream(c.getSuperTypes()))
      .map(t -> t.resolve())
      .filter(Objects::nonNull);
  }

  @Override
  public EntryPointsManager getEntryPointsManager() {
    EntryPointsManager entryPointsManager = myEntryPointsManager;
    if (entryPointsManager == null) {
      final Project project = myRefManager.getProject();
      myEntryPointsManager = entryPointsManager = new EntryPointsManagerBase(project) {
        @Override
        public void configureAnnotations() {
        }

        @Override
        public JButton createConfigureAnnotationsBtn() {
          return null;
        }
      };
      Disposer.register(project, entryPointsManager);

      ((EntryPointsManagerBase)entryPointsManager).addAllPersistentEntries(EntryPointsManagerBase.getInstance(project));
    }
    return entryPointsManager;
  }

  private class MyJavaElementVisitor extends JavaElementVisitor {
    private final RefJavaUtil myRefUtil = RefJavaUtil.getInstance();
    private final ExternalAnnotationsManager myExternalAnnotationsManager = ExternalAnnotationsManager.getInstance(myRefManager.getProject());

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
        if (Comparing.strEqual(tag.getName(), SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)) {
          final PsiElement[] dataElements = tag.getDataElements();
          if (dataElements.length > 0) {
            final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(comment, PsiModifierListOwner.class);
            if (listOwner != null) {
              final RefElementImpl element = (RefElementImpl)myRefManager.getReference(listOwner);
              if (element != null) {
                String suppression = StringUtil.join(dataElements, PsiElement::getText, ",");
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
        retrieveSuppressions(annotation, PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class));
      }
    }

    private void retrieveSuppressions(PsiAnnotation annotation, PsiModifierListOwner listOwner) {
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

    @Override
    public void visitModule(PsiJavaModule javaModule) {
      super.visitModule(javaModule);
      RefElement refElement = myRefManager.getReference(javaModule);
      if (refElement != null) {
        ((RefJavaModuleImpl)refElement).buildReferences();
      }
    }

    @Override
    public void visitJavaFile(PsiJavaFile file) {
      super.visitJavaFile(file);
      RefElement refElement = myRefManager.getReference(file);
      if (refElement != null) {
        ((RefJavaFileImpl)refElement).buildReferences();
      }
    }

    @Override
    public void visitModifierList(PsiModifierList list) {
      super.visitModifierList(list);
      PsiElement parent = list.getParent();
      if (parent instanceof PsiModifierListOwner) {
        PsiModifierListOwner listOwner = (PsiModifierListOwner)parent;
        PsiAnnotation externalAnnotation = myExternalAnnotationsManager.findExternalAnnotation(listOwner, 
                                                                                               BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
        if (externalAnnotation != null) {
          retrieveSuppressions(externalAnnotation, listOwner);
        }
      }
    }
  }
}
