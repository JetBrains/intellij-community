// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInspection.BatchSuppressManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.*;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.uast.UastVisitorAdapter;
import com.intellij.util.ObjectUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author anna
 */
public final class RefJavaManagerImpl extends RefJavaManager {
  private static final Condition<PsiElement> PROBLEM_ELEMENT_CONDITION =
    Conditions.or(Conditions.instanceOf(PsiFile.class, PsiJavaModule.class),
                  Conditions.and(Conditions.and(Conditions.notInstanceOf(PsiTypeParameter.class), Conditions.instanceOf(PsiNamedElement.class)), psi -> {
                    UDeclaration decl = UastContextKt.toUElement(psi, UDeclaration.class);
                    return decl != null && (decl instanceof UField || !(decl instanceof UVariable)) && (!(decl instanceof UClassInitializer));
                  }));

  private static final Logger LOG = Logger.getInstance(RefJavaManagerImpl.class);
  public static final String JAVAX_SERVLET_SERVLET = "javax.servlet.Servlet";
  private final PsiMethod myAppMainPattern;
  private final PsiMethod myAppPremainPattern;
  private final PsiMethod myAppAgentmainPattern;
  private final PsiClass myApplet;
  private volatile RefPackage myCachedDefaultPackage;  // cached value. benign race
  private Map<String, RefPackage> myPackages; // guarded by this
  private final RefManagerImpl myRefManager;
  private PsiElementVisitor myProjectIterator; // cached iterator. benign race
  private EntryPointsManager myEntryPointsManager; // cached manager. benign race

  public RefJavaManagerImpl(@NotNull RefManagerImpl manager) {
    myRefManager = manager;
    final Project project = manager.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    myAppMainPattern = factory.createMethodFromText("void main(String[] args);", null);
    myAppPremainPattern = factory.createMethodFromText("void premain(String[] args, java.lang.instrument.Instrumentation i);", null);
    myAppAgentmainPattern = factory.createMethodFromText("void agentmain(String[] args, java.lang.instrument.Instrumentation i);", null);

    myApplet = JavaPsiFacade.getInstance(project).findClass("java.applet.Applet", GlobalSearchScope.allScope(project));
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
        myPackages = new HashMap<>();
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
    final PsiElement element = refElement.getPsiElement();
    final VirtualFile file = PsiUtilCore.getVirtualFile(element);
    return file != null && !ProjectRootManager.getInstance(element.getProject()).getFileIndex().isInTestSourceContent(file);
  }

  @Nullable
  private UnusedDeclarationInspectionBase getDeadCodeTool(RefElement element) {
    PsiFile file = ((RefElementImpl)element).getContainingFile();
    if (file == null) return null;

    return getDeadCodeTool(file);
  }

