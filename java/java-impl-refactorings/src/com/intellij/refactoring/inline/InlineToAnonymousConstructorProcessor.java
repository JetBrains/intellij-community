// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiExpressionStatement;

class InlineToAnonymousConstructorProcessor {
  private static final Logger LOG = Logger.getInstance(InlineToAnonymousConstructorProcessor.class);

  private static final Key<PsiAssignmentExpression> ourAssignmentKey = Key.create("assignment");
  private static final Key<PsiCallExpression> ourCallKey = Key.create("call");
  public static final ElementPattern<PsiLiteralExpression> ourNullPattern = psiElement(PsiLiteralExpression.class).withText(JavaKeywords.NULL);
  private static final ElementPattern<PsiExpressionStatement>
    ourAssignmentPattern = psiExpressionStatement().withChild(psiElement(PsiAssignmentExpression.class).save(ourAssignmentKey));
  private static final ElementPattern<PsiExpressionStatement> ourSuperCallPattern = psiExpressionStatement().withFirstChild(
    psiElement(PsiMethodCallExpression.class).save(ourCallKey).withFirstChild(psiElement().withText(JavaKeywords.SUPER)));
  private static final ElementPattern<PsiExpressionStatement>
    ourThisCallPattern = psiExpressionStatement().withFirstChild(psiElement(PsiMethodCallExpression.class).withFirstChild(
    psiElement().withText(JavaKeywords.THIS)));

  private final PsiClass myClass;
  private PsiNewExpression myNewExpression;
  private final PsiType mySuperType;
  private final Map<String, PsiExpression> myFieldInitializers = new HashMap<>();
  private final Map<PsiParameter, PsiLocalVariable> myLocalsForParameters = new HashMap<>();
  private PsiElement myNewStatement;
  private final PsiElementFactory myElementFactory;
  private PsiMethod myConstructor;
  private PsiExpression[] myConstructorArguments;
  private PsiParameterList myConstructorParameters;

  InlineToAnonymousConstructorProcessor(PsiClass aClass, PsiNewExpression psiNewExpression, PsiType superType) {
    myClass = aClass;
    myNewExpression = psiNewExpression;
    mySuperType = superType;
    myNewStatement = PsiTreeUtil.getParentOfType(myNewExpression, PsiStatement.class, PsiLambdaExpression.class);
    myElementFactory = JavaPsiFacade.getElementFactory(myClass.getProject());
  }

