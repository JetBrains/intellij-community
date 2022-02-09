// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.AdjustFunctionContextFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

// java highlighting: problems in java code like unresolved/incompatible symbols/methods etc.
public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor {
  private HighlightInfoHolder myHolder;
  private RefCountHolder myRefCountHolder; // can be null during partial file update
  private LanguageLevel myLanguageLevel;
  private JavaSdkVersion myJavaSdkVersion;

  private PsiFile myFile;
  private PsiJavaModule myJavaModule;

  private PsiElementVisitor myPreviewFeatureVisitor;

  // map codeBlock->List of PsiReferenceExpression of uninitialized final variables
  private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new HashMap<>();
  // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new HashMap<>();

  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> mySingleImportedClasses = new HashMap<>();
  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiField>> mySingleImportedFields = new HashMap<>();

  private final PsiElementVisitor REGISTER_REFERENCES_VISITOR = new PsiRecursiveElementWalkingVisitor() {
    @Override public void visitElement(@NotNull PsiElement element) {
      super.visitElement(element);
      for (PsiReference reference : element.getReferences()) {
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiNamedElement) {
          myRefCountHolder.registerLocallyReferenced((PsiNamedElement)resolved);
          if (resolved instanceof PsiMember) {
            myRefCountHolder.registerReference(reference, new CandidateInfo(resolved, PsiSubstitutor.EMPTY));
          }
        }
      }
    }
  };
  private final Map<PsiClass, MostlySingularMultiMap<MethodSignature, PsiMethod>> myDuplicateMethods = new HashMap<>();
  private final Set<PsiClass> myOverrideEquivalentMethodsVisitedClasses = new HashSet<>();
  private final Map<PsiMethod, PsiType> myExpectedReturnTypes = new HashMap<>();
  private final Function<? super PsiElement, ? extends PsiClass> myInsideConstructorOfClass = this::findInsideConstructorClass;
  private final Map<PsiElement, PsiClass> myInsideConstructorOfClassCache = new HashMap<>(); // null value means "cached but no corresponding ctr found"

  private static class Holder {
    private static final boolean CHECK_ELEMENT_LEVEL = ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal();
  }

  @NotNull
  protected PsiResolveHelper getResolveHelper(@NotNull Project project) {
    return PsiResolveHelper.SERVICE.getInstance(project);
  }

  protected HighlightVisitorImpl() {
  }

  // element -> class inside which there is a constructor inside which this element is contained
  private PsiClass findInsideConstructorClass(@NotNull PsiElement entry) {
    PsiClass result = null;
    PsiElement parent;
    PsiElement element;
    for (element = entry; element != null && !(element instanceof PsiFile); element = parent) {
      result = myInsideConstructorOfClassCache.get(entry);
      if (result != null || myInsideConstructorOfClassCache.containsKey(entry)) {
        return result;
      }
      parent = element.getParent();
      if (parent instanceof PsiExpressionStatement) {
        PsiElement p2 = parent.getParent();
        if (p2 instanceof PsiCodeBlock) {
          PsiElement p3 = p2.getParent();
          if (p3 instanceof PsiMethod && ((PsiMethod)p3).isConstructor()) {
            PsiElement p4 = p3.getParent();
            if (p4 instanceof PsiClass) {
              result = (PsiClass)p4;
            }
          }
        }
      }
      if (result != null) {
        break;
      }
    }
    for (PsiElement e = entry; e != null && !(e instanceof PsiFile); e = e.getParent()) {
      myInsideConstructorOfClassCache.put(e, result);
      if (e == element) break;
    }
    return result;
  }

  /**
   * @deprecated use {@link #HighlightVisitorImpl()} and {@link #getResolveHelper(Project)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected HighlightVisitorImpl(@NotNull PsiResolveHelper psiResolveHelper) {
  }

  @NotNull
  private MostlySingularMultiMap<MethodSignature, PsiMethod> getDuplicateMethods(@NotNull PsiClass aClass) {
    MostlySingularMultiMap<MethodSignature, PsiMethod> signatures = myDuplicateMethods.get(aClass);
    if (signatures == null) {
      signatures = new MostlySingularMultiMap<>();
      for (PsiMethod method : aClass.getMethods()) {
        if (method instanceof ExternallyDefinedPsiElement) continue; // ignore aspectj-weaved methods; they are checked elsewhere
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        signatures.add(signature, method);
      }

      myDuplicateMethods.put(aClass, signatures);
    }
    return signatures;
  }

  @NotNull
  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public HighlightVisitorImpl clone() {
    return new HighlightVisitorImpl();
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(file.getProject());
    if (highlightingLevelManager.runEssentialHighlightingOnly(file)) {
      return false;
    }

    // both PsiJavaFile and PsiCodeFragment must match
    return file instanceof PsiImportHolder && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    if (Holder.CHECK_ELEMENT_LEVEL) {
      ((CheckLevelHighlightInfoHolder)myHolder).enterLevel(element);
      element.accept(this);
      ((CheckLevelHighlightInfoHolder)myHolder).enterLevel(null);
    }
    else {
      element.accept(this);
    }
  }

  private void registerReferencesFromInjectedFragments(@NotNull PsiElement element) {
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFile.getProject());
    manager.enumerateEx(element, myFile, false, (injectedPsi, places) -> injectedPsi.accept(REGISTER_REFERENCES_VISITOR));
  }

  @Override
  public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable highlight) {
    try {
      prepare(Holder.CHECK_ELEMENT_LEVEL ? new CheckLevelHighlightInfoHolder(file, holder) : holder, file);
      if (updateWholeFile) {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress == null) throw new IllegalStateException("Must be run under progress");
        Project project = file.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        TextRange dirtyScope = document == null ? null : DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap().getFileDirtyScope(document, Pass.UPDATE_ALL);
        if (dirtyScope == null) dirtyScope = file.getTextRange();
        RefCountHolder refCountHolder = RefCountHolder.get(file, dirtyScope);
        if (refCountHolder == null) {
          // RefCountHolder was GCed and queried again for some inner code block
          // "highlight.run()" can't fill it again because it runs for only a subset of elements,
          // so we have to restart the daemon for the whole file
          return false;
        }
        myRefCountHolder = refCountHolder;

        highlight.run();
        ProgressManager.checkCanceled();
        refCountHolder.storeReadyHolder(file);
        if (document != null) {
          new PostHighlightingVisitor(file, document, refCountHolder).collectHighlights(holder, progress);
        }
      }
      else {
        myRefCountHolder = null;
        highlight.run();
      }
    }
    finally {
      myUninitializedVarProblems.clear();
      myFinalVarProblems.clear();
      mySingleImportedClasses.clear();
      mySingleImportedFields.clear();
      myRefCountHolder = null;
      myJavaModule = null;
      myFile = null;
      myHolder = null;
      myPreviewFeatureVisitor = null;
      myDuplicateMethods.clear();
      myOverrideEquivalentMethodsVisitedClasses.clear();
      myExpectedReturnTypes.clear();
      myInsideConstructorOfClassCache.clear();
    }

    return true;
  }

  protected void prepareToRunAsInspection(@NotNull HighlightInfoHolder holder) {
    prepare(holder, holder.getContextFile());
  }

  private void prepare(@NotNull HighlightInfoHolder holder, @NotNull PsiFile file) {
    myHolder = holder;
    myFile = file;
    myLanguageLevel = PsiUtil.getLanguageLevel(file);
    myJavaSdkVersion = ObjectUtils
      .notNull(JavaVersionService.getInstance().getJavaSdkVersion(file), JavaSdkVersion.fromLanguageLevel(myLanguageLevel));
    myJavaModule = myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9) ? JavaModuleGraphUtil.findDescriptorByElement(file) : null;
    myPreviewFeatureVisitor = myLanguageLevel.isPreview() ? EMPTY_VISITOR : new PreviewFeatureVisitor(myLanguageLevel, myHolder);
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (myRefCountHolder != null && myFile instanceof ServerPageFile) {
      // in JSP, XmlAttributeValue may contain java references
      try {
        for (PsiReference reference : element.getReferences()) {
          JavaResolveResult result = resolveJavaReference(reference);
          if (result != null) {
            myRefCountHolder.registerReference(reference, result);
          }
        }
      }
      catch (IndexNotReadyException ignored) { }
    }

    if (!(myFile instanceof ServerPageFile)) {
      myHolder.add(DefaultHighlightUtil.checkBadCharacter(element));
    }
  }

  @Nullable
  public static JavaResolveResult resolveJavaReference(@NotNull PsiReference reference) {
    return reference instanceof PsiJavaReference ? ((PsiJavaReference)reference).advancedResolve(false) : null;
  }

  @Override
  public void visitAnnotation(PsiAnnotation annotation) {
    super.visitAnnotation(annotation);
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(annotation, HighlightingFeature.ANNOTATIONS));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkApplicability(annotation, myLanguageLevel, myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkAnnotationType(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkMissingAttributes(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkTargetAnnotationDuplicates(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkDuplicateAnnotations(annotation, myLanguageLevel));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkFunctionalInterface(annotation, myLanguageLevel));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkInvalidAnnotationOnRecordComponent(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkRepeatableAnnotation(annotation));
    if (CommonClassNames.JAVA_LANG_OVERRIDE.equals(annotation.getQualifiedName())) {
      PsiAnnotationOwner owner = annotation.getOwner();
      PsiElement parent = owner instanceof PsiModifierList ? ((PsiModifierList)owner).getParent() : null;
      if (parent instanceof PsiMethod) {
        myHolder.add(GenericsHighlightUtil.checkOverrideAnnotation((PsiMethod)parent, annotation, myLanguageLevel));
      }
    }
  }

  @Override
  public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    PsiMethod method = null;

    PsiElement parent = initializer.getParent();
    if (parent instanceof PsiNameValuePair) {
      PsiReference reference = parent.getReference();
      if (reference != null) {
        method = (PsiMethod)reference.resolve();
      }
    }
    else if (PsiUtil.isAnnotationMethod(parent)) {
      method = (PsiMethod)parent;
    }

    if (method != null) {
      PsiType type = method.getReturnType();
      if (type instanceof PsiArrayType) {
        type = ((PsiArrayType)type).getComponentType();
        PsiAnnotationMemberValue[] initializers = initializer.getInitializers();
        for (PsiAnnotationMemberValue initializer1 : initializers) {
          myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(initializer1, type, method));
        }
      }
    }
  }

  @Override
  public void visitAnnotationMethod(PsiAnnotationMethod method) {
    PsiType returnType = method.getReturnType();
    PsiAnnotationMemberValue value = method.getDefaultValue();
    if (returnType != null && value != null) {
      myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(value, returnType, method));
    }

    PsiTypeElement typeElement = method.getReturnTypeElement();
    if (typeElement != null) {
      myHolder.add(AnnotationsHighlightUtil.checkValidAnnotationType(returnType, typeElement));
    }

    PsiClass aClass = method.getContainingClass();
    if (typeElement != null && aClass != null) {
      myHolder.add(AnnotationsHighlightUtil.checkCyclicMemberType(typeElement, aClass));
    }

    myHolder.add(AnnotationsHighlightUtil.checkClashesWithSuperMethods(method));

    if (!myHolder.hasErrorResults() && aClass != null) {
      myHolder.add(HighlightMethodUtil.checkDuplicateMethod(aClass, method, getDuplicateMethods(aClass)));
    }
  }

  @Override
  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    super.visitArrayInitializerExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkArrayInitializerApplicable(expression));
    if (!(expression.getParent() instanceof PsiNewExpression)) {
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()));
    }
  }

  @Override
  public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssignmentCompatibleTypes(assignment));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssignmentOperatorApplicable(assignment));
    if (!myHolder.hasErrorResults()) visitExpression(assignment);
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    super.visitPolyadicExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkPolyadicOperatorApplicable(expression));
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    myHolder.add(checkFeature(expression, HighlightingFeature.LAMBDA_EXPRESSIONS));
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (toReportFunctionalExpressionProblemOnParent(parent)) return;
    if (!myHolder.hasErrorResults() && !LambdaUtil.isValidLambdaContext(parent)) {
      myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                     .descriptionAndTooltip(JavaErrorBundle.message("lambda.expression.not.expected")).create());
    }

    if (!myHolder.hasErrorResults()) myHolder.add(LambdaHighlightingUtil.checkConsistentParameterDeclaration(expression));

    PsiType functionalInterfaceType = null;
    if (!myHolder.hasErrorResults()) {
      functionalInterfaceType = expression.getFunctionalInterfaceType();
      if (functionalInterfaceType != null) {
        myHolder.add(HighlightClassUtil.checkExtendsSealedClass(expression, functionalInterfaceType));
        if (!myHolder.hasErrorResults()) {
          String notFunctionalMessage = LambdaHighlightingUtil.checkInterfaceFunctional(functionalInterfaceType);
          if (notFunctionalMessage != null) {
            myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                           .descriptionAndTooltip(notFunctionalMessage).create());
          }
          else {
            checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType);
          }
        }
      }
      else if (LambdaUtil.getFunctionalInterfaceType(expression, true) != null) {
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(
          JavaErrorBundle.message("cannot.infer.functional.interface.type")).create());
      }
    }

    if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
      String parentInferenceErrorMessage = null;
      PsiCallExpression callExpression = parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression ?
                                               (PsiCallExpression)parent.getParent() : null;
      JavaResolveResult containingCallResolveResult = callExpression != null ? callExpression.resolveMethodGenerics() : null;
      if (containingCallResolveResult instanceof MethodCandidateInfo) {
        parentInferenceErrorMessage = ((MethodCandidateInfo)containingCallResolveResult).getInferenceErrorMessage();
      }
      Map<PsiElement, @Nls String> returnErrors = LambdaUtil.checkReturnTypeCompatible(expression, LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType));
      if (parentInferenceErrorMessage != null && (returnErrors == null || !returnErrors.containsValue(parentInferenceErrorMessage))) {
        if (returnErrors == null) return;
        HighlightInfo info = HighlightMethodUtil.createIncompatibleTypeHighlightInfo(callExpression, getResolveHelper(myHolder.getProject()),
                                                                                     (MethodCandidateInfo)containingCallResolveResult, expression);
        returnErrors.keySet().forEach(k -> QuickFixAction.registerQuickFixAction(info, AdjustFunctionContextFix.createFix(k)));
        myHolder.add(info);
      }
      else if (returnErrors != null) {
        for (Map.Entry<PsiElement, @Nls String> entry : returnErrors.entrySet()) {
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(entry.getKey())
            .descriptionAndTooltip(entry.getValue()).create();
          QuickFixAction.registerQuickFixAction(info, AdjustFunctionContextFix.createFix(entry.getKey()));
          if (entry.getKey() instanceof PsiExpression) {
            PsiExpression expr = (PsiExpression)entry.getKey();
            HighlightFixUtil.registerLambdaReturnTypeFixes(info, expression, expr);
          }
          myHolder.add(info);
        }
      }
    }

    if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
      PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      if (interfaceMethod != null) {
        PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
        myHolder.add(LambdaHighlightingUtil.checkParametersCompatible(expression, parameters, LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)));
      }
    }

    if (!myHolder.hasErrorResults()) {
      PsiElement body = expression.getBody();
      if (body instanceof PsiCodeBlock) {
        myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement((PsiCodeBlock)body));
      }
    }
  }

  @Override
  public void visitBreakStatement(PsiBreakStatement statement) {
    super.visitBreakStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkBreakTarget(statement, myLanguageLevel));
  }

  @Override
  public void visitYieldStatement(PsiYieldStatement statement) {
    super.visitYieldStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkYieldOutsideSwitchExpression(statement));
    if (!myHolder.hasErrorResults()) {
      PsiExpression expression = statement.getExpression();
      if (expression != null) {
        myHolder.add(HighlightUtil.checkYieldExpressionType(expression));
      }
    }
  }

  @Override
  public void visitExpressionStatement(PsiExpressionStatement statement) {
    super.visitExpressionStatement(statement);
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiSwitchLabeledRuleStatement) {
      PsiSwitchBlock switchBlock = ((PsiSwitchLabeledRuleStatement)parent).getEnclosingSwitchBlock();
      if (switchBlock instanceof PsiSwitchExpression && !PsiPolyExpressionUtil.isPolyExpression((PsiExpression)switchBlock)) {
        myHolder.add(HighlightUtil.checkYieldExpressionType(statement.getExpression()));
      }
    }
  }

  @Override
  public void visitClass(PsiClass aClass) {
    super.visitClass(aClass);
    if (aClass instanceof PsiSyntheticClass) return;
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInterfaceMultipleInheritance(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkClassSupersAccessibility(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateTopLevelClass(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkMustNotBeLocal(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkImplicitThisReferenceBeforeSuper(aClass, myJavaSdkVersion));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassAndPackageConflict(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkPublicClassInRightFile(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkWellFormedRecord(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkSealedClassInheritors(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkSealedSuper(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParameterOverrideEquivalentMethods(aClass, myLanguageLevel));
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkIllegalInstanceMemberInRecord(initializer));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkInitializerCompleteNormally(initializer));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(initializer.getBody()));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(initializer, initializer.getContainingClass()));
    }
  }

  @Override
  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    super.visitClassObjectAccessExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkClassObjectAccessExpression(expression));
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    super.visitComment(comment);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkShebangComment(comment));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnclosedComment(comment));
    if (myRefCountHolder != null && !myHolder.hasErrorResults()) registerReferencesFromInjectedFragments(comment);
  }

  @Override
  public void visitContinueStatement(PsiContinueStatement statement) {
    super.visitContinueStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkContinueTarget(statement, myLanguageLevel));
  }

  @Override
  public void visitJavaToken(PsiJavaToken token) {
    super.visitJavaToken(token);

    IElementType type = token.getTokenType();
    if (!myHolder.hasErrorResults() && type == JavaTokenType.TEXT_BLOCK_LITERAL) {
      myHolder.add(checkFeature(token, HighlightingFeature.TEXT_BLOCKS));
    }

    if (!myHolder.hasErrorResults() && type == JavaTokenType.RBRACE && token.getParent() instanceof PsiCodeBlock) {
      PsiElement gParent = token.getParent().getParent();
      PsiCodeBlock codeBlock;
      PsiType returnType;
      if (gParent instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)gParent;
        codeBlock = method.getBody();
        returnType = method.getReturnType();
      }
      else if (gParent instanceof PsiLambdaExpression) {
        PsiElement body = ((PsiLambdaExpression)gParent).getBody();
        if (!(body instanceof PsiCodeBlock)) return;
        codeBlock = (PsiCodeBlock)body;
        returnType = LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)gParent);
      }
      else {
        return;
      }
      myHolder.add(HighlightControlFlowUtil.checkMissingReturnStatement(codeBlock, returnType));
    }
  }

  @Override
  public void visitDocComment(PsiDocComment comment) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnclosedComment(comment));
  }

  @Override
  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    super.visitEnumConstant(enumConstant);
    if (!myHolder.hasErrorResults()) GenericsHighlightUtil.checkEnumConstantForConstructorProblems(enumConstant, myHolder, myJavaSdkVersion);
    if (!myHolder.hasErrorResults()) registerConstructorCall(enumConstant);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnhandledExceptions(enumConstant));
  }

  @Override
  public void visitEnumConstantInitializer(PsiEnumConstantInitializer enumConstantInitializer) {
    super.visitEnumConstantInitializer(enumConstantInitializer);
    if (!myHolder.hasErrorResults()) {
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(enumConstantInitializer);
      myHolder.add(HighlightClassUtil.checkClassMustBeAbstract(enumConstantInitializer, textRange));
    }
  }

  @Override
  public void visitExpression(PsiExpression expression) {
    ProgressManager.checkCanceled(); // visitLiteralExpression is invoked very often in array initializers
    super.visitExpression(expression);

    PsiElement parent = expression.getParent();
    // Method expression of the call should not be especially processed
    if (parent instanceof PsiMethodCallExpression) return;
    PsiType type = expression.getType();

    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkMustBeBoolean(expression, type));
    if (!myHolder.hasErrorResults() && expression instanceof PsiArrayAccessExpression) {
      myHolder.add(HighlightUtil.checkValidArrayAccessExpression((PsiArrayAccessExpression)expression));
    }
    if (!myHolder.hasErrorResults() && parent instanceof PsiNewExpression &&
        ((PsiNewExpression)parent).getQualifier() != expression && ((PsiNewExpression)parent).getArrayInitializer() != expression) {
      myHolder.add(HighlightUtil.checkAssignability(PsiType.INT, expression.getType(), expression, expression));  // like in 'new String["s"]'
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkCannotWriteToFinal(expression,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableExpected(expression));
    if (!myHolder.hasErrorResults()) myHolder.addAll(HighlightUtil.checkArrayInitializer(expression, type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTernaryOperatorConditionIsBoolean(expression, type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssertOperatorTypes(expression, type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSynchronizedExpressionType(expression, type, myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkConditionalExpressionBranchTypesMatch(expression, type));
    if (!myHolder.hasErrorResults() && parent instanceof PsiThrowStatement && ((PsiThrowStatement)parent).getException() == expression && type != null) {
      myHolder.add(HighlightUtil.checkMustBeThrowable(type, expression, true));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkConstantExpression(expression));
    if (!myHolder.hasErrorResults() && shouldReportForeachNotApplicable(expression)) {
      myHolder.add(GenericsHighlightUtil.checkForeachExpressionTypeIsIterable(expression));
    }
  }

  private static boolean shouldReportForeachNotApplicable(@NotNull PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiForeachStatement)) return false;

    @NotNull PsiForeachStatement parentForEach = (PsiForeachStatement)expression.getParent();
    PsiExpression iteratedValue = parentForEach.getIteratedValue();
    if (iteratedValue != expression) return false;

    // Ignore if the type of the value which is being iterated over is not resolved yet
    PsiType iteratedValueType = iteratedValue.getType();
    return iteratedValueType == null || !PsiTypesUtil.hasUnresolvedComponents(iteratedValueType);
  }

  @Override
  public void visitExpressionList(PsiExpressionList list) {
    super.visitExpressionList(list);
    PsiElement parent = list.getParent();
    if (parent instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression expression = (PsiMethodCallExpression)parent;
      if (expression.getArgumentList() == list) {
        PsiReferenceExpression referenceExpression = expression.getMethodExpression();
        JavaResolveResult[] results = resolveOptimised(referenceExpression);
        if (results == null) return;
        JavaResolveResult result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
        PsiElement resolved = result.getElement();

        if ((!result.isAccessible() || !result.isStaticsScopeCorrect()) &&
            !HighlightMethodUtil.isDummyConstructorCall(expression, getResolveHelper(myHolder.getProject()), list, referenceExpression) &&
            // this check is for fake expression from JspMethodCallImpl
            referenceExpression.getParent() == expression) {
          try {
            if (PsiTreeUtil.findChildrenOfType(expression.getArgumentList(), PsiLambdaExpression.class).isEmpty()) {
              myHolder.add(HighlightMethodUtil.checkAmbiguousMethodCallArguments(referenceExpression, results, list, resolved, result, expression, getResolveHelper(myHolder.getProject()), list));
            }
          }
          catch (IndexNotReadyException ignored) { }
        }
      }
    }
  }

  @Override
  public void visitField(PsiField field) {
    super.visitField(field);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkIllegalInstanceMemberInRecord(field));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkFinalFieldInitialized(field));
  }

  @Override
  public void visitForStatement(PsiForStatement statement) {
    myHolder.add(HighlightUtil.checkForStatement(statement));
  }

  @Override
  public void visitForeachStatement(PsiForeachStatement statement) {
    myHolder.add(checkFeature(statement, HighlightingFeature.FOR_EACH));
  }

  @Override
  public void visitImportStaticStatement(PsiImportStaticStatement statement) {
    myHolder.add(checkFeature(statement, HighlightingFeature.STATIC_IMPORTS));
    if (!myHolder.hasErrorResults()) myHolder.add(ImportsHighlightUtil.checkStaticOnDemandImportResolvesToClass(statement));
    if (!myHolder.hasErrorResults()) {
      PsiJavaCodeReferenceElement importReference = statement.getImportReference();
      PsiClass targetClass = statement.resolveTargetClass();
      if (importReference != null) {
        PsiElement referenceNameElement = importReference.getReferenceNameElement();
        if (referenceNameElement != null && targetClass != null) {
          myHolder.add(GenericsHighlightUtil.checkClassSupersAccessibility(targetClass, referenceNameElement, myFile.getResolveScope()));
        }
      }
      if (!myHolder.hasErrorResults()) {
        statement.accept(myPreviewFeatureVisitor);
      }
    }
  }

  @Override
  public void visitIdentifier(PsiIdentifier identifier) {
    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;
      myHolder.add(HighlightUtil.checkVariableAlreadyDefined(variable));

      if (variable.getInitializer() == null) {
        PsiElement child = variable.getLastChild();
        if (child instanceof PsiErrorElement && child.getPrevSibling() == identifier) return;
      }
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      if (aClass.isAnnotationType()) {
        myHolder.add(checkFeature(identifier, HighlightingFeature.ANNOTATIONS));
      }

      myHolder.add(HighlightClassUtil.checkClassAlreadyImported(aClass, identifier));
      if (!myHolder.hasErrorResults()) {
        myHolder.add(HighlightClassUtil.checkClassRestrictedKeyword(myLanguageLevel, identifier));
      }
      if (!myHolder.hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        myHolder.add(GenericsHighlightUtil.checkUnrelatedDefaultMethods(aClass, identifier));
      }

      if (!myHolder.hasErrorResults()) {
        myHolder.add(GenericsHighlightUtil.checkUnrelatedConcrete(aClass, identifier));
      }
    }
    else if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      if (method.isConstructor()) {
        HighlightInfo info = HighlightMethodUtil.checkConstructorName(method);
        if (info != null) {
          PsiType expectedType = myExpectedReturnTypes.computeIfAbsent(method, HighlightMethodUtil::determineReturnType);
          if (expectedType != null) HighlightUtil.registerReturnTypeFixes(info, method, expectedType);
        }
        myHolder.add(info);
      }
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        myHolder.add(GenericsHighlightUtil.checkDefaultMethodOverrideEquivalentToObjectNonPrivate(myLanguageLevel, aClass, method, identifier));
      }
    }

    myHolder.add(HighlightUtil.checkUnderscore(identifier, myLanguageLevel));

    super.visitIdentifier(identifier);
  }

  @Override
  public void visitImportStatement(PsiImportStatement statement) {
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkSingleImportClassConflict(statement, mySingleImportedClasses,myFile));
    }
    if (!myHolder.hasErrorResults()) {
      statement.accept(myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement ref) {
    String refName = ref.getReferenceName();
    JavaResolveResult[] results = ref.multiResolve(false);

    PsiElement referenceNameElement = ref.getReferenceNameElement();
    if (results.length == 0) {
      String description = JavaErrorBundle.message("cannot.resolve.symbol", refName);
      assert referenceNameElement != null : ref;
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(referenceNameElement).descriptionAndTooltip(description).create();
      myHolder.add(info);
    }
    else {
      PsiManager manager = ref.getManager();
      for (JavaResolveResult result : results) {
        PsiElement element = result.getElement();

        String description = null;
        if (element instanceof PsiClass) {
          Pair<PsiImportStaticReferenceElement, PsiClass> imported = mySingleImportedClasses.get(refName);
          PsiClass aClass = Pair.getSecond(imported);
          if (aClass != null && !manager.areElementsEquivalent(aClass, element)) {
            description = imported.first == null
                          ? JavaErrorBundle.message("single.import.class.conflict", refName)
                          : imported.first.equals(ref)
                            ? JavaErrorBundle.message("class.is.ambiguous.in.single.static.import", refName)
                            : JavaErrorBundle.message("class.is.already.defined.in.single.static.import", refName);
          }
          mySingleImportedClasses.put(refName, Pair.create(ref, (PsiClass)element));
        }
        else if (element instanceof PsiField) {
          Pair<PsiImportStaticReferenceElement, PsiField> imported = mySingleImportedFields.get(refName);
          PsiField field = Pair.getSecond(imported);
          if (field != null && !manager.areElementsEquivalent(field, element)) {
            description = imported.first.equals(ref)
                          ? JavaErrorBundle.message("field.is.ambiguous.in.single.static.import", refName)
                          : JavaErrorBundle.message("field.is.already.defined.in.single.static.import", refName);
          }
          mySingleImportedFields.put(refName, Pair.create(ref, (PsiField)element));
        }

        if (description != null) {
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description).create());
        }
      }
    }
    if (!myHolder.hasErrorResults() && results.length == 1) {
      myHolder.add(HighlightUtil.checkReference(ref, results[0], myFile, myLanguageLevel));
      if (!myHolder.hasErrorResults()) {
        PsiElement element = results[0].getElement();
        PsiClass containingClass = element instanceof PsiMethod ? ((PsiMethod)element).getContainingClass() : null;
        if (containingClass != null && containingClass.isInterface()) {
          myHolder.add(HighlightMethodUtil.checkStaticInterfaceCallQualifier(ref, results[0], ObjectUtils.notNull(ref.getReferenceNameElement(), ref), containingClass));
        }
      }
    }
  }

  @Override
  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInstanceOfApplicable(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInstanceOfGenericType(myLanguageLevel, expression));
    if (!myHolder.hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_16)) {
      myHolder.add(HighlightUtil.checkInstanceOfPatternSupertype(expression));
    }
  }

  @Override
  public void visitKeyword(PsiKeyword keyword) {
    super.visitKeyword(keyword);
    PsiElement parent = keyword.getParent();
    String text = keyword.getText();
    if (parent instanceof PsiModifierList) {
      PsiModifierList psiModifierList = (PsiModifierList)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkNotAllowedModifier(keyword, psiModifierList));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalModifierCombination(keyword, psiModifierList));
      PsiElement pParent = psiModifierList.getParent();
      if (PsiModifier.ABSTRACT.equals(text) && pParent instanceof PsiMethod) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkAbstractMethodInConcreteClass((PsiMethod)pParent, keyword));
        }
      }
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkStaticDeclarationInInnerClass(keyword));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalVoidType(keyword));
  }

  @Override
  public void visitLabeledStatement(PsiLabeledStatement statement) {
    super.visitLabeledStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkLabelWithoutStatement(statement));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkLabelAlreadyInUse(statement));
  }

  @Override
  public void visitLiteralExpression(PsiLiteralExpression expression) {
    super.visitLiteralExpression(expression);

    if (!myHolder.hasErrorResults() && expression.getParent() instanceof PsiCaseLabelElementList && expression.textMatches(PsiKeyword.NULL)) {
      myHolder.add(checkFeature(expression, HighlightingFeature.PATTERNS_IN_SWITCH));
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLiteralExpressionParsingError(expression, myLanguageLevel,myFile));
    }

    if (myRefCountHolder != null && !myHolder.hasErrorResults()) {
      registerReferencesFromInjectedFragments(expression);
    }

    if (myRefCountHolder != null && !myHolder.hasErrorResults()) {
      for (PsiReference reference : expression.getReferences()) {
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiMember) {
          myRefCountHolder.registerReference(reference, new CandidateInfo(resolve, PsiSubstitutor.EMPTY));
        }
      }
    }
  }

  @Override
  public void visitErrorElement(@NotNull PsiErrorElement element) {
    super.visitErrorElement(element);
    myHolder.add(HighlightClassUtil.checkClassMemberDeclaredOutside(element));
  }

  @Override
  public void visitMethod(PsiMethod method) {
    super.visitMethod(method);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(method.getBody()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorHandleSuperClassExceptions(method));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkRecursiveConstructorInvocation(method));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkSafeVarargsAnnotation(method, myLanguageLevel));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkRecordAccessorDeclaration(method));
    if (!myHolder.hasErrorResults()) myHolder.addAll(HighlightMethodUtil.checkRecordConstructorDeclaration(method));

    PsiClass aClass = method.getContainingClass();
    if (!myHolder.hasErrorResults() && method.isConstructor()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(method, aClass));
    }
    if (!myHolder.hasErrorResults() && method.hasModifierProperty(PsiModifier.DEFAULT)) {
      myHolder.add(checkFeature(method, HighlightingFeature.EXTENSION_METHODS));
    }
    if (!myHolder.hasErrorResults() && aClass != null && aClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
      myHolder.add(checkFeature(method, HighlightingFeature.EXTENSION_METHODS));
    }
    if (!myHolder.hasErrorResults() && aClass != null) {
      myHolder.add(HighlightMethodUtil.checkDuplicateMethod(aClass, method, getDuplicateMethods(aClass)));
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumSuperConstructorCall(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkSuperQualifierType(myFile.getProject(), expression));
    if (!myHolder.hasErrorResults()) {
      try {
        HighlightMethodUtil.checkMethodCall(expression, getResolveHelper(myHolder.getProject()), myLanguageLevel, myJavaSdkVersion, myFile, myHolder);
      }
      catch (IndexNotReadyException ignored) { }
    }

    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallProblems(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkSuperAbstractMethodDirectCall(expression));

    if (!myHolder.hasErrorResults()) visitExpression(expression);
    if (!myHolder.hasErrorResults()) {
      expression.accept(myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitModifierList(PsiModifierList list) {
    super.visitModifierList(list);
    PsiElement parent = list.getParent();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodCanHaveBody(method, myLanguageLevel));
      MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
      PsiClass aClass = method.getContainingClass();
      if (!method.isConstructor()) {
        try {
          List<HierarchicalMethodSignature> superMethodSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
          if (!superMethodSignatures.isEmpty()) {
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
              if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodWeakerPrivileges(methodSignature, superMethodSignatures, true, myFile));
              if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodOverridesFinal(methodSignature, superMethodSignatures));
            }
            if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, true));
            if (aClass != null && !myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodIncompatibleThrows(methodSignature, superMethodSignatures, true, aClass));
          }
        }
        catch (IndexNotReadyException ignored) { }
      }
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodMustHaveBody(method, aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallsBaseClassConstructor(method, myRefCountHolder, getResolveHelper(myHolder.getProject())));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkStaticMethodOverride(method,myFile));
      if (!myHolder.hasErrorResults() && aClass != null && myOverrideEquivalentMethodsVisitedClasses.add(aClass)) {
        myHolder.addAll(GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass));
      }
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      try {
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateNestedClass(aClass));
        if (!myHolder.hasErrorResults()) {
          TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
          myHolder.add(HighlightClassUtil.checkClassMustBeAbstract(aClass, textRange));
        }
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightClassUtil.checkClassDoesNotCallSuperConstructorOrHandleExceptions(aClass, myRefCountHolder, getResolveHelper(myHolder.getProject())));
        }
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkOverrideEquivalentInheritedMethods(aClass, myFile, myLanguageLevel));
        if (!myHolder.hasErrorResults() && myOverrideEquivalentMethodsVisitedClasses.add(aClass)) {
          myHolder.addAll(GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass));
        }
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCyclicInheritance(aClass));
      }
      catch (IndexNotReadyException ignored) {
      }
    }
    else if (parent instanceof PsiEnumConstant) {
      if (!myHolder.hasErrorResults()) myHolder.addAll(GenericsHighlightUtil.checkEnumConstantModifierList(list));
    }
  }

  @Override
  public void visitNameValuePair(PsiNameValuePair pair) {
    myHolder.add(AnnotationsHighlightUtil.checkNameValuePair(pair, myRefCountHolder));
    if (!myHolder.hasErrorResults()) {
      PsiIdentifier nameId = pair.getNameIdentifier();
      if (nameId != null) {
        HighlightInfo result = HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.ANNOTATION_ATTRIBUTE_NAME).range(nameId).create();
        myHolder.add(result);
      }
    }
  }

  @Override
  public void visitNewExpression(PsiNewExpression expression) {
    PsiType type = expression.getType();
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    myHolder.add(HighlightUtil.checkUnhandledExceptions(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAnonymousInheritFinal(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAnonymousInheritProhibited(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAnonymousSealedProhibited(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkQualifiedNew(expression, type, aClass));
    if (aClass != null && !myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(expression, type, aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParameterInstantiation(expression));
    if (aClass != null && !myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkInstantiationOfAbstractClass(aClass, expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression, aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, type));
    if (!myHolder.hasErrorResults()) registerConstructorCall(expression);
    try {
      if (!myHolder.hasErrorResults()) HighlightMethodUtil.checkNewExpression(expression, type, myHolder, myJavaSdkVersion);
    }
    catch (IndexNotReadyException ignored) { }

    if (!myHolder.hasErrorResults()) visitExpression(expression);

    if (!myHolder.hasErrorResults()) {
      expression.accept(myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitPackageStatement(PsiPackageStatement statement) {
    super.visitPackageStatement(statement);
    myHolder.add(AnnotationsHighlightUtil.checkPackageAnnotationContainingFile(statement, myFile));
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkPackageStatement(statement, myFile, myJavaModule));
    }
  }

  @Override
  public void visitRecordComponent(PsiRecordComponent recordComponent) {
    super.visitRecordComponent(recordComponent);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkRecordComponentVarArg(recordComponent));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkRecordComponentCStyleDeclaration(recordComponent));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkRecordComponentName(recordComponent));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkRecordComponentInitialized(recordComponent));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkRecordAccessorReturnType(recordComponent));
  }

  @Override
  public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);

    PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList && parameter.isVarArgs()) {
      if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(parameter, HighlightingFeature.VARARGS));
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkVarArgParameterIsLast(parameter));
    }
    else if (parent instanceof PsiCatchSection) {
      if (!myHolder.hasErrorResults() && parameter.getType() instanceof PsiDisjunctionType) {
        myHolder.add(checkFeature(parameter, HighlightingFeature.MULTI_CATCH));
      }
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkCatchParameterIsThrowable(parameter));
      if (!myHolder.hasErrorResults()) myHolder.addAll(GenericsHighlightUtil.checkCatchParameterIsClass(parameter));
      if (!myHolder.hasErrorResults()) myHolder.addAll(HighlightUtil.checkCatchTypeIsDisjoint(parameter));
    }
    else if (parent instanceof PsiForeachStatement) {
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkForEachParameterType((PsiForeachStatement)parent, parameter));
    }
  }

  @Override
  public void visitParameterList(PsiParameterList list) {
    super.visitParameterList(list);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAnnotationMethodParameters(list));
  }

  @Override
  public void visitUnaryExpression(PsiUnaryExpression expression) {
    super.visitUnaryExpression(expression);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
    }
  }

  private void registerConstructorCall(@NotNull PsiConstructorCall constructorCall) {
    if (myRefCountHolder != null) {
      JavaResolveResult resolveResult = constructorCall.resolveMethodGenerics();
      PsiElement resolved = resolveResult.getElement();
      if (resolved instanceof PsiNamedElement) {
        myRefCountHolder.registerLocallyReferenced((PsiNamedElement)resolved);
      }
    }
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = doVisitReferenceElement(ref);
    if (result != null) {
      PsiElement resolved = result.getElement();
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkRawOnParameterizedType(ref, resolved));
      if (!myHolder.hasErrorResults() && resolved instanceof PsiModifierListOwner) {
        ref.accept(myPreviewFeatureVisitor);
      }
      if (!myHolder.hasErrorResults()) {
        HighlightMethodUtil.checkAmbiguousConstructorCall(ref, resolved, ref.getParent(), myHolder, myJavaSdkVersion);
      }
    }
  }

  private JavaResolveResult doVisitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = resolveOptimised(ref, myFile);
    if (result == null) return null;

    PsiElement resolved = result.getElement();
    PsiElement parent = ref.getParent();

    if (myRefCountHolder != null) {
      myRefCountHolder.registerReference(ref, result);
    }

    myHolder.add(HighlightUtil.checkReference(ref, result, myFile, myLanguageLevel));

    if (parent instanceof PsiJavaCodeReferenceElement || ref.isQualified()) {
      if (!myHolder.hasErrorResults() && resolved instanceof PsiTypeParameter) {
        boolean canSelectFromTypeParameter = myJavaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7);
        if (canSelectFromTypeParameter) {
          PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
          if (containingClass != null) {
            if (PsiTreeUtil.isAncestor(containingClass.getExtendsList(), ref, false) ||
                PsiTreeUtil.isAncestor(containingClass.getImplementsList(), ref, false)) {
              canSelectFromTypeParameter = false;
            }
          }
        }
        if (!canSelectFromTypeParameter) {
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(
            JavaErrorBundle.message("cannot.select.from.a.type.parameter")).range(ref).create());
        }
      }
    }

    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAbstractInstantiation(ref));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkExtendsDuplicate(ref, resolved,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassExtendsForeignInnerClass(ref, resolved));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkSelectStaticClassFromParameterizedType(resolved, ref));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, ref, result.getSubstitutor(), myJavaSdkVersion));
    }

    if (resolved != null && parent instanceof PsiReferenceList) {
      if (!myHolder.hasErrorResults()) {
        PsiReferenceList referenceList = (PsiReferenceList)parent;
        myHolder.add(HighlightUtil.checkElementInReferenceList(ref, referenceList, result));
      }
    }

    if (parent instanceof PsiAnonymousClass && ref.equals(((PsiAnonymousClass)parent).getBaseClassReference())) {
      if (myOverrideEquivalentMethodsVisitedClasses.add((PsiClass)parent)) {
        PsiClass aClass = (PsiClass)parent;
        myHolder.addAll(GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass));
      }
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericCannotExtendException((PsiAnonymousClass)parent));
    }

    if (parent instanceof PsiNewExpression &&
        !(resolved instanceof PsiClass) &&
        resolved instanceof PsiNamedElement &&
        ((PsiNewExpression)parent).getClassOrAnonymousClassReference() == ref) {
      String text = JavaErrorBundle.message("cannot.resolve.symbol", ((PsiNamedElement)resolved).getName());
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(text).create();

       if (HighlightUtil.isCallToStaticMember(parent)) {
        QuickFixAction.registerQuickFixAction(info, new RemoveNewKeywordFix(parent));
      }

      myHolder.add(info);
    }

    if (!myHolder.hasErrorResults() && resolved instanceof PsiClass) {
      PsiClass aClass = ((PsiClass)resolved).getContainingClass();
      if (aClass != null) {
        PsiElement qualifier = ref.getQualifier();
        PsiElement place;
        if (qualifier instanceof PsiJavaCodeReferenceElement) {
          place = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        }
        else {
          if (parent instanceof PsiNewExpression) {
            PsiExpression newQualifier = ((PsiNewExpression)parent).getQualifier();
            place = newQualifier == null ? ref : PsiUtil.resolveClassInType(newQualifier.getType());
          }
          else {
            place = ref;
          }
        }
        if (place != null && PsiTreeUtil.isAncestor(aClass, place, false) && aClass.hasTypeParameters()) {
          myHolder.add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(ref, place, (PsiClass)resolved));
        }
      }
      else if (resolved instanceof PsiTypeParameter) {
        PsiTypeParameterListOwner owner = ((PsiTypeParameter)resolved).getOwner();
        if (owner instanceof PsiClass) {
          PsiClass outerClass = (PsiClass)owner;
          if (!InheritanceUtil.hasEnclosingInstanceInScope(outerClass, ref, false, false)) {
            myHolder.add(HighlightClassUtil.checkIllegalEnclosingUsage(ref, null, (PsiClass)owner, ref));
          }
        }
        else if (owner instanceof PsiMethod) {
          PsiClass cls = ClassUtils.getContainingStaticClass(ref);
          if (cls != null && PsiTreeUtil.isAncestor(owner, cls, true)) {
            String description = JavaErrorBundle.message("cannot.be.referenced.from.static.context", ref.getReferenceName());
            myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description).create());
          }
        }
      }
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkPackageAndClassConflict(ref, myFile));
    }
    if (!myHolder.hasErrorResults() && resolved instanceof PsiClass) {
      myHolder.add(HighlightUtil.checkRestrictedIdentifierReference(ref, (PsiClass)resolved, myLanguageLevel));
    }
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(ref, resolved, myFile, myInsideConstructorOfClass));
    }

    return result;
  }

  @Nullable
  static JavaResolveResult resolveOptimised(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiFile containingFile) {
    try {
      if (ref instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
        JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(ref, resolver, true, true, containingFile);
        return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
      }
      return ref.advancedResolve(true);
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  private JavaResolveResult @Nullable [] resolveOptimised(@NotNull PsiReferenceExpression expression) {
    try {
      if (expression instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
        return JavaResolveUtil.resolveWithContainingFile(expression, resolver, true, true, myFile);
      }
      else {
        return expression.multiResolve(true);
      }
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    JavaResolveResult resultForIncompleteCode = doVisitReferenceElement(expression);

    if (!myHolder.hasErrorResults()) {
      visitExpression(expression);
      if (myHolder.hasErrorResults()) return;
    }

    JavaResolveResult[] results = resolveOptimised(expression);
    if (results == null) return;
    JavaResolveResult result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;

    PsiElement resolved = result.getElement();
    if (resolved instanceof PsiVariable && resolved.getContainingFile() == expression.getContainingFile()) {
      PsiVariable variable = (PsiVariable)resolved;
      boolean isFinal = variable.hasModifierProperty(PsiModifier.FINAL);
      if (isFinal && !variable.hasInitializer() && !(variable instanceof PsiPatternVariable)) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, myFinalVarProblems));
        }
      }
      if (!myHolder.hasErrorResults()) {
        try {
          myHolder.add(HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, (PsiVariable)resolved, myUninitializedVarProblems,myFile));
        }
        catch (IndexNotReadyException ignored) { }
      }
      if (!myHolder.hasErrorResults() && resolved instanceof PsiLocalVariable) {
        myHolder.add(HighlightUtil.checkVarTypeSelfReferencing((PsiLocalVariable)resolved, expression));
      }
    }

    PsiElement parent = expression.getParent();
    if (parent instanceof PsiMethodCallExpression &&
        ((PsiMethodCallExpression)parent).getMethodExpression() == expression &&
        (!result.isAccessible() || !result.isStaticsScopeCorrect())) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent;
      PsiExpressionList list = methodCallExpression.getArgumentList();
      PsiResolveHelper resolveHelper = getResolveHelper(myHolder.getProject());
      if (!HighlightMethodUtil.isDummyConstructorCall(methodCallExpression, resolveHelper, list, expression)) {
        try {
          myHolder.add(HighlightMethodUtil.checkAmbiguousMethodCallIdentifier(
            expression, results, list, resolved, result, methodCallExpression, resolveHelper, myLanguageLevel, myFile));

          if (!PsiTreeUtil.findChildrenOfType(methodCallExpression.getArgumentList(), PsiLambdaExpression.class).isEmpty()) {
            PsiElement nameElement = expression.getReferenceNameElement();
            if (nameElement != null) {
              myHolder.add(HighlightMethodUtil.checkAmbiguousMethodCallArguments(
                expression, results, list, resolved, result, methodCallExpression, resolveHelper, nameElement));
            }
          }
        }
        catch (IndexNotReadyException ignored) { }
      }
    }

    if (!myHolder.hasErrorResults() && resultForIncompleteCode != null && HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(expression)) {
      myHolder.add(HighlightUtil.checkPatternVariableRequired(expression, resultForIncompleteCode));
    }

    if (!myHolder.hasErrorResults() && resultForIncompleteCode != null) {
      myHolder.add(HighlightUtil.checkExpressionRequired(expression, resultForIncompleteCode));
    }

    if (!myHolder.hasErrorResults() && resolved instanceof PsiField) {
      try {
        myHolder.add(HighlightUtil.checkIllegalForwardReferenceToField(expression, (PsiField)resolved));
      }
      catch (IndexNotReadyException ignored) { }
    }
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkAccessStaticFieldFromEnumConstructor(expression, result));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkClassReferenceAfterQualifier(expression, resolved));
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    myHolder.add(HighlightUtil.checkUnqualifiedSuperInDefaultMethod(myLanguageLevel, expression, qualifierExpression));
    if (!myHolder.hasErrorResults() && myJavaModule == null && qualifierExpression != null) {
      if (parent instanceof PsiMethodCallExpression) {
        PsiClass psiClass = RefactoringChangeUtil.getQualifierClass(expression);
        if (psiClass != null) {
          myHolder.add(GenericsHighlightUtil.checkClassSupersAccessibility(psiClass, expression, myFile.getResolveScope()));
        }
      }
      if (!myHolder.hasErrorResults()) {
        myHolder.add(GenericsHighlightUtil.checkMemberSignatureTypesAccessibility(expression));
      }
    }
    if (!myHolder.hasErrorResults() && resolved instanceof PsiModifierListOwner) {
      expression.accept(myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    myHolder.add(checkFeature(expression, HighlightingFeature.METHOD_REFERENCES));
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (toReportFunctionalExpressionProblemOnParent(parent)) return;
    PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (functionalInterfaceType != null && !PsiTypesUtil.allTypeParametersResolved(expression, functionalInterfaceType)) return;

    JavaResolveResult result;
    JavaResolveResult[] results;
    try {
      results = expression.multiResolve(true);
      result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    }
    catch (IndexNotReadyException e) {
      return;
    }
    if (myRefCountHolder != null) {
      myRefCountHolder.registerReference(expression, result);
    }
    PsiElement method = result.getElement();
    if (method instanceof PsiMethod && myRefCountHolder != null) {
      for (PsiParameter parameter : ((PsiMethod)method).getParameterList().getParameters()) {
        myRefCountHolder.registerLocallyReferenced(parameter);
      }
    }
    if (method instanceof PsiJvmMember && !result.isAccessible()) {
      String accessProblem = HighlightUtil.accessProblemDescription(expression, method, result);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(accessProblem).create();
      HighlightFixUtil.registerAccessQuickFixAction((PsiJvmMember)method, expression, info, result.getCurrentFileResolveScope(), null);
      myHolder.add(info);
    }

    if (!LambdaUtil.isValidLambdaContext(parent)) {
      String description = JavaErrorBundle.message("method.reference.expression.is.not.expected");
      myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create());
    }

    if (!myHolder.hasErrorResults()) {
      PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement instanceof PsiKeyword) {
        if (!PsiMethodReferenceUtil.isValidQualifier(expression)) {
          PsiElement qualifier = expression.getQualifier();
          if (qualifier != null) {
            String description = JavaErrorBundle.message("cannot.find.class", qualifier.getText());
            myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description).create());
          }
        }
      }
    }

    if (functionalInterfaceType != null) {
      if (!myHolder.hasErrorResults()) {
        myHolder.add(HighlightClassUtil.checkExtendsSealedClass(expression, functionalInterfaceType));
      }
      if (!myHolder.hasErrorResults()) {
        boolean isFunctional = LambdaUtil.isFunctionalType(functionalInterfaceType);
        if (!isFunctional) {
          String description =
            JavaErrorBundle.message("not.a.functional.interface", functionalInterfaceType.getPresentableText());
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create());
        }
      }
      if (!myHolder.hasErrorResults()) {
        checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType);
      }
      if (!myHolder.hasErrorResults()) {
        String errorMessage = PsiMethodReferenceHighlightingUtil.checkMethodReferenceContext(expression);
        if (errorMessage != null) {
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(errorMessage).create();
          if (method instanceof PsiMethod &&
              !((PsiMethod)method).isConstructor() &&
              !((PsiMethod)method).hasModifierProperty(PsiModifier.ABSTRACT)) {
            boolean shouldHave = !((PsiMethod)method).hasModifierProperty(PsiModifier.STATIC);
            QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories.createModifierActions(
                      (JvmModifiersOwner)method, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, shouldHave)));
          }
          myHolder.add(info);
        }
      }
    }

    if (!myHolder.hasErrorResults()) {
      PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiTypeElement) {
        PsiType psiType = ((PsiTypeElement)qualifier).getType();
        HighlightInfo genericArrayCreationInfo = GenericsHighlightUtil.checkGenericArrayCreation(qualifier, psiType);
        if (genericArrayCreationInfo != null) {
          myHolder.add(genericArrayCreationInfo);
        }
        else {
          String wildcardMessage = PsiMethodReferenceUtil.checkTypeArguments((PsiTypeElement)qualifier, psiType);
          if (wildcardMessage != null) {
            myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(wildcardMessage).create());
          }
        }
      }
    }

    if (method instanceof PsiMethod && ((PsiMethod)method).hasModifierProperty(PsiModifier.STATIC)) {
      if (!myHolder.hasErrorResults() && ((PsiMethod)method).hasTypeParameters()) {
        myHolder.add(GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(method, expression, result.getSubstitutor(), myJavaSdkVersion));
      }

      PsiClass containingClass = ((PsiMethod)method).getContainingClass();
      if (!myHolder.hasErrorResults() && containingClass != null && containingClass.isInterface()) {
        myHolder.add(HighlightMethodUtil.checkStaticInterfaceCallQualifier(expression, result, expression, containingClass));
      }
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(PsiMethodReferenceHighlightingUtil.checkRawConstructorReference(expression));
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnhandledExceptions(expression));
    }

    if (!myHolder.hasErrorResults()) {
      if (results.length == 0 || results[0] instanceof MethodCandidateInfo &&
                                 !((MethodCandidateInfo)results[0]).isApplicable() &&
                                 functionalInterfaceType != null || results.length > 1) {
        String description = null;
        if (results.length == 1) {
          description = ((MethodCandidateInfo)results[0]).getInferenceErrorMessage();
        }
        if (expression.isConstructor()) {
          PsiClass containingClass = PsiMethodReferenceUtil.getQualifierResolveResult(expression).getContainingClass();

          if (containingClass != null) {
            if (!myHolder.add(HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, expression)) &&
                !myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression, containingClass)) &&
                containingClass.isPhysical() &&
                description == null) {
              description = JavaErrorBundle.message("cannot.resolve.constructor", containingClass.getName());
            }
          }
        }
        else if (description == null){
          if (results.length > 1) {
            String t1 = HighlightUtil.format(Objects.requireNonNull(results[0].getElement()));
            String t2 = HighlightUtil.format(Objects.requireNonNull(results[1].getElement()));
            description = JavaErrorBundle.message("ambiguous.reference", expression.getReferenceName(), t1, t2);
          }
          else {
            description = JavaErrorBundle.message("cannot.resolve.method", expression.getReferenceName());
          }
        }

        if (description != null) {
          PsiElement referenceNameElement = ObjectUtils.notNull(expression.getReferenceNameElement(), expression);
          HighlightInfoType type = results.length == 0 ? HighlightInfoType.WRONG_REF : HighlightInfoType.ERROR;
          HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(type).descriptionAndTooltip(description).range(referenceNameElement).create();
          myHolder.add(highlightInfo);
          TextRange fixRange = HighlightMethodUtil.getFixRange(referenceNameElement);
          QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QuickFixFactory.getInstance().createCreateMethodFromUsageFix(expression));
        }
      }
    }

    if (!myHolder.hasErrorResults()) {
      String badReturnTypeMessage = PsiMethodReferenceUtil.checkReturnType(expression, result, functionalInterfaceType);
      if (badReturnTypeMessage != null) {
        HighlightInfo info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(badReturnTypeMessage).create();
        QuickFixAction.registerQuickFixAction(info, AdjustFunctionContextFix.createFix(expression));
        myHolder.add(info);
      }
    }
    if (!myHolder.hasErrorResults() && method instanceof PsiModifierListOwner) {
      expression.accept(myPreviewFeatureVisitor);
    }
  }

  /**
   * @return true for {@code functional_expression;} or {@code var l = functional_expression;}
   */
  private static boolean toReportFunctionalExpressionProblemOnParent(@Nullable PsiElement parent) {
    if (parent instanceof PsiLocalVariable) {
      return ((PsiLocalVariable)parent).getTypeElement().isInferredType();
    }
    return parent instanceof PsiExpressionStatement && !(parent.getParent() instanceof PsiSwitchLabeledRuleStatement);
  }

  // 15.13 | 15.27
  // It is a compile-time error if any class or interface mentioned by either U or the function type of U
  // is not accessible from the class or interface in which the method reference expression appears.
  private void checkFunctionalInterfaceTypeAccessible(@NotNull PsiFunctionalExpression expression, @NotNull PsiType functionalInterfaceType) {
    checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType, true);
  }
  private boolean checkFunctionalInterfaceTypeAccessible(@NotNull PsiFunctionalExpression expression,
                                                         @NotNull PsiType functionalInterfaceType,
                                                         boolean checkFunctionalTypeSignature) {
    PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(PsiClassImplUtil.correctType(functionalInterfaceType, expression.getResolveScope()));
    PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null) {
      return false;
    }
    if (PsiUtil.isAccessible(myFile.getProject(), psiClass, expression, null)) {
      for (PsiType type : resolveResult.getSubstitutor().getSubstitutionMap().values()) {
        if (type != null && checkFunctionalInterfaceTypeAccessible(expression, type, false)) return true;
      }

      PsiMethod psiMethod = checkFunctionalTypeSignature ? LambdaUtil.getFunctionalInterfaceMethod(resolveResult) : null;
      if (psiMethod != null) {
        PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(psiMethod, resolveResult);
        for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
          PsiType substitute = substitutor.substitute(parameter.getType());
          if (substitute != null && checkFunctionalInterfaceTypeAccessible(expression, substitute, false)) return true;
        }

        PsiType substitute = substitutor.substitute(psiMethod.getReturnType());
        return substitute != null && checkFunctionalInterfaceTypeAccessible(expression, substitute, false);
      }
    }
    else {
      Pair<@Nls String, List<IntentionAction>> problem = HighlightUtil.accessProblemDescriptionAndFixes(expression, psiClass, resolveResult);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(problem.first).create();
      myHolder.add(info);
      if (problem.second != null) {
        problem.second.forEach(fix -> QuickFixAction.registerQuickFixAction(info, fix));
      }
      return true;
    }
    return false;
  }

  @Override
  public void visitReferenceList(PsiReferenceList list) {
    if (list.getFirstChild() == null) return;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiTypeParameter)) {
      myHolder.add(AnnotationsHighlightUtil.checkAnnotationDeclaration(parent, list));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkExtendsAllowed(list));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkImplementsAllowed(list));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassExtendsOnlyOneClass(list));
      if (!myHolder.hasErrorResults()) HighlightClassUtil.checkPermitsList(list, myHolder);
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericCannotExtendException(list));
    }
  }

  @Override
  public void visitReferenceParameterList(PsiReferenceParameterList list) {
    if (list.getTextLength() == 0) return;

    myHolder.add(checkFeature(list, HighlightingFeature.GENERICS));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkParametersAllowed(list));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkParametersOnRaw(list, myLanguageLevel));
    if (!myHolder.hasErrorResults()) {
      for (PsiTypeElement typeElement : list.getTypeParameterElements()) {
        if (typeElement.getType() instanceof PsiDiamondType) {
          myHolder.add(checkFeature(list, HighlightingFeature.DIAMOND_TYPES));
        }
      }
    }
  }

  @Override
  public void visitReturnStatement(PsiReturnStatement statement) {
    super.visitStatement(statement);
    if (!myHolder.hasErrorResults() && HighlightingFeature.ENHANCED_SWITCH.isAvailable(myFile)) {
      myHolder.add(HighlightUtil.checkReturnFromSwitchExpr(statement));
    }
    if (!myHolder.hasErrorResults()) {
      try {
        PsiElement parent = PsiTreeUtil.getParentOfType(statement, PsiFile.class, PsiClassInitializer.class,
                                                        PsiLambdaExpression.class, PsiMethod.class);
        HighlightInfo info;
        if (parent instanceof PsiMethod && JavaPsiRecordUtil.isCompactConstructor((PsiMethod)parent)) {
          info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement)
            .descriptionAndTooltip(JavaErrorBundle.message("record.compact.constructor.return")).create();
        }
        else {
          info = parent != null ? HighlightUtil.checkReturnStatementType(statement, parent) : null;
          if (info != null && parent instanceof PsiMethod) {
            PsiMethod method = (PsiMethod)parent;
            PsiType expectedType = myExpectedReturnTypes.computeIfAbsent(method, HighlightMethodUtil::determineReturnType);
            if (expectedType != null && !PsiType.VOID.equals(expectedType))
              HighlightUtil.registerReturnTypeFixes(info, method, expectedType);
          }
        }
        myHolder.add(info);
      }
      catch (IndexNotReadyException ignore) { }
    }
  }

  @Override
  public void visitStatement(PsiStatement statement) {
    super.visitStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkNotAStatement(statement));
  }

  @Override
  public void visitSuperExpression(PsiSuperExpression expr) {
    myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier(), myLanguageLevel));
    if (!myHolder.hasErrorResults()) visitExpression(expr);
  }

  @Override
  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkCaseStatement(statement));
  }

  @Override
  public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
    super.visitSwitchLabeledRuleStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkCaseStatement(statement));
  }

  @Override
  public void visitSwitchStatement(PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    checkSwitchBlock(statement);
  }

  @Override
  public void visitSwitchExpression(PsiSwitchExpression expression) {
    super.visitSwitchExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(expression, HighlightingFeature.SWITCH_EXPRESSION));
    checkSwitchBlock(expression);
    if (!myHolder.hasErrorResults()) myHolder.addAll(HighlightUtil.checkSwitchExpressionReturnTypeCompatible(expression));
    if (!myHolder.hasErrorResults()) myHolder.addAll(HighlightUtil.checkSwitchExpressionHasResult(expression));
  }

  private void checkSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
    SwitchBlockHighlightingModel model = SwitchBlockHighlightingModel.createInstance(myLanguageLevel, switchBlock, myFile);
    if (model == null) return;
    if (!myHolder.hasErrorResults()) myHolder.addAll(model.checkSwitchBlockStatements());
    if (!myHolder.hasErrorResults()) myHolder.addAll(model.checkSwitchSelectorType());
    if (!myHolder.hasErrorResults()) myHolder.addAll(model.checkSwitchLabelValues());
  }

  @Override
  public void visitThisExpression(PsiThisExpression expr) {
    if (!(expr.getParent() instanceof PsiReceiverParameter)) {
      myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier(), myLanguageLevel));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expr, null, myFile, myInsideConstructorOfClass));
      if (!myHolder.hasErrorResults()) visitExpression(expr);
    }
  }

  @Override
  public void visitThrowStatement(PsiThrowStatement statement) {
    myHolder.add(HighlightUtil.checkUnhandledExceptions(statement));
    if (!myHolder.hasErrorResults()) visitStatement(statement);
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    super.visitTryStatement(statement);
    if (!myHolder.hasErrorResults()) {
      Set<PsiClassType> thrownTypes = HighlightUtil.collectUnhandledExceptions(statement);
      for (PsiParameter parameter : statement.getCatchBlockParameters()) {
        boolean added = myHolder.addAll(HighlightUtil.checkExceptionAlreadyCaught(parameter));
        if (!added) {
          added = myHolder.addAll(HighlightUtil.checkExceptionThrownInTry(parameter, thrownTypes));
        }
        if (!added) {
          myHolder.addAll(HighlightUtil.checkWithImprovedCatchAnalysis(parameter, thrownTypes, myFile));
        }
      }
    }
  }

  @Override
  public void visitResourceList(PsiResourceList resourceList) {
    super.visitResourceList(resourceList);
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(resourceList, HighlightingFeature.TRY_WITH_RESOURCES));
  }

  @Override
  public void visitResourceVariable(PsiResourceVariable resource) {
    super.visitResourceVariable(resource);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTryResourceIsAutoCloseable(resource));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnhandledCloserExceptions(resource));
  }

  @Override
  public void visitResourceExpression(PsiResourceExpression resource) {
    super.visitResourceExpression(resource);
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(resource, HighlightingFeature.REFS_AS_RESOURCE));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkResourceVariableIsFinal(resource));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTryResourceIsAutoCloseable(resource));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnhandledCloserExceptions(resource));
  }

  @Override
  public void visitTypeElement(PsiTypeElement type) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalType(type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVarTypeApplicability(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkReferenceTypeUsedAsTypeArgument(type, myLanguageLevel));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkWildcardUsage(type));
    if (!myHolder.hasErrorResults()) type.accept(myPreviewFeatureVisitor);
  }

  @Override
  public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
    super.visitTypeCastExpression(typeCast);
    try {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIntersectionInTypeCast(typeCast, myLanguageLevel, myFile));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInconvertibleTypeCast(typeCast));
    }
    catch (IndexNotReadyException ignored) { }
  }

  @Override
  public void visitTypeParameterList(PsiTypeParameterList list) {
    PsiTypeParameter[] typeParameters = list.getTypeParameters();
    if (typeParameters.length > 0) {
      myHolder.add(checkFeature(list, HighlightingFeature.GENERICS));
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParametersList(list, typeParameters, myLanguageLevel));
    }
  }

  @Override
  public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);
    if (variable instanceof PsiPatternVariable) {
      myHolder.add(checkFeature(((PsiPatternVariable)variable).getNameIdentifier(), HighlightingFeature.PATTERNS));
    }
    try {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVarTypeApplicability(variable));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableInitializerType(variable));
    }
    catch (IndexNotReadyException ignored) { }
  }

  @Override
  public void visitConditionalExpression(PsiConditionalExpression expression) {
    super.visitConditionalExpression(expression);
    if (!myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) || !PsiPolyExpressionUtil.isPolyExpression(expression)) return;
    PsiExpression thenExpression = expression.getThenExpression();
    PsiExpression elseExpression = expression.getElseExpression();
    if (thenExpression == null || elseExpression == null) return;
    PsiType conditionalType = expression.getType();
    if (conditionalType == null) return;
    PsiExpression[] sides = {thenExpression, elseExpression};
    PsiType expectedType = null;
    boolean isExpectedTypeComputed = false;
    for (int i = 0; i < sides.length; i++) {
      PsiExpression side = sides[i];
      PsiExpression otherSide = i == 0 ? sides[1] : sides[0];
      PsiType sideType = side.getType();
      if (sideType != null && !TypeConversionUtil.isAssignable(conditionalType, sideType)) {
        HighlightInfo info = HighlightUtil.checkAssignability(conditionalType, sideType, side, side);
        if (info == null) continue;
        if (!TypeConversionUtil.isVoidType(sideType)) {
          if (TypeConversionUtil.isVoidType(otherSide.getType())) {
            HighlightUtil.registerChangeTypeFix(info, expression, sideType);
          }
          else {
            if (!isExpectedTypeComputed) {
              PsiElementFactory factory = PsiElementFactory.getInstance(expression.getProject());
              PsiExpression expressionCopy = factory.createExpressionFromText(expression.getText(), expression);
              expectedType = expressionCopy.getType();
              isExpectedTypeComputed = true;
            }
            if (expectedType != null) {
              HighlightUtil.registerChangeTypeFix(info, expression, expectedType);
            }
          }
        }
        myHolder.add(info);
      }
    }
  }

  @Override
  public void visitReceiverParameter(PsiReceiverParameter parameter) {
    super.visitReceiverParameter(parameter);
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(parameter, HighlightingFeature.RECEIVERS));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkReceiverPlacement(parameter));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkReceiverType(parameter));
  }

  @Override
  public void visitModule(PsiJavaModule module) {
    super.visitModule(module);
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(module, HighlightingFeature.MODULES));
    if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkFileName(module, myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkFileDuplicates(module, myFile));
    if (!myHolder.hasErrorResults()) myHolder.addAll(ModuleHighlightUtil.checkDuplicateStatements(module));
    if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkClashingReads(module));
    if (!myHolder.hasErrorResults()) myHolder.addAll(ModuleHighlightUtil.checkUnusedServices(module, myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkFileLocation(module, myFile));
  }

  @Override
  public void visitModuleStatement(PsiStatement statement) {
    super.visitModuleStatement(statement);
    if (!myHolder.hasErrorResults()) statement.accept(myPreviewFeatureVisitor);
  }

  @Override
  public void visitRequiresStatement(PsiRequiresStatement statement) {
    super.visitRequiresStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkModuleReference(statement));
      if (!myHolder.hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_10)) {
        myHolder.addAll(ModuleHighlightUtil.checkModifiers(statement));
      }
    }
  }

  @Override
  public void visitPackageAccessibilityStatement(PsiPackageAccessibilityStatement statement) {
    super.visitPackageAccessibilityStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkHostModuleStrength(statement));
      if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkPackageReference(statement, myFile));
      if (!myHolder.hasErrorResults()) myHolder.addAll(ModuleHighlightUtil.checkPackageAccessTargets(statement));
    }
  }

  @Override
  public void visitUsesStatement(PsiUsesStatement statement) {
    super.visitUsesStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkServiceReference(statement.getClassReference()));
    }
  }

  @Override
  public void visitProvidesStatement(PsiProvidesStatement statement) {
    super.visitProvidesStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) myHolder.addAll(ModuleHighlightUtil.checkServiceImplementations(statement, myFile));
    }
  }

  @Override
  public void visitDefaultCaseLabelElement(PsiDefaultCaseLabelElement element) {
    super.visitDefaultCaseLabelElement(element);
    myHolder.add(checkFeature(element, HighlightingFeature.PATTERNS_IN_SWITCH));
  }

  @Override
  public void visitParenthesizedPattern(PsiParenthesizedPattern pattern) {
    super.visitParenthesizedPattern(pattern);
    myHolder.add(checkFeature(pattern, HighlightingFeature.GUARDED_AND_PARENTHESIZED_PATTERNS));
  }

  @Override
  public void visitGuardedPattern(PsiGuardedPattern pattern) {
    super.visitGuardedPattern(pattern);
    myHolder.add(checkFeature(pattern, HighlightingFeature.GUARDED_AND_PARENTHESIZED_PATTERNS));
    if (myHolder.hasErrorResults()) return;
    PsiExpression guardingExpr = pattern.getGuardingExpression();
    if (guardingExpr == null) return;
    // 14.30.1 Kinds of Patterns GuardedPattern: PrimaryPattern && ConditionalAndExpression
    // 15.23. ConditionalAndExpression: Each operand of the conditional-and operator must be of type boolean or Boolean, or a compile-time error occurs.
    if (!TypeConversionUtil.isBooleanType(guardingExpr.getType())) {
      String message = JavaErrorBundle.message("incompatible.types", JavaHighlightUtil.formatType(PsiType.BOOLEAN),
                                               JavaHighlightUtil.formatType(guardingExpr.getType()));
      myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(guardingExpr).descriptionAndTooltip(message).create());
    }
  }

  @Override
  public void visitTypeTestPattern(PsiTypeTestPattern pattern) {
    super.visitTypeTestPattern(pattern);
    if (pattern.getParent() instanceof PsiCaseLabelElementList) {
      myHolder.add(checkFeature(pattern, HighlightingFeature.PATTERNS_IN_SWITCH));
    }
  }

  private HighlightInfo checkFeature(@NotNull PsiElement element, @NotNull HighlightingFeature feature) {
    return HighlightUtil.checkFeature(element, feature, myLanguageLevel, myFile);
  }

  private static class PreviewFeatureVisitor extends PreviewFeatureVisitorBase {
    private final LanguageLevel myLanguageLevel;
    private final HighlightInfoHolder myHolder;

    private PreviewFeatureVisitor(LanguageLevel level, HighlightInfoHolder holder) {
      myLanguageLevel = level;
      myHolder = holder;
    }

    @Override
    protected void registerProblem(PsiElement element, String description, HighlightingFeature feature, PsiAnnotation annotation) {
      boolean isReflective = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "reflective"));

      HighlightInfoType type = isReflective ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;

      HighlightInfo highlightInfo = HighlightUtil.checkFeature(element, feature, myLanguageLevel, element.getContainingFile(), description, type);
      myHolder.add(highlightInfo);
    }
  }
}
