// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.AdjustFunctionContextFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.reference.PsiMemberReference;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.psi.PsiModifier.SEALED;
import static com.intellij.util.ObjectUtils.tryCast;

// java highlighting: problems in java code like unresolved/incompatible symbols/methods etc.
public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor {
  private @NotNull HighlightInfoHolder myHolder;
  private RefCountHolder myRefCountHolder; // can be null during partial file update
  private @NotNull LanguageLevel myLanguageLevel;
  private JavaSdkVersion myJavaSdkVersion;

  private @NotNull PsiFile myFile;
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

  @NotNull
  protected PsiResolveHelper getResolveHelper(@NotNull Project project) {
    return PsiResolveHelper.getInstance(project);
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
  @Deprecated(forRemoval = true)
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
    element.accept(this);
  }

  private void registerReferencesFromInjectedFragments(@NotNull PsiElement element) {
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFile.getProject());
    manager.enumerateEx(element, myFile, false, (injectedPsi, places) -> {
      if (InjectedLanguageJavaReferenceSupplier.doRegisterReferences(injectedPsi.getLanguage().getID())) {
        injectedPsi.accept(REGISTER_REFERENCES_VISITOR);
      }
    });
  }

  @Override
  public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable highlight) {
    try {
      prepare(holder, file);
      if (updateWholeFile) {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        GlobalInspectionContextBase.assertUnderDaemonProgress();
        Project project = file.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        TextRange dirtyScope = document == null ? null : DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap().getFileDirtyScope(document, file, Pass.UPDATE_ALL);
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
      add(DefaultHighlightUtil.checkUnicodeBadCharacter(element));
    }
  }

  @Nullable
  public static JavaResolveResult resolveJavaReference(@NotNull PsiReference reference) {
    return reference instanceof PsiJavaReference ? ((PsiJavaReference)reference).advancedResolve(false) : null;
  }

  @Override
  public void visitAnnotation(@NotNull PsiAnnotation annotation) {
    super.visitAnnotation(annotation);
    if (!myHolder.hasErrorResults()) add(checkFeature(annotation, HighlightingFeature.ANNOTATIONS));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkApplicability(annotation, myLanguageLevel, myFile));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkAnnotationType(annotation));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkMissingAttributes(annotation));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkTargetAnnotationDuplicates(annotation));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkDuplicateAnnotations(annotation, myLanguageLevel));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkFunctionalInterface(annotation, myLanguageLevel));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkInvalidAnnotationOnRecordComponent(annotation));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkRepeatableAnnotation(annotation));
    if (CommonClassNames.JAVA_LANG_OVERRIDE.equals(annotation.getQualifiedName())) {
      PsiAnnotationOwner owner = annotation.getOwner();
      PsiElement parent = owner instanceof PsiModifierList ? ((PsiModifierList)owner).getParent() : null;
      if (parent instanceof PsiMethod) {
        add(GenericsHighlightUtil.checkOverrideAnnotation((PsiMethod)parent, annotation, myLanguageLevel));
      }
    }
  }
  private boolean add(@Nullable HighlightInfo.Builder builder) {
    if (builder != null) {
      return myHolder.add(builder.create());
    }
    return false;
  }

  @Override
  public void visitAnnotationArrayInitializer(@NotNull PsiArrayInitializerMemberValue initializer) {
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
          add(AnnotationsHighlightUtil.checkMemberValueType(initializer1, type, method));
        }
      }
    }
  }

  @Override
  public void visitAnnotationMethod(@NotNull PsiAnnotationMethod method) {
    PsiType returnType = method.getReturnType();
    PsiAnnotationMemberValue value = method.getDefaultValue();
    if (returnType != null && value != null) {
      add(AnnotationsHighlightUtil.checkMemberValueType(value, returnType, method));
    }

    PsiTypeElement typeElement = method.getReturnTypeElement();
    if (typeElement != null) {
      add(AnnotationsHighlightUtil.checkValidAnnotationType(returnType, typeElement));
    }

    PsiClass aClass = method.getContainingClass();
    if (typeElement != null && aClass != null) {
      add(AnnotationsHighlightUtil.checkCyclicMemberType(typeElement, aClass));
    }

    add(AnnotationsHighlightUtil.checkClashesWithSuperMethods(method));

    if (!myHolder.hasErrorResults() && aClass != null) {
      add(HighlightMethodUtil.checkDuplicateMethod(aClass, method, getDuplicateMethods(aClass)));
    }
  }

  @Override
  public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expression) {
    super.visitArrayInitializerExpression(expression);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkArrayInitializerApplicable(expression));
    if (!(expression.getParent() instanceof PsiNewExpression)) {
      if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()));
    }
  }

  @Override
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkAssignmentCompatibleTypes(assignment));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkAssignmentOperatorApplicable(assignment));
    if (!myHolder.hasErrorResults()) visitExpression(assignment);
  }

  @Override
  public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
    super.visitPolyadicExpression(expression);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkPolyadicOperatorApplicable(expression));
  }

  @Override
  public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
    add(checkFeature(expression, HighlightingFeature.LAMBDA_EXPRESSIONS));
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (toReportFunctionalExpressionProblemOnParent(parent)) return;
    if (!myHolder.hasErrorResults() && !LambdaUtil.isValidLambdaContext(parent)) {
      add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                     .descriptionAndTooltip(JavaErrorBundle.message("lambda.expression.not.expected")));
    }

    if (!myHolder.hasErrorResults()) add(LambdaHighlightingUtil.checkConsistentParameterDeclaration(expression));

    PsiType functionalInterfaceType = null;
    if (!myHolder.hasErrorResults()) {
      functionalInterfaceType = expression.getFunctionalInterfaceType();
      if (functionalInterfaceType != null) {
        add(HighlightClassUtil.checkExtendsSealedClass(expression, functionalInterfaceType));
        if (!myHolder.hasErrorResults()) {
          String notFunctionalMessage = LambdaHighlightingUtil.checkInterfaceFunctional(functionalInterfaceType);
          if (notFunctionalMessage != null) {
            add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                           .descriptionAndTooltip(notFunctionalMessage));
          }
          else {
            checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType);
          }
        }
      }
      else if (LambdaUtil.getFunctionalInterfaceType(expression, true) != null) {
        add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(
          JavaErrorBundle.message("cannot.infer.functional.interface.type")));
      }
    }

    if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
      PsiCallExpression callExpression = parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression ?
                                               (PsiCallExpression)parent.getParent() : null;
      MethodCandidateInfo parentCallResolveResult =
        callExpression != null ? tryCast(callExpression.resolveMethodGenerics(), MethodCandidateInfo.class) : null;
      String parentInferenceErrorMessage = parentCallResolveResult != null ? parentCallResolveResult.getInferenceErrorMessage() : null;
      PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
      Map<PsiElement, @Nls String> returnErrors = null;
      Set<PsiTypeParameter> parentTypeParameters = parentCallResolveResult == null ? Set.of() : Set.of(parentCallResolveResult.getElement().getTypeParameters());
      // If return type of the lambda was not fully inferred and lambda parameters don't mention the same type,
      // it means that lambda is not responsible for inference failure and blaming it would be unreasonable.
      boolean skipReturnCompatibility = parentCallResolveResult != null &&
                                        PsiTypesUtil.mentionsTypeParameters(returnType, parentTypeParameters)
                                        && !lambdaParametersMentionTypeParameter(functionalInterfaceType, parentTypeParameters);
      if (!skipReturnCompatibility) {
        returnErrors = LambdaUtil.checkReturnTypeCompatible(expression, returnType);
      }
      if (parentInferenceErrorMessage != null && (returnErrors == null || !returnErrors.containsValue(parentInferenceErrorMessage))) {
        if (returnErrors == null) return;
        HighlightInfo.Builder info = HighlightMethodUtil.createIncompatibleTypeHighlightInfo(callExpression, getResolveHelper(myHolder.getProject()),
                                                                                             parentCallResolveResult, expression);
        if (info != null) {
          returnErrors.keySet().forEach(k -> {
            IntentionAction action = AdjustFunctionContextFix.createFix(k);
            if (action != null) {
              info.registerFix(action, null, null, null, null);
            }
          });
          add(info);
        }
      }
      else if (returnErrors != null) {
        for (Map.Entry<PsiElement, @Nls String> entry : returnErrors.entrySet()) {
          PsiElement element = entry.getKey();
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(element)
            .descriptionAndTooltip(entry.getValue());
          IntentionAction action = AdjustFunctionContextFix.createFix(element);
          if (action != null) {
            info.registerFix(action, null, null, null, null);
          }
          if (element instanceof PsiExpression expr) {
            HighlightFixUtil.registerLambdaReturnTypeFixes(info, element.getTextRange(), expression, expr);
          }
          add(info);
        }
      }
    }

    if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
      PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      if (interfaceMethod != null) {
        PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
        add(LambdaHighlightingUtil.checkParametersCompatible(expression, parameters, LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)));
      }
    }

    if (!myHolder.hasErrorResults()) {
      PsiElement body = expression.getBody();
      if (body instanceof PsiCodeBlock) {
        add(HighlightControlFlowUtil.checkUnreachableStatement((PsiCodeBlock)body));
      }
    }
  }

  private static boolean lambdaParametersMentionTypeParameter(PsiType functionalInterfaceType, Set<PsiTypeParameter> parameters) {
    if (!(functionalInterfaceType instanceof PsiClassType classType)) return false;
    PsiSubstitutor substitutor = classType.resolveGenerics().getSubstitutor();
    PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (method == null) return false;
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      if (PsiTypesUtil.mentionsTypeParameters(substitutor.substitute(parameter.getType()), parameters)) return true;
    }
    return false;
  }

  @Override
  public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
    super.visitBreakStatement(statement);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkBreakTarget(statement, myLanguageLevel));
  }

  @Override
  public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
    super.visitYieldStatement(statement);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkYieldOutsideSwitchExpression(statement));
    if (!myHolder.hasErrorResults()) {
      PsiExpression expression = statement.getExpression();
      if (expression != null) {
        add(HighlightUtil.checkYieldExpressionType(expression));
      }
    }
  }

  @Override
  public void visitExpressionStatement(@NotNull PsiExpressionStatement statement) {
    super.visitExpressionStatement(statement);
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiSwitchLabeledRuleStatement) {
      PsiSwitchBlock switchBlock = ((PsiSwitchLabeledRuleStatement)parent).getEnclosingSwitchBlock();
      if (switchBlock instanceof PsiSwitchExpression && !PsiPolyExpressionUtil.isPolyExpression((PsiExpression)switchBlock)) {
        add(HighlightUtil.checkYieldExpressionType(statement.getExpression()));
      }
    }
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    super.visitClass(aClass);
    if (aClass instanceof PsiSyntheticClass) return;
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkInterfaceMultipleInheritance(aClass));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkClassSupersAccessibility(aClass));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkDuplicateTopLevelClass(aClass));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkMustNotBeLocal(aClass));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkImplicitThisReferenceBeforeSuper(aClass, myJavaSdkVersion));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkClassAndPackageConflict(aClass));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkPublicClassInRightFile(aClass));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkWellFormedRecord(aClass));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkSealedClassInheritors(aClass));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkSealedSuper(aClass));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkTypeParameterOverrideEquivalentMethods(aClass, myLanguageLevel));
  }

  @Override
  public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkIllegalInstanceMemberInRecord(initializer));
    if (!myHolder.hasErrorResults()) add(HighlightControlFlowUtil.checkInitializerCompleteNormally(initializer));
    if (!myHolder.hasErrorResults()) add(HighlightControlFlowUtil.checkUnreachableStatement(initializer.getBody()));
    if (!myHolder.hasErrorResults()) {
      add(HighlightClassUtil.checkThingNotAllowedInInterface(initializer, initializer.getContainingClass()));
    }
  }

  @Override
  public void visitClassObjectAccessExpression(@NotNull PsiClassObjectAccessExpression expression) {
    super.visitClassObjectAccessExpression(expression);
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkClassObjectAccessExpression(expression));
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    super.visitComment(comment);
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkShebangComment(comment));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkUnclosedComment(comment));
    if (!myHolder.hasErrorResults()) HighlightUtil.checkIllegalUnicodeEscapes(comment, myHolder);
    if (myRefCountHolder != null && !myHolder.hasErrorResults()) registerReferencesFromInjectedFragments(comment);
  }

  @Override
  public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
    super.visitContinueStatement(statement);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkContinueTarget(statement, myLanguageLevel));
  }

  @Override
  public void visitJavaToken(@NotNull PsiJavaToken token) {
    super.visitJavaToken(token);

    IElementType type = token.getTokenType();
    if (!myHolder.hasErrorResults() && type == JavaTokenType.TEXT_BLOCK_LITERAL) {
      add(checkFeature(token, HighlightingFeature.TEXT_BLOCKS));
    }

    if (!myHolder.hasErrorResults() && type == JavaTokenType.RBRACE && token.getParent() instanceof PsiCodeBlock) {
      PsiElement gParent = token.getParent().getParent();
      PsiCodeBlock codeBlock;
      PsiType returnType;
      if (gParent instanceof PsiMethod method) {
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
      add(HighlightControlFlowUtil.checkMissingReturnStatement(codeBlock, returnType));
    }
  }

  @Override
  public void visitDocComment(@NotNull PsiDocComment comment) {
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkUnclosedComment(comment));
    if (!myHolder.hasErrorResults()) HighlightUtil.checkIllegalUnicodeEscapes(comment, myHolder);
  }

  @Override
  public void visitEnumConstant(@NotNull PsiEnumConstant enumConstant) {
    super.visitEnumConstant(enumConstant);
    if (!myHolder.hasErrorResults()) GenericsHighlightUtil.checkEnumConstantForConstructorProblems(enumConstant, myHolder, myJavaSdkVersion);
    if (!myHolder.hasErrorResults()) registerConstructorCall(enumConstant);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkUnhandledExceptions(enumConstant));
  }

  @Override
  public void visitEnumConstantInitializer(@NotNull PsiEnumConstantInitializer enumConstantInitializer) {
    super.visitEnumConstantInitializer(enumConstantInitializer);
    if (!myHolder.hasErrorResults()) {
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(enumConstantInitializer);
      add(HighlightClassUtil.checkClassMustBeAbstract(enumConstantInitializer, textRange));
    }
  }

  @Override
  public void visitExpression(@NotNull PsiExpression expression) {
    ProgressManager.checkCanceled(); // visitLiteralExpression is invoked very often in array initializers
    super.visitExpression(expression);

    PsiElement parent = expression.getParent();
    // Method expression of the call should not be especially processed
    if (parent instanceof PsiMethodCallExpression) return;
    PsiType type = expression.getType();

    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkMustBeBoolean(expression, type));
    if (!myHolder.hasErrorResults() && expression instanceof PsiArrayAccessExpression) {
      add(HighlightUtil.checkValidArrayAccessExpression((PsiArrayAccessExpression)expression));
    }
    if (!myHolder.hasErrorResults() && parent instanceof PsiNewExpression &&
        ((PsiNewExpression)parent).getQualifier() != expression && ((PsiNewExpression)parent).getArrayInitializer() != expression) {
      add(HighlightUtil.checkAssignability(PsiTypes.intType(), expression.getType(), expression, expression));  // like in 'new String["s"]'
    }
    if (!myHolder.hasErrorResults()) add(HighlightControlFlowUtil.checkCannotWriteToFinal(expression,myFile));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkVariableExpected(expression));
    if (!myHolder.hasErrorResults()) HighlightUtil.checkArrayInitializer(expression, type, myHolder);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkTernaryOperatorConditionIsBoolean(expression, type));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkAssertOperatorTypes(expression, type));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkSynchronizedExpressionType(expression, type, myFile));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkConditionalExpressionBranchTypesMatch(expression, type));
    if (!myHolder.hasErrorResults() && parent instanceof PsiThrowStatement && ((PsiThrowStatement)parent).getException() == expression && type != null) {
      add(HighlightUtil.checkMustBeThrowable(type, expression, true));
    }
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkConstantExpression(expression));
    if (!myHolder.hasErrorResults() && shouldReportForeachNotApplicable(expression)) {
      add(GenericsHighlightUtil.checkForeachExpressionTypeIsIterable(expression));
    }
  }

  private static boolean shouldReportForeachNotApplicable(@NotNull PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiForeachStatementBase parentForEach)) return false;

    PsiExpression iteratedValue = parentForEach.getIteratedValue();
    if (iteratedValue != expression) return false;

    // Ignore if the type of the value which is being iterated over is not resolved yet
    PsiType iteratedValueType = iteratedValue.getType();
    return iteratedValueType == null || !PsiTypesUtil.hasUnresolvedComponents(iteratedValueType);
  }

  @Override
  public void visitExpressionList(@NotNull PsiExpressionList list) {
    super.visitExpressionList(list);
    PsiElement parent = list.getParent();
    if (parent instanceof PsiMethodCallExpression expression && expression.getArgumentList() == list) {
      PsiReferenceExpression referenceExpression = expression.getMethodExpression();
      JavaResolveResult[] results = resolveOptimised(referenceExpression);
      if (results == null) return;
      JavaResolveResult result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;

      if ((!result.isAccessible() || !result.isStaticsScopeCorrect()) &&
          !HighlightMethodUtil.isDummyConstructorCall(expression, getResolveHelper(myHolder.getProject()), list, referenceExpression) &&
          // this check is for fake expression from JspMethodCallImpl
          referenceExpression.getParent() == expression) {
        try {
          if (PsiTreeUtil.findChildrenOfType(expression.getArgumentList(), PsiLambdaExpression.class).isEmpty()) {
            PsiElement resolved = result.getElement();
            add(HighlightMethodUtil.checkAmbiguousMethodCallArguments(referenceExpression, results, list, resolved, result, expression,
                                                                      getResolveHelper(myHolder.getProject()), list));
          }
        }
        catch (IndexNotReadyException ignored) {
        }
      }
    }
  }

  @Override
  public void visitField(@NotNull PsiField field) {
    super.visitField(field);
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkIllegalInstanceMemberInRecord(field));
    if (!myHolder.hasErrorResults()) add(HighlightControlFlowUtil.checkFinalFieldInitialized(field));
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    add(HighlightUtil.checkForStatement(statement));
  }

  @Override
  public void visitImportStaticStatement(@NotNull PsiImportStaticStatement statement) {
    add(checkFeature(statement, HighlightingFeature.STATIC_IMPORTS));
    if (!myHolder.hasErrorResults()) add(ImportsHighlightUtil.checkStaticOnDemandImportResolvesToClass(statement));
    if (!myHolder.hasErrorResults()) {
      PsiJavaCodeReferenceElement importReference = statement.getImportReference();
      PsiClass targetClass = statement.resolveTargetClass();
      if (importReference != null) {
        PsiElement referenceNameElement = importReference.getReferenceNameElement();
        if (referenceNameElement != null && targetClass != null) {
          add(GenericsHighlightUtil.checkClassSupersAccessibility(targetClass, referenceNameElement, myFile.getResolveScope()));
        }
      }
      if (!myHolder.hasErrorResults()) {
        statement.accept(myPreviewFeatureVisitor);
      }
    }
  }

  @Override
  public void visitIdentifier(@NotNull PsiIdentifier identifier) {
    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable variable) {
      add(HighlightUtil.checkVariableAlreadyDefined(variable));

      if (variable.getInitializer() == null) {
        PsiElement child = variable.getLastChild();
        if (child instanceof PsiErrorElement && child.getPrevSibling() == identifier) return;
      }
    }
    else if (parent instanceof PsiClass aClass) {
      if (aClass.isAnnotationType()) {
        add(checkFeature(identifier, HighlightingFeature.ANNOTATIONS));
      }

      add(HighlightClassUtil.checkClassAlreadyImported(aClass, identifier));
      if (!myHolder.hasErrorResults()) {
        add(HighlightClassUtil.checkClassRestrictedKeyword(myLanguageLevel, identifier));
      }
      if (!myHolder.hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        add(GenericsHighlightUtil.checkUnrelatedDefaultMethods(aClass, identifier));
      }

      if (!myHolder.hasErrorResults()) {
        add(GenericsHighlightUtil.checkUnrelatedConcrete(aClass, identifier));
      }
    }
    else if (parent instanceof PsiMethod method) {
      if (method.isConstructor()) {
        HighlightInfo.Builder info = HighlightMethodUtil.checkConstructorName(method);
        if (info != null) {
          PsiType expectedType = myExpectedReturnTypes.computeIfAbsent(method, HighlightMethodUtil::determineReturnType);
          if (expectedType != null) {
            HighlightUtil.registerReturnTypeFixes(info, method, expectedType);
          }
        }
        add(info);
      }
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        add(GenericsHighlightUtil.checkDefaultMethodOverridesMemberOfJavaLangObject(myLanguageLevel, aClass, method, identifier));
      }
    }

    add(HighlightUtil.checkUnderscore(identifier, myLanguageLevel));

    super.visitIdentifier(identifier);
  }

  @Override
  public void visitImportStatement(@NotNull PsiImportStatement statement) {
    if (!myHolder.hasErrorResults()) {
      add(HighlightUtil.checkSingleImportClassConflict(statement, mySingleImportedClasses,myFile));
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
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(referenceNameElement).descriptionAndTooltip(description);
      add(info);
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
          add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description));
        }
      }
    }
    if (!myHolder.hasErrorResults() && results.length == 1) {
      add(HighlightUtil.checkReference(ref, results[0], myFile, myLanguageLevel));
      if (!myHolder.hasErrorResults()) {
        PsiElement element = results[0].getElement();
        PsiClass containingClass = element instanceof PsiMethod ? ((PsiMethod)element).getContainingClass() : null;
        if (containingClass != null && containingClass.isInterface()) {
          add(HighlightMethodUtil.checkStaticInterfaceCallQualifier(ref, results[0], ObjectUtils.notNull(ref.getReferenceNameElement(), ref), containingClass));
        }
      }
    }
  }

  @Override
  public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    if (!myHolder.hasErrorResults()) HighlightUtil.checkInstanceOfApplicable(expression, myHolder);
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkInstanceOfGenericType(myLanguageLevel, expression));
    if (!myHolder.hasErrorResults() &&
        myLanguageLevel.isAtLeast(LanguageLevel.JDK_16) &&
        // 5.20.2 Removed restriction on pattern instanceof for unconditional patterns (JEP 427)
        myLanguageLevel.isLessThan(LanguageLevel.JDK_19_PREVIEW)) {
      add(HighlightUtil.checkInstanceOfPatternSupertype(expression));
    }
  }

  @Override
  public void visitKeyword(@NotNull PsiKeyword keyword) {
    super.visitKeyword(keyword);
    PsiElement parent = keyword.getParent();
    String text = keyword.getText();
    if (parent instanceof PsiModifierList psiModifierList) {
      if (!myHolder.hasErrorResults()) add(HighlightUtil.checkNotAllowedModifier(keyword, psiModifierList));
      if (!myHolder.hasErrorResults()) add(HighlightUtil.checkIllegalModifierCombination(keyword, psiModifierList));
      PsiElement pParent = psiModifierList.getParent();
      if (PsiModifier.ABSTRACT.equals(text) && pParent instanceof PsiMethod) {
        if (!myHolder.hasErrorResults()) {
          add(HighlightMethodUtil.checkAbstractMethodInConcreteClass((PsiMethod)pParent, keyword));
        }
      }
    }
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkStaticDeclarationInInnerClass(keyword));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkIllegalVoidType(keyword));
  }

  @Override
  public void visitLabeledStatement(@NotNull PsiLabeledStatement statement) {
    super.visitLabeledStatement(statement);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkLabelWithoutStatement(statement));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkLabelAlreadyInUse(statement));
  }

  @Override
  public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
    super.visitLiteralExpression(expression);

    if (!myHolder.hasErrorResults() && expression.getParent() instanceof PsiCaseLabelElementList && expression.textMatches(PsiKeyword.NULL)) {
      add(checkFeature(expression, HighlightingFeature.PATTERNS_IN_SWITCH));
    }

    if (!myHolder.hasErrorResults()) HighlightUtil.checkIllegalUnicodeEscapes(expression, myHolder);
    if (!myHolder.hasErrorResults()) {
      add(HighlightUtil.checkLiteralExpressionParsingError(expression, myLanguageLevel,myFile, null));
    }

    if (myRefCountHolder != null && !myHolder.hasErrorResults()) {
      registerReferencesFromInjectedFragments(expression);
    }

    if (myRefCountHolder != null && !myHolder.hasErrorResults()) {
      for (PsiReference reference : expression.getReferences()) {
        if (reference instanceof PsiMemberReference) {
          PsiElement resolve = reference.resolve();
          if (resolve instanceof PsiMember) {
            myRefCountHolder.registerReference(reference, new CandidateInfo(resolve, PsiSubstitutor.EMPTY));
          }
        }
      }
    }
  }

  @Override
  public void visitErrorElement(@NotNull PsiErrorElement element) {
    super.visitErrorElement(element);
    add(HighlightClassUtil.checkClassMemberDeclaredOutside(element));
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    super.visitMethod(method);
    if (!myHolder.hasErrorResults()) add(HighlightControlFlowUtil.checkUnreachableStatement(method.getBody()));
    if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkConstructorHandleSuperClassExceptions(method));
    if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkRecursiveConstructorInvocation(method));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkSafeVarargsAnnotation(method, myLanguageLevel));
    if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkRecordAccessorDeclaration(method));
    if (!myHolder.hasErrorResults()) HighlightMethodUtil.checkRecordConstructorDeclaration(method, myHolder);

    PsiClass aClass = method.getContainingClass();
    if (!myHolder.hasErrorResults() && method.isConstructor()) {
      add(HighlightClassUtil.checkThingNotAllowedInInterface(method, aClass));
    }
    if (!myHolder.hasErrorResults() && method.hasModifierProperty(PsiModifier.DEFAULT)) {
      add(checkFeature(method, HighlightingFeature.EXTENSION_METHODS));
    }
    if (!myHolder.hasErrorResults() && aClass != null && aClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
      add(checkFeature(method, HighlightingFeature.EXTENSION_METHODS));
    }
    if (!myHolder.hasErrorResults() && aClass != null) {
      add(HighlightMethodUtil.checkDuplicateMethod(aClass, method, getDuplicateMethods(aClass)));
    }
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkEnumSuperConstructorCall(expression));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkSuperQualifierType(myFile.getProject(), expression));
    if (!myHolder.hasErrorResults()) {
      try {
        HighlightMethodUtil.checkMethodCall(expression, getResolveHelper(myHolder.getProject()), myLanguageLevel, myJavaSdkVersion, myFile, myHolder);
      }
      catch (IndexNotReadyException ignored) { }
    }

    if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkConstructorCallProblems(expression));
    if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkSuperAbstractMethodDirectCall(expression));

    if (!myHolder.hasErrorResults()) visitExpression(expression);
    if (!myHolder.hasErrorResults()) {
      expression.accept(myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitModifierList(@NotNull PsiModifierList list) {
    super.visitModifierList(list);
    PsiElement parent = list.getParent();
    if (parent instanceof PsiMethod method) {
      if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkMethodCanHaveBody(method, myLanguageLevel));
      MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
      PsiClass aClass = method.getContainingClass();
      if (!method.isConstructor()) {
        try {
          List<HierarchicalMethodSignature> superMethodSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
          if (!superMethodSignatures.isEmpty()) {
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
              if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkMethodWeakerPrivileges(methodSignature, superMethodSignatures, true, myFile,
                                                                                                            null));
              if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkMethodOverridesFinal(methodSignature, superMethodSignatures));
            }
            if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, true,
                                                                                                                null));
            if (aClass != null && !myHolder.hasErrorResults()) add(HighlightMethodUtil.checkMethodIncompatibleThrows(methodSignature, superMethodSignatures, true, aClass,
                                                                                                                              null));
          }
        }
        catch (IndexNotReadyException ignored) { }
      }
      if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkMethodMustHaveBody(method, aClass));
      if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkConstructorCallsBaseClassConstructor(method, myRefCountHolder, getResolveHelper(myHolder.getProject())));
      if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkStaticMethodOverride(method,myFile));
      if (!myHolder.hasErrorResults() && aClass != null && myOverrideEquivalentMethodsVisitedClasses.add(aClass)) {
        GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass, myHolder, false);
      }
    }
    else if (parent instanceof PsiClass aClass) {
      try {
        if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkDuplicateNestedClass(aClass));
        if (!myHolder.hasErrorResults()) {
          TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
          add(HighlightClassUtil.checkClassMustBeAbstract(aClass, textRange));
        }
        if (!myHolder.hasErrorResults()) {
          add(HighlightClassUtil.checkClassDoesNotCallSuperConstructorOrHandleExceptions(aClass, myRefCountHolder, getResolveHelper(myHolder.getProject())));
        }
        if (!myHolder.hasErrorResults()) add(HighlightMethodUtil.checkOverrideEquivalentInheritedMethods(aClass, myFile, myLanguageLevel));
        if (!myHolder.hasErrorResults() && myOverrideEquivalentMethodsVisitedClasses.add(aClass)) {
          GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass, myHolder, false);
        }
        if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkCyclicInheritance(aClass));
      }
      catch (IndexNotReadyException ignored) {
      }
    }
    else if (parent instanceof PsiEnumConstant) {
      if (!myHolder.hasErrorResults()) GenericsHighlightUtil.checkEnumConstantModifierList(list, myHolder);
    }
  }

  @Override
  public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
    add(AnnotationsHighlightUtil.checkNameValuePair(pair, myRefCountHolder));
    if (!myHolder.hasErrorResults()) {
      PsiIdentifier nameId = pair.getNameIdentifier();
      if (nameId != null) {
        HighlightInfo.Builder result = HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.ANNOTATION_ATTRIBUTE_NAME).range(nameId);
        add(result);
      }
    }
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    PsiType type = expression.getType();
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    add(HighlightUtil.checkUnhandledExceptions(expression));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkAnonymousInheritFinal(expression));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkAnonymousInheritProhibited(expression));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkAnonymousSealedProhibited(expression));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkQualifiedNew(expression, type, aClass));
    if (aClass != null && !myHolder.hasErrorResults()) add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(expression, type, aClass));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkTypeParameterInstantiation(expression));
    if (aClass != null && !myHolder.hasErrorResults()) add(HighlightClassUtil.checkInstantiationOfAbstractClass(aClass, expression));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkEnumInstantiation(expression, aClass));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkGenericArrayCreation(expression, type));
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
  public void visitPackageStatement(@NotNull PsiPackageStatement statement) {
    super.visitPackageStatement(statement);
    add(AnnotationsHighlightUtil.checkPackageAnnotationContainingFile(statement, myFile));
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) add(ModuleHighlightUtil.checkPackageStatement(statement, myFile, myJavaModule));
    }
  }

  @Override
  public void visitRecordComponent(@NotNull PsiRecordComponent recordComponent) {
    super.visitRecordComponent(recordComponent);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkRecordComponentVarArg(recordComponent));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkCStyleDeclaration(recordComponent));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkRecordComponentName(recordComponent));
    if (!myHolder.hasErrorResults()) add(HighlightControlFlowUtil.checkRecordComponentInitialized(recordComponent));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkRecordAccessorReturnType(recordComponent));
  }

  @Override
  public void visitParameter(@NotNull PsiParameter parameter) {
    super.visitParameter(parameter);

    PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList && parameter.isVarArgs()) {
      if (!myHolder.hasErrorResults()) add(checkFeature(parameter, HighlightingFeature.VARARGS));
      if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkVarArgParameterIsLast(parameter));
      if (!myHolder.hasErrorResults()) add(HighlightUtil.checkCStyleDeclaration(parameter));
    }
    else if (parent instanceof PsiCatchSection) {
      if (!myHolder.hasErrorResults() && parameter.getType() instanceof PsiDisjunctionType) {
        add(checkFeature(parameter, HighlightingFeature.MULTI_CATCH));
      }
      if (!myHolder.hasErrorResults()) add(HighlightUtil.checkCatchParameterIsThrowable(parameter));
      if (!myHolder.hasErrorResults()) GenericsHighlightUtil.checkCatchParameterIsClass(parameter, myHolder);
      if (!myHolder.hasErrorResults()) HighlightUtil.checkCatchTypeIsDisjoint(parameter, myHolder);
    }
    else if (parent instanceof PsiForeachStatement forEach) {
      add(checkFeature(forEach, HighlightingFeature.FOR_EACH));
      if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkForEachParameterType((PsiForeachStatement)parent, parameter));
    }
  }

  @Override
  public void visitParameterList(@NotNull PsiParameterList list) {
    super.visitParameterList(list);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkAnnotationMethodParameters(list));
  }

  @Override
  public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
    super.visitUnaryExpression(expression);
    if (!myHolder.hasErrorResults()) {
      add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
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
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = doVisitReferenceElement(ref);
    if (result != null) {
      PsiElement resolved = result.getElement();
      if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkRawOnParameterizedType(ref, resolved));
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

    add(HighlightUtil.checkReference(ref, result, myFile, myLanguageLevel));

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
          add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(
            JavaErrorBundle.message("cannot.select.from.a.type.parameter")).range(ref));
        }
      }
    }

    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkAbstractInstantiation(ref));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkExtendsDuplicate(ref, resolved,myFile));
    if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkClassExtendsForeignInnerClass(ref, resolved));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkSelectStaticClassFromParameterizedType(resolved, ref));
    if (!myHolder.hasErrorResults()) {
      add(GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, ref, result.getSubstitutor(), myJavaSdkVersion));
    }

    if (resolved != null && parent instanceof PsiReferenceList) {
      if (!myHolder.hasErrorResults()) {
        PsiReferenceList referenceList = (PsiReferenceList)parent;
        add(HighlightUtil.checkElementInReferenceList(ref, referenceList, result));
      }
    }

    if (parent instanceof PsiAnonymousClass && ref.equals(((PsiAnonymousClass)parent).getBaseClassReference())) {
      if (myOverrideEquivalentMethodsVisitedClasses.add((PsiClass)parent)) {
        PsiClass aClass = (PsiClass)parent;
        GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass, myHolder, false);
      }
      if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkGenericCannotExtendException((PsiAnonymousClass)parent));
    }

    if (parent instanceof PsiNewExpression newExpression &&
        !(resolved instanceof PsiClass) &&
        resolved instanceof PsiNamedElement &&
        newExpression.getClassOrAnonymousClassReference() == ref) {
      String text = JavaErrorBundle.message("cannot.resolve.symbol", ((PsiNamedElement)resolved).getName());
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(text);
      if (HighlightUtil.isCallToStaticMember(newExpression)) {
        var action = new RemoveNewKeywordFix(newExpression);
        info.registerFix(action, null, null, null, null);
      }
      add(info);
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
        if (place != null && 
            PsiTreeUtil.isAncestor(aClass, place, false) && 
            aClass.hasTypeParameters() && 
            !PsiUtil.isInsideJavadocComment(place)) {
          add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(ref, place, (PsiClass)resolved));
        }
      }
      else if (resolved instanceof PsiTypeParameter) {
        PsiTypeParameterListOwner owner = ((PsiTypeParameter)resolved).getOwner();
        if (owner instanceof PsiClass outerClass) {
          if (!InheritanceUtil.hasEnclosingInstanceInScope(outerClass, ref, false, false)) {
            add(HighlightClassUtil.checkIllegalEnclosingUsage(ref, null, outerClass, ref));
          }
        }
        else if (owner instanceof PsiMethod) {
          PsiClass cls = ClassUtils.getContainingStaticClass(ref);
          if (cls != null && PsiTreeUtil.isAncestor(owner, cls, true)) {
            String description = JavaErrorBundle.message("cannot.be.referenced.from.static.context", ref.getReferenceName());
            add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description));
          }
        }
      }
    }

    if (!myHolder.hasErrorResults()) {
      add(HighlightUtil.checkPackageAndClassConflict(ref, myFile));
    }
    if (!myHolder.hasErrorResults() && resolved instanceof PsiClass) {
      add(HighlightUtil.checkRestrictedIdentifierReference(ref, (PsiClass)resolved, myLanguageLevel));
    }
    if (!myHolder.hasErrorResults()) {
      add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(ref, resolved, myFile, myInsideConstructorOfClass));
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
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    JavaResolveResult resultForIncompleteCode = doVisitReferenceElement(expression);

    if (!myHolder.hasErrorResults()) {
      visitExpression(expression);
      if (myHolder.hasErrorResults()) return;
    }

    JavaResolveResult[] results = resolveOptimised(expression);
    if (results == null) return;
    JavaResolveResult result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;

    PsiElement resolved = result.getElement();
    if (resolved instanceof PsiVariable variable && resolved.getContainingFile() == expression.getContainingFile()) {
      boolean isFinal = variable.hasModifierProperty(PsiModifier.FINAL);
      if (isFinal && !variable.hasInitializer() && !(variable instanceof PsiPatternVariable)) {
        if (!myHolder.hasErrorResults()) {
          add(HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, myFinalVarProblems));
        }
      }
      if (!myHolder.hasErrorResults()) {
        try {
          add(HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, variable, myUninitializedVarProblems, myFile));
        }
        catch (IndexNotReadyException ignored) { }
      }
      if (!myHolder.hasErrorResults() && resolved instanceof PsiLocalVariable) {
        add(HighlightUtil.checkVarTypeSelfReferencing((PsiLocalVariable)resolved, expression));
      }
    }

    PsiElement parent = expression.getParent();
    if (parent instanceof PsiMethodCallExpression methodCallExpression &&
        ((PsiMethodCallExpression)parent).getMethodExpression() == expression &&
        (!result.isAccessible() || !result.isStaticsScopeCorrect())) {
      PsiExpressionList list = methodCallExpression.getArgumentList();
      PsiResolveHelper resolveHelper = getResolveHelper(myHolder.getProject());
      if (!HighlightMethodUtil.isDummyConstructorCall(methodCallExpression, resolveHelper, list, expression)) {
        try {
          add(HighlightMethodUtil.checkAmbiguousMethodCallIdentifier(
            expression, results, list, resolved, result, methodCallExpression, resolveHelper, myLanguageLevel, myFile));

          if (!PsiTreeUtil.findChildrenOfType(methodCallExpression.getArgumentList(), PsiLambdaExpression.class).isEmpty()) {
            PsiElement nameElement = expression.getReferenceNameElement();
            if (nameElement != null) {
              add(HighlightMethodUtil.checkAmbiguousMethodCallArguments(
                expression, results, list, resolved, result, methodCallExpression, resolveHelper, nameElement));
            }
          }
        }
        catch (IndexNotReadyException ignored) { }
      }
    }

    if (!myHolder.hasErrorResults() && resultForIncompleteCode != null && HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(expression)) {
      add(HighlightUtil.checkPatternVariableRequired(expression, resultForIncompleteCode));
    }

    if (!myHolder.hasErrorResults() && resultForIncompleteCode != null) {
      add(HighlightUtil.checkExpressionRequired(expression, resultForIncompleteCode, myFile));
    }

    if (!myHolder.hasErrorResults() && resolved instanceof PsiField) {
      try {
        add(HighlightUtil.checkIllegalForwardReferenceToField(expression, (PsiField)resolved));
      }
      catch (IndexNotReadyException ignored) { }
    }
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkAccessStaticFieldFromEnumConstructor(expression, result));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkClassReferenceAfterQualifier(expression, resolved));
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    add(HighlightUtil.checkUnqualifiedSuperInDefaultMethod(myLanguageLevel, expression, qualifierExpression));
    if (!myHolder.hasErrorResults() && myJavaModule == null && qualifierExpression != null) {
      if (parent instanceof PsiMethodCallExpression) {
        PsiClass psiClass = RefactoringChangeUtil.getQualifierClass(expression);
        if (psiClass != null) {
          add(GenericsHighlightUtil.checkClassSupersAccessibility(psiClass, expression, myFile.getResolveScope()));
        }
      }
      if (!myHolder.hasErrorResults()) {
        add(GenericsHighlightUtil.checkMemberSignatureTypesAccessibility(expression));
      }
    }
    if (!myHolder.hasErrorResults() && resolved instanceof PsiModifierListOwner) {
      expression.accept(myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    add(checkFeature(expression, HighlightingFeature.METHOD_REFERENCES));
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
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(accessProblem);
      HighlightFixUtil.registerAccessQuickFixAction(info, expression.getTextRange(), (PsiJvmMember)method, expression, result.getCurrentFileResolveScope(), null);
      add(info);
    }

    if (!LambdaUtil.isValidLambdaContext(parent)) {
      String description = JavaErrorBundle.message("method.reference.expression.is.not.expected");
      add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description));
    }

    if (!myHolder.hasErrorResults()) {
      PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement instanceof PsiKeyword) {
        if (!PsiMethodReferenceUtil.isValidQualifier(expression)) {
          PsiElement qualifier = expression.getQualifier();
          if (qualifier != null) {
            String description = JavaErrorBundle.message("cannot.find.class", qualifier.getText());
            add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description));
          }
        }
      }
    }

    if (functionalInterfaceType != null) {
      if (!myHolder.hasErrorResults()) {
        add(HighlightClassUtil.checkExtendsSealedClass(expression, functionalInterfaceType));
      }
      if (!myHolder.hasErrorResults()) {
        boolean isFunctional = LambdaUtil.isFunctionalType(functionalInterfaceType);
        if (!isFunctional) {
          String description =
            JavaErrorBundle.message("not.a.functional.interface", functionalInterfaceType.getPresentableText());
          add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description));
        }
      }
      if (!myHolder.hasErrorResults()) {
        checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType);
      }
      if (!myHolder.hasErrorResults()) {
        String errorMessage = PsiMethodReferenceHighlightingUtil.checkMethodReferenceContext(expression);
        if (errorMessage != null) {
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(errorMessage);
          if (method instanceof PsiMethod &&
              !((PsiMethod)method).isConstructor() &&
              !((PsiMethod)method).hasModifierProperty(PsiModifier.ABSTRACT)) {
            boolean shouldHave = !((PsiMethod)method).hasModifierProperty(PsiModifier.STATIC);
            QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories.createModifierActions(
                      (JvmModifiersOwner)method, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, shouldHave)));
          }
          add(info);
        }
      }
    }

    if (!myHolder.hasErrorResults()) {
      PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiTypeElement) {
        PsiType psiType = ((PsiTypeElement)qualifier).getType();
        HighlightInfo.Builder genericArrayCreationInfo = GenericsHighlightUtil.checkGenericArrayCreation(qualifier, psiType);
        if (genericArrayCreationInfo != null) {
          add(genericArrayCreationInfo);
        }
        else {
          String wildcardMessage = PsiMethodReferenceUtil.checkTypeArguments((PsiTypeElement)qualifier, psiType);
          if (wildcardMessage != null) {
            add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(wildcardMessage));
          }
        }
      }
    }

    if (method instanceof PsiMethod && ((PsiMethod)method).hasModifierProperty(PsiModifier.STATIC)) {
      if (!myHolder.hasErrorResults() && ((PsiMethod)method).hasTypeParameters()) {
        add(GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(method, expression, result.getSubstitutor(), myJavaSdkVersion));
      }

      PsiClass containingClass = ((PsiMethod)method).getContainingClass();
      if (!myHolder.hasErrorResults() && containingClass != null && containingClass.isInterface()) {
        add(HighlightMethodUtil.checkStaticInterfaceCallQualifier(expression, result, expression, containingClass));
      }
    }

    if (!myHolder.hasErrorResults()) {
      add(PsiMethodReferenceHighlightingUtil.checkRawConstructorReference(expression));
    }

    if (!myHolder.hasErrorResults()) {
      add(HighlightUtil.checkUnhandledExceptions(expression));
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
            if (!add(HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, expression)) &&
                !add(GenericsHighlightUtil.checkEnumInstantiation(expression, containingClass)) &&
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
          HighlightInfo.Builder highlightInfo =
            HighlightInfo.newHighlightInfo(type).descriptionAndTooltip(description).range(referenceNameElement);
          TextRange fixRange = HighlightMethodUtil.getFixRange(referenceNameElement);
          IntentionAction action = QuickFixFactory.getInstance().createCreateMethodFromUsageFix(expression);
          highlightInfo.registerFix(action, null, null, fixRange, null);
          add(highlightInfo);
        }
      }
    }

    if (!myHolder.hasErrorResults()) {
      String badReturnTypeMessage = PsiMethodReferenceUtil.checkReturnType(expression, result, functionalInterfaceType);
      if (badReturnTypeMessage != null) {
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(badReturnTypeMessage);
        IntentionAction action = AdjustFunctionContextFix.createFix(expression);
        if (action != null) {
          info.registerFix(action, null, null, null, null);
        }
        add(info);
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
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(problem.first);
      if (problem.second != null) {
        problem.second.forEach(fix -> info.registerFix(fix, List.of(), null, null, null));
      }
      add(info);
      return true;
    }
    return false;
  }

  @Override
  public void visitReferenceList(@NotNull PsiReferenceList list) {
    if (list.getFirstChild() == null) return;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiTypeParameter)) {
      add(AnnotationsHighlightUtil.checkAnnotationDeclaration(parent, list));
      if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkExtendsAllowed(list));
      if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkImplementsAllowed(list));
      if (!myHolder.hasErrorResults()) add(HighlightClassUtil.checkClassExtendsOnlyOneClass(list));
      if (!myHolder.hasErrorResults()) HighlightClassUtil.checkPermitsList(list, myHolder);
      if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkGenericCannotExtendException(list));
    }
  }

  @Override
  public void visitReferenceParameterList(@NotNull PsiReferenceParameterList list) {
    if (list.getTextLength() == 0) return;

    add(checkFeature(list, HighlightingFeature.GENERICS));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkParametersAllowed(list));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkParametersOnRaw(list, myLanguageLevel));
    if (!myHolder.hasErrorResults()) {
      for (PsiTypeElement typeElement : list.getTypeParameterElements()) {
        if (typeElement.getType() instanceof PsiDiamondType) {
          add(checkFeature(list, HighlightingFeature.DIAMOND_TYPES));
        }
      }
    }
  }

  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    super.visitStatement(statement);
    if (!myHolder.hasErrorResults() && HighlightingFeature.ENHANCED_SWITCH.isAvailable(myFile)) {
      add(HighlightUtil.checkReturnFromSwitchExpr(statement));
    }
    if (!myHolder.hasErrorResults()) {
      try {
        PsiElement parent = PsiTreeUtil.getParentOfType(statement, PsiFile.class, PsiClassInitializer.class,
                                                        PsiLambdaExpression.class, PsiMethod.class);
        HighlightInfo.Builder info;
        if (parent instanceof PsiMethod && JavaPsiRecordUtil.isCompactConstructor((PsiMethod)parent)) {
          info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement)
            .descriptionAndTooltip(JavaErrorBundle.message("record.compact.constructor.return"));
        }
        else {
          info = parent != null ? HighlightUtil.checkReturnStatementType(statement, parent) : null;
          if (info != null && parent instanceof PsiMethod method) {
            PsiType expectedType = myExpectedReturnTypes.computeIfAbsent(method, HighlightMethodUtil::determineReturnType);
            if (expectedType != null && !PsiTypes.voidType().equals(expectedType))
              HighlightUtil.registerReturnTypeFixes(info, method, expectedType);
          }
        }
        add(info);
      }
      catch (IndexNotReadyException ignore) { }
    }
  }

  @Override
  public void visitStatement(@NotNull PsiStatement statement) {
    super.visitStatement(statement);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkNotAStatement(statement));
  }

  @Override
  public void visitSuperExpression(@NotNull PsiSuperExpression expr) {
    add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier(), myLanguageLevel));
    if (!myHolder.hasErrorResults()) visitExpression(expr);
  }

  @Override
  public void visitSwitchLabelStatement(@NotNull PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkCaseStatement(statement));
  }

  @Override
  public void visitSwitchLabeledRuleStatement(@NotNull PsiSwitchLabeledRuleStatement statement) {
    super.visitSwitchLabeledRuleStatement(statement);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkCaseStatement(statement));
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    checkSwitchBlock(statement);
  }

  @Override
  public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
    super.visitSwitchExpression(expression);
    if (!myHolder.hasErrorResults()) add(checkFeature(expression, HighlightingFeature.SWITCH_EXPRESSION));
    checkSwitchBlock(expression);
    if (!myHolder.hasErrorResults()) HighlightUtil.checkSwitchExpressionReturnTypeCompatible(expression, myHolder);
    if (!myHolder.hasErrorResults()) HighlightUtil.checkSwitchExpressionHasResult(expression, myHolder);
  }

  private void checkSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
    SwitchBlockHighlightingModel model = SwitchBlockHighlightingModel.createInstance(myLanguageLevel, switchBlock, myFile);
    if (model == null) return;
    if (!myHolder.hasErrorResults()) model.checkSwitchBlockStatements(myHolder);
    if (!myHolder.hasErrorResults()) model.checkSwitchSelectorType(myHolder);
    if (!myHolder.hasErrorResults()) model.checkSwitchLabelValues(myHolder);
  }

  @Override
  public void visitThisExpression(@NotNull PsiThisExpression expr) {
    if (!(expr.getParent() instanceof PsiReceiverParameter)) {
      add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier(), myLanguageLevel));
      if (!myHolder.hasErrorResults()) add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expr, null, myFile, myInsideConstructorOfClass));
      if (!myHolder.hasErrorResults()) visitExpression(expr);
    }
  }

  @Override
  public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
    add(HighlightUtil.checkUnhandledExceptions(statement));
    if (!myHolder.hasErrorResults()) visitStatement(statement);
  }

  @Override
  public void visitTryStatement(@NotNull PsiTryStatement statement) {
    super.visitTryStatement(statement);
    if (!myHolder.hasErrorResults()) {
      Set<PsiClassType> thrownTypes = HighlightUtil.collectUnhandledExceptions(statement);
      for (PsiParameter parameter : statement.getCatchBlockParameters()) {
        HighlightUtil.checkExceptionAlreadyCaught(parameter, myHolder);
        if (!myHolder.hasErrorResults()) {
          HighlightUtil.checkExceptionThrownInTry(parameter, thrownTypes, myHolder);
        }
        if (!myHolder.hasErrorResults()) {
          HighlightUtil.checkWithImprovedCatchAnalysis(parameter, thrownTypes, myFile, myHolder);
        }
      }
    }
  }

  @Override
  public void visitResourceList(@NotNull PsiResourceList resourceList) {
    super.visitResourceList(resourceList);
    if (!myHolder.hasErrorResults()) add(checkFeature(resourceList, HighlightingFeature.TRY_WITH_RESOURCES));
  }

  @Override
  public void visitResourceVariable(@NotNull PsiResourceVariable resource) {
    super.visitResourceVariable(resource);
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkTryResourceIsAutoCloseable(resource));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkUnhandledCloserExceptions(resource));
  }

  @Override
  public void visitResourceExpression(@NotNull PsiResourceExpression resource) {
    super.visitResourceExpression(resource);
    if (!myHolder.hasErrorResults()) add(checkFeature(resource, HighlightingFeature.REFS_AS_RESOURCE));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkResourceVariableIsFinal(resource));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkTryResourceIsAutoCloseable(resource));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkUnhandledCloserExceptions(resource));
  }

  @Override
  public void visitTypeElement(@NotNull PsiTypeElement type) {
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkIllegalType(type, myFile));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkVarTypeApplicability(type));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkReferenceTypeUsedAsTypeArgument(type, myLanguageLevel));
    if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkWildcardUsage(type));
    if (!myHolder.hasErrorResults()) add(HighlightUtil.checkArrayType(type));
    if (!myHolder.hasErrorResults()) type.accept(myPreviewFeatureVisitor);
  }

  @Override
  public void visitTypeCastExpression(@NotNull PsiTypeCastExpression typeCast) {
    super.visitTypeCastExpression(typeCast);
    try {
      if (!myHolder.hasErrorResults()) add(HighlightUtil.checkIntersectionInTypeCast(typeCast, myLanguageLevel, myFile));
      if (!myHolder.hasErrorResults()) add(HighlightUtil.checkInconvertibleTypeCast(typeCast));
    }
    catch (IndexNotReadyException ignored) { }
  }

  @Override
  public void visitTypeParameterList(@NotNull PsiTypeParameterList list) {
    PsiTypeParameter[] typeParameters = list.getTypeParameters();
    if (typeParameters.length > 0) {
      add(checkFeature(list, HighlightingFeature.GENERICS));
      if (!myHolder.hasErrorResults()) add(GenericsHighlightUtil.checkTypeParametersList(list, typeParameters, myLanguageLevel));
    }
  }

  @Override
  public void visitVariable(@NotNull PsiVariable variable) {
    super.visitVariable(variable);
    if (variable instanceof PsiPatternVariable) {
      PsiElement context = PsiTreeUtil.getParentOfType(variable,
                                                       PsiInstanceOfExpression.class,
                                                       PsiCaseLabelElementList.class,
                                                       PsiForeachStatement.class);
      if (!(context instanceof PsiForeachStatement)) {
        HighlightingFeature feature = context instanceof PsiInstanceOfExpression ?
                                      HighlightingFeature.PATTERNS :
                                      HighlightingFeature.PATTERNS_IN_SWITCH;
        PsiIdentifier varIdentifier = ((PsiPatternVariable)variable).getNameIdentifier();
        add(checkFeature(varIdentifier, feature));
      }
    }
    try {
      if (!myHolder.hasErrorResults()) add(HighlightUtil.checkVarTypeApplicability(variable));
      if (!myHolder.hasErrorResults()) add(HighlightUtil.checkVariableInitializerType(variable));
    }
    catch (IndexNotReadyException ignored) { }
  }

  @Override
  public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
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
        HighlightInfo.Builder info = HighlightUtil.checkAssignability(conditionalType, sideType, side, side);
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
        add(info);
      }
    }
  }

  @Override
  public void visitReceiverParameter(@NotNull PsiReceiverParameter parameter) {
    super.visitReceiverParameter(parameter);
    if (!myHolder.hasErrorResults()) add(checkFeature(parameter, HighlightingFeature.RECEIVERS));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkReceiverPlacement(parameter));
    if (!myHolder.hasErrorResults()) add(AnnotationsHighlightUtil.checkReceiverType(parameter));
  }

  @Override
  public void visitModule(@NotNull PsiJavaModule module) {
    super.visitModule(module);
    if (!myHolder.hasErrorResults()) add(checkFeature(module, HighlightingFeature.MODULES));
    if (!myHolder.hasErrorResults()) add(ModuleHighlightUtil.checkFileName(module, myFile));
    if (!myHolder.hasErrorResults()) add(ModuleHighlightUtil.checkFileDuplicates(module, myFile));
    if (!myHolder.hasErrorResults()) ModuleHighlightUtil.checkDuplicateStatements(module, myHolder);
    if (!myHolder.hasErrorResults()) add(ModuleHighlightUtil.checkClashingReads(module));
    if (!myHolder.hasErrorResults()) ModuleHighlightUtil.checkUnusedServices(module, myFile, myHolder);
    if (!myHolder.hasErrorResults()) add(ModuleHighlightUtil.checkFileLocation(module, myFile));
  }

  @Override
  public void visitModuleStatement(@NotNull PsiStatement statement) {
    super.visitModuleStatement(statement);
    if (!myHolder.hasErrorResults()) statement.accept(myPreviewFeatureVisitor);
  }

  @Override
  public void visitRequiresStatement(@NotNull PsiRequiresStatement statement) {
    super.visitRequiresStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) add(ModuleHighlightUtil.checkModuleReference(statement));
      if (!myHolder.hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_10)) {
        ModuleHighlightUtil.checkModifiers(statement, myHolder);
      }
    }
  }

  @Override
  public void visitPackageAccessibilityStatement(@NotNull PsiPackageAccessibilityStatement statement) {
    super.visitPackageAccessibilityStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) add(ModuleHighlightUtil.checkHostModuleStrength(statement));
      if (!myHolder.hasErrorResults()) add(ModuleHighlightUtil.checkPackageReference(statement, myFile));
      if (!myHolder.hasErrorResults()) ModuleHighlightUtil.checkPackageAccessTargets(statement, myHolder);
    }
  }

  @Override
  public void visitUsesStatement(@NotNull PsiUsesStatement statement) {
    super.visitUsesStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) add(ModuleHighlightUtil.checkServiceReference(statement.getClassReference()));
    }
  }

  @Override
  public void visitProvidesStatement(@NotNull PsiProvidesStatement statement) {
    super.visitProvidesStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) ModuleHighlightUtil.checkServiceImplementations(statement, myFile, myHolder);
    }
  }

  @Override
  public void visitDefaultCaseLabelElement(@NotNull PsiDefaultCaseLabelElement element) {
    super.visitDefaultCaseLabelElement(element);
    // "case default:" will be highlighted as "The label for the default case must only use the 'default' keyword, without 'case'".
    // see SwitchBlockHighlightingModel#checkSwitchLabelValues
    // The "case default:" syntax was only allowed in outdated preview versions of Java (from Java 17 Preview to Java 19 Preview).
    // And even if the "case default" syntax was allowed not only in outdated preview versions of Java, using "case default:"
    // instead of "default:" looks weird. Therefore, for this case, we do not check the feature availability and do not
    // suggest increasing the language level.
    if (!(element.getParent() instanceof PsiCaseLabelElementList labelElementList) || labelElementList.getElementCount() != 1) {
      add(checkFeature(element, HighlightingFeature.PATTERNS_IN_SWITCH));
    }
  }

  @Override
  public void visitParenthesizedPattern(@NotNull PsiParenthesizedPattern pattern) {
    super.visitParenthesizedPattern(pattern);
    add(checkFeature(pattern, HighlightingFeature.GUARDED_AND_PARENTHESIZED_PATTERNS));
  }

  @Override
  public void visitGuardedPattern(@NotNull PsiGuardedPattern pattern) {
    super.visitGuardedPattern(pattern);
    if (HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isAvailable(pattern)) {
      String message = JavaErrorBundle.message("guarded.patterns.unavailable");
      add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pattern.getNode()).descriptionAndTooltip(message));
    }
    if (!myHolder.hasErrorResults()) {
      add(checkFeature(pattern, HighlightingFeature.GUARDED_AND_PARENTHESIZED_PATTERNS));
    }
    if (myHolder.hasErrorResults()) return;
    PsiExpression guardingExpr = pattern.getGuardingExpression();
    add(checkGuardingExpressionHasBooleanType(guardingExpr));
  }

  @Override
  public void visitPatternGuard(@NotNull PsiPatternGuard guard) {
    super.visitPatternGuard(guard);
    add(checkFeature(guard, HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS));
    if (myHolder.hasErrorResults()) return;
    PsiExpression guardingExpr = guard.getGuardingExpression();
    add(checkGuardingExpressionHasBooleanType(guardingExpr));
    if (myHolder.hasErrorResults()) return;
    Object constVal = ExpressionUtils.computeConstantExpression(guardingExpr);
    if (Boolean.FALSE.equals(constVal)) {
      String message = JavaErrorBundle.message("when.expression.is.false");
      add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(guardingExpr).descriptionAndTooltip(message));
    }
  }

  @Override
  public void visitPatternVariable(@NotNull PsiPatternVariable variable) {
    super.visitPatternVariable(variable);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_20_PREVIEW) && variable.getPattern() instanceof PsiDeconstructionPattern) {
      String message = JavaErrorBundle.message("identifier.is.not.allowed.here");
      add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(variable).descriptionAndTooltip(message));
    }
  }

  @Nullable
  private static HighlightInfo.Builder checkGuardingExpressionHasBooleanType(@Nullable PsiExpression guardingExpression) {
    if (guardingExpression != null && !TypeConversionUtil.isBooleanType(guardingExpression.getType())) {
      String message = JavaErrorBundle.message("incompatible.types", JavaHighlightUtil.formatType(PsiTypes.booleanType()),
                                               JavaHighlightUtil.formatType(guardingExpression.getType()));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(guardingExpression).descriptionAndTooltip(message);
    }
    return null;
  }

  @Override
  public void visitDeconstructionPattern(@NotNull PsiDeconstructionPattern deconstructionPattern) {
    super.visitDeconstructionPattern(deconstructionPattern);
    add(checkFeature(deconstructionPattern, HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS));
    if (myHolder.hasErrorResults()) return;
    PsiElement parent = deconstructionPattern.getParent();
    if (parent instanceof PsiForeachPatternStatement forEach) {
      add(checkFeature(deconstructionPattern, HighlightingFeature.RECORD_PATTERNS_IN_FOR_EACH));
      if (myHolder.hasErrorResults()) return;
      PsiTypeElement typeElement = JavaPsiPatternUtil.getPatternTypeElement(deconstructionPattern);
      if (typeElement == null) return;
      PsiType patternType = typeElement.getType();
      PsiExpression iteratedValue = forEach.getIteratedValue();
      PsiType itemType = iteratedValue == null ? null : JavaGenericsUtil.getCollectionItemType(iteratedValue);
      if (itemType == null) return;
      checkForEachPatternApplicable(deconstructionPattern, patternType, itemType);
      if (myHolder.hasErrorResults()) return;
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(itemType));
      if (selectorClass != null && (selectorClass.hasModifierProperty(SEALED) || selectorClass.isRecord())) {
        if (!PatternHighlightingModel.checkRecordExhaustiveness(Collections.singletonList(deconstructionPattern)).isExhaustive()) {
          add(createPatternIsNotExhaustiveError(deconstructionPattern, patternType, itemType));
        }
      }
      else {
        add(createPatternIsNotExhaustiveError(deconstructionPattern, patternType, itemType));
      }
    }
  }

  private static HighlightInfo.Builder createPatternIsNotExhaustiveError(@NotNull PsiDeconstructionPattern pattern,
                                                                         @NotNull PsiType patternType,
                                                                         @NotNull PsiType itemType) {
    String description = JavaErrorBundle.message("pattern.is.not.exhaustive", JavaHighlightUtil.formatType(patternType),
                                                 JavaHighlightUtil.formatType(itemType));
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pattern).descriptionAndTooltip(description);
  }

  private void checkForEachPatternApplicable(@NotNull PsiDeconstructionPattern pattern,
                                             @NotNull PsiType patternType,
                                             @NotNull PsiType itemType) {
    if (!TypeConversionUtil.areTypesConvertible(itemType, patternType)) {
      add(HighlightUtil.createIncompatibleTypeHighlightInfo(itemType, patternType, pattern.getTextRange(), 0));
      return;
    }
    HighlightInfo.Builder error = PatternHighlightingModel.getUncheckedPatternConversionError(pattern);
    if (error != null) {
      add(error);
    }
    else {
      PatternHighlightingModel.createDeconstructionErrors(pattern, myHolder);
    }
  }

  @Override
  public void visitDeconstructionList(@NotNull PsiDeconstructionList deconstructionList) {
    super.visitDeconstructionList(deconstructionList);
    // We are checking the case when the pattern looks similar to method call in switch and want to show user-friendly message that here
    // only constant expressions are expected.
    // it is required to do it in deconstruction list because unresolved reference won't let any parents show any highlighting,
    // so we need element which is not parent
    PsiElement parent = deconstructionList.getParent();
    PsiDeconstructionPattern pattern = tryCast(parent, PsiDeconstructionPattern.class);
    if (pattern == null) return;
    PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiCaseLabelElementList)) return;
    PsiTypeElement typeElement = pattern.getTypeElement();
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(typeElement, PsiJavaCodeReferenceElement.class);
    if (ref == null) return;
    if (ref.multiResolve(true).length == 0) {
      PsiElementFactory elementFactory = PsiElementFactory.getInstance(myFile.getProject());
      if (pattern.getPatternVariable() == null && pattern.getDeconstructionList().getDeconstructionComponents().length == 0) {
        PsiClassType type = tryCast(pattern.getTypeElement().getType(), PsiClassType.class);
        if (type != null && ContainerUtil.exists(type.getParameters(), PsiWildcardType.class::isInstance)) return;
        PsiExpression expression = elementFactory.createExpressionFromText(pattern.getText(), grandParent);
        PsiMethodCallExpression call = tryCast(expression, PsiMethodCallExpression.class);
        if (call == null) return;
        if (call.getMethodExpression().resolve() != null) {
          add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pattern.getTextRange())
                         .descriptionAndTooltip(JavaErrorBundle.message("switch.constant.expression.required")));
        }
      }
    }
  }

  @Override
  public void visitTypeTestPattern(@NotNull PsiTypeTestPattern pattern) {
    super.visitTypeTestPattern(pattern);
    if (pattern.getParent() instanceof PsiCaseLabelElementList) {
      add(checkFeature(pattern, HighlightingFeature.PATTERNS_IN_SWITCH));
    }
  }

  private HighlightInfo.Builder checkFeature(@NotNull PsiElement element, @NotNull HighlightingFeature feature) {
    return HighlightUtil.checkFeature(element, feature, myLanguageLevel, myFile);
  }

  private static class PreviewFeatureVisitor extends PreviewFeatureVisitorBase {
    private final LanguageLevel myLanguageLevel;
    private final HighlightInfoHolder myHolder;

    private PreviewFeatureVisitor(@NotNull LanguageLevel level, @NotNull HighlightInfoHolder holder) {
      myLanguageLevel = level;
      myHolder = holder;
    }

    @Override
    protected void registerProblem(PsiElement element, String description, HighlightingFeature feature, PsiAnnotation annotation) {
      boolean isReflective = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "reflective"));

      HighlightInfoType type = isReflective ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;

      HighlightInfo.Builder highlightInfo = HighlightUtil.checkFeature(element, feature, myLanguageLevel, element.getContainingFile(), description, type);
      if (highlightInfo != null) {
        myHolder.add(highlightInfo.create());
      }
    }
  }
}
