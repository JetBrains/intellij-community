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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MostlySingularMultiMap;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor {
  private final PsiResolveHelper myResolveHelper;

  private HighlightInfoHolder myHolder;

  private RefCountHolder myRefCountHolder;

  // map codeBlock->List of PsiReferenceExpression of uninitialized final variables
  private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new THashMap<PsiElement, Collection<PsiReferenceExpression>>();
  // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new THashMap<PsiElement, Collection<ControlFlowUtil.VariableInfo>>();

  // value==1: no info if the parameter was reassigned (but the parameter is present in current file), value==2: parameter was reassigned
  private final TObjectIntHashMap<PsiParameter> myReassignedParameters = new TObjectIntHashMap<PsiParameter>();

  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> mySingleImportedClasses = new THashMap<String, Pair<PsiImportStaticReferenceElement, PsiClass>>();
  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiField>> mySingleImportedFields = new THashMap<String, Pair<PsiImportStaticReferenceElement, PsiField>>();
  private PsiFile myFile;
  private final PsiElementVisitor REGISTER_REFERENCES_VISITOR = new PsiRecursiveElementWalkingVisitor() {
    @Override public void visitElement(PsiElement element) {
      super.visitElement(element);
      for (PsiReference reference : element.getReferences()) {
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiNamedElement) {
          myRefCountHolder.registerLocallyReferenced((PsiNamedElement)resolved);
        }
      }
    }
  };
  private final Map<PsiClass, MostlySingularMultiMap<MethodSignature, PsiMethod>> myDuplicateMethods = new THashMap<PsiClass, MostlySingularMultiMap<MethodSignature, PsiMethod>>();
  private LanguageLevel myLanguageLevel;
  private JavaSdkVersion myJavaSdkVersion;

  public HighlightVisitorImpl(@NotNull PsiResolveHelper resolveHelper) {
    myResolveHelper = resolveHelper;
  }

  @NotNull
  private MostlySingularMultiMap<MethodSignature, PsiMethod> getDuplicateMethods(PsiClass aClass) {
    MostlySingularMultiMap<MethodSignature, PsiMethod> signatures = myDuplicateMethods.get(aClass);
    if (signatures == null) {
      signatures = new MostlySingularMultiMap<MethodSignature, PsiMethod>();
      for (PsiMethod method : aClass.getMethods()) {
        if (method instanceof ExternallyDefinedPsiElement) continue; // ignore aspectj-weaved methods; they are checked elsewhere
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        signatures.add(signature, method);
      }

      myDuplicateMethods.put(aClass, signatures);
    }
    return signatures;
  }

  @Override
  @NotNull
  public HighlightVisitorImpl clone() {
    return new HighlightVisitorImpl(myResolveHelper);
  }

  @Override
  public int order() {
    return 0;
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    return !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    element.accept(this);
  }

  private void registerReferencesFromInjectedFragments(@NotNull PsiElement element) {
    InjectedLanguageManager.getInstance(myFile.getProject()).enumerateEx(element, myFile, false,
                                                                         new PsiLanguageInjectionHost.InjectedPsiVisitor() {
                                                                           @Override
                                                                           public void visit(@NotNull final PsiFile injectedPsi,
                                                                                             @NotNull final List<PsiLanguageInjectionHost.Shred> places) {
                                                                             injectedPsi.accept(REGISTER_REFERENCES_VISITOR);
                                                                           }
                                                                         });
  }

  @Override
  public boolean analyze(@NotNull final PsiFile file,
                         final boolean updateWholeFile,
                         @NotNull HighlightInfoHolder holder,
                         @NotNull final Runnable action) {
    myFile = file;
    myHolder = holder;
    boolean success = true;
    try {
      myLanguageLevel = PsiUtil.getLanguageLevel(file);
      myJavaSdkVersion = ObjectUtils.notNull(JavaVersionService.getInstance().getJavaSdkVersion(file), JavaSdkVersion.fromLanguageLevel(myLanguageLevel));
      if (updateWholeFile) {
        Project project = file.getProject();
        DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
        FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator == null) throw new IllegalStateException("Must be run under progress");
        RefCountHolder refCountHolder = RefCountHolder.startUsing(file, indicator);
        myRefCountHolder = refCountHolder;
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        TextRange dirtyScope = document == null ? file.getTextRange() : fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL);
        success = refCountHolder.analyze(file, dirtyScope, action, indicator);
      }
      else {
        myRefCountHolder = null;
        action.run();
      }
    }
    finally {
      myUninitializedVarProblems.clear();
      myFinalVarProblems.clear();
      mySingleImportedClasses.clear();
      mySingleImportedFields.clear();
      myReassignedParameters.clear();

      myRefCountHolder = null;
      myFile = null;
      myHolder = null;
      myDuplicateMethods.clear();
    }

    return success;
  }

  @Override
  public void visitElement(final PsiElement element) {
    if (myRefCountHolder != null && myFile instanceof ServerPageFile) {
      // in jsp XmlAttributeValue may contain java references
      try {
        for (PsiReference reference : element.getReferences()) {
          if(reference instanceof PsiJavaReference){
            final PsiJavaReference psiJavaReference = (PsiJavaReference)reference;
            myRefCountHolder.registerReference(psiJavaReference, psiJavaReference.advancedResolve(false));
          }
        }
      }
      catch (IndexNotReadyException ignored) {
      }
    }
  }

  @Override
  public void visitAnnotation(PsiAnnotation annotation) {
    super.visitAnnotation(annotation);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAnnotationFeature(annotation, myLanguageLevel, myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkApplicability(annotation, myLanguageLevel,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkAnnotationType(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkMissingAttributes(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkTargetAnnotationDuplicates(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkDuplicateAnnotations(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkForeignInnerClassesUsed(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkFunctionalInterface(annotation, myLanguageLevel));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkRepeatableAnnotation(annotation));
  }

  @Override
  public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    PsiMethod method = null;
    PsiElement parent = initializer.getParent();
    if (parent instanceof PsiNameValuePair) {
      method = (PsiMethod)parent.getReference().resolve();
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

    myHolder.add(AnnotationsHighlightUtil.checkValidAnnotationType(method.getReturnTypeElement()));
    myHolder.add(AnnotationsHighlightUtil.checkCyclicMemberType(method.getReturnTypeElement(), method.getContainingClass()));
    myHolder.add(AnnotationsHighlightUtil.checkClashesWithSuperMethods(method));
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
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssignmentOperatorApplicable(assignment,myFile));
    if (!myHolder.hasErrorResults()) visitExpression(assignment);
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    super.visitPolyadicExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkPolyadicOperatorApplicable(expression));
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    myHolder.add(HighlightUtil.checkLambdaFeature(expression, myLanguageLevel,myFile));
    if (!myHolder.hasErrorResults()) {
      if (LambdaUtil.isValidLambdaContext(expression.getParent())) {
        final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
        if (functionalInterfaceType != null) {
          final String notFunctionalMessage = LambdaHighlightingUtil.checkInterfaceFunctional(functionalInterfaceType);
          if (notFunctionalMessage != null) {
            HighlightInfo result =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(notFunctionalMessage)
                .create();
            myHolder.add(result);
          }
          else {
            if (!LambdaUtil.isLambdaFullyInferred(expression, functionalInterfaceType) && !expression.hasFormalParameterTypes()) {
              HighlightInfo result =
                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip("Cyclic inference")
                  .create();
              myHolder.add(result); //todo[ann] append not inferred type params info
            }
            else {
              final String incompatibleReturnTypesMessage = LambdaHighlightingUtil
                .checkReturnTypeCompatible(expression, LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType));
              if (incompatibleReturnTypesMessage != null) {
                HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                  .descriptionAndTooltip(incompatibleReturnTypesMessage).create();
                myHolder.add(result);
              }
              else {
                final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
                final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
                if (interfaceMethod != null) {
                  final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
                  final PsiParameter[] lambdaParameters = expression.getParameterList().getParameters();
                  final String incompatibleTypesMessage = "Incompatible parameter types in lambda expression";
                  if (lambdaParameters.length != parameters.length) {
                    HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                      .descriptionAndTooltip(incompatibleTypesMessage).create();
                    myHolder.add(result);
                  }
                  else {
                    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult);
                    if (expression.hasFormalParameterTypes()) {
                      for (int i = 0; i < lambdaParameters.length; i++) {
                        if (!PsiTypesUtil.compareTypes(lambdaParameters[i].getType(), substitutor.substitute(parameters[i].getType()), true)) {
                          HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(lambdaParameters[i])
                            .descriptionAndTooltip(incompatibleTypesMessage)
                            .create();
                          myHolder.add(result);
                          break;
                        }
                      }
                    } else {
                      for (int i = 0; i < lambdaParameters.length; i++) {
                        PsiParameter lambdaParameter = lambdaParameters[i];
                        if (!TypeConversionUtil.isAssignable(lambdaParameter.getType(),
                                                             GenericsUtil.eliminateWildcards(substitutor.substitute(parameters[i].getType())))) {
                          HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(lambdaParameter)
                            .descriptionAndTooltip(incompatibleTypesMessage).create();
                          myHolder.add(result);
                          break;
                        }
                      }
                    }
                  }
                  if (!myHolder.hasErrorResults()) {
                    final PsiClass samClass = resolveResult.getElement();
                    if (!PsiUtil.isAccessible(myFile.getProject(), samClass, expression, null)) {
                      myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                                     .descriptionAndTooltip(HighlightUtil.buildProblemWithAccessDescription(expression, resolveResult)).create());
                    }
                  }
                }
              }
            }
          }
        } else if (LambdaUtil.getFunctionalInterfaceType(expression, true) != null) {
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip("Cannot infer functional interface type").create());
        }
      }
      else {
        HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
          .descriptionAndTooltip("Lambda expression not expected here").create();
        myHolder.add(result);
      }
      if (!myHolder.hasErrorResults()) {
        final PsiElement body = expression.getBody();
        if (body instanceof PsiCodeBlock) {
          myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement((PsiCodeBlock)body));
        }
      }
    }
  }

  @Override
  public void visitBreakStatement(PsiBreakStatement statement) {
    super.visitBreakStatement(statement);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findExitedStatement()));
    }
  }

  @Override
  public void visitClass(PsiClass aClass) {
    super.visitClass(aClass);
    if (aClass instanceof PsiSyntheticClass) return;
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInterfaceMultipleInheritance(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateTopLevelClass(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumMustNotBeLocal(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkImplicitThisReferenceBeforeSuper(aClass, myJavaSdkVersion));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassAndPackageConflict(aClass));
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
        myHolder.add(HighlightNamesUtil.highlightMethodName((PsiMethod)element, ((PsiDocMethodOrFieldRef)value).getNameElement(), false,
                                                            colorsScheme));
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

    if (expression.getParent() instanceof PsiNewExpression
        && ((PsiNewExpression)expression.getParent()).getQualifier() != expression
        && ((PsiNewExpression)expression.getParent()).getArrayInitializer() != expression) {
      // like in 'new String["s"]'
      myHolder.add(HighlightUtil.checkAssignability(PsiType.INT, expression.getType(), expression, expression));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkCannotWriteToFinal(expression,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableExpected(expression));
    if (!myHolder.hasErrorResults()) myHolder.addAll(HighlightUtil.checkArrayInitializer(expression, type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTernaryOperatorConditionIsBoolean(expression, type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssertOperatorTypes(expression, type));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSynchronizedExpressionType(expression, type,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkConditionalExpressionBranchTypesMatch(expression, type));
    if (!myHolder.hasErrorResults()
        && expression.getParent() instanceof PsiThrowStatement
        && ((PsiThrowStatement)expression.getParent()).getException() == expression) {
      myHolder.add(HighlightUtil.checkMustBeThrowable(type, expression, true));
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(AnnotationsHighlightUtil.checkConstantExpression(expression));
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
  public void visitForeachStatement(final PsiForeachStatement statement) {
    myHolder.add(HighlightUtil.checkForEachFeature(statement, myLanguageLevel, myFile));
  }

  @Override
  public void visitImportStaticStatement(final PsiImportStaticStatement statement) {
    myHolder.add(HighlightUtil.checkStaticImportFeature(statement, myLanguageLevel, myFile));
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
      if (!isMethodParameter) { // method params are highlighted in visitMethod since we should make sure the method body was visited before
        if (HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems)) {
          myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, identifier));
        }
        else {
          myHolder.add(HighlightNamesUtil.highlightVariableName(variable, identifier, colorsScheme));
        }
      }
      else {
        myReassignedParameters.put((PsiParameter)variable, 1); // mark param as present in current file
      }

      myHolder.add(HighlightUtil.checkUnderscore(identifier, variable));
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      if (aClass.isAnnotationType()) {
        myHolder.add(HighlightUtil.checkAnnotationFeature(identifier, myLanguageLevel, myFile));
      }

      myHolder.add(HighlightClassUtil.checkClassAlreadyImported(aClass, identifier));
      if (!(parent instanceof PsiAnonymousClass) && aClass.getNameIdentifier() == identifier) {
        myHolder.add(HighlightNamesUtil.highlightClassName(aClass, identifier, colorsScheme));
      }
    }
    else if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      if (method.isConstructor()) {
        myHolder.add(HighlightMethodUtil.checkConstructorName(method));
      }
      myHolder.add(HighlightNamesUtil.highlightMethodName(method, identifier, true, colorsScheme));
    }
    else {
      visitParentReference(parent);
    }

    super.visitIdentifier(identifier);
  }

  private void visitParentReference(PsiElement parent) {
    if (parent instanceof PsiJavaCodeReferenceElement && !(parent.getParent() instanceof PsiJavaCodeReferenceElement) &&
             !((PsiJavaCodeReferenceElement)parent).isQualified()) {
      PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)parent;
      JavaResolveResult result;
      try {
        result = ref.advancedResolve(true);
      }
      catch (IndexNotReadyException e) {
        return;
      }
      myHolder.add(HighlightUtil.checkReference(ref, result, myFile, myLanguageLevel));
      if (myRefCountHolder != null) {
        myRefCountHolder.registerReference(ref, result);
      }
    }
  }

  @Override
  public void visitImportStatement(final PsiImportStatement statement) {
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkSingleImportClassConflict(statement, mySingleImportedClasses,myFile));
    }
  }

  @Override
  public void visitImportStaticReferenceElement(final PsiImportStaticReferenceElement ref) {
    final String refName = ref.getReferenceName();
    final JavaResolveResult[] results = ref.multiResolve(false);

    if (results.length == 0) {
      final String description = JavaErrorMessages.message("cannot.resolve.symbol", refName);
      final PsiElement nameElement = ref.getReferenceNameElement();
      assert nameElement != null : ref;
      final HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(nameElement).descriptionAndTooltip(description).create();
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
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkPublicClassInRightFile(keyword, psiModifierList));
      if (PsiModifier.ABSTRACT.equals(text) && psiModifierList.getParent() instanceof PsiMethod) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkAbstractMethodInConcreteClass((PsiMethod)psiModifierList.getParent(), keyword));
        }
      }
    }
    else if (PsiKeyword.CONTINUE.equals(text) && parent instanceof PsiContinueStatement) {
      PsiContinueStatement statement = (PsiContinueStatement)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkContinueOutsideLoop(statement));
    }
    else if (PsiKeyword.BREAK.equals(text) && parent instanceof PsiBreakStatement) {
      PsiBreakStatement statement = (PsiBreakStatement)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkBreakOutsideLoop(statement));
    }
    else if (PsiKeyword.INTERFACE.equals(text) && parent instanceof PsiClass) {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkInterfaceCannotBeLocal((PsiClass)parent));
    }
    else {
      visitParentReference(parent);
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkStaticDeclarationInInnerClass(keyword));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalVoidType(keyword));

    if (PsiTreeUtil.getParentOfType(keyword, PsiDocTagValue.class) != null) {
      HighlightInfo result = HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.JAVA_KEYWORD).range(keyword).create();
      myHolder.add(result);
    }
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
  }

  @Override
  public void visitMethod(PsiMethod method) {
    super.visitMethod(method);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(method.getBody()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorHandleSuperClassExceptions(method));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkRecursiveConstructorInvocation(method));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkOverrideAnnotation(method, myLanguageLevel));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkSafeVarargsAnnotation(method));
    if (!myHolder.hasErrorResults() && method.isConstructor()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(method, method.getContainingClass()));
    }

    // method params are highlighted in visitMethod since we should make sure the method body was visited before
    PsiParameter[] parameters = method.getParameterList().getParameters();
    final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();

    for (PsiParameter parameter : parameters) {
      int info = myReassignedParameters.get(parameter);
      if (info == 0) continue; // out of this file
      if (info == 2) {// reassigned
        myHolder.add(HighlightNamesUtil.highlightReassignedVariable(parameter, parameter.getNameIdentifier()));
      }
      else {
        myHolder.add(HighlightNamesUtil.highlightVariableName(parameter, parameter.getNameIdentifier(), colorsScheme));
      }
    }
  }

  private void highlightReferencedMethodOrClassName(PsiJavaCodeReferenceElement element, PsiElement resolved) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiJavaCodeReferenceElement) {
      return;
    }
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
        if (method == null) {
          if (resolved instanceof PsiClass) {
            myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, element, colorsScheme));
          }
        }
        else {
          final PsiElement referenceNameElement = element.getReferenceNameElement();
          if(referenceNameElement != null) {
            // exclude type parameters from the highlighted text range
            TextRange range = new TextRange(element.getTextRange().getStartOffset(), referenceNameElement.getTextRange().getEndOffset());
            myHolder.add(HighlightNamesUtil.highlightMethodName(method, referenceNameElement, range, colorsScheme, false));
          }
        }
      }
      catch (IndexNotReadyException ignored) {
      }
    }
    else if (parent instanceof PsiImportStatement && ((PsiImportStatement)parent).isOnDemand()) {
      // highlight on demand import as class
      myHolder.add(HighlightNamesUtil.highlightClassName(null, element, colorsScheme));
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
        myHolder.add(HighlightMethodUtil.checkMethodCall(expression, myResolveHelper, myLanguageLevel,myJavaSdkVersion));
      }
      catch (IndexNotReadyException ignored) {
      }
    }

    if (!myHolder.hasErrorResults()) visitExpression(expression);
  }

  @Override
  public void visitModifierList(PsiModifierList list) {
    super.visitModifierList(list);
    PsiElement parent = list.getParent();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodCanHaveBody(method, myLanguageLevel,myFile));
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
        catch (IndexNotReadyException ignored) {
        }
      }
      PsiClass aClass = method.getContainingClass();
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodMustHaveBody(method, aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkDuplicateMethod(aClass, method, getDuplicateMethods(aClass)));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallsBaseClassConstructor(method, myRefCountHolder, myResolveHelper));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkStaticMethodOverride(method,myFile));
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
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkOverrideEquivalentInheritedMethods(aClass, myFile));
        if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass));
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
        HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ANNOTATION_ATTRIBUTE_NAME).range(nameId).create();
        myHolder.add(result);
      }
    }
  }

  @Override
  public void visitNewExpression(PsiNewExpression expression) {
    myHolder.add(HighlightUtil.checkUnhandledExceptions(expression, null));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAnonymousInheritFinal(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkQualifiedNew(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParameterInstantiation(expression));
    try {
      if (!myHolder.hasErrorResults()) HighlightMethodUtil.checkNewExpression(expression, myHolder, myJavaSdkVersion);
    }
    catch (IndexNotReadyException ignored) {
    }
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()));
    if (!myHolder.hasErrorResults()) registerConstructorCall(expression);

    if (!myHolder.hasErrorResults()) visitExpression(expression);
  }

  @Override
  public void visitPackageStatement(PsiPackageStatement statement) {
    super.visitPackageStatement(statement);
    myHolder.add(AnnotationsHighlightUtil.checkPackageAnnotationContainingFile(statement));
  }

  @Override
  public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);

    final PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList) {
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkVarArgParameterIsLast(parameter,
                                                                                                     myLanguageLevel,myFile));
    }
    else if (parent instanceof PsiForeachStatement) {
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkForeachLoopParameterType((PsiForeachStatement)parent));
    }
    else if (parent instanceof PsiCatchSection) {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkMultiCatchFeature(parameter, myLanguageLevel,myFile));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkCatchParameterIsThrowable(parameter));
      if (!myHolder.hasErrorResults()) myHolder.addAll(GenericsHighlightUtil.checkCatchParameterIsClass(parameter));
      if (!myHolder.hasErrorResults()) myHolder.addAll(HighlightUtil.checkCatchTypeIsDisjoint(parameter));
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

  private void registerConstructorCall(PsiConstructorCall constructorCall) {
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
    doVisitReferenceElement(ref);
  }

  private JavaResolveResult doVisitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result;
    try {
      if (ref instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl referenceExpression = (PsiReferenceExpressionImpl)ref;
        JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(referenceExpression,
                                                                                 PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE,
                                                                                 true, true,
                                                                                 myFile);
        result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
      }
      else {
        result = ref.advancedResolve(true);
      }
    }
    catch (IndexNotReadyException e) {
      return null;
    }
    PsiElement resolved = result.getElement();
    PsiElement parent = ref.getParent();

    if (parent instanceof PsiJavaCodeReferenceElement || ref.isQualified()) {
      if (myRefCountHolder != null) {
        myRefCountHolder.registerReference(ref, result);
      }
      myHolder.add(HighlightUtil.checkReference(ref, result, myFile, myLanguageLevel));
      if (!myHolder.hasErrorResults() && resolved instanceof PsiTypeParameter) {
        boolean cannotSelectFromTypeParameter = !myJavaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7);
        if (!cannotSelectFromTypeParameter) {
          final PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
          if (containingClass != null) {
            if (PsiTreeUtil.isAncestor(containingClass.getExtendsList(), ref, false) ||
                PsiTreeUtil.isAncestor(containingClass.getImplementsList(), ref, false)) {
              cannotSelectFromTypeParameter = true;
            }
          }
        }
        if (cannotSelectFromTypeParameter) {
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip("Cannot select from a type parameter").range(ref).create());
        }
      }
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAbstractInstantiation(ref, resolved));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkExtendsDuplicate(ref, resolved,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassExtendsForeignInnerClass(ref, resolved));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkSelectStaticClassFromParameterizedType(resolved, ref));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, ref,
                                                                                                                 result.getSubstitutor(),
                                                                                                                 myJavaSdkVersion));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkCannotPassInner(ref));

    if (resolved != null && parent instanceof PsiReferenceList) {
      if (!myHolder.hasErrorResults()) {
        PsiReferenceList referenceList = (PsiReferenceList)parent;
        myHolder.add(HighlightUtil.checkElementInReferenceList(ref, referenceList, result, myLanguageLevel));
      }
    }

    if (parent instanceof PsiAnonymousClass && ref.equals(((PsiAnonymousClass)parent).getBaseClassReference())) {
      myHolder.add(GenericsHighlightUtil.checkOverrideEquivalentMethods((PsiClass)parent));
    }

    if (resolved instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)resolved;

      final PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
      if (containingClass instanceof PsiAnonymousClass &&
          !PsiTreeUtil.isAncestor(containingClass, variable, false) &&
          !(variable instanceof PsiField)) {
        if (!PsiTreeUtil.isAncestor(((PsiAnonymousClass) containingClass).getArgumentList(), ref, false)) {
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.IMPLICIT_ANONYMOUS_CLASS_PARAMETER).range(ref).create());
        }
      }

      if (variable instanceof PsiParameter && ref instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)ref)) {
        myReassignedParameters.put((PsiParameter)variable, 2);
      }

      final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (!variable.hasModifierProperty(PsiModifier.FINAL) && isReassigned(variable)) {
        myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, ref));
      }
      else {
        myHolder.add(HighlightNamesUtil.highlightVariableName(variable, ref.getReferenceNameElement(), colorsScheme));
      }
      myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(ref, colorsScheme));
    }
    else {
      highlightReferencedMethodOrClassName(ref, resolved);
    }

    if (parent instanceof PsiNewExpression && !(resolved instanceof PsiClass) && resolved instanceof PsiNamedElement && ((PsiNewExpression)parent).getClassOrAnonymousClassReference() == ref) {
       myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref)
                      .descriptionAndTooltip("Cannot find symbol " + ((PsiNamedElement)resolved).getName()).create());
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
          if (!InheritanceUtil.hasEnclosingInstanceInScope(outerClass, ref, true, false)) {
            myHolder.add(HighlightClassUtil.reportIllegalEnclosingUsage(ref, aClass, (PsiClass)owner, ref));
          }
        }
      }
    }

    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkPackageAndClassConflict(ref));

    return result;
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    JavaResolveResult resultForIncompleteCode = doVisitReferenceElement(expression);
    if (!myHolder.hasErrorResults()) {
      visitExpression(expression);
      if (myHolder.hasErrorResults()) return;
    }
    JavaResolveResult result;
    JavaResolveResult[] results;
    try {
      if (expression instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl referenceExpression = (PsiReferenceExpressionImpl)expression;
        results = JavaResolveUtil.resolveWithContainingFile(referenceExpression,
                                                             PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE, true, true,
                                                             myFile);
      }
      else {
        results = expression.multiResolve(true);
      }
      result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    }
    catch (IndexNotReadyException e) {
      return;
    }
    PsiElement resolved = result.getElement();
    if (resolved instanceof PsiVariable && resolved.getContainingFile() == expression.getContainingFile()) {
      if (!myHolder.hasErrorResults()) {
        try {
          myHolder.add(HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, (PsiVariable)resolved, myUninitializedVarProblems,myFile));
        }
        catch (IndexNotReadyException ignored) {
        }
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
    if (parent instanceof PsiMethodCallExpression && ((PsiMethodCallExpression)parent).getMethodExpression() == expression && (!result.isAccessible() || !result.isStaticsScopeCorrect())) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent;
      PsiExpressionList list = methodCallExpression.getArgumentList();
      if (!HighlightMethodUtil.isDummyConstructorCall(methodCallExpression, myResolveHelper, list, expression)) {
        try {
          HighlightInfo info = HighlightMethodUtil.checkAmbiguousMethodCall(expression, results, list, resolved, result, methodCallExpression, myResolveHelper);
          myHolder.add(info);
        }
        catch (IndexNotReadyException ignored) {
        }
      }
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkExpressionRequired(expression, resultForIncompleteCode));
    if (!myHolder.hasErrorResults() && resolved instanceof PsiField) {
      try {
        myHolder.add(HighlightUtil.checkIllegalForwardReferenceToField(expression, (PsiField)resolved));
      }
      catch (IndexNotReadyException ignored) {
      }
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallMustBeFirstStatement(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkAccessStaticFieldFromEnumConstructor(expression, result));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkClassReferenceAfterQualifier(expression, resolved));
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    myHolder.add(HighlightUtil.checkMethodReferencesFeature(expression, myLanguageLevel,myFile));
    JavaResolveResult result;
    try {
      result = expression.advancedResolve(true);
    }
    catch (IndexNotReadyException e) {
      return;
    }
    if (myRefCountHolder != null) {
      myRefCountHolder.registerReference(expression, result);
    }
    final PsiElement method = result.getElement();
    if (method != null && !result.isAccessible()) {
      final String accessProblem = HighlightUtil.buildProblemWithAccessDescription(expression, result);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(accessProblem).create();
      myHolder.add(info);
    }
    if (!myHolder.hasErrorResults()) {
      final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
      if (functionalInterfaceType != null && LambdaUtil.dependsOnTypeParams(functionalInterfaceType, functionalInterfaceType, expression)) {
        HighlightInfo result1 =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip("Cyclic inference").create();
        myHolder.add(result1); //todo[ann] append not inferred type params info
      } else {
        final PsiElement referenceNameElement = expression.getReferenceNameElement();
        if (referenceNameElement instanceof PsiKeyword) {
          if (!PsiMethodReferenceUtil.isValidQualifier(expression)) {
            final PsiElement qualifier = expression.getQualifier();
            String description = "Cannot find class " + qualifier.getText();
            HighlightInfo result1 =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description).create();
            myHolder.add(result1);
          }
        }
      }
      if (!myHolder.hasErrorResults()) {
        final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
        final PsiClass psiClass = resolveResult.getElement();
        if (psiClass != null && !PsiUtil.isAccessible(myFile.getProject(), psiClass, expression, null)) {
          myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                         .descriptionAndTooltip(HighlightUtil.buildProblemWithAccessDescription(expression, resolveResult)).create());
        }

        final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
        if (interfaceMethod != null) {
          if (!myHolder.hasErrorResults()) {
            final String errorMessage = PsiMethodReferenceUtil.checkMethodReferenceContext(expression);
            if (errorMessage != null) {
              myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(errorMessage).create());
            }
          }

          if (!myHolder.hasErrorResults()) {
            final String badReturnTypeMessage = PsiMethodReferenceUtil.checkReturnType(expression, result, functionalInterfaceType);
            if (badReturnTypeMessage != null) {
              myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(badReturnTypeMessage).create());
            }
          }
        }
      }
    }

    if (!myHolder.hasErrorResults()) {
      PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiTypeElement) {
        final PsiType psiType = ((PsiTypeElement)qualifier).getType();
        final HighlightInfo genericArrayCreationInfo = GenericsHighlightUtil.checkGenericArrayCreation(qualifier, psiType);
        if (genericArrayCreationInfo != null) {
          myHolder.add(genericArrayCreationInfo);
        } else {
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

    if (!myHolder.hasErrorResults() && method instanceof PsiTypeParameterListOwner) {
      myHolder.add(GenericsHighlightUtil.checkInferredTypeArguments((PsiTypeParameterListOwner)method, expression, result.getSubstitutor()));
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
    myHolder.add(GenericsHighlightUtil.checkParametersAllowed(list, myLanguageLevel,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkParametersOnRaw(list));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkRawOnParameterizedType(list));
  }

  @Override
  public void visitReturnStatement(PsiReturnStatement statement) {
    try {
      myHolder.add(HighlightUtil.checkReturnStatementType(statement));
    }
    catch (IndexNotReadyException ignore) {
    }
  }

  @Override
  public void visitStatement(PsiStatement statement) {
    super.visitStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkNotAStatement(statement));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkStatementPrependedWithCaseInsideSwitch(statement));
  }

  @Override
  public void visitSuperExpression(PsiSuperExpression expr) {
    myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkAbstractMethodDirectCall(expr));
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
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSwitchSelectorType(statement));
  }

  @Override
  public void visitThisExpression(PsiThisExpression expr) {
    myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier()));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expr, null, myFile));
    }
    if (!myHolder.hasErrorResults()) {
      visitExpression(expr);
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
  public void visitResourceVariable(final PsiResourceVariable resourceVariable) {
    visitVariable(resourceVariable);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTryWithResourcesFeature(resourceVariable, myLanguageLevel,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTryResourceIsAutoCloseable(resourceVariable));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnhandledCloserExceptions(resourceVariable));
  }

  @Override
  public void visitTypeElement(final PsiTypeElement type) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkDiamondFeature(type, myLanguageLevel,myFile));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalType(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkReferenceTypeUsedAsTypeArgument(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkWildcardUsage(type));
  }

  @Override
  public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
    super.visitTypeCastExpression(typeCast);
    try {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIntersectionInTypeCast(typeCast));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInconvertibleTypeCast(typeCast));
    }
    catch (IndexNotReadyException ignore) {
    }
  }

  @Override
  public void visitTypeParameterList(PsiTypeParameterList list) {
    myHolder.add(GenericsHighlightUtil.checkTypeParametersList(list, myLanguageLevel,myFile));
  }

  @Override
  public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);
    try {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableInitializerType(variable));
    }
    catch (IndexNotReadyException ignored) {
    }
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
}
