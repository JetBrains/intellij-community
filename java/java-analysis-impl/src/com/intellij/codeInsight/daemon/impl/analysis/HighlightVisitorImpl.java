/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil.Feature;
import com.intellij.codeInsight.daemon.impl.quickfix.AdjustFunctionContextFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
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
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.*;
import com.intellij.util.containers.MostlySingularMultiMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.notNull;

public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor {
  private final PsiResolveHelper myResolveHelper;

  private HighlightInfoHolder myHolder;
  private RefCountHolder myRefCountHolder;
  private LanguageLevel myLanguageLevel;
  private JavaSdkVersion myJavaSdkVersion;

  @SuppressWarnings("StatefulEp") private PsiFile myFile;
  @SuppressWarnings("StatefulEp") private PsiJavaModule myJavaModule;

  // map codeBlock->List of PsiReferenceExpression of uninitialized final variables
  private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new THashMap<>();
  // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new THashMap<>();

  // value==1: no info if the parameter was reassigned (but the parameter is present in current file), value==2: parameter was reassigned
  private final TObjectIntHashMap<PsiParameter> myReassignedParameters = new TObjectIntHashMap<>();

  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> mySingleImportedClasses = new THashMap<>();
  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiField>> mySingleImportedFields = new THashMap<>();

  private final PsiElementVisitor REGISTER_REFERENCES_VISITOR = new PsiRecursiveElementWalkingVisitor() {
    @Override public void visitElement(PsiElement element) {
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
  private final Map<PsiClass, MostlySingularMultiMap<MethodSignature, PsiMethod>> myDuplicateMethods = new THashMap<>();
  private final Set<PsiClass> myOverrideEquivalentMethodsVisitedClasses = new THashSet<>();

  private static class Holder {
    private static final boolean CHECK_ELEMENT_LEVEL = ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal();
  }

  protected HighlightVisitorImpl(@NotNull PsiResolveHelper resolveHelper) {
    myResolveHelper = resolveHelper;
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
    return new HighlightVisitorImpl(myResolveHelper);
  }

  @Override
  public int order() {
    return 0;
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
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
    boolean success = true;
    try {
      prepare(Holder.CHECK_ELEMENT_LEVEL ? new CheckLevelHighlightInfoHolder(file, holder) : holder, file);
      if (updateWholeFile) {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress == null) throw new IllegalStateException("Must be run under progress");
        RefCountHolder refCountHolder = RefCountHolder.get(file);
        myRefCountHolder = refCountHolder;
        Project project = file.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        TextRange dirtyScope = Optional.ofNullable(document)
          .map(doc -> DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap().getFileDirtyScope(doc, Pass.UPDATE_ALL))
          .orElse(file.getTextRange());
        success = refCountHolder.analyze(file, dirtyScope, progress, () -> {
          highlight.run();
          ProgressManager.checkCanceled();
          if (document != null) {
            new PostHighlightingVisitor(file, document, refCountHolder).collectHighlights(holder, progress);
          }
        });
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
      myReassignedParameters.clear();

      myRefCountHolder = null;
      myJavaModule = null;
      myFile = null;
      myHolder = null;
      myDuplicateMethods.clear();
      myOverrideEquivalentMethodsVisitedClasses.clear();
    }

    return success;
  }

  protected void prepareToRunAsInspection(@NotNull HighlightInfoHolder holder) {
    prepare(holder, holder.getContextFile());
  }

  private void prepare(HighlightInfoHolder holder, PsiFile file) {
    myHolder = holder;
    myFile = file;
    myLanguageLevel = PsiUtil.getLanguageLevel(file);
    myJavaSdkVersion = notNull(JavaVersionService.getInstance().getJavaSdkVersion(file), JavaSdkVersion.fromLanguageLevel(myLanguageLevel));
    myJavaModule = myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9) ? JavaModuleGraphUtil.findDescriptorByElement(file) : null;
  }

  @Override
  public void visitElement(PsiElement element) {
    if (myRefCountHolder != null && myFile instanceof ServerPageFile) {
      // in JSP, XmlAttributeValue may contain java references
      try {
        for (PsiReference reference : element.getReferences()) {
          if (reference instanceof PsiJavaReference) {
            PsiJavaReference psiJavaReference = (PsiJavaReference)reference;
            myRefCountHolder.registerReference(psiJavaReference, psiJavaReference.advancedResolve(false));
          }
        }
      }
      catch (IndexNotReadyException ignored) { }
    }

    if (!(myFile instanceof ServerPageFile)) {
      myHolder.add(DefaultHighlightUtil.checkBadCharacter(element));
    }
  }

  @Override
  public void visitAnnotation(PsiAnnotation annotation) {
    super.visitAnnotation(annotation);
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(annotation, Feature.ANNOTATIONS));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkApplicability(annotation, myLanguageLevel, myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkAnnotationType(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkMissingAttributes(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkTargetAnnotationDuplicates(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkDuplicateAnnotations(annotation, myLanguageLevel));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkFunctionalInterface(annotation, myLanguageLevel));
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
          myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(initializer1, type));
        }
      }
    }
  }

  @Override
  public void visitAnnotationMethod(PsiAnnotationMethod method) {
    PsiType returnType = method.getReturnType();
    PsiAnnotationMemberValue value = method.getDefaultValue();
    if (returnType != null && value != null) {
      myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(value, returnType));
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
    myHolder.add(checkFeature(expression, Feature.LAMBDA_EXPRESSIONS));
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiExpressionStatement) return;
    if (!myHolder.hasErrorResults() && !LambdaUtil.isValidLambdaContext(parent)) {
      myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                     .descriptionAndTooltip("Lambda expression not expected here").create());
    }

    PsiType functionalInterfaceType = null;
    if (!myHolder.hasErrorResults()) {
      functionalInterfaceType = expression.getFunctionalInterfaceType();
      if (functionalInterfaceType != null) {
        final String notFunctionalMessage = LambdaHighlightingUtil.checkInterfaceFunctional(functionalInterfaceType);
        if (notFunctionalMessage != null) {
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                         .descriptionAndTooltip(notFunctionalMessage).create());
        }
        else {
          checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType);
        }
      }
      else if (LambdaUtil.getFunctionalInterfaceType(expression, true) != null) {
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip("Cannot infer functional interface type").create());
      }
    }

    if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
      String parentInferenceErrorMessage = null;
      final PsiCallExpression callExpression = parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression ?
                                               (PsiCallExpression)parent.getParent() : null;
      final JavaResolveResult containingCallResolveResult = callExpression != null ? callExpression.resolveMethodGenerics() : null;
      if (containingCallResolveResult instanceof MethodCandidateInfo) {
        parentInferenceErrorMessage = ((MethodCandidateInfo)containingCallResolveResult).getInferenceErrorMessage();
      }
      final Map<PsiElement, String> returnErrors = LambdaUtil.checkReturnTypeCompatible(expression, LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType));
      if (parentInferenceErrorMessage != null && (returnErrors == null || !returnErrors.containsValue(parentInferenceErrorMessage))) {
        HighlightInfo info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(parentInferenceErrorMessage)
            .create();
        if (returnErrors != null) {
          returnErrors.keySet().forEach(k -> QuickFixAction.registerQuickFixAction(info, AdjustFunctionContextFix.createFix(k)));
        }
        myHolder.add(info);
      }
      else if (returnErrors != null) {
        for (Map.Entry<PsiElement, String> entry : returnErrors.entrySet()) {
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(entry.getKey())
            .descriptionAndTooltip(entry.getValue()).create();
          QuickFixAction.registerQuickFixAction(info, AdjustFunctionContextFix.createFix(entry.getKey()));
          if (entry.getKey() instanceof PsiExpression) {
            PsiExpression expr = (PsiExpression)entry.getKey();
            HighlightUtil.addLambdaReturnTypeFixes(info, expression, expr);
          }
          myHolder.add(info);
        }
      }
    }

    if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      if (interfaceMethod != null) {
        final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
        myHolder.add(LambdaHighlightingUtil.checkParametersCompatible(expression, parameters, LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)));
      }
    }

    if (!myHolder.hasErrorResults()) {
      final PsiElement body = expression.getBody();
      if (body instanceof PsiCodeBlock) {
        myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement((PsiCodeBlock)body));
      }
    }
  }

  @Override
  public void visitBreakStatement(PsiBreakStatement statement) {
    super.visitBreakStatement(statement);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findExitedStatement()));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkBreakOutsideLoop(statement));
  }

  @Override
  public void visitClass(PsiClass aClass) {
    super.visitClass(aClass);
    if (aClass instanceof PsiSyntheticClass) return;
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInterfaceMultipleInheritance(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkClassSupersAccessibility(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateTopLevelClass(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumMustNotBeLocal(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumWithoutConstantsCantHaveAbstractMethods(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkImplicitThisReferenceBeforeSuper(aClass, myJavaSdkVersion));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassAndPackageConflict(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkPublicClassInRightFile(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParameterOverrideEquivalentMethods(aClass, myLanguageLevel));
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
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
  public void visitComment(PsiComment comment) {
    super.visitComment(comment);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnclosedComment(comment));
    if (myRefCountHolder != null && !myHolder.hasErrorResults()) registerReferencesFromInjectedFragments(comment);
  }

  @Override
  public void visitContinueStatement(PsiContinueStatement statement) {
    super.visitContinueStatement(statement);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findContinuedStatement()));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkContinueOutsideLoop(statement));
  }

  @Override
  public void visitJavaToken(PsiJavaToken token) {
    super.visitJavaToken(token);
    if (!myHolder.hasErrorResults()
        && token.getTokenType() == JavaTokenType.RBRACE
        && token.getParent() instanceof PsiCodeBlock) {

      final PsiElement gParent = token.getParent().getParent();
      final PsiCodeBlock codeBlock;
      final PsiType returnType;
      if (gParent instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)gParent;
        codeBlock = method.getBody();
        returnType = method.getReturnType();
      }
      else if (gParent instanceof PsiLambdaExpression) {
        final PsiElement body = ((PsiLambdaExpression)gParent).getBody();
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
  public void visitDocTagValue(PsiDocTagValue value) {
    PsiReference reference = value.getReference();
    if (reference != null) {
      PsiElement element = reference.resolve();
      final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (element instanceof PsiMethod) {
        PsiElement nameElement = ((PsiDocMethodOrFieldRef)value).getNameElement();
        if (nameElement != null) {
          myHolder.add(HighlightNamesUtil.highlightMethodName((PsiMethod)element, nameElement, false, colorsScheme));
        }
      }
      else if (element instanceof PsiParameter) {
        myHolder.add(HighlightNamesUtil.highlightVariableName((PsiVariable)element, value.getNavigationElement(), colorsScheme));
      }
    }
  }

  @Override
  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    super.visitEnumConstant(enumConstant);
    if (!myHolder.hasErrorResults()) GenericsHighlightUtil.checkEnumConstantForConstructorProblems(enumConstant, myHolder, myJavaSdkVersion);
    if (!myHolder.hasErrorResults()) registerConstructorCall(enumConstant);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnhandledExceptions(enumConstant, null));
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
    PsiType type = expression.getType();
    if (myHolder.add(HighlightUtil.checkMustBeBoolean(expression, type))) return;

    if (expression instanceof PsiArrayAccessExpression) {
      myHolder.add(HighlightUtil.checkValidArrayAccessExpression((PsiArrayAccessExpression)expression));
    }

    PsiElement parent = expression.getParent();
    if (parent instanceof PsiNewExpression
        && ((PsiNewExpression)parent).getQualifier() != expression
        && ((PsiNewExpression)parent).getArrayInitializer() != expression) {
      // like in 'new String["s"]'
      myHolder.add(HighlightUtil.checkAssignability(PsiType.INT, expression.getType(), expression, expression));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkCannotWriteToFinal(expression,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableExpected(expression));
    if (!myHolder.hasErrorResults()) myHolder.addAll(HighlightUtil.checkArrayInitializer(expression, type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTernaryOperatorConditionIsBoolean(expression, type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssertOperatorTypes(expression, type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSynchronizedExpressionType(expression, type, myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkConditionalExpressionBranchTypesMatch(expression, type));
    if (!myHolder.hasErrorResults()
        && parent instanceof PsiThrowStatement
        && ((PsiThrowStatement)parent).getException() == expression) {
      myHolder.add(HighlightUtil.checkMustBeThrowable(type, expression, true));
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(AnnotationsHighlightUtil.checkConstantExpression(expression));
    }
    if (!myHolder.hasErrorResults() && parent instanceof PsiForeachStatement && ((PsiForeachStatement)parent).getIteratedValue() == expression) {
      myHolder.add(GenericsHighlightUtil.checkForeachExpressionTypeIsIterable(expression));
    }
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
            !HighlightMethodUtil.isDummyConstructorCall(expression, myResolveHelper, list, referenceExpression) &&
            // this check is for fake expression from JspMethodCallImpl
            referenceExpression.getParent() == expression) {
          try {
            if (PsiTreeUtil.findChildrenOfType(expression.getArgumentList(), PsiLambdaExpression.class).isEmpty()) {
              myHolder.add(HighlightMethodUtil.checkAmbiguousMethodCallArguments(referenceExpression, results, list, resolved, result, expression, myResolveHelper, list));
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
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkFinalFieldInitialized(field));
  }

  @Override
  public void visitForStatement(PsiForStatement statement) {
    myHolder.add(HighlightUtil.checkForStatement(statement));
  }

  @Override
  public void visitForeachStatement(PsiForeachStatement statement) {
    myHolder.add(checkFeature(statement, Feature.FOR_EACH));
  }

  @Override
  public void visitImportStaticStatement(PsiImportStaticStatement statement) {
    myHolder.add(checkFeature(statement, Feature.STATIC_IMPORTS));
    if (!myHolder.hasErrorResults()) myHolder.add(ImportsHighlightUtil.checkStaticOnDemandImportResolvesToClass(statement));
  }

  @Override
  public void visitIdentifier(final PsiIdentifier identifier) {
    TextAttributesScheme colorsScheme = myHolder.getColorsScheme();

    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;
      myHolder.add(HighlightUtil.checkVariableAlreadyDefined(variable));

      if (variable.getInitializer() == null) {
        final PsiElement child = variable.getLastChild();
        if (child instanceof PsiErrorElement && child.getPrevSibling() == identifier) return;
      }

      boolean isMethodParameter = variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiMethod;
      if (isMethodParameter) {
        myReassignedParameters.put((PsiParameter)variable, 1); // mark param as present in current file
      }
      else {
        // method params are highlighted in visitMethod since we should make sure the method body was visited before
        if (HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems)) {
          myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, identifier));
        }
        else {
          myHolder.add(HighlightNamesUtil.highlightVariableName(variable, identifier, colorsScheme));
        }
      }
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      if (aClass.isAnnotationType()) {
        myHolder.add(checkFeature(identifier, Feature.ANNOTATIONS));
      }

      myHolder.add(HighlightClassUtil.checkClassAlreadyImported(aClass, identifier));
      if (!(parent instanceof PsiAnonymousClass) && aClass.getNameIdentifier() == identifier) {
        myHolder.add(HighlightNamesUtil.highlightClassName(aClass, identifier, colorsScheme));
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
        myHolder.add(HighlightMethodUtil.checkConstructorName(method));
      }
      myHolder.add(HighlightNamesUtil.highlightMethodName(method, identifier, true, colorsScheme));
      final PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        myHolder.add(GenericsHighlightUtil.checkDefaultMethodOverrideEquivalentToObjectNonPrivate(myLanguageLevel, aClass, method, identifier));
      }
    }

    myHolder.add(HighlightUtil.checkUnderscore(identifier, myLanguageLevel));

    super.visitIdentifier(identifier);
  }

  @Override
  public void visitImportStatement(final PsiImportStatement statement) {
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkSingleImportClassConflict(statement, mySingleImportedClasses,myFile));
    }
  }

  @Override
  public void visitImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement ref) {
    final String refName = ref.getReferenceName();
    final JavaResolveResult[] results = ref.multiResolve(false);

    final PsiElement referenceNameElement = ref.getReferenceNameElement();
    if (results.length == 0) {
      final String description = JavaErrorMessages.message("cannot.resolve.symbol", refName);
      assert referenceNameElement != null : ref;
      final HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(referenceNameElement).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createSetupJDKFix());
      myHolder.add(info);
    }
    else {
      final PsiManager manager = ref.getManager();
      for (JavaResolveResult result : results) {
        final PsiElement element = result.getElement();

        String description = null;
        if (element instanceof PsiClass) {
          final Pair<PsiImportStaticReferenceElement, PsiClass> imported = mySingleImportedClasses.get(refName);
          final PsiClass aClass = imported == null ? null : imported.getSecond();
          if (aClass != null && !manager.areElementsEquivalent(aClass, element)) {
            //noinspection ConditionalExpressionWithIdenticalBranches
            description = imported.first == null
                          ? JavaErrorMessages.message("single.import.class.conflict", refName)
                          : imported.first.equals(ref)
                            ? JavaErrorMessages.message("class.is.ambiguous.in.single.static.import", refName)
                            : JavaErrorMessages.message("class.is.already.defined.in.single.static.import", refName);
          }
          mySingleImportedClasses.put(refName, Pair.create(ref, (PsiClass)element));
        }
        else if (element instanceof PsiField) {
          final Pair<PsiImportStaticReferenceElement, PsiField> imported = mySingleImportedFields.get(refName);
          final PsiField field = imported == null ? null : imported.getSecond();
          if (field != null && !manager.areElementsEquivalent(field, element)) {
            //noinspection ConditionalExpressionWithIdenticalBranches
            description = imported.first.equals(ref)
                          ? JavaErrorMessages.message("field.is.ambiguous.in.single.static.import", refName)
                          : JavaErrorMessages.message("field.is.already.defined.in.single.static.import", refName);
          }
          mySingleImportedFields.put(refName, Pair.create(ref, (PsiField)element));
        }

        if (description != null) {
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description).create());
        }
      }
    }
    if (!myHolder.hasErrorResults()) {
      PsiElement resolved = results.length >= 1 ? results[0].getElement() : null;
      if (results.length > 1) {
        for (int i = 1; i < results.length; i++) {
          final PsiElement element = results[i].getElement();
          if (resolved instanceof PsiMethod && !(element instanceof PsiMethod) ||
              resolved instanceof PsiVariable && !(element instanceof PsiVariable) ||
              resolved instanceof PsiClass && !(element instanceof PsiClass)) {
            resolved = null;
            break;
          }
        }
      }
      final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (resolved instanceof PsiClass) {
        myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, ref, colorsScheme));
      }
      else {
        myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(ref, colorsScheme));
        if (referenceNameElement != null) {
          if (resolved instanceof PsiVariable) {
            myHolder.add(HighlightNamesUtil.highlightVariableName((PsiVariable)resolved, referenceNameElement, colorsScheme));
          }
          else if (resolved instanceof PsiMethod) {
            myHolder.add(HighlightNamesUtil.highlightMethodName((PsiMethod)resolved, referenceNameElement, false, colorsScheme));
          }
        }
      }
    }
  }

  @Override
  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInstanceOfApplicable(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInstanceOfGenericType(expression));
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
      if (PsiModifier.ABSTRACT.equals(text) && psiModifierList.getParent() instanceof PsiMethod) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkAbstractMethodInConcreteClass((PsiMethod)psiModifierList.getParent(), keyword));
        }
      }
    }
    else if (PsiKeyword.INTERFACE.equals(text) && parent instanceof PsiClass) {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkInterfaceCannotBeLocal((PsiClass)parent));
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
    if (myHolder.hasErrorResults()) return;
    myHolder.add(HighlightUtil.checkLiteralExpressionParsingError(expression, myLanguageLevel,myFile));
    if (myRefCountHolder != null && !myHolder.hasErrorResults()) registerReferencesFromInjectedFragments(expression);

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
  public void visitMethod(PsiMethod method) {
    super.visitMethod(method);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(method.getBody()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorHandleSuperClassExceptions(method));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkRecursiveConstructorInvocation(method));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkSafeVarargsAnnotation(method, myLanguageLevel));

    PsiClass aClass = method.getContainingClass();
    if (!myHolder.hasErrorResults() && method.isConstructor()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(method, aClass));
    }
    if (!myHolder.hasErrorResults() && method.hasModifierProperty(PsiModifier.DEFAULT)) {
      myHolder.add(checkFeature(method, Feature.EXTENSION_METHODS));
    }
    if (!myHolder.hasErrorResults() && aClass != null && aClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
      myHolder.add(checkFeature(method, Feature.EXTENSION_METHODS));
    }
    if (!myHolder.hasErrorResults() && aClass != null) {
      myHolder.add(HighlightMethodUtil.checkDuplicateMethod(aClass, method, getDuplicateMethods(aClass)));
    }

    // method params are highlighted in visitMethod since we should make sure the method body was visited before
    PsiParameter[] parameters = method.getParameterList().getParameters();
    final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();

    for (PsiParameter parameter : parameters) {
      int info = myReassignedParameters.get(parameter);
      if (info == 0) continue; // out of this file

      PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
      if (nameIdentifier != null) {
        if (info == 2) { // reassigned
          myHolder.add(HighlightNamesUtil.highlightReassignedVariable(parameter, nameIdentifier));
        }
        else {
          myHolder.add(HighlightNamesUtil.highlightVariableName(parameter, nameIdentifier, colorsScheme));
        }
      }
    }
  }

  private void highlightReferencedMethodOrClassName(@NotNull PsiJavaCodeReferenceElement element, PsiElement resolved) {
    PsiElement parent = element.getParent();
    final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
    if (parent instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)parent).resolveMethod();
      PsiElement methodNameElement = element.getReferenceNameElement();
      if (method != null && methodNameElement != null&& !(methodNameElement instanceof PsiKeyword)) {
        myHolder.add(HighlightNamesUtil.highlightMethodName(method, methodNameElement, false, colorsScheme));
        myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(element, colorsScheme));
      }
    }
    else if (parent instanceof PsiConstructorCall) {
      try {
        PsiMethod method = ((PsiConstructorCall)parent).resolveConstructor();
        PsiMember methodOrClass = method != null ? method : resolved instanceof PsiClass ? (PsiClass)resolved : null;
        if (methodOrClass != null) {
          final PsiElement referenceNameElement = element.getReferenceNameElement();
          if(referenceNameElement != null) {
            // exclude type parameters from the highlighted text range
            TextRange range = referenceNameElement.getTextRange();
            myHolder.add(HighlightNamesUtil.highlightMethodName(methodOrClass, referenceNameElement, range, colorsScheme, false));
          }
        }
      }
      catch (IndexNotReadyException ignored) { }
    }
    else if (resolved instanceof PsiPackage) {
      // highlight package (and following dot) as a class
      myHolder.add(HighlightNamesUtil.highlightPackage(resolved, element, colorsScheme));
    }
    else if (resolved instanceof PsiClass) {
      myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, element, colorsScheme));
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumSuperConstructorCall(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkSuperQualifierType(myFile.getProject(), expression));
    // in case of JSP synthetic method call, do not check
    if (myFile.isPhysical() && !myHolder.hasErrorResults()) {
      try {
        myHolder.add(HighlightMethodUtil.checkMethodCall(expression, myResolveHelper, myLanguageLevel, myJavaSdkVersion, myFile));
      }
      catch (IndexNotReadyException ignored) { }
    }

    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallMustBeFirstStatement(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkSuperAbstractMethodDirectCall(expression));

    if (!myHolder.hasErrorResults()) visitExpression(expression);
  }

  @Override
  public void visitModifierList(PsiModifierList list) {
    super.visitModifierList(list);
    PsiElement parent = list.getParent();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodCanHaveBody(method, myLanguageLevel));
      MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
      if (!method.isConstructor()) {
        try {
          List<HierarchicalMethodSignature> superMethodSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
          if (!superMethodSignatures.isEmpty()) {
            if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, true));
            if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodIncompatibleThrows(methodSignature, superMethodSignatures, true, method.getContainingClass()));
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
              if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodWeakerPrivileges(methodSignature, superMethodSignatures, true, myFile));
              if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodOverridesFinal(methodSignature, superMethodSignatures));
            }
          }
        }
        catch (IndexNotReadyException ignored) { }
      }
      PsiClass aClass = method.getContainingClass();
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodMustHaveBody(method, aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallsBaseClassConstructor(method, myRefCountHolder, myResolveHelper));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkStaticMethodOverride(method,myFile));
      if (!myHolder.hasErrorResults() && aClass != null &&
          myOverrideEquivalentMethodsVisitedClasses.add(aClass)) {
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
          myHolder.add(HighlightClassUtil.checkClassDoesNotCallSuperConstructorOrHandleExceptions(aClass, myRefCountHolder, myResolveHelper));
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
    myHolder.add(AnnotationsHighlightUtil.checkNameValuePair(pair));
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
    final PsiType type = expression.getType();
    final PsiClass aClass = PsiUtil.resolveClassInType(type);
    myHolder.add(HighlightUtil.checkUnhandledExceptions(expression, null));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAnonymousInheritFinal(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkQualifiedNew(expression, type, aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(expression, type, aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParameterInstantiation(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkInstantiationOfAbstractClass(aClass, expression));
    try {
      if (!myHolder.hasErrorResults()) HighlightMethodUtil.checkNewExpression(expression, type, myHolder, myJavaSdkVersion);
    }
    catch (IndexNotReadyException ignored) { }
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression, aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, type));
    if (!myHolder.hasErrorResults()) registerConstructorCall(expression);

    if (!myHolder.hasErrorResults()) visitExpression(expression);
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
  public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);

    final PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList && parameter.isVarArgs()) {
      if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(parameter, Feature.VARARGS));
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkVarArgParameterIsLast(parameter));
    }
    else if (parent instanceof PsiCatchSection) {
      if (!myHolder.hasErrorResults() && parameter.getType() instanceof PsiDisjunctionType) {
        myHolder.add(checkFeature(parameter, Feature.MULTI_CATCH));
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
  public void visitPostfixExpression(PsiPostfixExpression expression) {
    super.visitPostfixExpression(expression);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
    }
  }

  @Override
  public void visitPrefixExpression(PsiPrefixExpression expression) {
    super.visitPrefixExpression(expression);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
    }
  }

  private void registerConstructorCall(@NotNull PsiConstructorCall constructorCall) {
    if (myRefCountHolder != null) {
      JavaResolveResult resolveResult = constructorCall.resolveMethodGenerics();
      final PsiElement resolved = resolveResult.getElement();
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
    }
  }

  private JavaResolveResult doVisitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = resolveOptimised(ref);
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
          final PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
          if (containingClass != null) {
            if (PsiTreeUtil.isAncestor(containingClass.getExtendsList(), ref, false) ||
                PsiTreeUtil.isAncestor(containingClass.getImplementsList(), ref, false)) {
              canSelectFromTypeParameter = false;
            }
          }
        }
        if (!canSelectFromTypeParameter) {
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip("Cannot select from a type parameter").range(ref).create());
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
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkCannotPassInner(ref));

    if (resolved != null && parent instanceof PsiReferenceList) {
      if (!myHolder.hasErrorResults()) {
        PsiReferenceList referenceList = (PsiReferenceList)parent;
        myHolder.add(HighlightUtil.checkElementInReferenceList(ref, referenceList, result));
      }
    }

    if (parent instanceof PsiAnonymousClass && ref.equals(((PsiAnonymousClass)parent).getBaseClassReference()) &&
        myOverrideEquivalentMethodsVisitedClasses.add((PsiClass)parent)) {
      PsiClass aClass = (PsiClass)parent;
      myHolder.addAll(GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass));
    }

    if (resolved instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)resolved;

      PsiElement containingClass = PsiTreeUtil.getNonStrictParentOfType(ref, PsiClass.class, PsiLambdaExpression.class);
      if ((containingClass instanceof PsiAnonymousClass || containingClass instanceof PsiLambdaExpression) &&
          !PsiTreeUtil.isAncestor(containingClass, variable, false) &&
          !(variable instanceof PsiField) &&
          (containingClass instanceof PsiLambdaExpression ||
           !PsiTreeUtil.isAncestor(((PsiAnonymousClass)containingClass).getArgumentList(), ref, false))) {
        myHolder.add(HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.IMPLICIT_ANONYMOUS_CLASS_PARAMETER).range(ref).create());
      }

      if (variable instanceof PsiParameter && ref instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)ref)) {
        myReassignedParameters.put((PsiParameter)variable, 2);
      }

      final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (!variable.hasModifierProperty(PsiModifier.FINAL) && isReassigned(variable)) {
        myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, ref));
      }
      else {
        PsiElement nameElement = ref.getReferenceNameElement();
        if (nameElement != null) {
          myHolder.add(HighlightNamesUtil.highlightVariableName(variable, nameElement, colorsScheme));
        }
      }
      myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(ref, colorsScheme));
    }
    else {
      highlightReferencedMethodOrClassName(ref, resolved);
    }

    if (parent instanceof PsiNewExpression &&
        !(resolved instanceof PsiClass) &&
        resolved instanceof PsiNamedElement &&
        ((PsiNewExpression)parent).getClassOrAnonymousClassReference() == ref) {
      String text = JavaErrorMessages.message("cannot.resolve.symbol", ((PsiNamedElement)resolved).getName());
      myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(text).create());
    }

    if (!myHolder.hasErrorResults() && resolved instanceof PsiClass) {
      final PsiClass aClass = ((PsiClass)resolved).getContainingClass();
      if (aClass != null) {
        final PsiElement qualifier = ref.getQualifier();
        final PsiElement place;
        if (qualifier instanceof PsiJavaCodeReferenceElement) {
          place = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        }
        else {
          if (parent instanceof PsiNewExpression) {
            final PsiExpression newQualifier = ((PsiNewExpression)parent).getQualifier();
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
        final PsiTypeParameterListOwner owner = ((PsiTypeParameter)resolved).getOwner();
        if (owner instanceof PsiClass) {
          final PsiClass outerClass = (PsiClass)owner;
          if (!InheritanceUtil.hasEnclosingInstanceInScope(outerClass, ref, false, false)) {
            myHolder.add(HighlightClassUtil.reportIllegalEnclosingUsage(ref, null, (PsiClass)owner, ref));
          }
        }
      }
    }

    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkPackageAndClassConflict(ref, myFile));

    return result;
  }

  @Nullable
  private JavaResolveResult resolveOptimised(@NotNull PsiJavaCodeReferenceElement ref) {
    try {
      if (ref instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
        JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(ref, resolver, true, true, myFile);
        return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
      }
      else {
        return ref.advancedResolve(true);
      }
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Nullable
  private JavaResolveResult[] resolveOptimised(@NotNull PsiReferenceExpression expression) {
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
      if (!myHolder.hasErrorResults()) {
        try {
          myHolder.add(HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, (PsiVariable)resolved, myUninitializedVarProblems,myFile));
        }
        catch (IndexNotReadyException ignored) { }
      }
      PsiVariable variable = (PsiVariable)resolved;
      boolean isFinal = variable.hasModifierProperty(PsiModifier.FINAL);
      if (isFinal && !variable.hasInitializer()) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, myFinalVarProblems));
        }
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkFinalVariableInitializedInLoop(expression, resolved));
      }
    }

    PsiElement parent = expression.getParent();
    if (parent instanceof PsiMethodCallExpression &&
        ((PsiMethodCallExpression)parent).getMethodExpression() == expression &&
        (!result.isAccessible() || !result.isStaticsScopeCorrect())) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent;
      PsiExpressionList list = methodCallExpression.getArgumentList();
      if (!HighlightMethodUtil.isDummyConstructorCall(methodCallExpression, myResolveHelper, list, expression)) {
        try {
          myHolder.add(HighlightMethodUtil.checkAmbiguousMethodCallIdentifier(
            expression, results, list, resolved, result, methodCallExpression, myResolveHelper, myLanguageLevel, myFile));

          if (!PsiTreeUtil.findChildrenOfType(methodCallExpression.getArgumentList(), PsiLambdaExpression.class).isEmpty()) {
            PsiElement nameElement = expression.getReferenceNameElement();
            if (nameElement != null) {
              myHolder.add(HighlightMethodUtil.checkAmbiguousMethodCallArguments(
                expression, results, list, resolved, result, methodCallExpression, myResolveHelper, nameElement));
            }
          }
        }
        catch (IndexNotReadyException ignored) { }
      }
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
    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    myHolder.add(HighlightUtil.checkUnqualifiedSuperInDefaultMethod(myLanguageLevel, expression, qualifierExpression));
    if (!myHolder.hasErrorResults() && qualifierExpression != null) {
      PsiType type = qualifierExpression.getType();
      if (type instanceof PsiCapturedWildcardType) {
        type = ((PsiCapturedWildcardType)type).getUpperBound();
      }
      PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass != null) {
        if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkClassSupersAccessibility(psiClass, expression));
        if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkMemberSignatureTypesAccessibility(expression));
      }
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    myHolder.add(checkFeature(expression, Feature.METHOD_REFERENCES));
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiExpressionStatement) return;

    final JavaResolveResult result;
    final JavaResolveResult[] results;
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
    final PsiElement method = result.getElement();
    if (method != null && !result.isAccessible()) {
      String accessProblem = HighlightUtil.buildProblemWithAccessDescription(expression, method, result);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(accessProblem).create();
      HighlightUtil.registerAccessQuickFixAction((PsiMember)method, expression, info, result.getCurrentFileResolveScope());
      myHolder.add(info);
    }
    else {
      final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (method instanceof PsiMethod && !expression.isConstructor()) {
        PsiElement methodNameElement = expression.getReferenceNameElement();
        if (methodNameElement != null) {
          myHolder.add(HighlightNamesUtil.highlightMethodName((PsiMethod)method, methodNameElement, false, colorsScheme));
        }
      }
      myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(expression, colorsScheme));
    }

    if (!LambdaUtil.isValidLambdaContext(parent)) {
      String description = "Method reference expression is not expected here";
      myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create());
    }

    final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (!myHolder.hasErrorResults()) {
      if (functionalInterfaceType != null) {
        final boolean notFunctional = !LambdaUtil.isFunctionalType(functionalInterfaceType);
        if (notFunctional) {
          String description = functionalInterfaceType.getPresentableText() + " is not a functional interface";
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create());
        }
      }
    }
      if (!myHolder.hasErrorResults()) {
        final PsiElement referenceNameElement = expression.getReferenceNameElement();
        if (referenceNameElement instanceof PsiKeyword) {
          if (!PsiMethodReferenceUtil.isValidQualifier(expression)) {
            PsiElement qualifier = expression.getQualifier();
            if (qualifier != null) {
              String description = "Cannot find class " + qualifier.getText();
              myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description).create());
            }
          }
        }
      }
    if (!myHolder.hasErrorResults()) {
      checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType);
    }

    if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
      final String errorMessage = PsiMethodReferenceUtil.checkMethodReferenceContext(expression);
      if (errorMessage != null) {
        final HighlightInfo info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(errorMessage).create();
        if (method instanceof PsiMethod && !((PsiMethod)method).isConstructor() &&
            !((PsiMethod)method).hasModifierProperty(PsiModifier.ABSTRACT)) {
          final boolean shouldHave = !((PsiMethod)method).hasModifierProperty(PsiModifier.STATIC);
          final LocalQuickFixAndIntentionActionOnPsiElement fixStaticModifier =
            QuickFixFactory.getInstance().createModifierListFix((PsiModifierListOwner)method, PsiModifier.STATIC, shouldHave, false);
          QuickFixAction.registerQuickFixAction(info, fixStaticModifier);
        }
        myHolder.add(info);
      }
    }

    if (!myHolder.hasErrorResults()) {
      PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiTypeElement) {
        final PsiType psiType = ((PsiTypeElement)qualifier).getType();
        final HighlightInfo genericArrayCreationInfo = GenericsHighlightUtil.checkGenericArrayCreation(qualifier, psiType);
        if (genericArrayCreationInfo != null) {
          myHolder.add(genericArrayCreationInfo);
        }
        else {
          final String wildcardMessage = PsiMethodReferenceUtil.checkTypeArguments((PsiTypeElement)qualifier, psiType);
          if (wildcardMessage != null) {
            myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(wildcardMessage).create());
          }
        }
      }
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(PsiMethodReferenceHighlightingUtil.checkRawConstructorReference(expression));
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnhandledExceptions(expression, expression.getTextRange()));
    }

    if (!myHolder.hasErrorResults()) {
      final String badReturnTypeMessage = PsiMethodReferenceUtil.checkReturnType(expression, result, functionalInterfaceType);
      if (badReturnTypeMessage != null) {
        HighlightInfo info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(badReturnTypeMessage).create();
        QuickFixAction.registerQuickFixAction(info, AdjustFunctionContextFix.createFix(expression));
        myHolder.add(info);
      }
    }

    if (!myHolder.hasErrorResults()) {
      if (results.length == 0 || results[0] instanceof MethodCandidateInfo &&
                                 !((MethodCandidateInfo)results[0]).isApplicable() &&
                                 functionalInterfaceType != null) {
        String description = null;
        if (results.length == 1) {
          description = ((MethodCandidateInfo)results[0]).getInferenceErrorMessage();
        }
        if (expression.isConstructor()) {
          final PsiClass containingClass = PsiMethodReferenceUtil.getQualifierResolveResult(expression).getContainingClass();

          if (containingClass != null) {
            if (!myHolder.add(HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, expression)) &&
                !myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression, containingClass)) &&
                containingClass.isPhysical() &&
                description == null) {
              description = JavaErrorMessages.message("cannot.resolve.constructor", containingClass.getName());
            }
          }
        }
        else if (description == null){
          description = JavaErrorMessages.message("cannot.resolve.method", expression.getReferenceName());
        }

        if (description != null) {
          PsiElement referenceNameElement = notNull(expression.getReferenceNameElement(), expression);
          HighlightInfoType type = results.length == 0 ? HighlightInfoType.WRONG_REF : HighlightInfoType.ERROR;
          HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(type).descriptionAndTooltip(description).range(referenceNameElement).create();
          myHolder.add(highlightInfo);
          TextRange fixRange = HighlightMethodUtil.getFixRange(referenceNameElement);
          QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QuickFixFactory.getInstance().createCreateMethodFromUsageFix(expression));
        }
      }
    }
  }

  // 15.13 | 15.27
  // It is a compile-time error if any class or interface mentioned by either U or the function type of U
  // is not accessible from the class or interface in which the method reference expression appears.
  private void checkFunctionalInterfaceTypeAccessible(@NotNull PsiFunctionalExpression expression, PsiType functionalInterfaceType) {
    PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    PsiClass psiClass = resolveResult.getElement();
    if (psiClass != null) {
      if (!PsiUtil.isAccessible(myFile.getProject(), psiClass, expression, null)) {
        String text = HighlightUtil.buildProblemWithAccessDescription(expression, psiClass, resolveResult);
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(text).create());
      }
      else {
        for (PsiType type : resolveResult.getSubstitutor().getSubstitutionMap().values()) {
          checkFunctionalInterfaceTypeAccessible(expression, type);
        }
      }
    }
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
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericCannotExtendException(list));
    }
  }

  @Override
  public void visitReferenceParameterList(PsiReferenceParameterList list) {
    if (list.getTextLength() == 0) return;

    myHolder.add(checkFeature(list, Feature.GENERICS));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkParametersAllowed(list));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkParametersOnRaw(list));
    if (!myHolder.hasErrorResults()) {
      for (PsiTypeElement typeElement : list.getTypeParameterElements()) {
        if (typeElement.getType() instanceof PsiDiamondType) {
          myHolder.add(checkFeature(list, Feature.DIAMOND_TYPES));
        }
      }
    }
  }

  @Override
  public void visitReturnStatement(PsiReturnStatement statement) {
    try {
      myHolder.add(HighlightUtil.checkReturnStatementType(statement));
    }
    catch (IndexNotReadyException ignore) { }
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
  public void visitSwitchStatement(PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    myHolder.add(HighlightUtil.checkStatementPrependedWithCaseInsideSwitch(statement));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSwitchSelectorType(statement, myLanguageLevel));
  }

  @Override
  public void visitThisExpression(PsiThisExpression expr) {
    if (!(expr.getParent() instanceof PsiReceiverParameter)) {
      myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier(), myLanguageLevel));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expr, null, myFile));
      if (!myHolder.hasErrorResults()) visitExpression(expr);
    }
  }

  @Override
  public void visitThrowStatement(PsiThrowStatement statement) {
    myHolder.add(HighlightUtil.checkUnhandledExceptions(statement, null));
    if (!myHolder.hasErrorResults()) visitStatement(statement);
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    super.visitTryStatement(statement);
    if (!myHolder.hasErrorResults()) {
      final Set<PsiClassType> thrownTypes = HighlightUtil.collectUnhandledExceptions(statement);
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
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(resourceList, Feature.TRY_WITH_RESOURCES));
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
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(resource, Feature.REFS_AS_RESOURCE));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkResourceVariableIsFinal(resource));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTryResourceIsAutoCloseable(resource));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnhandledCloserExceptions(resource));
  }

  @Override
  public void visitTypeElement(PsiTypeElement type) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalType(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkReferenceTypeUsedAsTypeArgument(type, myLanguageLevel));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkWildcardUsage(type));
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
      myHolder.add(checkFeature(list, Feature.GENERICS));
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParametersList(list, typeParameters, myLanguageLevel));
    }
  }

  @Override
  public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);
    try {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableInitializerType(variable));
    }
    catch (IndexNotReadyException ignored) { }
  }

  private boolean isReassigned(@NotNull PsiVariable variable) {
    try {
      boolean reassigned;
      if (variable instanceof PsiParameter) {
        reassigned = myReassignedParameters.get((PsiParameter)variable) == 2;
      }
      else  {
        reassigned = HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems);
      }

      return reassigned;
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  @Override
  public void visitConditionalExpression(PsiConditionalExpression expression) {
    super.visitConditionalExpression(expression);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) && PsiPolyExpressionUtil.isPolyExpression(expression)) {
      final PsiExpression thenExpression = expression.getThenExpression();
      final PsiExpression elseExpression = expression.getElseExpression();
      if (thenExpression != null && elseExpression != null) {
        final PsiType conditionalType = expression.getType();
        if (conditionalType != null) {
          final PsiExpression[] sides = {thenExpression, elseExpression};
          for (PsiExpression side : sides) {
            final PsiType sideType = side.getType();
            if (sideType != null && !TypeConversionUtil.isAssignable(conditionalType, sideType)) {
              myHolder.add(HighlightUtil.checkAssignability(conditionalType, sideType, side, side));
            }
          }
        }
      }
    }
  }

  @Override
  public void visitReceiverParameter(PsiReceiverParameter parameter) {
    super.visitReceiverParameter(parameter);
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(parameter, Feature.RECEIVERS));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkReceiverPlacement(parameter));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkReceiverType(parameter));
  }

  @Override
  public void visitModule(PsiJavaModule module) {
    super.visitModule(module);
    if (!myHolder.hasErrorResults()) myHolder.add(checkFeature(module, Feature.MODULES));
    if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkFileName(module, myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkFileDuplicates(module, myFile));
    if (!myHolder.hasErrorResults()) myHolder.addAll(ModuleHighlightUtil.checkDuplicateStatements(module));
    if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkClashingReads(module));
    if (!myHolder.hasErrorResults()) myHolder.addAll(ModuleHighlightUtil.checkUnusedServices(module));
    if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkFileLocation(module, myFile));
  }

  @Override
  public void visitRequiresStatement(PsiRequiresStatement statement) {
    super.visitRequiresStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      PsiJavaModule container = (PsiJavaModule)statement.getParent();
      PsiJavaModuleReferenceElement ref = statement.getReferenceElement();
      if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkModuleReference(ref, container));
    }
  }

  @Override
  public void visitPackageAccessibilityStatement(PsiPackageAccessibilityStatement statement) {
    super.visitPackageAccessibilityStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkHostModuleStrength(statement));
      if (!myHolder.hasErrorResults()) myHolder.add(ModuleHighlightUtil.checkPackageReference(statement));
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
      if (!myHolder.hasErrorResults()) myHolder.addAll(ModuleHighlightUtil.checkServiceImplementations(statement));
    }
  }

  @Nullable
  private HighlightInfo checkFeature(@NotNull PsiElement element, @NotNull Feature feature) {
    return HighlightUtil.checkFeature(element, feature, myLanguageLevel, myFile);
  }
}