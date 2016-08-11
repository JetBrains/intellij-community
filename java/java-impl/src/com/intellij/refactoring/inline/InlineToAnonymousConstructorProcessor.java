/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiExpressionStatement;

/**
 * @author yole
*/
class InlineToAnonymousConstructorProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineToAnonymousConstructorProcessor");

  private static final Key<PsiAssignmentExpression> ourAssignmentKey = Key.create("assignment");
  private static final Key<PsiCallExpression> ourCallKey = Key.create("call");
  public static final ElementPattern ourNullPattern = psiElement(PsiLiteralExpression.class).withText(PsiKeyword.NULL);
  private static final ElementPattern ourAssignmentPattern = psiExpressionStatement().withChild(psiElement(PsiAssignmentExpression.class).save(ourAssignmentKey));
  private static final ElementPattern ourSuperCallPattern = psiExpressionStatement().withFirstChild(
    psiElement(PsiMethodCallExpression.class).save(ourCallKey).withFirstChild(psiElement().withText(PsiKeyword.SUPER)));
  private static final ElementPattern ourThisCallPattern = psiExpressionStatement().withFirstChild(psiElement(PsiMethodCallExpression.class).withFirstChild(
    psiElement().withText(PsiKeyword.THIS)));

  private final PsiClass myClass;
  private PsiNewExpression myNewExpression;
  private final PsiType mySuperType;
  private final Map<String, PsiExpression> myFieldInitializers = new HashMap<>();
  private final Map<PsiParameter, PsiVariable> myLocalsForParameters = new HashMap<>();
  private PsiStatement myNewStatement;
  private final PsiElementFactory myElementFactory;
  private PsiMethod myConstructor;
  private PsiExpressionList myConstructorArguments;
  private PsiParameterList myConstructorParameters;

  public InlineToAnonymousConstructorProcessor(final PsiClass aClass, final PsiNewExpression psiNewExpression,
                                               final PsiType superType) {
    myClass = aClass;
    myNewExpression = psiNewExpression;
    mySuperType = superType;
    myNewStatement = PsiTreeUtil.getParentOfType(myNewExpression, PsiStatement.class);
    myElementFactory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
  }

  public void run() throws IncorrectOperationException {
    checkInlineChainingConstructor();
    JavaResolveResult classResolveResult = myNewExpression.getClassReference().advancedResolve(false);
    JavaResolveResult methodResolveResult = myNewExpression.resolveMethodGenerics();
    myConstructor = (PsiMethod) methodResolveResult.getElement();
    myConstructorArguments = myNewExpression.getArgumentList();

    PsiSubstitutor classResolveSubstitutor = classResolveResult.getSubstitutor();
    PsiType substType = classResolveSubstitutor.substitute(mySuperType);

    PsiTypeParameter[] typeParams = myClass.getTypeParameters();
    PsiType[] substitutedParameters = PsiType.createArray(typeParams.length);
    for(int i=0; i< typeParams.length; i++) {
      substitutedParameters [i] = classResolveSubstitutor.substitute(typeParams [i]);
    }

    @NonNls StringBuilder builder = new StringBuilder("new ");
    builder.append(substType.getCanonicalText());
    builder.append("() {}");

    PsiNewExpression superNewExpressionTemplate = (PsiNewExpression) myElementFactory.createExpressionFromText(builder.toString(),
                                                                                                      myNewExpression.getContainingFile());
    PsiClassInitializer initializerBlock = myElementFactory.createClassInitializer();
    PsiVariable outerClassLocal = null;
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
    if (initializerBlock.getBody().getStatements().length > 0 && fieldCount == 0) {
      insertInitializerBefore(initializerBlock, anonymousClass, token);
    }

    for(PsiElement child: classCopy.getChildren()) {
      if ((child instanceof PsiMethod && !((PsiMethod) child).isConstructor()) ||
          child instanceof PsiClassInitializer || child instanceof PsiClass) {
        if (!myFieldInitializers.isEmpty() || !myLocalsForParameters.isEmpty() || classResolveSubstitutor != PsiSubstitutor.EMPTY || outerClassLocal != null) {
          replaceReferences((PsiMember) child, substitutedParameters, outerClassLocal);
        }
        child = anonymousClass.addBefore(child, token);
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField) child;
        replaceReferences(field, substitutedParameters, outerClassLocal);
        PsiExpression initializer = myFieldInitializers.get(field.getName());
        field = (PsiField) anonymousClass.addBefore(field, token);
        if (initializer != null) {
          field.setInitializer(initializer);
        }
        processedFields++;
        if (processedFields == fieldCount && initializerBlock.getBody().getStatements().length > 0) {
          insertInitializerBefore(initializerBlock, anonymousClass, token);
        }
      }
    }
    if (PsiTreeUtil.getChildrenOfType(anonymousClass, PsiMember.class) == null) {
      anonymousClass.deleteChildRange(anonymousClass.getLBrace(), anonymousClass.getRBrace());
    }
    PsiNewExpression superNewExpression = (PsiNewExpression) myNewExpression.replace(superNewExpressionTemplate);
    superNewExpression = (PsiNewExpression)ChangeContextUtil.decodeContextInfo(superNewExpression, superNewExpression.getAnonymousClass(), null);
    JavaCodeStyleManager.getInstance(superNewExpression.getProject()).shortenClassReferences(superNewExpression);
  }

  private void insertInitializerBefore(final PsiClassInitializer initializerBlock, final PsiClass anonymousClass, final PsiElement token)
      throws IncorrectOperationException {
    anonymousClass.addBefore(CodeEditUtil.createLineFeed(token.getManager()), token);
    anonymousClass.addBefore(initializerBlock, token);
    anonymousClass.addBefore(CodeEditUtil.createLineFeed(token.getManager()), token);
  }

  private void checkInlineChainingConstructor() {
    while(true) {
      PsiMethod constructor = myNewExpression.resolveConstructor();
      if (constructor == null || !InlineMethodHandler.isChainingConstructor(constructor)) break;
      InlineMethodProcessor.inlineConstructorCall(myNewExpression);
    }
  }

  private void analyzeConstructor(final PsiCodeBlock initializerBlock) throws IncorrectOperationException {
    PsiCodeBlock body = myConstructor.getBody();
    assert body != null;
    for(PsiElement child: body.getChildren()) {
      if (child instanceof PsiStatement) {
        PsiStatement stmt = (PsiStatement) child;
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

  private boolean processAssignmentInConstructor(final PsiAssignmentExpression expression) {
    if (expression.getLExpression() instanceof PsiReferenceExpression) {
      PsiReferenceExpression lExpr = (PsiReferenceExpression) expression.getLExpression();
      final PsiExpression rExpr = expression.getRExpression();
      if (rExpr == null) return false;
      final PsiElement psiElement = lExpr.resolve();
      if (psiElement instanceof PsiField) {
        PsiField field = (PsiField) psiElement;
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

  public static boolean isConstant(final PsiExpression expr) {
    Object constantValue = JavaPsiFacade.getInstance(expr.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr);
    return constantValue != null || ourNullPattern.accepts(expr);
  }

  private PsiVariable generateOuterClassLocal() {
    PsiClass outerClass = myClass.getContainingClass();
    assert outerClass != null;
    return generateLocal(StringUtil.decapitalize(outerClass.getName()),
                         myElementFactory.createType(outerClass), myNewExpression.getQualifier());
  }

  private PsiVariable generateLocal(final String baseName, final PsiType type, final PsiExpression initializer) {
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
      PsiVariable variable = (PsiVariable)declaration.getDeclaredElements()[0];
      if (!PsiUtil.isLanguageLevel8OrHigher(myNewExpression) || CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS) {
        PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
      }
      final PsiElement parent = myNewStatement.getParent();
      if (parent instanceof PsiCodeBlock) {
        variable = (PsiVariable)((PsiDeclarationStatement)parent.addBefore(declaration, myNewStatement)).getDeclaredElements()[0];
      }
      else {
        final int offsetInStatement = myNewExpression.getTextRange().getStartOffset() - myNewStatement.getTextRange().getStartOffset();
        final PsiBlockStatement blockStatement = (PsiBlockStatement)myElementFactory.createStatementFromText("{}", null);
        PsiCodeBlock block = blockStatement.getCodeBlock();
        block.add(declaration);
        block.add(myNewStatement);
        block = ((PsiBlockStatement)myNewStatement.replace(blockStatement)).getCodeBlock();

        variable = (PsiVariable)((PsiDeclarationStatement)block.getStatements()[0]).getDeclaredElements()[0];
        myNewStatement = block.getStatements()[1];
        myNewExpression = PsiTreeUtil.getParentOfType(myNewStatement.findElementAt(offsetInStatement), PsiNewExpression.class);
      }

      return variable;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private void generateLocalsForArguments() {
    PsiExpression[] expressions = myConstructorArguments.getExpressions();
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expr = expressions[i];
      PsiParameter parameter = myConstructorParameters.getParameters()[i];
      if (parameter.isVarArgs()) {
        PsiEllipsisType ellipsisType = (PsiEllipsisType)parameter.getType();
        PsiType baseType = ellipsisType.getComponentType();
        @NonNls StringBuilder exprBuilder = new StringBuilder("new ");
        exprBuilder.append(baseType.getCanonicalText());
        exprBuilder.append("[] { }");
        try {
          PsiNewExpression newExpr = (PsiNewExpression) myElementFactory.createExpressionFromText(exprBuilder.toString(), myClass);
          PsiArrayInitializerExpression arrayInitializer = newExpr.getArrayInitializer();
          assert arrayInitializer != null;
          for(int j=i; j < expressions.length; j++) {
            arrayInitializer.add(expressions [j]);
          }

          PsiVariable variable = generateLocal(parameter.getName(), ellipsisType.toArrayType(), newExpr);
          myLocalsForParameters.put(parameter, variable);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }

        break;
      }
      else if (!isConstant(expr)) {
        PsiVariable variable = generateLocal(parameter.getName(), parameter.getType(), expr);
        myLocalsForParameters.put(parameter, variable);
      }
    }
  }

  private void addSuperConstructorArguments(PsiExpressionList argumentList) throws IncorrectOperationException {
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
      for(PsiExpression argument: superArguments.getExpressions()) {
        final PsiElement superArgument = replaceParameterReferences(argument.copy(), new ArrayList<>(), true);
        argumentList.add(superArgument);
      }
    }
  }

  private PsiElement replaceParameterReferences(PsiElement argument,
                                                @Nullable final List<PsiReferenceExpression> localVarRefs,
                                                final boolean replaceFieldsWithInitializers) throws IncorrectOperationException {
    if (argument instanceof PsiReferenceExpression) {
      PsiElement element = ((PsiReferenceExpression)argument).resolve();
      if (element instanceof PsiParameter) {
        PsiParameter parameter = (PsiParameter)element;
        if (myLocalsForParameters.containsKey(parameter)) {
          return argument.replace(getParameterReference(parameter));
        }
        int index = myConstructorParameters.getParameterIndex(parameter);
        return argument.replace(myConstructorArguments.getExpressions() [index]);
      }
    }

    final List<Pair<PsiReferenceExpression, PsiParameter>> parameterReferences = new ArrayList<>();
    final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<>();
    argument.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement psiElement = expression.resolve();
        if (psiElement instanceof PsiParameter) {
          parameterReferences.add(Pair.create(expression, (PsiParameter)psiElement));
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
          if (replaceFieldsWithInitializers && psiElement instanceof PsiField && ((PsiField) psiElement).getContainingClass() == myClass) {
            final PsiExpression initializer = ((PsiField)psiElement).getInitializer();
            if (isConstant(initializer)) {
              elementsToReplace.put(expression, initializer);
            }
          }
        }
      }
    });
    for (Pair<PsiReferenceExpression, PsiParameter> pair: parameterReferences) {
      PsiReferenceExpression ref = pair.first;
      PsiParameter param = pair.second;
      if (myLocalsForParameters.containsKey(param)) {
        ref.replace(getParameterReference(param));
      }
      else {
        int index = myConstructorParameters.getParameterIndex(param);
        if (ref == argument) {
          argument = argument.replace(myConstructorArguments.getExpressions() [index]);
        }
        else {
          ref.replace(myConstructorArguments.getExpressions() [index]);
        }
      }
    }
    return RefactoringUtil.replaceElementsWithMap(argument, elementsToReplace);
  }

  private PsiExpression getParameterReference(final PsiParameter parameter) throws IncorrectOperationException {
    PsiVariable variable = myLocalsForParameters.get(parameter);
    return myElementFactory.createExpressionFromText(variable.getName(), myClass);
  }

  private void replaceReferences(final PsiMember method,
                                 final PsiType[] substitutedParameters, final PsiVariable outerClassLocal) throws IncorrectOperationException {
    final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<>();
    method.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (element instanceof PsiField) {
          try {
            PsiField field = (PsiField)element;
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

      @Override public void visitTypeParameter(final PsiTypeParameter classParameter) {
        super.visitTypeParameter(classParameter);
        PsiReferenceList list = classParameter.getExtendsList();
        PsiJavaCodeReferenceElement[] referenceElements = list.getReferenceElements();
        for(PsiJavaCodeReferenceElement reference: referenceElements) {
          PsiElement psiElement = reference.resolve();
          if (psiElement instanceof PsiTypeParameter) {
            checkReplaceTypeParameter(reference, (PsiTypeParameter) psiElement);
          }
        }
      }

      @Override public void visitTypeElement(final PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        if (typeElement.getType() instanceof PsiClassType) {
          PsiClassType classType = (PsiClassType) typeElement.getType();
          PsiClass psiClass = classType.resolve();
          if (psiClass instanceof PsiTypeParameter) {
            checkReplaceTypeParameter(typeElement, (PsiTypeParameter) psiClass);
          }
        }
      }

      private void checkReplaceTypeParameter(PsiElement element, PsiTypeParameter target) {
        PsiClass containingClass = method.getContainingClass();
        PsiTypeParameter[] psiTypeParameters = containingClass.getTypeParameters();
        for(int i=0; i<psiTypeParameters.length; i++) {
          if (psiTypeParameters [i] == target) {
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
    RefactoringUtil.replaceElementsWithMap(method, elementsToReplace);
  }
}