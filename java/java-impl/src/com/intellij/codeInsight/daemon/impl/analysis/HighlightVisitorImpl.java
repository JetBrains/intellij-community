/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.SetupJDKFix;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor, DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl");

  private final PsiResolveHelper myResolveHelper;

  private HighlightInfoHolder myHolder;

  private RefCountHolder myRefCountHolder;

  // map codeBlock->List of PsiReferenceExpression of uninitialized final variables
  private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new THashMap<PsiElement, Collection<PsiReferenceExpression>>();
  // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new THashMap<PsiElement, Collection<ControlFlowUtil.VariableInfo>>();
  private final Map<PsiParameter, Boolean> myParameterIsReassigned = new THashMap<PsiParameter, Boolean>();

  private final Map<String, Pair<PsiImportStatementBase, PsiClass>> mySingleImportedClasses = new THashMap<String, Pair<PsiImportStatementBase, PsiClass>>();
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
  
  @SuppressWarnings({"UnusedDeclaration"}) //in plugin.xml
  public HighlightVisitorImpl(Project project) {
    this(JavaPsiFacade.getInstance(project).getResolveHelper());
  }

  private HighlightVisitorImpl(@NotNull PsiResolveHelper resolveHelper) {
    myResolveHelper = resolveHelper;
  }

  @NotNull
  public HighlightVisitorImpl clone() {
    return new HighlightVisitorImpl(myResolveHelper);
  }

  public int order() {
    return 0;
  }  

  public boolean suitableForFile(@NotNull PsiFile file) {
    return !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
  }

  public void visit(@NotNull PsiElement element, @NotNull HighlightInfoHolder holder) {
    myHolder = holder;

    if (LOG.isDebugEnabled()) {
      LOG.assertTrue(element.isValid());
    }
    element.accept(this);
  }

  private void registerReferencesFromInjectedFragments(final PsiElement element) {
    InjectedLanguageUtil.enumerate(element, myFile, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull final PsiFile injectedPsi, @NotNull final List<PsiLanguageInjectionHost.Shred> places) {
        injectedPsi.accept(REGISTER_REFERENCES_VISITOR);
      }
    }, false);
  }

  public boolean analyze(@NotNull final Runnable action, final boolean updateWholeFile, @NotNull final PsiFile file) {
    myFile = file;
    boolean success = true;
    try {
      if (updateWholeFile) {
        Project project = file.getProject();
        DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
        FileStatusMap fileStatusMap = ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap();
        RefCountHolder refCountHolder = RefCountHolder.getInstance(file);
        myRefCountHolder = refCountHolder;
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        TextRange dirtyScope = document == null ? file.getTextRange() : fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL);
        success = refCountHolder.analyze(action, dirtyScope, file);
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
      myParameterIsReassigned.clear();

      myRefCountHolder = null;
      myFile = null;
      myHolder = null;
    }

    return success;
  }

  public void visitElement(final PsiElement element) {
    if (element instanceof XmlAttributeValue) {
      try {
        for (PsiReference reference : element.getReferences()) {
          if(reference instanceof PsiJavaReference && myRefCountHolder != null){
            final PsiJavaReference psiJavaReference = (PsiJavaReference)reference;
            myRefCountHolder.registerReference(psiJavaReference, psiJavaReference.advancedResolve(false));
          }
        }
      }
      catch (IndexNotReadyException ignored) {
      }
    }
  }

  @Override public void visitAnnotation(PsiAnnotation annotation) {
    super.visitAnnotation(annotation);
    if (!PsiUtil.isLanguageLevel5OrHigher(annotation)) {
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, annotation, JavaErrorMessages.message("annotations.prior.15"));
      QuickFixAction.registerQuickFixAction(info, new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_5));
      myHolder.add(info);
      return;
    }

    myHolder.add(AnnotationsHighlightUtil.checkApplicability(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkAnnotationType(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkMissingAttributes(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkTargetAnnotationDuplicates(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkDuplicateAnnotations(annotation));
  }

  @Override public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    PsiMethod method = null;
    PsiElement parent = initializer.getParent();
    if (parent instanceof PsiNameValuePair) {
      method = (PsiMethod)parent.getReference().resolve();
    }
    else if (parent instanceof PsiAnnotationMethod) {
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

  @Override public void visitAnnotationMethod(PsiAnnotationMethod method) {
    PsiType returnType = method.getReturnType();
    PsiAnnotationMemberValue value = method.getDefaultValue();
    if (returnType != null && value != null) {
      myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(value, returnType));
    }

    myHolder.add(AnnotationsHighlightUtil.checkValidAnnotationType(method.getReturnTypeElement()));
    myHolder.add(AnnotationsHighlightUtil.checkCyclicMemberType(method.getReturnTypeElement(), method.getContainingClass()));
  }

  @Override public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    super.visitArrayInitializerExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkArrayInitializerApplicable(expression));
    if (!(expression.getParent() instanceof PsiNewExpression)) {
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()));
    }
  }

  @Override public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssignmentCompatibleTypes(assignment));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssignmentOperatorApplicable(assignment));
    if (!myHolder.hasErrorResults()) visitExpression(assignment);
  }

  @Override public void visitBinaryExpression(PsiBinaryExpression expression) {
    super.visitBinaryExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkBinaryOperatorApplicable(expression));
  }

  @Override public void visitBreakStatement(PsiBreakStatement statement) {
    super.visitBreakStatement(statement);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findExitedStatement()));
    }
  }

  @Override public void visitClass(PsiClass aClass) {
    super.visitClass(aClass);
    if (aClass instanceof JspClass) return;
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInterfaceMultipleInheritance(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateTopLevelClass(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumMustNotBeLocal(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkImplicitThisReferenceBeforeSuper(aClass));
  }

  @Override public void visitClassInitializer(PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkInitializerCompleteNormally(initializer));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(initializer.getBody()));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(initializer, initializer.getContainingClass()));
    }
  }

  @Override public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    super.visitClassObjectAccessExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkClassObjectAccessExpression(expression));
  }

  @Override public void visitComment(PsiComment comment) {
    super.visitComment(comment);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnclosedComment(comment));
    if (myRefCountHolder != null && !myHolder.hasErrorResults()) registerReferencesFromInjectedFragments(comment);
  }

  @Override public void visitContinueStatement(PsiContinueStatement statement) {
    super.visitContinueStatement(statement);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findContinuedStatement()));
    }
  }

  @Override public void visitJavaToken(PsiJavaToken token) {
    super.visitJavaToken(token);
    if (!myHolder.hasErrorResults()
        && token.getTokenType() == JavaTokenType.RBRACE
        && token.getParent() instanceof PsiCodeBlock
        && token.getParent().getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)token.getParent().getParent();
      myHolder.add(HighlightControlFlowUtil.checkMissingReturnStatement(method));
    }

  }

  @Override public void visitDocComment(PsiDocComment comment) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnclosedComment(comment));
  }

  @Override public void visitDocTagValue(PsiDocTagValue value) {
    PsiReference reference = value.getReference();
    if (reference != null) {
      PsiElement element = reference.resolve();
      final EditorColorsScheme colorsScheme = myHolder.getColorsScheme();
      if (element instanceof PsiMethod) {
        myHolder.add(HighlightNamesUtil.highlightMethodName((PsiMethod)element, ((PsiDocMethodOrFieldRef)value).getNameElement(), false,
                                                            colorsScheme));
      }
      else if (element instanceof PsiParameter) {
        myHolder.add(HighlightNamesUtil.highlightVariableName((PsiVariable)element, value.getNavigationElement(), colorsScheme));
      }
    }
  }

  @Override public void visitEnumConstant(PsiEnumConstant enumConstant) {
    super.visitEnumConstant(enumConstant);
    if (!myHolder.hasErrorResults()) GenericsHighlightUtil.checkEnumConstantForConstructorProblems(enumConstant, myHolder);
    if (!myHolder.hasErrorResults()) registerConstructorCall(enumConstant);
  }

  @Override public void visitEnumConstantInitializer(PsiEnumConstantInitializer enumConstantInitializer) {
    super.visitEnumConstantInitializer(enumConstantInitializer);
    if (!myHolder.hasErrorResults()) {
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(enumConstantInitializer);
      myHolder.add(HighlightClassUtil.checkClassMustBeAbstract(enumConstantInitializer, textRange));
    }
  }

  @Override public void visitExpression(PsiExpression expression) {
    ProgressManager.checkCanceled(); // visitLiteralExpression is invoked very often in array initializers
    
    super.visitExpression(expression);
    if (myHolder.add(HighlightUtil.checkMustBeBoolean(expression))) return;
    if (expression instanceof PsiArrayAccessExpression
        && ((PsiArrayAccessExpression)expression).getIndexExpression() != null) {
      myHolder.add(HighlightUtil.checkValidArrayAccessExpression(((PsiArrayAccessExpression)expression).getArrayExpression(),
                                                                 ((PsiArrayAccessExpression)expression).getIndexExpression()));
    }
    if (expression.getParent() instanceof PsiNewExpression
             && ((PsiNewExpression)expression.getParent()).getQualifier() != expression
             && ((PsiNewExpression)expression.getParent()).getArrayInitializer() != expression) {
      // like in 'new String["s"]'
      myHolder.add(HighlightUtil.checkValidArrayAccessExpression(null, expression));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkCannotWriteToFinal(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableExpected(expression));
    if (!myHolder.hasErrorResults()) myHolder.addAll(HighlightUtil.checkArrayInitializer(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTernaryOperatorConditionIsBoolean(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssertOperatorTypes(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSynchronizedExpressionType(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkConditionalExpressionBranchTypesMatch(expression));
    if (!myHolder.hasErrorResults()
        && expression.getParent() instanceof PsiThrowStatement
        && ((PsiThrowStatement)expression.getParent()).getException() == expression) {
      PsiType type = expression.getType();
      myHolder.add(HighlightUtil.checkMustBeThrowable(type, expression, true));
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(AnnotationsHighlightUtil.checkConstantExpression(expression));
    }
  }

  @Override public void visitField(PsiField field) {
    super.visitField(field);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkFinalFieldInitialized(field));
  }

  @Override public void visitForeachStatement(PsiForeachStatement statement) {
    if (!PsiUtil.isLanguageLevel5OrHigher(statement)) {
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement.getFirstChild(), JavaErrorMessages.message("foreach.prior.15"));
      QuickFixAction.registerQuickFixAction(info, new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_5));
      myHolder.add(info);
    }
  }

  @Override public void visitImportStaticStatement(PsiImportStaticStatement statement) {
    if (!PsiUtil.isLanguageLevel5OrHigher(statement)) {
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement.getFirstChild(), JavaErrorMessages.message("static.imports.prior.15"));
      QuickFixAction.registerQuickFixAction(info, new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_5));
      myHolder.add(info);
    }
  }

  @Override public void visitIdentifier(PsiIdentifier identifier) {
    PsiElement parent = identifier.getParent();
    final EditorColorsScheme colorsScheme = myHolder.getColorsScheme();
    if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;
      myHolder.add(HighlightUtil.checkVariableAlreadyDefined(variable));

      if (variable.getInitializer() == null) {
        final PsiElement child = variable.getLastChild();
        if (child instanceof PsiErrorElement && child.getPrevSibling() == identifier) return;
      }
      if (isReassigned(variable)) {
        myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, variable.getNameIdentifier()));
      }
      else {
        myHolder.add(HighlightNamesUtil.highlightVariableName(variable, identifier, colorsScheme));
      }
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      if (aClass.isAnnotationType() && !PsiUtil.isLanguageLevel5OrHigher(aClass)) {
        HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, identifier, JavaErrorMessages.message("annotations.prior.15"));
        QuickFixAction.registerQuickFixAction(info, new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_5));
        myHolder.add(info);
      }

      myHolder.add(HighlightClassUtil.checkClassAlreadyImported(aClass, identifier));
      myHolder.add(HighlightClassUtil.checkExternalizableHasPublicNoArgsConstructor(aClass, identifier));
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
      PsiElement resolved = result.getElement();
      myHolder.add(HighlightUtil.checkReference(ref, result, resolved));
      if (myRefCountHolder != null) {
        myRefCountHolder.registerReference(ref, result);
      }
    }
  }

  @Override public void visitImportStatement(PsiImportStatement statement) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSingleImportClassConflict(statement, mySingleImportedClasses));
  }

  @Override public void visitImportStaticReferenceElement(PsiImportStaticReferenceElement ref) {
    String refName = ref.getReferenceName();
    JavaResolveResult[] results = ref.multiResolve(false);

    if (results.length == 0) {
      String description = JavaErrorMessages.message("cannot.resolve.symbol", refName);
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getReferenceNameElement(), description);
      myHolder.add(info);
      QuickFixAction.registerQuickFixAction(info, SetupJDKFix.getInstnace());
    }
    else {
      PsiManager manager = ref.getManager();
      for (JavaResolveResult result : results) {
        PsiElement element = result.getElement();
        if (!(element instanceof PsiModifierListOwner) || !((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        @NonNls String messageKey = null;
        if (element instanceof PsiClass) {
          Pair<PsiImportStatementBase, PsiClass> imported = mySingleImportedClasses.get(refName);
          PsiClass aClass = imported == null ? null : imported.getSecond();
          PsiImportStaticStatement statement = (PsiImportStaticStatement)ref.getParent();

          if (aClass != null && !manager.areElementsEquivalent(aClass, element) && !imported.getFirst().equals(statement)) {
            messageKey = "class.is.already.defined.in.single.type.import";
          }
          mySingleImportedClasses.put(refName, Pair.<PsiImportStatementBase, PsiClass>create(statement, (PsiClass)element));
        }
        else if (element instanceof PsiField) {
          Pair<PsiImportStaticReferenceElement, PsiField> imported = mySingleImportedFields.get(refName);
          PsiField field = imported == null ? null : imported.getSecond();

          if (field != null && !manager.areElementsEquivalent(field, element) && !imported.getFirst().equals(ref.getParent())) {
            messageKey = "field.is.already.defined.in.single.type.import";
          }
          mySingleImportedFields.put(refName, Pair.create(ref, (PsiField)element));
        }

        if (messageKey != null) {
          String description = JavaErrorMessages.message(messageKey, refName);
          myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, ref, description));
        }
      }
    }
  }

  @Override public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInstanceOfApplicable(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInstanceOfGenericType(expression));
  }

  @Override public void visitKeyword(PsiKeyword keyword) {
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
      myHolder.add(HighlightInfo.createHighlightInfo(JavaHighlightInfoTypes.JAVA_KEYWORD, keyword, null));
    }
  }

  @Override public void visitLabeledStatement(PsiLabeledStatement statement) {
    super.visitLabeledStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkLabelWithoutStatement(statement));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkLabelAlreadyInUse(statement));
  }

  @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
    super.visitLiteralExpression(expression);
    if (myHolder.hasErrorResults()) return;
    myHolder.add(HighlightUtil.checkLiteralExpressionParsingError(expression));
    if (myRefCountHolder != null && !myHolder.hasErrorResults()) registerReferencesFromInjectedFragments(expression);
  }

  @Override public void visitMethod(PsiMethod method) {
    super.visitMethod(method);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(method.getBody()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorHandleSuperClassExceptions(method));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkRecursiveConstructorInvocation(method));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkOverrideAnnotation(method));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkSafeVarargsAnnotation(method));
    if (!myHolder.hasErrorResults() && method.isConstructor()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(method, method.getContainingClass()));
    }
  }

  private void highlightReferencedMethodOrClassName(PsiJavaCodeReferenceElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiJavaCodeReferenceElement) {
      return;
    }
    final EditorColorsScheme colorsScheme = myHolder.getColorsScheme();
    if (parent instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)parent).resolveMethod();
      PsiElement methodNameElement = element.getReferenceNameElement();
      if (method != null && methodNameElement != null) {
        myHolder.add(HighlightNamesUtil.highlightMethodName(method, methodNameElement, false, colorsScheme));
        myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(element, colorsScheme));
      }
    }
    else if (parent instanceof PsiConstructorCall) {
      try {
        PsiMethod method = ((PsiConstructorCall)parent).resolveConstructor();
        if (method == null) {
          PsiElement resolved = element.resolve();
          if (resolved instanceof PsiClass) {
            myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, element, colorsScheme));
          }
        }
        else {
          myHolder.add(HighlightNamesUtil.highlightMethodName(method, element, false, colorsScheme));
        }
      }
      catch (IndexNotReadyException ignored) {
      }
    }
    else if (parent instanceof PsiImportStatement && ((PsiImportStatement)parent).isOnDemand()) {
      // highlight on demand import as class
      myHolder.add(HighlightNamesUtil.highlightClassName(null, element, colorsScheme));
    }
    else {
      PsiElement resolved = element.resolve();
      if (resolved instanceof PsiClass) {
        myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, element, colorsScheme));
      }
    }
  }

  @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumSuperConstructorCall(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkSuperQualifierType(expression));
    // in case of JSP synthetic method call, do not check
    if (expression.getMethodExpression().isPhysical() && !myHolder.hasErrorResults()) {
      try {
        myHolder.add(HighlightMethodUtil.checkMethodCall(expression, myResolveHelper));
      }
      catch (IndexNotReadyException ignored) {
      }
    }

    if (!myHolder.hasErrorResults()) visitExpression(expression);
  }

  @Override public void visitModifierList(PsiModifierList list) {
    super.visitModifierList(list);
    PsiElement parent = list.getParent();
    if (!myHolder.hasErrorResults() && parent instanceof PsiMethod) {
      myHolder.add(HighlightMethodUtil.checkMethodCanHaveBody((PsiMethod)parent));
    }
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
      if (!method.isConstructor()) {
        List<HierarchicalMethodSignature> superMethodSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
        if (!superMethodSignatures.isEmpty()) {
          if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, true));
          if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodIncompatibleThrows(methodSignature, superMethodSignatures, true, method.getContainingClass()));
          if (!method.hasModifierProperty(PsiModifier.STATIC)) {
            if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodWeakerPrivileges(methodSignature, superMethodSignatures, true));
            if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodOverridesFinal(methodSignature, superMethodSignatures));
          }
        }
      }
      PsiClass aClass = method.getContainingClass();
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodMustHaveBody(method, aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkDuplicateMethod(aClass, method));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallsBaseClassConstructor(method, myRefCountHolder, myResolveHelper));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkStaticMethodOverride(method));
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateNestedClass(aClass));
      if (!myHolder.hasErrorResults()) {
        TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
        myHolder.add(HighlightClassUtil.checkClassMustBeAbstract(aClass, textRange));
      }
      if (!myHolder.hasErrorResults()) {
        myHolder.add(HighlightClassUtil.checkClassDoesNotCallSuperConstructorOrHandleExceptions(aClass, myRefCountHolder, myResolveHelper));
      }
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkOverrideEquivalentInheritedMethods(aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCyclicInheritance(aClass));
    }
    else if (parent instanceof PsiEnumConstant) {
      if (!myHolder.hasErrorResults()) myHolder.addAll(GenericsHighlightUtil.checkEnumConstantModifierList(list));
    }
  }

  @Override public void visitNameValuePair(PsiNameValuePair pair) {
    myHolder.add(AnnotationsHighlightUtil.checkNameValuePair(pair));
    if (!myHolder.hasErrorResults()) {
      PsiIdentifier nameId = pair.getNameIdentifier();
      if (nameId != null) myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ANNOTATION_ATTRIBUTE_NAME, nameId, null));
    }
  }

  @Override public void visitNewExpression(PsiNewExpression expression) {
    myHolder.add(HighlightUtil.checkUnhandledExceptions(expression, null));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAnonymousInheritFinal(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkQualifiedNewOfStaticClass(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParameterInstantiation(expression));
    try {
      if (!myHolder.hasErrorResults()) HighlightMethodUtil.checkNewExpression(expression, myHolder);
    }
    catch (IndexNotReadyException ignored) {
    }
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()));
    if (!myHolder.hasErrorResults()) registerConstructorCall(expression);

    if (!myHolder.hasErrorResults()) visitExpression(expression);
  }

  @Override public void visitPackageStatement(PsiPackageStatement statement) {
    super.visitPackageStatement(statement);
    myHolder.add(AnnotationsHighlightUtil.checkPackageAnnotationContainingFile(statement));
  }

  @Override
  public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);

    final PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList) {
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkVarArgParameterIsLast(parameter));
    }
    else if (parent instanceof PsiForeachStatement) {
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkForeachLoopParameterType((PsiForeachStatement)parent));
    }
    else if (parent instanceof PsiCatchSection) {
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

  @Override public void visitPostfixExpression(PsiPostfixExpression expression) {
    super.visitPostfixExpression(expression);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
    }
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
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
    JavaResolveResult result;
    try {
      result = ref.advancedResolve(true);
    }
    catch (IndexNotReadyException e) {
      return;
    }
    PsiElement resolved = result.getElement();
    PsiElement parent = ref.getParent();

    if (parent instanceof PsiJavaCodeReferenceElement || ref.isQualified()) {
      if (myRefCountHolder != null) {
        myRefCountHolder.registerReference(ref, result);
      }
      myHolder.add(HighlightUtil.checkReference(ref, result, resolved));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAbstractInstantiation(ref));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkExtendsDuplicate(ref, resolved));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassExtendsForeignInnerClass(ref, resolved));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkSelectStaticClassFromParameterizedType(resolved, ref));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, ref,
                                                                                                                 result.getSubstitutor()));

    if (resolved != null && parent instanceof PsiReferenceList) {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkElementInReferenceList(ref, (PsiReferenceList)parent, result));
    }

    if (parent instanceof PsiAnonymousClass && ref.equals(((PsiAnonymousClass)parent).getBaseClassReference())) {
      myHolder.add(GenericsHighlightUtil.checkOverrideEquivalentMethods((PsiClass)parent));
    }

    if (resolved instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)resolved;

      final PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
      if (containingClass instanceof PsiAnonymousClass && !PsiTreeUtil.isAncestor(containingClass, variable, false) && !(variable instanceof PsiField)) {
        if (!PsiTreeUtil.isAncestor(((PsiAnonymousClass) containingClass).getArgumentList(), ref, false)) {
          myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.IMPLICIT_ANONYMOUS_CLASS_PARAMETER, ref, null));
        }
      }

      final EditorColorsScheme colorsScheme = myHolder.getColorsScheme();
      if (!variable.hasModifierProperty(PsiModifier.FINAL) && isReassigned(variable)) {
        myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, ref));
      }
      else {
        myHolder.add(HighlightNamesUtil.highlightVariableName(variable, ref.getReferenceNameElement(), colorsScheme));
      }
      myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(ref, colorsScheme));
    }
    else {
      highlightReferencedMethodOrClassName(ref);
    }
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
    if (!myHolder.hasErrorResults()) {
      visitExpression(expression);
      if (myHolder.hasErrorResults()) return;
    }
    JavaResolveResult result;
    try {
      result = expression.advancedResolve(false);
    }
    catch (IndexNotReadyException e) {
      return;
    }
    PsiElement resolved = result.getElement();
    if (resolved instanceof PsiVariable && resolved.getContainingFile() == expression.getContainingFile()) {
      if (!myHolder.hasErrorResults()) {
        try {
          myHolder.add(HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, (PsiVariable)resolved, myUninitializedVarProblems));
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

    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkExpressionRequired(expression));
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

  @Override public void visitReferenceList(PsiReferenceList list) {
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

  @Override public void visitReferenceParameterList(PsiReferenceParameterList list) {
    myHolder.add(GenericsHighlightUtil.checkParametersOnRaw(list));
  }

  @Override public void visitReturnStatement(PsiReturnStatement statement) {
    try {
      myHolder.add(HighlightUtil.checkReturnStatementType(statement));
    }
    catch (IndexNotReadyException ignore) {
    }
  }

  @Override public void visitStatement(PsiStatement statement) {
    super.visitStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkNotAStatement(statement));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkStatementPrependedWithCaseInsideSwitch(statement));
  }

  @Override public void visitSuperExpression(PsiSuperExpression expr) {
    myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkAbstractMethodDirectCall(expr));
    if (!myHolder.hasErrorResults()) visitExpression(expr);
  }

  @Override public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkCaseStatement(statement));
  }

  @Override public void visitSwitchStatement(PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSwitchSelectorType(statement));
  }

  @Override public void visitThisExpression(PsiThisExpression expr) {
    myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier()));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expr, null));
    }
    if (!myHolder.hasErrorResults()) {
      visitExpression(expr);
    }
  }

  @Override public void visitThrowStatement(PsiThrowStatement statement) {
    myHolder.add(HighlightUtil.checkUnhandledExceptions(statement, null));
    if (!myHolder.hasErrorResults()) visitStatement(statement);
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    super.visitTryStatement(statement);
    if (!myHolder.hasErrorResults()) {
      for (PsiParameter parameter : statement.getCatchBlockParameters()) {
        boolean added = myHolder.addAll(HighlightUtil.checkExceptionAlreadyCaught(parameter));
        if (!added) myHolder.addAll(HighlightUtil.checkExceptionThrownInTry(parameter));
      }
    }
  }

  @Override
  public void visitResourceVariable(final PsiResourceVariable resourceVariable) {
    visitVariable(resourceVariable);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTryResourceIsAutoCloseable(resourceVariable));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnhandledCloserExceptions(resourceVariable));
  }

  @Override public void visitTypeElement(PsiTypeElement type) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalType(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkReferenceTypeUsedAsTypeArgument(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkWildcardUsage(type));
  }

  @Override public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
    super.visitTypeCastExpression(typeCast);
    try {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInconvertibleTypeCast(typeCast));
    }
    catch (IndexNotReadyException ignore) {
    }
  }

  @Override public void visitTypeParameterList(PsiTypeParameterList list) {
    myHolder.add(GenericsHighlightUtil.checkTypeParametersList(list));
  }

  @Override public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableInitializerType(variable));
  }

  private boolean isReassigned(PsiVariable variable) {
    try {
      return HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems, myParameterIsReassigned);
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }
}