  public void run() {
    checkInlineChainingConstructor();
    JavaResolveResult classResolveResult = myNewExpression.getClassReference().advancedResolve(false);
    JavaResolveResult methodResolveResult = myNewExpression.resolveMethodGenerics();
    final PsiElement element = methodResolveResult.getElement();
    myConstructor = element != null ? (PsiMethod) element.getNavigationElement() : null;
    myConstructorArguments = initConstructorArguments();

    PsiSubstitutor classResolveSubstitutor = classResolveResult.getSubstitutor();
    PsiType substType = classResolveSubstitutor.substitute(mySuperType);

    PsiTypeParameter[] typeParams = myClass.getTypeParameters();
    PsiType[] substitutedParameters = PsiType.createArray(typeParams.length);
    for (int i = 0; i < typeParams.length; i++) {
      substitutedParameters[i] = classResolveSubstitutor.substitute(typeParams[i]);
    }

    PsiNewExpression superNewExpressionTemplate = (PsiNewExpression) 
      myElementFactory.createExpressionFromText("new " + substType.getCanonicalText() + "() {}", myNewExpression.getContainingFile());
    PsiClassInitializer initializerBlock = myElementFactory.createClassInitializer();
    PsiLocalVariable outerClassLocal = null;
    if (myNewExpression.getQualifier() != null && myClass.getContainingClass() != null) {
      outerClassLocal = generateOuterClassLocal();
    }
    if (myConstructor != null) {
      myConstructorParameters = myConstructor.getParameterList();

      final PsiExpressionList argumentList = superNewExpressionTemplate.getArgumentList();
      assert argumentList != null;

      if (myNewStatement != null) {
        generateLocalsForArguments();
      }
      analyzeConstructor(initializerBlock.getBody());
      addSuperConstructorArguments(argumentList);
    }

    ChangeContextUtil.encodeContextInfo(myClass.getNavigationElement(), true);
    PsiClass classCopy = (PsiClass) myClass.getNavigationElement().copy();
    ChangeContextUtil.clearContextInfo(myClass);
    final PsiClass anonymousClass = superNewExpressionTemplate.getAnonymousClass();
    assert anonymousClass != null;

    int fieldCount = myClass.getFields().length;
    int processedFields = 0;
    PsiElement token = anonymousClass.getRBrace();
    if (!initializerBlock.getBody().isEmpty() && fieldCount == 0) {
      insertInitializerBefore(initializerBlock, anonymousClass, token);
    }

    for (PsiElement child : classCopy.getChildren()) {
      if ((child instanceof PsiMethod method && !method.isConstructor()) ||
          child instanceof PsiClassInitializer || child instanceof PsiClass) {
        if (!myFieldInitializers.isEmpty() || !myLocalsForParameters.isEmpty() || classResolveSubstitutor != PsiSubstitutor.EMPTY || outerClassLocal != null) {
          replaceReferences((PsiMember) child, substitutedParameters, outerClassLocal);
        }
        anonymousClass.addBefore(child, token);
      }
      else if (child instanceof PsiField field) {
        replaceReferences(field, substitutedParameters, outerClassLocal);
        PsiExpression initializer = myFieldInitializers.get(field.getName());
        field = (PsiField) anonymousClass.addBefore(field, token);
        if (initializer != null) {
          field.setInitializer(initializer);
        }
        processedFields++;
        if (processedFields == fieldCount && !initializerBlock.getBody().isEmpty()) {
          insertInitializerBefore(initializerBlock, anonymousClass, token);
        }
      }
    }
    if (PsiTreeUtil.getChildrenOfType(anonymousClass, PsiMember.class) == null) {
      anonymousClass.deleteChildRange(anonymousClass.getLBrace(), anonymousClass.getRBrace());
    }
    PsiNewExpression superNewExpression = (PsiNewExpression) myNewExpression.replace(superNewExpressionTemplate);
    superNewExpression = (PsiNewExpression)ChangeContextUtil.decodeContextInfo(superNewExpression, superNewExpression.getAnonymousClass(), null);
    PsiAnonymousClass newExpressionAnonymousClass = superNewExpression.getAnonymousClass();
    if (newExpressionAnonymousClass != null && 
        AnonymousCanBeLambdaInspection.isLambdaForm(newExpressionAnonymousClass, false, Collections.emptySet())) {
      PsiExpression lambda = AnonymousCanBeLambdaInspection.replaceAnonymousWithLambda(superNewExpression, newExpressionAnonymousClass.getBaseClassType());
      JavaCodeStyleManager.getInstance(newExpressionAnonymousClass.getProject()).shortenClassReferences(superNewExpression.replace(lambda));
    }
    else {
      JavaCodeStyleManager.getInstance(superNewExpression.getProject()).shortenClassReferences(superNewExpression);
    }
  }

  private static void insertInitializerBefore(PsiClassInitializer initializerBlock, PsiClass anonymousClass, PsiElement token) {
    anonymousClass.addBefore(CodeEditUtil.createLineFeed(token.getManager()), token);
    anonymousClass.addBefore(initializerBlock, token);
    anonymousClass.addBefore(CodeEditUtil.createLineFeed(token.getManager()), token);
  }

  private void checkInlineChainingConstructor() {
    while(true) {
      PsiMethod constructor = myNewExpression.resolveConstructor();
      if (constructor == null || !InlineUtil.isChainingConstructor(constructor)) break;
      InlineMethodProcessor.inlineConstructorCall(myNewExpression);
    }
  }

