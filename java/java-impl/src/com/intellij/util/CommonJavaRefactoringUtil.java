// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.containers.Stack;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Common utils shared between refactorings module and 'java.impl' module.
 */
public class CommonJavaRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(CommonJavaRefactoringUtil.class);
  private static final List<? extends PsiType> PRIMITIVE_TYPES = Arrays.asList(
      PsiType.BYTE, PsiType.CHAR, PsiType.SHORT, PsiType.INT, PsiType.LONG, PsiType.FLOAT, PsiType.DOUBLE
  );

  @Nullable
  public static PsiType getTypeByExpression(@NotNull PsiExpression expr) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    PsiType type = getTypeByExpression(expr, factory);
    if (LambdaUtil.notInferredType(type)) {
      type = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, expr.getResolveScope());
    }
    return type;
  }

  public static PsiType getTypeByExpression(PsiExpression expr, final PsiElementFactory factory) {
    PsiType type = RefactoringChangeUtil.getTypeByExpression(expr);
    if (PsiType.NULL.equals(type)) {
      ExpectedTypeInfo[] infos = ExpectedTypesProvider.getExpectedTypes(expr, false);
      if (infos.length > 0) {
        type = infos[0].getType();
        if (type instanceof PsiPrimitiveType) {
          type = infos.length > 1 && !(infos[1].getType() instanceof PsiPrimitiveType) ? infos[1].getType()
                                                                                       : ((PsiPrimitiveType)type).getBoxedType(expr);
        }
      }
      else {
        type = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, expr.getResolveScope());
      }
    }

    return type;
  }

  @Contract("null, _ -> null")
  public static PsiExpression convertInitializerToNormalExpression(PsiExpression expression, PsiType forcedReturnType)
    throws IncorrectOperationException {
    if (expression instanceof PsiArrayInitializerExpression && (forcedReturnType == null || forcedReturnType instanceof PsiArrayType)) {
      return createNewExpressionFromArrayInitializer((PsiArrayInitializerExpression)expression, forcedReturnType);
    }
    return expression;
  }

  public static PsiExpression createNewExpressionFromArrayInitializer(PsiArrayInitializerExpression initializer, PsiType forcedType)
    throws IncorrectOperationException {
    PsiType initializerType = null;
    if (initializer != null) {
      if (forcedType != null) {
        initializerType = forcedType;
      }
      else {
        initializerType = getTypeByExpression(initializer);
      }
    }
    if (initializerType == null) {
      return initializer;
    }
    LOG.assertTrue(initializerType instanceof PsiArrayType);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(initializer.getProject());
    PsiNewExpression result =
      (PsiNewExpression)factory.createExpressionFromText("new " + initializerType.getPresentableText() + "{}", null);
    result = (PsiNewExpression)CodeStyleManager.getInstance(initializer.getProject()).reformat(result);
    PsiArrayInitializerExpression arrayInitializer = result.getArrayInitializer();
    LOG.assertTrue(arrayInitializer != null);
    arrayInitializer.replace(initializer);
    return result;
  }

  public static String qNameToCreateInSourceRoot(PackageWrapper aPackage, final VirtualFile sourceRoot) throws IncorrectOperationException {
    String targetQName = aPackage.getQualifiedName();
    String sourceRootPackage =
      ProjectRootManager.getInstance(aPackage.getManager().getProject()).getFileIndex().getPackageNameByDirectory(sourceRoot);
    if (!canCreateInSourceRoot(sourceRootPackage, targetQName)) {
      throw new IncorrectOperationException(
        "Cannot create package '" + targetQName + "' in source folder " + sourceRoot.getPresentableUrl());
    }
    String result = targetQName.substring(sourceRootPackage.length());
    if (StringUtil.startsWithChar(result, '.')) result = result.substring(1);  // remove initial '.'
    return result;
  }

  public static boolean canCreateInSourceRoot(final String sourceRootPackage, final String targetQName) {
    if (sourceRootPackage == null || !targetQName.startsWith(sourceRootPackage)) return false;
    if (sourceRootPackage.isEmpty() || targetQName.length() == sourceRootPackage.length()) return true;
    return targetQName.charAt(sourceRootPackage.length()) == '.';
  }

  @Nullable
  public static PsiDirectory findPackageDirectoryInSourceRoot(PackageWrapper aPackage, final VirtualFile sourceRoot) {
    final PsiDirectory[] directories = aPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (VfsUtilCore.isAncestor(sourceRoot, directory.getVirtualFile(), false)) {
        return directory;
      }
    }
    String qNameToCreate;
    try {
      qNameToCreate = qNameToCreateInSourceRoot(aPackage, sourceRoot);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
    final String[] shortNames = qNameToCreate.split("\\.");
    PsiDirectory current = aPackage.getManager().findDirectory(sourceRoot);
    LOG.assertTrue(current != null);
    for (String shortName : shortNames) {
      PsiDirectory subdirectory = current.findSubdirectory(shortName);
      if (subdirectory == null) {
        return null;
      }
      current = subdirectory;
    }
    return current;
  }

  @NotNull
  public static PsiDirectory createPackageDirectoryInSourceRoot(@NotNull PackageWrapper aPackage, @NotNull final VirtualFile sourceRoot)
    throws IncorrectOperationException {
    PsiDirectory[] existing = aPackage.getDirectories(
      GlobalSearchScopes.directoryScope(aPackage.getManager().getProject(), sourceRoot, true));
    if (existing.length > 0) {
      return existing[0];
    }
    String qNameToCreate = qNameToCreateInSourceRoot(aPackage, sourceRoot);
    PsiDirectory current = aPackage.getManager().findDirectory(sourceRoot);
    LOG.assertTrue(current != null);
    if (qNameToCreate.isEmpty()) {
      return current;
    }
    final String[] shortNames = qNameToCreate.split("\\.");
    for (String shortName : shortNames) {
      PsiDirectory subdirectory = current.findSubdirectory(shortName);
      if (subdirectory == null) {
        subdirectory = current.createSubdirectory(shortName);
      }
      current = subdirectory;
    }
    return current;
  }

  @Contract(pure = true)
  public static boolean isLoopOrIf(@Nullable PsiElement element) {
    return element instanceof PsiLoopStatement || element instanceof PsiIfStatement;
  }

  @Contract(value = "null -> null", pure = true)
  public static PsiExpression unparenthesizeExpression(PsiExpression expression) {
    while (expression instanceof PsiParenthesizedExpression) {
      final PsiExpression innerExpression = ((PsiParenthesizedExpression)expression).getExpression();
      if (innerExpression == null) return expression;
      expression = innerExpression;
    }
    return expression;
  }

  public static void processSuperTypes(PsiType type, SuperTypeVisitor visitor) {
    if (type instanceof PsiPrimitiveType) {
      int index = PRIMITIVE_TYPES.indexOf(type);
      if (index >= 0) {
        for (int i = index + 1; i < PRIMITIVE_TYPES.size(); i++) {
          visitor.visitType(PRIMITIVE_TYPES.get(i));
        }
      }
    }
    else {
      InheritanceUtil.processSuperTypes(type, false, aType -> {
        visitor.visitType(aType);
        return true;
      });
    }
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Document document,
                                                       final int offset,
                                                       boolean acceptVoid) {
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(offset))) {
      correctedOffset--;
    }
    if (correctedOffset < 0) {
      correctedOffset = offset;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(correctedOffset))) {
      if (text.charAt(correctedOffset) == ';') {//initially caret on the end of line
        correctedOffset--;
      }
      if (correctedOffset < 0 || text.charAt(correctedOffset) != ')' && text.charAt(correctedOffset) != '.' && text.charAt(correctedOffset) != '}') {
        correctedOffset = offset;
      }
    }
    final PsiElement elementAtCaret = file.findElementAt(correctedOffset);
    final List<PsiExpression> expressions = new ArrayList<>();
    PsiExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    while (expression != null) {
      if (!expressions.contains(expression) && !(expression instanceof PsiParenthesizedExpression) && !(expression instanceof PsiSuperExpression) &&
          (acceptVoid || !PsiType.VOID.equals(expression.getType()))) {
        if (isExtractable(expression)) {
          expressions.add(expression);
        }
      }
      expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
    }
    return expressions;
  }

  public static boolean isExtractable(PsiExpression expression) {
    if (expression instanceof PsiMethodReferenceExpression) {
      return true;
    }
    else if (!(expression instanceof PsiAssignmentExpression)) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return true;
      }
      else {
        if (!(expression.getParent() instanceof PsiMethodCallExpression)) {
          final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
          if (!(resolve instanceof PsiClass) && !(resolve instanceof PsiPackage)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static PsiCodeBlock expandExpressionLambdaToCodeBlock(@NotNull PsiLambdaExpression lambdaExpression) {
    final PsiElement body = lambdaExpression.getBody();
    if (!(body instanceof PsiExpression)) return (PsiCodeBlock)body;

    @NonNls String newLambdaText = "{";
    if (!PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression))) newLambdaText += "return ";
    newLambdaText += "a;}";

    final Project project = lambdaExpression.getProject();
    final PsiCodeBlock codeBlock = JavaPsiFacade.getElementFactory(project).createCodeBlockFromText(newLambdaText, lambdaExpression);
    PsiStatement statement = codeBlock.getStatements()[0];
    if (statement instanceof PsiReturnStatement) {
      PsiExpression value = ((PsiReturnStatement)statement).getReturnValue();
      LOG.assertTrue(value != null);
      value.replace(body);
    }
    else if (statement instanceof PsiExpressionStatement){
      ((PsiExpressionStatement)statement).getExpression().replace(body);
    }
    PsiElement arrow = PsiTreeUtil.skipWhitespacesBackward(body);
    if (arrow != null && arrow.getNextSibling() != body) {
      lambdaExpression.deleteChildRange(arrow.getNextSibling(), body.getPrevSibling());
    }
    return (PsiCodeBlock)CodeStyleManager.getInstance(project).reformat(body.replace(codeBlock));
  }

  @Nullable
  public static PsiElement getParentStatement(@Nullable PsiElement place, boolean skipScopingStatements) {
    PsiElement parent = place;
    while (true) {
      if (parent == null) return null;
      if (parent instanceof PsiStatement) break;
      if (parent instanceof PsiExpression && parent.getParent() instanceof PsiLambdaExpression) return parent;
      parent = parent.getParent();
    }
    PsiElement parentStatement = parent;
    while (parent instanceof PsiStatement && !(parent instanceof PsiSwitchLabeledRuleStatement)) {
      if (!skipScopingStatements && ((parent instanceof PsiForStatement && parentStatement == ((PsiForStatement)parent).getBody()) || (
        parent instanceof PsiForeachStatement && parentStatement == ((PsiForeachStatement)parent).getBody()) || (
        parent instanceof PsiWhileStatement && parentStatement == ((PsiWhileStatement)parent).getBody()) || (
        parent instanceof PsiIfStatement &&
        (parentStatement == ((PsiIfStatement)parent).getThenBranch() || parentStatement == ((PsiIfStatement)parent).getElseBranch())))) {
        return parentStatement;
      }
      parentStatement = parent;
      parent = parent.getParent();
    }
    return parentStatement;
  }

  public static PsiStatement putStatementInLoopBody(PsiStatement declaration,
                                                    PsiElement container,
                                                    PsiElement finalAnchorStatement,
                                                    boolean replaceBody)
    throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(container.getProject());
    if(isLoopOrIf(container)) {
      PsiStatement loopBody = getLoopBody(container, finalAnchorStatement);
      PsiStatement loopBodyCopy = loopBody != null ? (PsiStatement) loopBody.copy() : null;
      PsiBlockStatement blockStatement = (PsiBlockStatement)elementFactory
        .createStatementFromText("{}", null);
      blockStatement = (PsiBlockStatement) CodeStyleManager.getInstance(container.getProject()).reformat(blockStatement);
      final PsiElement prevSibling = loopBody.getPrevSibling();
      if(prevSibling instanceof PsiWhiteSpace) {
        final PsiElement pprev = prevSibling.getPrevSibling();
        if (!(pprev instanceof PsiComment) || !((PsiComment)pprev).getTokenType().equals(JavaTokenType.END_OF_LINE_COMMENT)) {
          prevSibling.delete();
        }
      }
      blockStatement = (PsiBlockStatement) loopBody.replace(blockStatement);
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      declaration = (PsiStatement) codeBlock.add(declaration);
      JavaCodeStyleManager.getInstance(declaration.getProject()).shortenClassReferences(declaration);
      if (loopBodyCopy != null && !replaceBody) codeBlock.add(loopBodyCopy);
    } else if (container instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)container;
      final PsiElement invalidBody = lambdaExpression.getBody();
      if (invalidBody == null) return declaration;

      String lambdaParamListWithArrowAndComments = lambdaExpression.getText()
        .substring(0, (declaration.isPhysical() ? declaration : invalidBody).getStartOffsetInParent());
      final PsiLambdaExpression expressionFromText = (PsiLambdaExpression)elementFactory.createExpressionFromText(lambdaParamListWithArrowAndComments + "{}", lambdaExpression.getParent());
      PsiCodeBlock newBody = (PsiCodeBlock)expressionFromText.getBody();
      LOG.assertTrue(newBody != null);
      newBody.add(declaration);

      final PsiElement lambdaExpressionBody = lambdaExpression.getBody();
      LOG.assertTrue(lambdaExpressionBody != null);
      final PsiStatement lastBodyStatement;
      if (PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression))) {
        if (replaceBody) {
          lastBodyStatement = null;
        } else {
          lastBodyStatement = elementFactory.createStatementFromText("a;", lambdaExpression);
          ((PsiExpressionStatement)lastBodyStatement).getExpression().replace(lambdaExpressionBody);
        }
      }
      else {
        lastBodyStatement = elementFactory.createStatementFromText("return a;", lambdaExpression);
        final PsiExpression returnValue = ((PsiReturnStatement)lastBodyStatement).getReturnValue();
        LOG.assertTrue(returnValue != null);
        returnValue.replace(lambdaExpressionBody);
      }
      if (lastBodyStatement != null) {
        newBody.add(lastBodyStatement);
      }

      final PsiLambdaExpression copy = (PsiLambdaExpression)lambdaExpression.replace(expressionFromText);
      newBody = (PsiCodeBlock)copy.getBody();
      LOG.assertTrue(newBody != null);
      declaration = newBody.getStatements()[0];
      declaration = (PsiStatement)JavaCodeStyleManager.getInstance(declaration.getProject()).shortenClassReferences(declaration);
    }
    return declaration;
  }

  @Nullable
  private static PsiStatement getLoopBody(PsiElement container, PsiElement anchorStatement) {
    if(container instanceof PsiLoopStatement) {
      return ((PsiLoopStatement) container).getBody();
    }
    else if (container instanceof PsiIfStatement) {
      final PsiStatement thenBranch = ((PsiIfStatement)container).getThenBranch();
      if (thenBranch != null && PsiTreeUtil.isAncestor(thenBranch, anchorStatement, false)) {
        return thenBranch;
      }
      final PsiStatement elseBranch = ((PsiIfStatement)container).getElseBranch();
      if (elseBranch != null && PsiTreeUtil.isAncestor(elseBranch, anchorStatement, false)) {
        return elseBranch;
      }
      LOG.assertTrue(false);
    }
    LOG.assertTrue(false);
    return null;
  }

  public static PsiStatement putStatementInLoopBody(PsiStatement declaration,
                                                    PsiElement container,
                                                    PsiElement finalAnchorStatement) throws IncorrectOperationException {
    return putStatementInLoopBody(declaration, container, finalAnchorStatement, false);
  }

  public static void collectTypeParameters(final Set<? super PsiTypeParameter> used, final PsiElement element) {
    collectTypeParameters(used, element, Conditions.alwaysTrue());
  }

  public static void collectTypeParameters(final Set<? super PsiTypeParameter> used, final PsiElement element,
                                           final Condition<? super PsiTypeParameter> filter) {
    element.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        if (!reference.isQualified()) {
          final PsiElement resolved = reference.resolve();
          if (resolved instanceof PsiTypeParameter) {
            final PsiTypeParameter typeParameter = (PsiTypeParameter)resolved;
            if (PsiTreeUtil.isAncestor(typeParameter.getOwner(), element, false) && filter.value(typeParameter)) {
              used.add(typeParameter);
            }
          }
        }
      }

      @Override
      public void visitExpression(final PsiExpression expression) {
        super.visitExpression(expression);
        final PsiType type = expression.getType();
        if (type != null) {
          final PsiTypesUtil.TypeParameterSearcher searcher = new PsiTypesUtil.TypeParameterSearcher();
          type.accept(searcher);
          for (PsiTypeParameter typeParam : searcher.getTypeParameters()) {
            if (PsiTreeUtil.isAncestor(typeParam.getOwner(), element, false) && filter.value(typeParam)){
              used.add(typeParam);
            }
          }
        }
      }
    });
  }

  public static boolean canBeDeclaredFinal(@NotNull PsiVariable variable) {
    LOG.assertTrue(variable instanceof PsiLocalVariable || variable instanceof PsiParameter);
    final boolean isReassigned = HighlightControlFlowUtil
      .isReassigned(variable, new HashMap<>());
    return !isReassigned;
  }

  public static PsiElement getParentExpressionAnchorElement(PsiElement place) {
    PsiElement parent = place.getUserData(ElementToWorkOn.PARENT);
    if (place.getUserData(ElementToWorkOn.OUT_OF_CODE_BLOCK) != null) return parent;
    if (parent == null) parent = place;
    while (true) {
      if (isExpressionAnchorElement(parent)) return parent;
      if (parent instanceof PsiExpression && parent.getParent() instanceof PsiLambdaExpression) return parent;
      parent = parent.getParent();
      if (parent == null) return null;
    }
  }

  public static boolean isExpressionAnchorElement(PsiElement element) {
    if (element instanceof PsiDeclarationStatement && element.getParent() instanceof PsiForStatement) return false;
    return element instanceof PsiStatement || element instanceof PsiClassInitializer || element instanceof PsiField ||
           element instanceof PsiMethod;
  }

  public static PsiElement getAnchorElementForMultipleExpressions(PsiExpression @NotNull [] occurrences, PsiElement scope) {
    PsiElement anchor = null;
    for (PsiExpression occurrence : occurrences) {
      if (scope != null && !PsiTreeUtil.isAncestor(scope, occurrence, false)) {
        continue;
      }
      PsiElement anchor1 = getParentExpressionAnchorElement(occurrence);

      if (anchor1 == null) {
        if (occurrence.isPhysical()) return null;
        continue;
      }

      if (anchor == null) {
        anchor = anchor1;
      }
      else {
        PsiElement commonParent = PsiTreeUtil.findCommonParent(anchor, anchor1);
        if (commonParent == null || anchor.getTextRange() == null || anchor1.getTextRange() == null) return null;
        PsiElement firstAnchor = anchor.getTextRange().getStartOffset() < anchor1.getTextRange().getStartOffset() ? anchor : anchor1;
        if (commonParent.equals(firstAnchor)) {
          anchor = firstAnchor;
        }
        else {
          if (commonParent instanceof PsiStatement) {
            anchor = commonParent;
          }
          else {
            PsiElement parent = firstAnchor;
            while (!parent.getParent().equals(commonParent)) {
              parent = parent.getParent();
            }
            final PsiElement newAnchor = getParentExpressionAnchorElement(parent);
            if (newAnchor != null) {
              anchor = newAnchor;
            }
            else {
              anchor = parent;
            }
          }
        }
      }
    }

    if (anchor == null) return null;
    if (occurrences.length > 1 && anchor.getParent().getParent() instanceof PsiSwitchStatement) {
      PsiSwitchStatement switchStatement = (PsiSwitchStatement)anchor.getParent().getParent();
      if (switchStatement.getBody().equals(anchor.getParent())) {
        int startOffset = occurrences[0].getTextRange().getStartOffset();
        int endOffset = occurrences[occurrences.length - 1].getTextRange().getEndOffset();
        PsiStatement[] statements = switchStatement.getBody().getStatements();
        boolean isInDifferentCases = false;
        for (PsiStatement statement : statements) {
          if (statement instanceof PsiSwitchLabelStatement) {
            int caseOffset = statement.getTextRange().getStartOffset();
            if (startOffset < caseOffset && caseOffset < endOffset) {
              isInDifferentCases = true;
              break;
            }
          }
        }
        if (isInDifferentCases) {
          anchor = switchStatement;
        }
      }
    }

    return anchor;
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Editor editor,
                                                       final int offset) {
    return collectExpressions(file, editor, offset, false);
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Editor editor,
                                                       final int offset,
                                                       boolean acceptVoid) {
    return collectExpressions(file, editor.getDocument(), offset, acceptVoid);
  }

  public static SuggestedNameInfo getSuggestedName(@Nullable PsiType type, @NotNull final PsiExpression expression) {
    return getSuggestedName(type, expression, expression);
  }

  public static SuggestedNameInfo getSuggestedName(@Nullable PsiType type,
                                                   @NotNull final PsiExpression expression,
                                                   final PsiElement anchor) {
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
    final SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expression, type);
    final String[] strings = JavaCompletionUtil
      .completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo);
    final SuggestedNameInfo.Delegate delegate = new SuggestedNameInfo.Delegate(strings, nameInfo);
    return codeStyleManager.suggestUniqueVariableName(delegate, anchor, true);
  }

  public static PsiType getTypeByExpressionWithExpectedType(PsiExpression expr) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    PsiType typeByExpression = getTypeByExpression(expr, factory);
    PsiType type = typeByExpression;
    final boolean isFunctionalType = LambdaUtil.notInferredType(type);
    PsiType exprType = expr.getType();
    final boolean detectConjunct = exprType instanceof PsiIntersectionType ||
                                   exprType instanceof PsiWildcardType && ((PsiWildcardType)exprType).getBound() instanceof PsiIntersectionType ||
                                   exprType instanceof PsiCapturedWildcardType && ((PsiCapturedWildcardType)exprType).getUpperBound() instanceof PsiIntersectionType;
    if (type != null && !isFunctionalType && !detectConjunct) {
      return type;
    }
    ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes(expr, false);
    if (expectedTypes.length == 1 || (isFunctionalType || detectConjunct) && expectedTypes.length > 0 ) {
      if (typeByExpression != null && Arrays.stream(expectedTypes).anyMatch(typeInfo -> typeByExpression.isAssignableFrom(typeInfo.getType()))) {
        return type;
      }
      type = expectedTypes[0].getType();
      if (!type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return type;
    }
    return detectConjunct ? type : null;
  }

  public static boolean isInStaticContext(PsiElement element, @Nullable final PsiClass aClass) {
    return PsiUtil.getEnclosingStaticElement(element, aClass) != null;
  }

  public static PsiExpression outermostParenthesizedExpression(PsiExpression expression) {
    while (expression.getParent() instanceof PsiParenthesizedExpression) {
      expression = (PsiParenthesizedExpression)expression.getParent();
    }
    return expression;
  }

  @Nullable
  public static PsiMethod getChainedConstructor(PsiMethod constructor) {
    final PsiCodeBlock constructorBody = constructor.getBody();
    if (constructorBody == null) return null;
    final PsiStatement[] statements = constructorBody.getStatements();
    if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
      final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
      if (expression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpr = methodCallExpression.getMethodExpression();
        if ("this".equals(methodExpr.getReferenceName())) {
          return (PsiMethod)methodExpr.resolve();
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiTypeParameterList createTypeParameterListWithUsedTypeParameters(final PsiElement @NotNull ... elements) {
    return createTypeParameterListWithUsedTypeParameters(null, elements);
  }

  @Nullable
  public static PsiTypeParameterList createTypeParameterListWithUsedTypeParameters(@Nullable final PsiTypeParameterList fromList,
                                                                                   final PsiElement @NotNull ... elements) {
    return createTypeParameterListWithUsedTypeParameters(fromList, Conditions.alwaysTrue(), elements);
  }

  @Nullable
  public static PsiTypeParameterList createTypeParameterListWithUsedTypeParameters(@Nullable final PsiTypeParameterList fromList,
                                                                                   Condition<? super PsiTypeParameter> filter,
                                                                                   final PsiElement @NotNull ... elements) {
    if (elements.length == 0) return null;
    final Set<PsiTypeParameter> used = new HashSet<>();
    for (final PsiElement element : elements) {
      if (element == null) continue;
      collectTypeParameters(used, element, filter);  //pull up extends cls class with type params

    }

    collectTypeParametersInDependencies(filter, used);

    if (fromList != null) {
      used.retainAll(Arrays.asList(fromList.getTypeParameters()));
    }

    PsiTypeParameter[] typeParameters = used.toArray(PsiTypeParameter.EMPTY_ARRAY);

    Arrays.sort(typeParameters, Comparator.comparingInt(tp -> tp.getTextRange().getStartOffset()));

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(elements[0].getProject());
    try {
      final PsiClass aClass = elementFactory.createClassFromText("class A {}", null);
      PsiTypeParameterList list = aClass.getTypeParameterList();
      assert list != null;
      for (final PsiTypeParameter typeParameter : typeParameters) {
        list.add(typeParameter);
      }
      return list;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      assert false;
      return null;
    }
  }

  private static void collectTypeParametersInDependencies(Condition<? super PsiTypeParameter> filter, Set<PsiTypeParameter> used) {
    Stack<PsiTypeParameter> toProcess = new com.intellij.util.containers.Stack<>();
    toProcess.addAll(used);
    while (!toProcess.isEmpty()) {
      PsiTypeParameter parameter = toProcess.pop();
      HashSet<PsiTypeParameter> dependencies = new HashSet<>();
      collectTypeParameters(dependencies, parameter, param -> filter.value(param) && !used.contains(param));
      used.addAll(dependencies);
      toProcess.addAll(dependencies);
    }
  }

  private static String getNameOfReferencedParameter(PsiDocTag tag) {
    LOG.assertTrue("param".equals(tag.getName()));
    final PsiElement[] dataElements = tag.getDataElements();
    if (dataElements.length < 1) return null;
    return dataElements[0].getText();
  }

  public static void fixJavadocsForParams(PsiMethod method, Set<? extends PsiParameter> newParameters) throws IncorrectOperationException {
    fixJavadocsForParams(method, newParameters, Conditions.alwaysFalse());
  }

  public static void fixJavadocsForParams(PsiMethod method,
                                          Set<? extends PsiParameter> newParameters,
                                          Condition<? super Pair<PsiParameter, String>> eqCondition) throws IncorrectOperationException {
    fixJavadocsForParams(method, newParameters, eqCondition, Conditions.alwaysTrue());
  }

  public static void fixJavadocsForParams(@NotNull PsiMethod method,
                                          @NotNull Set<? extends PsiParameter> newParameters,
                                          @NotNull Condition<? super Pair<PsiParameter, String>> eqCondition,
                                          @NotNull Condition<? super String> matchedToOldParam) throws IncorrectOperationException {
    fixJavadocsForParams(method, method.getDocComment(), newParameters, eqCondition, matchedToOldParam);
  }

  public static void fixJavadocsForParams(@NotNull PsiMethod method,
                                          @Nullable PsiDocComment docComment,
                                          @NotNull Set<? extends PsiParameter> newParameters,
                                          @NotNull Condition<? super Pair<PsiParameter, String>> eqCondition,
                                          @NotNull Condition<? super String> matchedToOldParam) throws IncorrectOperationException {
    if (docComment == null) return;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiDocTag[] paramTags = docComment.findTagsByName("param");
    if (parameters.length > 0 && newParameters.size() < parameters.length && paramTags.length == 0) return;
    Map<PsiParameter, PsiDocTag> tagForParam = new HashMap<>();
    for (PsiParameter parameter : parameters) {
      boolean found = false;
      for (PsiDocTag paramTag : paramTags) {
        if (parameter.getName().equals(getNameOfReferencedParameter(paramTag))) {
          tagForParam.put(parameter, paramTag);
          found = true;
          break;
        }
      }
      if (!found) {
        for (PsiDocTag paramTag : paramTags) {
          final String paramName = getNameOfReferencedParameter(paramTag);
          if (eqCondition.value(Pair.create(parameter, paramName))) {
            tagForParam.put(parameter, paramTag);
            found = true;
            break;
          }
        }
      }
      if (!found && !newParameters.contains(parameter)) {
        tagForParam.put(parameter, null);
      }
    }

    List<PsiDocTag> newTags = new ArrayList<>();

    for (PsiDocTag paramTag : paramTags) {
      final String paramName = getNameOfReferencedParameter(paramTag);
      if (!tagForParam.containsValue(paramTag) && !matchedToOldParam.value(paramName)) {
        newTags.add((PsiDocTag)paramTag.copy());
      }
    }

    for (PsiParameter parameter : parameters) {
      if (tagForParam.containsKey(parameter)) {
        final PsiDocTag psiDocTag = tagForParam.get(parameter);
        if (psiDocTag != null) {
          final PsiDocTag copy = (PsiDocTag)psiDocTag.copy();
          final PsiDocTagValue valueElement = copy.getValueElement();
          if (valueElement != null) {
            valueElement.replace(createParamTag(parameter).getValueElement());
          }
          newTags.add(copy);
        }
      }
      else {
        newTags.add(createParamTag(parameter));
      }
    }
    PsiElement anchor = paramTags.length > 0 ? paramTags[0].getPrevSibling() : null;
    for (PsiDocTag paramTag : paramTags) {
      paramTag.delete();
    }
    for (PsiDocTag psiDocTag : newTags) {
      anchor = anchor != null && anchor.isValid()
               ? docComment.addAfter(psiDocTag, anchor)
               : docComment.add(psiDocTag);
    }
    formatJavadocIgnoringSettings(method, docComment);
  }

  public static void formatJavadocIgnoringSettings(@NotNull PsiMethod method, @NotNull PsiDocComment docComment) {
    PsiFile containingFile = method.getContainingFile();
    if (containingFile == null) {
      return;
    }

    // Here we temporarily enable javadoc (we can't produce reasonable javadoc in change signature without formatting)
    // This is an explicit action which affects javadoc, so it shouldn't be unexpected for user.
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(containingFile);
    boolean javadocEnabled = settings.ENABLE_JAVADOC_FORMATTING;
    try {
      settings.ENABLE_JAVADOC_FORMATTING = true;
      CommentFormatter formatter = new CommentFormatter(method.getContainingFile());
      formatter.processComment(docComment.getNode());
    } finally {
      settings.ENABLE_JAVADOC_FORMATTING = javadocEnabled;
    }
  }

  @NotNull
  private static PsiDocTag createParamTag(@NotNull PsiParameter parameter) {
    return JavaPsiFacade.getElementFactory(parameter.getProject()).createParamTag(parameter.getName(), "");
  }

  public static PsiElement replaceElementsWithMap(PsiElement replaceIn, final Map<PsiElement, PsiElement> elementsToReplace) throws IncorrectOperationException {
    for(Map.Entry<PsiElement, PsiElement> e: elementsToReplace.entrySet()) {
      if (e.getKey() == replaceIn) {
        return e.getKey().replace(e.getValue());
      }
      e.getKey().replace(e.getValue());
    }
    return replaceIn;
  }

  public static PsiField appendField(final PsiClass destClass,
                                   final PsiField psiField,
                                   final PsiElement anchorMember,
                                   final PsiField forwardReference) {
    final PsiClass parentClass = PsiTreeUtil.getParentOfType(anchorMember, PsiClass.class);

    if (anchorMember instanceof PsiField &&
        anchorMember.getParent() == parentClass &&
        destClass == parentClass &&
        ((PsiField)anchorMember).hasModifierProperty(PsiModifier.STATIC) == psiField.hasModifierProperty(PsiModifier.STATIC)) {
      return (PsiField)destClass.addBefore(psiField, anchorMember);
    }
    else if (anchorMember instanceof PsiClassInitializer &&
             anchorMember.getParent() == parentClass &&
             destClass == parentClass) {
      PsiField field = (PsiField)destClass.addBefore(psiField, anchorMember);
      destClass.addBefore(CodeEditUtil.createLineFeed(field.getManager()), anchorMember);
      return field;
    }
    else {
      if (forwardReference != null &&forwardReference.getParent() == destClass) {
        return (PsiField)destClass.addAfter(psiField, forwardReference);
      }
      return (PsiField)destClass.add(psiField);
    }
  }

  public static PsiTypeCodeFragment createTableCodeFragment(@Nullable PsiClassType type,
                                                            @NotNull PsiElement context,
                                                            @NotNull JavaCodeFragmentFactory factory,
                                                            boolean allowConjunctions) {
    return factory.createTypeCodeFragment(type == null ? "" : type.getCanonicalText(),
                                          context,
                                          true,
                                          (allowConjunctions && PsiUtil.isLanguageLevel8OrHigher(context)) ? JavaCodeFragmentFactory.ALLOW_INTERSECTION : 0);
  }

  public static void inlineArrayCreationForVarargs(final PsiNewExpression arrayCreation) {
    PsiExpressionList argumentList = (PsiExpressionList)PsiUtil.skipParenthesizedExprUp(arrayCreation.getParent());
    if (argumentList == null) return;
    PsiExpression[] args = argumentList.getExpressions();
    PsiArrayInitializerExpression arrayInitializer = arrayCreation.getArrayInitializer();
    try {
      if (arrayInitializer == null) {
        arrayCreation.delete();
        return;
      }

      CommentTracker cm = new CommentTracker();
      PsiExpression[] initializers = arrayInitializer.getInitializers();
      if (initializers.length > 0) {
        PsiElement lastInitializerSibling = initializers[initializers.length - 1];
        while (true) {
          final PsiElement nextSibling = lastInitializerSibling.getNextSibling();
          if (nextSibling == null) {
            break;
          }
          if (PsiUtil.isJavaToken(nextSibling, JavaTokenType.RBRACE)) break;
          lastInitializerSibling = nextSibling;
        }
        if (lastInitializerSibling instanceof PsiWhiteSpace) {
          lastInitializerSibling = lastInitializerSibling.getPrevSibling();
        }
        if (lastInitializerSibling instanceof PsiComment) {
          final PsiElement possibleComma = PsiTreeUtil.skipWhitespacesAndCommentsBackward(lastInitializerSibling);
          if (PsiUtil.isJavaToken(possibleComma, JavaTokenType.COMMA)) {
            possibleComma.delete();
          }
        }
        else if (PsiUtil.isJavaToken(lastInitializerSibling, JavaTokenType.COMMA)) {
          lastInitializerSibling = lastInitializerSibling.getPrevSibling();
        }
        PsiElement firstElement = initializers[0];
        final PsiElement leadingComment = PsiTreeUtil.skipWhitespacesBackward(firstElement);
        if (leadingComment instanceof PsiComment) {
          firstElement = leadingComment;
        }
        argumentList.addRange(firstElement, lastInitializerSibling);
        cm.markRangeUnchanged(firstElement, lastInitializerSibling);
      }
      cm.deleteAndRestoreComments(args[args.length - 1]);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static PsiElement getVariableScope(PsiLocalVariable localVar) {
    if (!(localVar instanceof ImplicitVariable)) {
      return localVar.getParent().getParent();
    }
    else {
      return ((ImplicitVariable)localVar).getDeclarationScope();
    }
  }

  /**
   * @see JavaCodeStyleManager#suggestUniqueVariableName(String, PsiElement, boolean)
   * Cannot use method from code style manager: a collision with fieldToReplace is not a collision
   */
  public static String suggestUniqueVariableName(String baseName, PsiElement place, PsiField fieldToReplace) {
    for(int index = 0;;index++) {
      final String name = index > 0 ? baseName + index : baseName;
      final PsiManager manager = place.getManager();
      PsiResolveHelper helper = JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper();
      PsiVariable refVar = helper.resolveAccessibleReferencedVariable(name, place);
      if (refVar != null && !manager.areElementsEquivalent(refVar, fieldToReplace)) continue;
      final boolean[] found = {false};
      place.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitClass(PsiClass aClass) {

        }

        @Override
        public void visitVariable(PsiVariable variable) {
          if (name.equals(variable.getName())) {
            found[0] = true;
            stopWalking();
          }
        }
      });
      if (found[0]) {
        continue;
      }

      return name;
    }
  }

  public static boolean deepTypeEqual(PsiType type1, PsiType type2) {
    if (type1 == type2) return true;
    if (type1 == null || !type1.equals(type2)) return false;
    return Objects.equals(type1.getCanonicalText(true), type2.getCanonicalText(true));
  }

  public interface SuperTypeVisitor {
    void visitType(PsiType aType);

    void visitClass(PsiClass aClass);
  }
}
