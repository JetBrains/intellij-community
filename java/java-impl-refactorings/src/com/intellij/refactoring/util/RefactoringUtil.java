// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class RefactoringUtil {
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

  public static PsiElement replaceOccurenceWithFieldRef(PsiExpression occurrence, PsiField newField, PsiClass destinationClass)
    throws IncorrectOperationException {
    final PsiManager manager = destinationClass.getManager();
    final String fieldName = newField.getName();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiElement element = occurrence.getUserData(ElementToWorkOn.PARENT);
    final PsiVariable psiVariable =
      facade.getResolveHelper().resolveAccessibleReferencedVariable(fieldName, element != null ? element : occurrence);
    final PsiElementFactory factory = facade.getElementFactory();
    if (psiVariable != null && psiVariable.equals(newField)) {
      return IntroduceVariableUtil.replace(occurrence, factory.createExpressionFromText(fieldName, null), manager.getProject());
    }
    else {
      final PsiReferenceExpression ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + fieldName, null);
      if (!occurrence.isValid()) return null;
      if (newField.hasModifierProperty(PsiModifier.STATIC)) {
        ref.setQualifierExpression(factory.createReferenceExpression(destinationClass));
      }
      return IntroduceVariableUtil.replace(occurrence, ref, manager.getProject());
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


  /**
   * @param expression
   * @return loop body if expression is part of some loop's condition or for loop's increment part
   * null otherwise
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
    if (occurrences.length > 1) {
      for (PsiElement occurrence : occurrences) {
        final RangeMarker rangeMarker = occurrence.getUserData(ElementToWorkOn.TEXT_RANGE);
        if (rangeMarker != null && rangeMarker.isValid()) {
          highlightManager.addRangeHighlight(editor, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(),
                                             EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters);
        }
        else {
          final TextRange textRange = occurrence.getTextRange();
          highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(),
                                             EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters);
        }
      }
    }
    return highlighters;
  }

  public static String createTempVar(PsiExpression expr, PsiElement context, boolean declareFinal) throws IncorrectOperationException {
    PsiElement anchorStatement = CommonJavaRefactoringUtil.getParentStatement(context, true);
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

  public static String getNewInnerClassName(PsiClass aClass, String oldInnerClassName, String newName) {
    String className = aClass.getName();
    if (className == null || !oldInnerClassName.endsWith(className)) return newName;
    StringBuilder buffer = new StringBuilder(oldInnerClassName);
    buffer.replace(buffer.length() - className.length(), buffer.length(), newName);
    return buffer.toString();
  }

  public static void visitImplicitSuperConstructorUsages(PsiClass subClass,
                                                         final ImplicitConstructorUsageVisitor implicitConstructorUsageVisitor,
                                                         PsiClass superClass) {
    final PsiMethod baseDefaultConstructor = findDefaultConstructor(superClass);
    final PsiMethod[] constructors = subClass.getConstructors();
    if (constructors.length > 0) {
      for (PsiMethod constructor : constructors) {
        PsiCodeBlock body = constructor.getBody();
        if (body == null) continue;
        final PsiStatement[] statements = body.getStatements();
        if (statements.length < 1 || !JavaHighlightUtil.isSuperOrThisCall(statements[0], true, true)) {
          implicitConstructorUsageVisitor.visitConstructor(constructor, baseDefaultConstructor);
        }
      }
    }
    else {
      implicitConstructorUsageVisitor.visitClassWithoutConstructors(subClass);
    }
  }

  private static PsiMethod findDefaultConstructor(final PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().isEmpty()) return constructor;
    }

    return null;
  }

  /**
   * @deprecated use CommonJavaRefactoringUtil.suggestUniqueVariableName instead.
   */
  @Deprecated
  public static String suggestUniqueVariableName(String baseName, PsiElement place, PsiField fieldToReplace) {
    return CommonJavaRefactoringUtil.suggestUniqueVariableName(baseName, place, fieldToReplace);
  }

  /**
   * @deprecated use CommonJavaRefactoringUtil.convertInitializerToNormalExpression instead.
   */
  @Deprecated
  @Contract("null, _ -> null")
  public static PsiExpression convertInitializerToNormalExpression(PsiExpression expression, PsiType forcedReturnType) throws IncorrectOperationException {
    return CommonJavaRefactoringUtil.convertInitializerToNormalExpression(expression, forcedReturnType);
  }

  /**
   * @deprecated use CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock instead.
   */
  @Deprecated
  public static PsiCodeBlock expandExpressionLambdaToCodeBlock(@NotNull PsiLambdaExpression lambdaExpression) {
    return CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock(lambdaExpression);
  }

  /**
   * @deprecated use CommonJavaRefactoringUtil.findPackageDirectoryInSourceRoot instead.
   */
  @Deprecated
  public static PsiDirectory findPackageDirectoryInSourceRoot(PackageWrapper aPackage, final VirtualFile sourceRoot) {
    return CommonJavaRefactoringUtil.findPackageDirectoryInSourceRoot(aPackage, sourceRoot);
  }

  /**
   * @deprecated use CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot instead.
   */
  @Deprecated
  @NotNull
  public static PsiDirectory createPackageDirectoryInSourceRoot(@NotNull PackageWrapper aPackage, @NotNull final VirtualFile sourceRoot)
    throws IncorrectOperationException {
    return CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(aPackage, sourceRoot);
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
            final PsiJavaCodeReferenceElement codeReferenceElement =
              PsiTreeUtil.getTopmostParentOfType(parent, PsiJavaCodeReferenceElement.class);
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
          substitutor = substitutor.put(parameter, PsiSubstitutor.EMPTY.substitute(
            factory.createTypeParameter(nameGenerator.generateUniqueName(parameterName), PsiClassType.EMPTY_ARRAY)));
        }
      }
      if (!PsiSubstitutor.EMPTY.equals(substitutor)) {
        replaceMovedMemberTypeParameters(memberCopy, PsiUtil.typeParametersIterable((PsiTypeParameterListOwner)memberCopy), substitutor,
                                         factory);//rename usages in the method
        for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
          entry.getKey().setName(entry.getValue().getCanonicalText()); //rename declaration after all usages renamed
        }
      }
    }
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

  @NlsContexts.DialogMessage
  public static String checkEnumConstantInSwitchLabel(PsiExpression expr) {
    if (PsiImplUtil.getSwitchLabel(expr) != null) {
      PsiReferenceExpression ref = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expr), PsiReferenceExpression.class);
      if (ref != null && ref.resolve() instanceof PsiEnumConstant) {
        return RefactoringBundle.getCannotRefactorMessage(
          JavaRefactoringBundle.message("refactoring.introduce.variable.enum.in.label.message"));
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
   * Note that {@code graph.getTargets()} is not necessarily a subset of {@code graph.getVertex()}
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
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
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
}