  private void analyzeConstructor(PsiCodeBlock initializerBlock) {
    PsiCodeBlock body = myConstructor.getBody();
    assert body != null;
    for (PsiElement child : body.getChildren()) {
      if (child instanceof PsiStatement stmt) {
        ProcessingContext context = new ProcessingContext();
        if (ourAssignmentPattern.accepts(stmt, context)) {
          PsiAssignmentExpression expression = context.get(ourAssignmentKey);
          if (processAssignmentInConstructor(expression)) {
            initializerBlock.addBefore(replaceParameterReferences(stmt, null, false), initializerBlock.getRBrace());
          }
        }
        else if (!ourSuperCallPattern.accepts(stmt) && !ourThisCallPattern.accepts(stmt)) {
          replaceParameterReferences(stmt, new ArrayList<>(), false);
          initializerBlock.addBefore(stmt, initializerBlock.getRBrace());
        }
      }
      else if (child instanceof PsiComment) {
        if (child.getPrevSibling() instanceof PsiWhiteSpace) {
          initializerBlock.addBefore(child.getPrevSibling(), initializerBlock.getRBrace());
        }
        initializerBlock.addBefore(child, initializerBlock.getRBrace());
      }
    }
  }

  private boolean processAssignmentInConstructor(PsiAssignmentExpression expression) {
    if (expression.getLExpression() instanceof PsiReferenceExpression lExpr) {
      final PsiExpression rExpr = expression.getRExpression();
      if (rExpr == null) return false;
      final PsiElement psiElement = lExpr.resolve();
      if (psiElement instanceof PsiField field) {
        if (myClass.getManager().areElementsEquivalent(field.getContainingClass(), myClass)) {
          final List<PsiReferenceExpression> localVarRefs = new ArrayList<>();
          final PsiExpression initializer;
          try {
            initializer = (PsiExpression) replaceParameterReferences(rExpr.copy(), localVarRefs, false);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return false;
          }
          if (!localVarRefs.isEmpty()) {
            return true;
          }

          myFieldInitializers.put(field.getName(), initializer);
        }
      }
      else if (psiElement instanceof PsiVariable) {
        return true;
      }
    }
    return false;
  }

  public static boolean isConstant(PsiExpression expr) {
    Object constantValue = JavaPsiFacade.getInstance(expr.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr);
    return constantValue != null || ourNullPattern.accepts(expr);
  }

  private PsiLocalVariable generateOuterClassLocal() {
    PsiClass outerClass = myClass.getContainingClass();
    assert outerClass != null;
    return generateLocal(StringUtil.decapitalize(StringUtil.notNullize(outerClass.getName())),
                         myElementFactory.createType(outerClass), myNewExpression.getQualifier());
  }