  private UnusedDeclarationInspectionBase getDeadCodeTool(PsiFile file) {
    GlobalInspectionContextBase contextBase = (GlobalInspectionContextBase)myRefManager.getContext();
    Tools tools = contextBase.getTools().get(UnusedDeclarationInspectionBase.SHORT_NAME);
    InspectionToolWrapper<?,?> toolWrapper;
    if (tools != null) {
      toolWrapper = tools.getEnabledTool(file);
    }
    else  {
      String singleTool = contextBase.getCurrentProfile().getSingleTool();
      if (singleTool != null && !UnusedDeclarationInspectionBase.SHORT_NAME.equals(singleTool)) {
        return UnusedDeclarationInspectionBase.findUnusedDeclarationInspection(file);
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
      myCachedDefaultPackage = defaultPackage = getPackage(JavaAnalysisBundle.message("inspection.reference.default.package"));
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
  public String getAppletQName() {
    return myApplet.getQualifiedName();
  }

  @Override
  public String getServletQName() {
    return JAVAX_SERVLET_SERVLET;
  }

  @Override
  public RefParameter getParameterReference(UParameter param, int index, RefElement refElement) {
    LOG.assertTrue(myRefManager.isValidPointForReference(), "References may become invalid after process is finished");

    PsiElement sourcePsi = param.getSourcePsi();
    LOG.assertTrue(sourcePsi != null, "UParameter " + param + " has null sourcePsi");
    RefElement result = myRefManager.getFromRefTableOrCache(sourcePsi, () -> {
      RefParameterImpl ref = new RefParameterImpl(param, sourcePsi, index, myRefManager, refElement);
      ref.initialize();
      ref.setInitialized(true);
      return (RefElement)ref;
    });
    return result instanceof RefParameter ? (RefParameter)result : null;
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
  public RefElement createRefElement(@NotNull final PsiElement psi) {
    if (!getLanguages().contains(psi.getLanguage())) {
      return null;
    }
    if (psi instanceof PsiFile) {
      return new RefJavaFileImpl((PsiFile)psi, myRefManager);
    }
    else if (psi instanceof PsiJavaModule) {
      return new RefJavaModuleImpl((PsiJavaModule)psi, myRefManager);
    }
    UElement uElement = psi instanceof UElement ? (UElement)psi : UastContextKt.toUElement(psi);
    if (uElement instanceof UClass) {
      return new RefClassImpl((UClass)uElement, psi, myRefManager);
    }
    if (uElement instanceof UMethod) {
      return new RefMethodImpl((UMethod)uElement, psi, myRefManager);
    }
    else if (uElement instanceof UField) {
      return new RefFieldImpl((UField)uElement, psi, myRefManager);
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
    if (ref instanceof RefFunctionalExpression) {
      return FUNCTIONAL_EXPRESSION;
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
      myProjectIterator = projectIterator = new UastVisitorAdapter(new MyJavaElementVisitor(), true) {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          super.visitElement(element);
          if (element instanceof PsiJavaModule) {
            visitJavaModule((PsiJavaModule)element);
          }
          else if (element instanceof PsiFunctionalExpression) {
            RefElement decl = myRefManager.getReference(element);
            if (decl != null) {
              myRefManager.executeTask(() -> ((RefElementImpl)decl).buildReferences());
            }
          }
        }

        private void visitJavaModule(PsiJavaModule module) {
          RefElement refElement = myRefManager.getReference(module);
          if (refElement != null) {
            myRefManager.executeTask(() -> ((RefJavaModuleImpl)refElement).buildReferences());
          }
        }
      };
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
      packageElement.addContent(packageName.isEmpty() ? JavaAnalysisBundle.message("inspection.reference.default.package") : packageName);
      element.addContent(packageElement);
    }
  }

  @Override
  public void onEntityInitialized(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    if (myRefManager.isOfflineView()) return;
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
      .map(PsiClassType::resolve)
      .filter(Objects::nonNull);
  }

  @Override
  public void markExternalReferencesProcessed(@NotNull RefElement file) {
    getEntryPointsManager().addEntryPoint(file, false);
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
      };
      Disposer.register(project, entryPointsManager);

      ((EntryPointsManagerBase)entryPointsManager).addAllPersistentEntries(EntryPointsManagerBase.getInstance(project));
    }
    return entryPointsManager;
  }

  private class MyJavaElementVisitor extends AbstractUastNonRecursiveVisitor {
    private final RefJavaUtil myRefUtil = RefJavaUtil.getInstance();
    private final ExternalAnnotationsManager myExternalAnnotationsManager = ExternalAnnotationsManager.getInstance(myRefManager.getProject());

    @Override
    public boolean visitFile(@NotNull UFile node) {
      RefElement refElement = myRefManager.getReference(node.getSourcePsi());
      if (refElement instanceof RefJavaFileImpl) {
        myRefManager.executeTask(() -> ((RefJavaFileImpl)refElement).buildReferences());
      }
      return true;
    }

    @Override
    public boolean visitDeclaration(@NotNull UDeclaration node) {
      processComments(node);
      RefElement decl = myRefManager.getReference(node.getSourcePsi());
      if (decl != null) {
        myRefManager.executeTask(() -> ((RefElementImpl)decl).buildReferences());
      }

      PsiModifierListOwner javaModifiersListOwner = ObjectUtils.tryCast(node.getJavaPsi(), PsiModifierListOwner.class);
      if (javaModifiersListOwner != null) {
        PsiAnnotation externalAnnotation = myExternalAnnotationsManager.findExternalAnnotation(javaModifiersListOwner,
                                                                                               BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
        UAnnotation uAnnotation = UastContextKt.toUElement(externalAnnotation, UAnnotation.class);
        if (uAnnotation != null) {
          retrieveSuppressions(uAnnotation, node);
        }
      }
      return true;
    }

    @Override
    public boolean visitField(@NotNull UField node) {
      visitDeclaration(node);
      return false;
    }

    @Override
    public boolean visitVariable(@NotNull UVariable variable) {
      myRefUtil.addTypeReference((UElement)variable, variable.getType(), myRefManager);
      if (variable instanceof UParameter) {
        final RefElement reference = myRefManager.getReference(variable.getSourcePsi());
        if (reference instanceof RefParameterImpl) {
          myRefManager.executeTask(() -> ((RefParameterImpl)reference).buildReferences());
        }
      }
      return false;
    }

    @Override
    public boolean visitAnnotation(@NotNull UAnnotation annotation) {
      String qualifiedName = annotation.getQualifiedName();
      if (Comparing.strEqual(qualifiedName, BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME) ||
          Comparing.strEqual(qualifiedName, "kotlin.Suppress")) {
        UAnnotated annotated = UastUtils.getParentOfType(annotation, UAnnotated.class);
        // TODO support kotlin suppressions
        if (annotated == null) {
          UElement parent = annotation.getUastParent();
          if (parent == null) {
            PsiElement sourcePsi = annotation.getSourcePsi();
            if (sourcePsi != null) {
              annotated = UastContextKt.toUElement(sourcePsi.getContainingFile(), UFile.class);
            }
          }
        }
        retrieveSuppressions(annotation, annotated);
      }
      return false;
    }

    private void retrieveSuppressions(UAnnotation annotation, UAnnotated annotated) {
      if (annotated != null) {
        PsiElement annotatedSrc = annotated.getSourcePsi();
        final WritableRefElement element = (WritableRefElement)myRefManager.getReference(annotatedSrc);
        if (element != null) {
          StringBuilder buf = new StringBuilder();
          final List<UNamedExpression> nameValuePairs = annotation.getAttributeValues();
          for (UNamedExpression nameValuePair : nameValuePairs) {
            UExpression value = nameValuePair.getExpression();
            if (value instanceof ULiteralExpression) {
              Object val = ((ULiteralExpression)value).getValue();
              if (val instanceof String) {
                buf.append(",").append(String.valueOf(val).replaceAll("[{}\"]", ""));
              }
            }
            else if (value instanceof UCallExpression && ((UCallExpression)value).getKind() == UastCallKind.NESTED_ARRAY_INITIALIZER) {
              for (UExpression argument : ((UCallExpression)value).getValueArguments()) {
                if (argument instanceof ULiteralExpression) {
                  Object val = ((ULiteralExpression)argument).getValue();
                  if (val instanceof String) {
                    buf.append(",").append(val);
                  }
                }
              }
            }
          }

          if (buf.length() > 0) {
            String suppressId = buf.substring(1);
            element.addSuppression(suppressId);

            if (annotatedSrc instanceof PsiField) {
              addSuppressionsForSiblings((PsiField)annotatedSrc, suppressId);
            }
          }
        }
      }
    }

    //TODO support suppressions by comment tag in kotlin
    private void processComments(@NotNull UElement node) {
      for (UComment comment : node.getComments()) {
        PsiElement psi = comment.getSourcePsi();
        if (psi instanceof PsiDocComment) {
          final PsiDocTag[] tags = ((PsiDocComment)psi).getTags();
          for (PsiDocTag tag : tags) {
            if (Comparing.strEqual(tag.getName(), SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)) {
              final PsiElement[] dataElements = tag.getDataElements();
              if (dataElements.length > 0) {
                final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(psi, PsiModifierListOwner.class);
                if (listOwner != null) {
                  final WritableRefElement element = (WritableRefElement)myRefManager.getReference(listOwner);
                  if (element != null) {
                    String suppression = StringUtil.join(dataElements, PsiElement::getText, ",");
                    element.addSuppression(suppression);
                  }
                }
              }
            }
          }
        }
      }
    }

    private void addSuppressionsForSiblings(PsiField listOwner, String suppressId) {
      PsiField field = listOwner;
      while (true) {
        PsiElement psiElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(field);
        if (psiElement == null || !JavaTokenType.COMMA.equals(psiElement.getNode().getElementType())) {
          break;
        }
        psiElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(psiElement);
        if (psiElement instanceof PsiField) {
          field = (PsiField)psiElement;
          RefElement refElement = myRefManager.getReference(field);
          if (refElement != null) {
            ((WritableRefElement)refElement).addSuppression(suppressId);
          }
        }
        else {
          break;
        }
      }
    }
  }
}
