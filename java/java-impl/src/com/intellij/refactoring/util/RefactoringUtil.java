// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RefactoringUtil {
  private static final Logger LOG = Logger.getInstance(RefactoringUtil.class);
  public static final int EXPR_COPY_SAFE = 0;
  public static final int EXPR_COPY_UNSAFE = 1;
  public static final int EXPR_COPY_PROHIBITED = 2;

  private RefactoringUtil() {
  }

  public static boolean isSourceRoot(final PsiDirectory directory) {
    if (directory.getManager() == null) return false;
    final Project project = directory.getProject();
    final VirtualFile virtualFile = directory.getVirtualFile();
    final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(virtualFile);
    return Comparing.equal(virtualFile, sourceRootForFile);
  }

  public static boolean isInStaticContext(PsiElement element, @Nullable final PsiClass aClass) {
    return PsiUtil.getEnclosingStaticElement(element, aClass) != null;
  }

  /**
   * @deprecated use {@link PsiTypesUtil#hasUnresolvedComponents(PsiType)}
   */
  @Deprecated
  public static boolean isResolvableType(PsiType type) {
    return !PsiTypesUtil.hasUnresolvedComponents(type);
  }

  public static PsiElement replaceOccurenceWithFieldRef(PsiExpression occurrence, PsiField newField, PsiClass destinationClass)
    throws IncorrectOperationException {
    final PsiManager manager = destinationClass.getManager();
    final String fieldName = newField.getName();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiElement element = occurrence.getUserData(ElementToWorkOn.PARENT);
    final PsiVariable psiVariable = facade.getResolveHelper().resolveAccessibleReferencedVariable(fieldName, element != null ? element : occurrence);
    final PsiElementFactory factory = facade.getElementFactory();
    if (psiVariable != null && psiVariable.equals(newField)) {
      return IntroduceVariableBase.replace(occurrence, factory.createExpressionFromText(fieldName, null), manager.getProject());
    }
    else {
      final PsiReferenceExpression ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + fieldName, null);
      if (!occurrence.isValid()) return null;
      if (newField.hasModifierProperty(PsiModifier.STATIC)) {
        ref.setQualifierExpression(factory.createReferenceExpression(destinationClass));
      }
      return IntroduceVariableBase.replace(occurrence, ref, manager.getProject());
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

  @Nullable
  public static String suggestNewOverriderName(String oldOverriderName, String oldBaseName, String newBaseName) {
    if (oldOverriderName.equals(oldBaseName)) {
      return newBaseName;
    }
    int i;
    if (oldOverriderName.startsWith(oldBaseName)) {
      i = 0;
    }
    else {
      i = StringUtil.indexOfIgnoreCase(oldOverriderName, oldBaseName, 0);
    }
    if (i >= 0) {
      String newOverriderName = oldOverriderName.substring(0, i);
      if (Character.isUpperCase(oldOverriderName.charAt(i))) {
        newOverriderName += StringUtil.capitalize(newBaseName);
      }
      else {
        newOverriderName += newBaseName;
      }
      final int j = i + oldBaseName.length();
      if (j < oldOverriderName.length()) {
        newOverriderName += oldOverriderName.substring(j);
      }

      return newOverriderName;
    }
    return null;
  }

  public static boolean hasOnDemandStaticImport(final PsiElement element, final PsiClass aClass) {
    if (element.getContainingFile() instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)element.getContainingFile()).getImportList();
      if (importList != null) {
        final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
        return Arrays.stream(importStaticStatements).anyMatch(stmt -> stmt.isOnDemand() && stmt.resolveTargetClass() == aClass);
      }
    }
    return false;
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

  public static PsiElement getVariableScope(PsiLocalVariable localVar) {
    if (!(localVar instanceof ImplicitVariable)) {
      return localVar.getParent().getParent();
    }
    else {
      return ((ImplicitVariable)localVar).getDeclarationScope();
    }
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

  /**
   * @param expression
   * @return loop body if expression is part of some loop's condition or for loop's increment part
   *         null otherwise
   */
  public static PsiElement getLoopForLoopCondition(PsiExpression expression) {
    PsiExpression outermost = expression;
    while (outermost.getParent() instanceof PsiExpression) {
      outermost = (PsiExpression)outermost.getParent();
    }
    if (outermost.getParent() instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)outermost.getParent();
      if (forStatement.getCondition() == outermost) {
        return forStatement;
      }
      else {
        return null;
      }
    }
    if (outermost.getParent() instanceof PsiExpressionStatement && outermost.getParent().getParent() instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)outermost.getParent().getParent();
      if (forStatement.getUpdate() == outermost.getParent()) {
        return forStatement;
      }
      else {
        return null;
      }
    }
    if (outermost.getParent() instanceof PsiWhileStatement) {
      return outermost.getParent();
    }
    if (outermost.getParent() instanceof PsiDoWhileStatement) {
      return outermost.getParent();
    }
    return null;
  }

  public static PsiClass getThisResolveClass(final PsiReferenceExpression place) {
    final JavaResolveResult resolveResult = place.advancedResolve(false);
    final PsiElement scope = resolveResult.getCurrentFileResolveScope();
    if (scope instanceof PsiClass) {
      return (PsiClass)scope;
    }
    return null;
  }

  public static PsiCall getEnclosingConstructorCall(PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (ref instanceof PsiReferenceExpression && parent instanceof PsiMethodCallExpression) return (PsiCall)parent;

    if (parent instanceof PsiAnonymousClass) {
      parent = parent.getParent();
    }

    return parent instanceof PsiNewExpression ? (PsiNewExpression)parent : null;
  }

  public static PsiMethod getEnclosingMethod(PsiElement element) {
    final PsiElement container = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class, PsiLambdaExpression.class);
    return container instanceof PsiMethod ? (PsiMethod)container : null;
  }

  public static void renameVariableReferences(PsiVariable variable, String newName, SearchScope scope) throws IncorrectOperationException {
    renameVariableReferences(variable, newName, scope, false);
  }

  public static void renameVariableReferences(PsiVariable variable,
                                              String newName,
                                              SearchScope scope,
                                              final boolean ignoreAccessScope) throws IncorrectOperationException {
    for (PsiReference reference : ReferencesSearch.search(variable, scope, ignoreAccessScope)) {
      reference.handleElementRename(newName);
    }
  }

  public static boolean canBeDeclaredFinal(@NotNull PsiVariable variable) {
    LOG.assertTrue(variable instanceof PsiLocalVariable || variable instanceof PsiParameter);
    final boolean isReassigned = HighlightControlFlowUtil
      .isReassigned(variable, new THashMap<>());
    return !isReassigned;
  }

  /**
   * removes a reference to the specified class from the reference list given
   *
   * @return if removed  - a reference to the class or null if there were no references to this class in the reference list
   */
  public static PsiJavaCodeReferenceElement removeFromReferenceList(PsiReferenceList refList, PsiClass aClass)
    throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] refs = refList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      if (ref.isReferenceTo(aClass)) {
        PsiJavaCodeReferenceElement refCopy = (PsiJavaCodeReferenceElement)ref.copy();
        ref.delete();
        return refCopy;
      }
    }
    return null;
  }

  public static PsiJavaCodeReferenceElement findReferenceToClass(PsiReferenceList refList, PsiClass aClass) {
    PsiJavaCodeReferenceElement[] refs = refList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      if (ref.isReferenceTo(aClass)) {
        return ref;
      }
    }
    return null;
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

  public static PsiType getTypeByExpression(PsiExpression expr) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    PsiType type = getTypeByExpression(expr, factory);
    if (LambdaUtil.notInferredType(type)) {
      type = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, expr.getResolveScope());
    }
    return type;
  }

  private static PsiType getTypeByExpression(PsiExpression expr, final PsiElementFactory factory) {
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

  public static boolean isAssignmentLHS(@NotNull PsiElement element) {
    return element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element);
  }

  private static void removeFinalParameters(PsiMethod method) throws IncorrectOperationException {
    PsiParameterList paramList = method.getParameterList();
    PsiParameter[] params = paramList.getParameters();

    for (PsiParameter param : params) {
      if (param.hasModifierProperty(PsiModifier.FINAL)) {
        PsiUtil.setModifierProperty(param, PsiModifier.FINAL, false);
      }
    }
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

  public static boolean isMethodUsage(PsiElement element) {
    if (element instanceof PsiEnumConstant) {
      return JavaLanguage.INSTANCE.equals(element.getLanguage());
    }
    if (!(element instanceof PsiJavaCodeReferenceElement)) return false;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiCall) {
      return true;
    }
    else if (parent instanceof PsiAnonymousClass) {
      return element.equals(((PsiAnonymousClass)parent).getBaseClassReference());
    }
    return false;
  }

  @Nullable
  public static PsiExpressionList getArgumentListByMethodReference(PsiElement ref) {
    if (ref instanceof PsiCall) return ((PsiCall)ref).getArgumentList();
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiCall) {
      return ((PsiCall)parent).getArgumentList();
    }
    else if (parent instanceof PsiAnonymousClass) {
      return ((PsiNewExpression)parent.getParent()).getArgumentList();
    }
    LOG.assertTrue(false);
    return null;
  }

  public static PsiCall getCallExpressionByMethodReference(PsiElement ref) {
    if (ref instanceof PsiCall) return (PsiCall)ref;
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiMethodCallExpression) {
      return (PsiMethodCallExpression)parent;
    }
    else if (parent instanceof PsiNewExpression) {
      return (PsiNewExpression)parent;
    }
    else if (parent instanceof PsiAnonymousClass) {
      return (PsiNewExpression)parent.getParent();
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  /**
   * @return List of highlighters
   */
  public static List<RangeHighlighter> highlightAllOccurrences(Project project, PsiElement[] occurrences, Editor editor) {
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (occurrences.length > 1) {
      for (PsiElement occurrence : occurrences) {
        final RangeMarker rangeMarker = occurrence.getUserData(ElementToWorkOn.TEXT_RANGE);
        if (rangeMarker != null && rangeMarker.isValid()) {
          highlightManager
            .addRangeHighlight(editor, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), attributes, true, highlighters);
        }
        else {
          final TextRange textRange = occurrence.getTextRange();
          highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, true, highlighters);
        }
      }
    }
    return highlighters;
  }

  public static String createTempVar(PsiExpression expr, PsiElement context, boolean declareFinal) throws IncorrectOperationException {
    PsiElement anchorStatement = getParentStatement(context, true);
    LOG.assertTrue(anchorStatement != null && anchorStatement.getParent() != null);

    Project project = expr.getProject();
    String[] suggestedNames =
      JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expr, null).names;
    final String prefix = suggestedNames.length > 0 ? suggestedNames[0] : "var";
    final String id = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(prefix, context, true);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());

    if (expr instanceof PsiParenthesizedExpression) {
      PsiExpression expr1 = ((PsiParenthesizedExpression)expr).getExpression();
      if (expr1 != null) {
        expr = expr1;
      }
    }
    PsiDeclarationStatement decl = factory.createVariableDeclarationStatement(id, expr.getType(), expr);
    if (declareFinal) {
      PsiUtil.setModifierProperty(((PsiLocalVariable)decl.getDeclaredElements()[0]), PsiModifier.FINAL, true);
    }
    anchorStatement.getParent().addBefore(decl, anchorStatement);

    return id;
  }

  public static int verifySafeCopyExpression(PsiElement expr) {
    return verifySafeCopyExpressionSubElement(expr);

  }

  private static int verifySafeCopyExpressionSubElement(PsiElement element) {
    int result = EXPR_COPY_SAFE;
    if (element == null) return result;

    if (element instanceof PsiThisExpression || element instanceof PsiSuperExpression || element instanceof PsiIdentifier) {
      return EXPR_COPY_SAFE;
    }

    if (element instanceof PsiMethodCallExpression) {
      result = EXPR_COPY_UNSAFE;
    }

    if (element instanceof PsiNewExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (element instanceof PsiAssignmentExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (PsiUtil.isIncrementDecrementOperation(element)) {
      return EXPR_COPY_PROHIBITED;
    }

    PsiElement[] children = element.getChildren();

    for (PsiElement child : children) {
      int childResult = verifySafeCopyExpressionSubElement(child);
      result = Math.max(result, childResult);
    }
    return result;
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

  public static void makeMethodAbstract(@NotNull PsiClass targetClass, @NotNull PsiMethod method) throws IncorrectOperationException {
    if (!method.hasModifierProperty(PsiModifier.DEFAULT)) {
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        body.delete();
      }

      PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, true);
    }

    if (!targetClass.isInterface()) {
      PsiUtil.setModifierProperty(targetClass, PsiModifier.ABSTRACT, true);
      prepareForAbstract(method);
    }
    else {
      prepareForInterface(method);
    }

  }

  public static void makeMethodDefault(@NotNull PsiMethod method) throws IncorrectOperationException {
    PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, !method.hasModifierProperty(PsiModifier.STATIC));
    PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, false);

    prepareForInterface(method);
  }

  private static void prepareForInterface(PsiMethod method) {
    PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, false);
    PsiUtil.setModifierProperty(method, PsiModifier.PRIVATE, false);
    PsiUtil.setModifierProperty(method, PsiModifier.PROTECTED, false);
    prepareForAbstract(method);
  }

  private static void prepareForAbstract(PsiMethod method) {
    PsiUtil.setModifierProperty(method, PsiModifier.FINAL, false);
    PsiUtil.setModifierProperty(method, PsiModifier.SYNCHRONIZED, false);
    PsiUtil.setModifierProperty(method, PsiModifier.NATIVE, false);
    removeFinalParameters(method);
  }

  public static boolean isInsideAnonymousOrLocal(PsiElement element, PsiElement upTo) {
    for (PsiElement current = element; current != null && current != upTo; current = current.getParent()) {
      if (current instanceof PsiAnonymousClass) return true;
      if (current instanceof PsiClass && current.getParent() instanceof PsiDeclarationStatement) {
        return true;
      }
    }
    return false;
  }

  public static PsiExpression unparenthesizeExpression(PsiExpression expression) {
    while (expression instanceof PsiParenthesizedExpression) {
      final PsiExpression innerExpression = ((PsiParenthesizedExpression)expression).getExpression();
      if (innerExpression == null) return expression;
      expression = innerExpression;
    }
    return expression;
  }

  public static PsiExpression outermostParenthesizedExpression(PsiExpression expression) {
    while (expression.getParent() instanceof PsiParenthesizedExpression) {
      expression = (PsiParenthesizedExpression)expression.getParent();
    }
    return expression;
  }

  public static String getNewInnerClassName(PsiClass aClass, String oldInnerClassName, String newName) {
    String className = aClass.getName();
    if (className == null || !oldInnerClassName.endsWith(className)) return newName;
    StringBuilder buffer = new StringBuilder(oldInnerClassName);
    buffer.replace(buffer.length() - className.length(), buffer.length(), newName);
    return buffer.toString();
  }

  public static void visitImplicitSuperConstructorUsages(PsiClass subClass,
                                                         final ImplicitConstructorUsageVisitor implicitConstructorUsageVistor,
                                                         PsiClass superClass) {
    final PsiMethod baseDefaultConstructor = findDefaultConstructor(superClass);
    final PsiMethod[] constructors = subClass.getConstructors();
    if (constructors.length > 0) {
      for (PsiMethod constructor : constructors) {
        PsiCodeBlock body = constructor.getBody();
        if (body == null) continue;
        final PsiStatement[] statements = body.getStatements();
        if (statements.length < 1 || !JavaHighlightUtil.isSuperOrThisCall(statements[0], true, true)) {
          implicitConstructorUsageVistor.visitConstructor(constructor, baseDefaultConstructor);
        }
      }
    }
    else {
      implicitConstructorUsageVistor.visitClassWithoutConstructors(subClass);
    }
  }

  private static PsiMethod findDefaultConstructor(final PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().isEmpty()) return constructor;
    }

    return null;
  }

  public static void replaceMovedMemberTypeParameters(final PsiElement member,
                                                      final Iterable<? extends PsiTypeParameter> parametersIterable,
                                                      final PsiSubstitutor substitutor,
                                                      final PsiElementFactory factory) {
    final Map<PsiElement, PsiElement> replacement = new LinkedHashMap<>();
    for (PsiTypeParameter parameter : parametersIterable) {
      final PsiType substitutedType = substitutor.substitute(parameter);
      final PsiType erasedType = substitutedType == null ? TypeConversionUtil.erasure(factory.createType(parameter))
                                                         : substitutedType;
      for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(member))) {
        final PsiElement element = reference.getElement();
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiTypeElement) {
          if (substitutedType == null) {
            //extends/implements list of type parameters: S extends List<T>
            final PsiJavaCodeReferenceElement codeReferenceElement = PsiTreeUtil.getTopmostParentOfType(parent, PsiJavaCodeReferenceElement.class);
            if (codeReferenceElement != null) {
              final PsiJavaCodeReferenceElement copy = (PsiJavaCodeReferenceElement)codeReferenceElement.copy();
              final PsiReferenceParameterList parameterList = copy.getParameterList();
              if (parameterList != null) {
                parameterList.delete();
              }
              replacement.put(codeReferenceElement, copy);
            }
            else {
              //nested types List<List<T> listOfLists;
              PsiTypeElement topPsiTypeElement = PsiTreeUtil.getTopmostParentOfType(parent, PsiTypeElement.class);
              if (topPsiTypeElement == null) {
                topPsiTypeElement = (PsiTypeElement)parent;
              }
              replacement.put(topPsiTypeElement, factory.createTypeElement(TypeConversionUtil.erasure(topPsiTypeElement.getType())));
            }
          }
          else {
            replacement.put(parent, factory.createTypeElement(substitutedType));
          }
        }
        else if (element instanceof PsiJavaCodeReferenceElement && erasedType instanceof PsiClassType) {
          replacement.put(element, factory.createReferenceElementByType((PsiClassType)erasedType));
        }
      }
    }
    for (PsiElement element : replacement.keySet()) {
      if (element.isValid()) {
        element.replace(replacement.get(element));
      }
    }
  }

  public static void renameConflictingTypeParameters(PsiMember memberCopy, PsiClass targetClass) {
    if (memberCopy instanceof PsiTypeParameterListOwner && !memberCopy.hasModifierProperty(PsiModifier.STATIC)) {
      UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
      PsiUtil.typeParametersIterable(targetClass).forEach(param -> {
        String paramName = param.getName();
        if (paramName != null) {
          nameGenerator.addExistingName(paramName);
        }
      });

      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(memberCopy.getProject());
      for (PsiTypeParameter parameter : ((PsiTypeParameterListOwner)memberCopy).getTypeParameters()) {
        String parameterName = parameter.getName();
        if (parameterName == null) continue;
        if (!nameGenerator.isUnique(parameterName)) {
          substitutor = substitutor.put(parameter, PsiSubstitutor.EMPTY.substitute(factory.createTypeParameter(nameGenerator.generateUniqueName(parameterName), PsiClassType.EMPTY_ARRAY)));
        }
      }
      if (!PsiSubstitutor.EMPTY.equals(substitutor)) {
        replaceMovedMemberTypeParameters(memberCopy, PsiUtil.typeParametersIterable((PsiTypeParameterListOwner)memberCopy), substitutor, factory);//rename usages in the method
        for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
          entry.getKey().setName(entry.getValue().getCanonicalText()); //rename declaration after all usages renamed
        }
      }
    }
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

  public static boolean isInMovedElement(PsiElement element, Set<? extends PsiMember> membersToMove) {
    for (PsiMember member : membersToMove) {
      if (PsiTreeUtil.isAncestor(member, element, false)) return true;
    }
    return false;
  }

  public static boolean inImportStatement(PsiReference ref, PsiElement element) {
    if (PsiTreeUtil.getParentOfType(element, PsiImportStatement.class) != null) return true;
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)containingFile).getImportList();
      if (importList != null) {
        final TextRange refRange = ref.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
        for (PsiImportStatementBase importStatementBase : importList.getAllImportStatements()) {
          final TextRange textRange = importStatementBase.getTextRange();
          if (textRange.contains(refRange)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static PsiStatement putStatementInLoopBody(PsiStatement declaration,
                                                  PsiElement container,
                                                  PsiElement finalAnchorStatement) throws IncorrectOperationException {
    return putStatementInLoopBody(declaration, container, finalAnchorStatement, false);
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

  public static boolean isLoopOrIf(PsiElement element) {
    return element instanceof PsiLoopStatement || element instanceof PsiIfStatement;
  }

  public static PsiCodeBlock expandExpressionLambdaToCodeBlock(@NotNull PsiLambdaExpression lambdaExpression) {
    final PsiElement body = lambdaExpression.getBody();
    if (!(body instanceof PsiExpression)) return (PsiCodeBlock)body;

    String newLambdaText = "{";
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

  public static String checkEnumConstantInSwitchLabel(PsiExpression expr) {
    if (PsiImplUtil.getSwitchLabel(expr) != null) {
      PsiReferenceExpression ref = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expr), PsiReferenceExpression.class);
      if (ref != null && ref.resolve() instanceof PsiEnumConstant) {
        return RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("refactoring.introduce.variable.enum.in.label.message"));
      }
    }
    return null;
  }

  public interface ImplicitConstructorUsageVisitor {
    void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor);

    void visitClassWithoutConstructors(PsiClass aClass);
  }

  public interface Graph<T> {
    Set<T> getVertices();

    Set<T> getTargets(T source);
  }

  /**
   * Returns subset of {@code graph.getVertices()} that is a transitive closure (by <code>graph.getTargets()<code>)
   * of the following property: initialRelation.value() of vertex or {@code graph.getTargets(vertex)} is true.
   * <p/>
   * Note that {@code graph.getTargets()} is not neccesrily a subset of {@code graph.getVertex()}
   *
   * @param graph
   * @param initialRelation
   * @return subset of graph.getVertices()
   */
  public static <T> Set<T> transitiveClosure(Graph<T> graph, Condition<? super T> initialRelation) {
    Set<T> result = new HashSet<>();

    final Set<T> vertices = graph.getVertices();
    boolean anyChanged;
    do {
      anyChanged = false;
      for (T currentVertex : vertices) {
        if (!result.contains(currentVertex)) {
          if (!initialRelation.value(currentVertex)) {
            Set<T> targets = graph.getTargets(currentVertex);
            for (T currentTarget : targets) {
              if (result.contains(currentTarget) || initialRelation.value(currentTarget)) {
                result.add(currentVertex);
                anyChanged = true;
                break;
              }
            }
          }
          else {
            result.add(currentVertex);
          }
        }
      }
    }
    while (anyChanged);
    return result;
  }

  public static boolean equivalentTypes(PsiType t1, PsiType t2, PsiManager manager) {
    while (t1 instanceof PsiArrayType) {
      if (!(t2 instanceof PsiArrayType)) return false;
      t1 = ((PsiArrayType)t1).getComponentType();
      t2 = ((PsiArrayType)t2).getComponentType();
    }

    if (t1 instanceof PsiPrimitiveType) {
      return t2 instanceof PsiPrimitiveType && t1.equals(t2);
    }

    return manager.areElementsEquivalent(PsiUtil.resolveClassInType(t1), PsiUtil.resolveClassInType(t2));
  }

  public static List<PsiVariable> collectReferencedVariables(PsiElement scope) {
    final List<PsiVariable> result = new ArrayList<>();
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiElement element = expression.resolve();
        if (element instanceof PsiVariable) {
          result.add((PsiVariable)element);
        }
        final PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) {
          qualifier.accept(this);
        }
      }
    });
    return result;
  }

  public static boolean isModifiedInScope(PsiVariable variable, PsiElement scope) {
    for (PsiReference reference : ReferencesSearch.search(variable, new LocalSearchScope(scope), false)) {
      if (isAssignmentLHS(reference.getElement())) return true;
    }
    return false;
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
      anchor = anchor != null && anchor.isValid() ? docComment.addAfter(psiDocTag, anchor) : docComment.add(psiDocTag);
    }
  }

  @NotNull
  private static PsiDocTag createParamTag(@NotNull PsiParameter parameter) {
    return JavaPsiFacade.getElementFactory(parameter.getProject()).createParamTag(parameter.getName(), "");
  }

  @NotNull
  public static PsiDirectory createPackageDirectoryInSourceRoot(@NotNull PackageWrapper aPackage, @NotNull final VirtualFile sourceRoot)
    throws IncorrectOperationException {
    final PsiDirectory[] directories = aPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (VfsUtilCore.isAncestor(sourceRoot, directory.getVirtualFile(), false)) {
        return directory;
      }
    }
    String qNameToCreate = qNameToCreateInSourceRoot(aPackage, sourceRoot);
    final String[] shortNames = qNameToCreate.split("\\.");
    PsiDirectory current = aPackage.getManager().findDirectory(sourceRoot);
    LOG.assertTrue(current != null);
    for (String shortName : shortNames) {
      PsiDirectory subdirectory = current.findSubdirectory(shortName);
      if (subdirectory == null) {
        subdirectory = current.createSubdirectory(shortName);
      }
      current = subdirectory;
    }
    return current;
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

  public static class ConditionCache<T> implements Condition<T> {
    private final Condition<? super T> myCondition;
    private final HashSet<T> myProcessedSet = new HashSet<>();
    private final HashSet<T> myTrueSet = new HashSet<>();

    public ConditionCache(Condition<? super T> condition) {
      myCondition = condition;
    }

    @Override
    public boolean value(T object) {
      if (!myProcessedSet.contains(object)) {
        myProcessedSet.add(object);
        final boolean value = myCondition.value(object);
        if (value) {
          myTrueSet.add(object);
          return true;
        }
        return false;
      }
      return myTrueSet.contains(object);
    }
  }

  public static class IsDescendantOf implements Condition<PsiClass> {
    private final PsiClass myClass;
    private final ConditionCache<PsiClass> myConditionCache;

    public IsDescendantOf(PsiClass aClass) {
      myClass = aClass;
      myConditionCache = new ConditionCache<>(aClass1 -> InheritanceUtil.isInheritorOrSelf(aClass1, myClass, true));
    }

    @Override
    public boolean value(PsiClass aClass) {
      return myConditionCache.value(aClass);
    }
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
    Stack<PsiTypeParameter> toProcess = new Stack<>();
    toProcess.addAll(used);
    while (!toProcess.isEmpty()) {
      PsiTypeParameter parameter = toProcess.pop();
      HashSet<PsiTypeParameter> dependencies = new HashSet<>();
      collectTypeParameters(dependencies, parameter, param -> filter.value(param) && !used.contains(param));
      used.addAll(dependencies);
      toProcess.addAll(dependencies);
    }
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
}