  private PsiLocalVariable generateLocal(String baseName, @NotNull PsiType type, PsiExpression initializer) {
    final Project project = myClass.getProject();
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    String baseNameForIndex = baseName;
    int index = 0;
    String localName;
    while(true) {
      localName = codeStyleManager.suggestUniqueVariableName(baseNameForIndex, myNewExpression, true);
      if (myClass.findFieldByName(localName, false) == null) {
        break;
      }
      index++;
      baseNameForIndex = baseName + index;
    }
    try {
      final PsiDeclarationStatement declaration = myElementFactory.createVariableDeclarationStatement(localName, type, initializer);
      PsiLocalVariable variable = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      if (!PsiUtil.isAvailable(JavaFeature.EFFECTIVELY_FINAL, myNewExpression) ||
          JavaCodeStyleSettings.getInstance(initializer.getContainingFile()).GENERATE_FINAL_LOCALS) {
        PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
      }
      final PsiElement parent = myNewStatement.getParent();
      if (parent instanceof PsiCodeBlock) {
        variable = (PsiLocalVariable)((PsiDeclarationStatement)parent.addBefore(declaration, myNewStatement)).getDeclaredElements()[0];
      }
      else if (myNewStatement instanceof PsiLambdaExpression expression) {
        final Object marker = new Object();
        PsiTreeUtil.mark(myNewExpression, marker);
        PsiCodeBlock block = CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock(expression);
        myNewStatement = block.getStatements()[0];
        myNewExpression = (PsiNewExpression)PsiTreeUtil.releaseMark(myNewStatement, marker);
        variable = (PsiLocalVariable)((PsiDeclarationStatement)block.addBefore(declaration, myNewStatement)).getDeclaredElements()[0];
        myConstructorArguments = initConstructorArguments();
      }
      else {
        final int offsetInStatement = myNewExpression.getTextRange().getStartOffset() - myNewStatement.getTextRange().getStartOffset();
        final PsiBlockStatement blockStatement = (PsiBlockStatement)myElementFactory.createStatementFromText("{}", null);
        PsiCodeBlock block = blockStatement.getCodeBlock();
        block.add(declaration);
        block.add(myNewStatement);
        block = ((PsiBlockStatement)myNewStatement.replace(blockStatement)).getCodeBlock();

        variable = (PsiLocalVariable)((PsiDeclarationStatement)block.getStatements()[0]).getDeclaredElements()[0];
        myNewStatement = block.getStatements()[1];
        myNewExpression = PsiTreeUtil.getParentOfType(myNewStatement.findElementAt(offsetInStatement), PsiNewExpression.class);
        myConstructorArguments = initConstructorArguments();
      }

      return variable;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private PsiExpression[] initConstructorArguments() {
    return CommonJavaRefactoringUtil.getNonVarargArguments(myNewExpression);
  }

  private void generateLocalsForArguments() {
    for (int i = 0; i < myConstructorArguments.length; i++) {
      PsiExpression expr = myConstructorArguments[i];
      PsiParameter parameter = myConstructorParameters.getParameters()[i];
      if (!isConstant(expr)) {
        myLocalsForParameters.put(parameter, generateLocal(parameter.getName(), parameter.getType(), expr));
      }
    }
  }

  private void addSuperConstructorArguments(PsiExpressionList argumentList) {
    final PsiCodeBlock body = myConstructor.getBody();
    assert body != null;
    PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return;
    }
    ProcessingContext context = new ProcessingContext();
    if (!ourSuperCallPattern.accepts(statements[0], context)) {
      return;
    }
    PsiExpressionList superArguments = context.get(ourCallKey).getArgumentList();
    if (superArguments != null) {
      for (PsiExpression argument : superArguments.getExpressions()) {
        final PsiElement superArgument = replaceParameterReferences(argument.copy(), new ArrayList<>(), true);
        argumentList.add(superArgument);
      }
    }
  }

  private PsiElement replaceParameterReferences(PsiElement argument,
                                                @Nullable List<? super PsiReferenceExpression> localVarRefs,
                                                boolean replaceFieldsWithInitializers) {
    if (argument instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiParameter parameter) {
      if (myLocalsForParameters.containsKey(parameter)) {
        return argument.replace(getParameterReference(parameter));
      }
      int index = myConstructorParameters.getParameterIndex(parameter);
      return argument.replace(myConstructorArguments[index]);
    }

    final List<Pair<PsiReferenceExpression, PsiParameter>> parameterReferences = new ArrayList<>();
    final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<>();
    argument.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement psiElement = expression.resolve();
        if (psiElement instanceof PsiParameter parameter && parameter.getDeclarationScope() == myConstructor) {
          parameterReferences.add(Pair.create(expression, parameter));
        }
        else if ((psiElement instanceof PsiField || psiElement instanceof PsiMethod) &&
                 ((PsiMember) psiElement).getContainingClass() == myClass.getSuperClass()) {
          PsiMember member = (PsiMember) psiElement;
          if (member.hasModifierProperty(PsiModifier.STATIC) &&
              expression.getQualifierExpression() == null) {
            final String qualifiedText = myClass.getSuperClass().getQualifiedName() + "." + member.getName();
            try {
              final PsiExpression replacement = myElementFactory.createExpressionFromText(qualifiedText, myClass);
              elementsToReplace.put(expression, replacement); 
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
        else if (psiElement instanceof PsiVariable) {
          if (localVarRefs != null) {
            localVarRefs.add(expression);
          }
          if (replaceFieldsWithInitializers && psiElement instanceof PsiField field && field.getContainingClass() == myClass) {
            final PsiExpression initializer = field.getInitializer();
            if (initializer != null && isConstant(initializer)) {
              elementsToReplace.put(expression, initializer);
            }
          }
        }
      }
    });
    for (Pair<PsiReferenceExpression, PsiParameter> pair : parameterReferences) {
      PsiReferenceExpression ref = pair.first;
      PsiParameter param = pair.second;
      if (myLocalsForParameters.containsKey(param)) {
        ref.replace(getParameterReference(param));
      }
      else {
        int index = myConstructorParameters.getParameterIndex(param);
        if (ref == argument) {
          argument = argument.replace(myConstructorArguments[index]);
        }
        else {
          ref.replace(myConstructorArguments[index]);
        }
      }
    }
    return CommonJavaRefactoringUtil.replaceElementsWithMap(argument, elementsToReplace);
  }

  private PsiExpression getParameterReference(PsiParameter parameter) {
    PsiLocalVariable variable = myLocalsForParameters.get(parameter);
    return myElementFactory.createExpressionFromText(variable.getName(), myClass);
  }

  private void replaceReferences(PsiMember method, PsiType[] substitutedParameters, PsiLocalVariable outerClassLocal) {
    final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<>();
    method.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (element instanceof PsiField field) {
          try {
            if (myClass.getContainingClass() != null && field.getContainingClass() == myClass.getContainingClass() &&
                     outerClassLocal != null) {
              PsiReferenceExpression expr = (PsiReferenceExpression)expression.copy();
              PsiExpression qualifier = myElementFactory.createExpressionFromText(outerClassLocal.getName(), field.getContainingClass());
              expr.setQualifierExpression(qualifier);
              elementsToReplace.put(expression, expr);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }

      @Override public void visitTypeParameter(@NotNull PsiTypeParameter classParameter) {
        super.visitTypeParameter(classParameter);
        PsiReferenceList list = classParameter.getExtendsList();
        for (PsiJavaCodeReferenceElement reference : list.getReferenceElements()) {
          if (reference.resolve() instanceof PsiTypeParameter parameter) {
            checkReplaceTypeParameter(reference, parameter);
          }
        }
      }

      @Override public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        if (typeElement.getType() instanceof PsiClassType classType && classType.resolve() instanceof PsiTypeParameter typeParameter) {
          checkReplaceTypeParameter(typeElement, typeParameter);
        }
      }

      private void checkReplaceTypeParameter(PsiElement element, PsiTypeParameter target) {
        PsiClass containingClass = method.getContainingClass();
        PsiTypeParameter[] psiTypeParameters = containingClass.getTypeParameters();
        for (int i = 0; i < psiTypeParameters.length; i++) {
          if (psiTypeParameters[i] == target) {
            PsiType substType = substitutedParameters[i];
            if (substType == null) {
              substType = PsiType.getJavaLangObject(element.getManager(), ProjectScope.getAllScope(element.getProject()));
            }
            if (element instanceof PsiJavaCodeReferenceElement) {
              LOG.assertTrue(substType instanceof PsiClassType);
              elementsToReplace.put(element, myElementFactory.createReferenceElementByType((PsiClassType)substType));
            } else {
              elementsToReplace.put(element, myElementFactory.createTypeElement(substType));
            }
          }
        }
      }
    });
    CommonJavaRefactoringUtil.replaceElementsWithMap(method, elementsToReplace);
  }
}