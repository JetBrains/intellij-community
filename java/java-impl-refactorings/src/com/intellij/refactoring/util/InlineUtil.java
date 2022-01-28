// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.inline.InlineTransformer;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author ven
 */
public final class InlineUtil {
  private static final Logger LOG = Logger.getInstance(InlineUtil.class);

  private InlineUtil() {}

  @NotNull
  public static PsiExpression inlineVariable(PsiVariable variable, PsiExpression initializer, PsiJavaCodeReferenceElement ref) throws IncorrectOperationException {
    return inlineVariable(variable, initializer, ref, null);
  }

  @NotNull
  public static PsiExpression inlineVariable(PsiVariable variable,
                                             PsiExpression initializer,
                                             PsiJavaCodeReferenceElement ref,
                                             PsiExpression thisAccessExpr)
    throws IncorrectOperationException {
    final PsiElement parent = ref.getParent();
    if (parent instanceof PsiResourceExpression) {
      LOG.error("Unable to inline resource reference");
      return (PsiExpression)ref;
    }
    PsiManager manager = initializer.getManager();

    PsiClass thisClass = RefactoringChangeUtil.getThisClass(initializer);
    PsiClass refParent = RefactoringChangeUtil.getThisClass(ref);
    final PsiType varType = variable.getType();
    initializer = CommonJavaRefactoringUtil.convertInitializerToNormalExpression(initializer, varType);
    if (initializer instanceof PsiPolyadicExpression) {
      final IElementType operationTokenType = ((PsiPolyadicExpression)initializer).getOperationTokenType();
      if ((operationTokenType == JavaTokenType.PLUS || operationTokenType == JavaTokenType.MINUS) &&
          parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType() == JavaTokenType.PLUS) {
        final PsiType type = ((PsiPolyadicExpression)parent).getType();
        if (type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(initializer.getProject());
          initializer = factory.createExpressionFromText("(" + initializer.getText() + ")", initializer);
        }
      }
    }
    solveVariableNameConflicts(initializer, ref, initializer);

    ChangeContextUtil.encodeContextInfo(initializer, false);
    PsiExpression expr = (PsiExpression)replaceDiamondWithInferredTypesIfNeeded(initializer, ref);

    if (thisAccessExpr == null) {
      thisAccessExpr = createThisExpression(manager, thisClass, refParent);
    }

    expr = (PsiExpression)ChangeContextUtil.decodeContextInfo(expr, thisClass, thisAccessExpr);
    PsiType exprType = CommonJavaRefactoringUtil.getTypeByExpression(expr);
    if (exprType != null && !exprType.equals(varType)) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(manager.getProject());
      PsiMethod method = qualifyWithExplicitTypeArguments(initializer, expr, varType);
      if (method != null) {
        if (expr instanceof PsiMethodCallExpression) {
          final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expr).getMethodExpression();
          final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
          if (qualifierExpression == null) {
            final PsiClass containingClass = method.getContainingClass();
            LOG.assertTrue(containingClass != null);
            if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
              methodExpression.setQualifierExpression(elementFactory.createReferenceExpression(containingClass));
            }
            else {
              methodExpression.setQualifierExpression(createThisExpression(method.getManager(), thisClass, refParent));
            }
          }
        }
      }
      else if (varType instanceof PsiEllipsisType &&
               ((PsiEllipsisType)varType).getComponentType().equals(exprType)) { //convert vararg to array
        final PsiExpressionList argumentList = PsiTreeUtil.getParentOfType(expr, PsiExpressionList.class);
        LOG.assertTrue(argumentList != null);
        final PsiExpression[] arguments = argumentList.getExpressions();
        String varargsWrapper = "new " + exprType.getCanonicalText() + "[]{" + StringUtil.join(Arrays.asList(arguments), PsiElement::getText, ",") + '}';
        expr.replace(elementFactory.createExpressionFromText(varargsWrapper, argumentList));
      }
      else {
        boolean insertCastWhenUnchecked = !(exprType instanceof PsiClassType && ((PsiClassType)exprType).isRaw() && parent instanceof PsiExpressionList);
        if (expr instanceof PsiFunctionalExpression || !PsiPolyExpressionUtil.isPolyExpression(expr) && insertCastWhenUnchecked) {
          expr = surroundWithCast(variable, expr);
        }
      }
    }

    ChangeContextUtil.clearContextInfo(initializer);

    return expr;
  }

  private static PsiMethod qualifyWithExplicitTypeArguments(PsiExpression initializer,
                                                            PsiExpression expr,
                                                            PsiType varType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(initializer.getProject());
    if (expr instanceof PsiCallExpression && ((PsiCallExpression)expr).getTypeArguments().length == 0) {
      final JavaResolveResult resolveResult = ((PsiCallExpression)initializer).resolveMethodGenerics();
      final PsiElement resolved = resolveResult.getElement();
      if (resolved instanceof PsiMethod) {
        final PsiTypeParameter[] typeParameters = ((PsiMethod)resolved).getTypeParameters();
        if (typeParameters.length > 0) {
          final PsiCallExpression copy = (PsiCallExpression)expr.copy();
          for (final PsiTypeParameter typeParameter : typeParameters) {
            final PsiType substituted = resolveResult.getSubstitutor().substitute(typeParameter);
            if (substituted == null) break;
            copy.getTypeArgumentList().add(elementFactory.createTypeElement(substituted));
          }
          if (varType.equals(copy.getType()) && copy.resolveMethodGenerics().isValidResult()) {
            ((PsiCallExpression)expr).getTypeArgumentList().replace(copy.getTypeArgumentList());
            return (PsiMethod)resolved;
          }
        }
      }
    }
    return null;
  }

  private static PsiExpression surroundWithCast(PsiVariable variable, PsiExpression expr) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(t)a", null);
    PsiTypeElement castTypeElement = cast.getCastType();
    assert castTypeElement != null;
    PsiTypeElement typeElement = variable.getTypeElement();
    if (typeElement == null) {
      typeElement = factory.createTypeElement(variable.getType());
    }
    else if (typeElement.isInferredType()) {
      return expr;
    }
    castTypeElement.replace(typeElement);
    final PsiExpression operand = cast.getOperand();
    assert operand != null;
    operand.replace(expr);
    expr = (PsiTypeCastExpression)expr.replace(cast);
    if (RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)expr)) {
      return RemoveRedundantCastUtil.removeCast((PsiTypeCastExpression)expr);
    }

    return expr;
  }

  private static PsiThisExpression createThisExpression(PsiManager manager, PsiClass thisClass, PsiClass refParent) {
    PsiThisExpression thisAccessExpr = null;
    if (Comparing.equal(thisClass, refParent)) {
      thisAccessExpr = RefactoringChangeUtil.createThisExpression(manager, null);
    }
    else {
      if (!(thisClass instanceof PsiAnonymousClass)) {
        thisAccessExpr = RefactoringChangeUtil.createThisExpression(manager, thisClass);
      }
    }
    return thisAccessExpr;
  }

  public static boolean allUsagesAreTailCalls(final PsiMethod method) {
    final List<PsiReference> nonTailCallUsages = Collections.synchronizedList(new ArrayList<>());
    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      (Runnable)() -> ReferencesSearch.search(method).forEach(psiReference -> {
        ProgressManager.checkCanceled();
        if (getTailCallType(psiReference) == TailCallType.None) {
          nonTailCallUsages.add(psiReference);
          return false;
        }
        return true;
      }), JavaRefactoringBundle.message("inline.method.checking.tail.calls.progress"), true, method.getProject());
    return result && nonTailCallUsages.isEmpty();
  }

  public static TailCallType getTailCallType(@NotNull final PsiReference psiReference) {
    PsiElement element = psiReference.getElement();
    if (element instanceof PsiMethodReferenceExpression) return TailCallType.Return;
    PsiExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (methodCall == null) return TailCallType.None;
    PsiElement callParent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
    if (callParent instanceof PsiReturnStatement || callParent instanceof PsiLambdaExpression) {
      return TailCallType.Return;
    }
    if (callParent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)callParent)) {
      PsiElement negationParent = PsiUtil.skipParenthesizedExprUp(callParent.getParent());
      if (negationParent instanceof PsiReturnStatement || negationParent instanceof PsiLambdaExpression) {
        return TailCallType.Invert;
      }
    }
    if (callParent instanceof PsiExpressionStatement) {
      PsiStatement curElement = (PsiStatement)callParent;
      while (true) {
        if (PsiTreeUtil.getNextSiblingOfType(curElement, PsiStatement.class) != null) return TailCallType.None;
        PsiElement parent = curElement.getParent();
        if (parent instanceof PsiCodeBlock) {
          PsiElement blockParent = parent.getParent();
          if (blockParent instanceof PsiMethod || blockParent instanceof PsiLambdaExpression) return TailCallType.Simple;
          if (!(blockParent instanceof PsiBlockStatement)) return TailCallType.None;
          parent = blockParent.getParent();
          if (parent instanceof PsiLoopStatement) return TailCallType.Continue;
        }
        if (!(parent instanceof PsiLabeledStatement) && !(parent instanceof PsiIfStatement)) return TailCallType.None;
        curElement = (PsiStatement)parent;
      }
    }
    return TailCallType.None;
  }

  public static void substituteTypeParams(PsiElement scope, final PsiSubstitutor substitutor, final PsiElementFactory factory) {
    final Map<PsiElement, PsiElement> replacement = new HashMap<>();
    scope.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitTypeElement(PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        PsiType type = typeElement.getType();
        if (type instanceof PsiClassType) {
          JavaResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
          PsiElement resolved = resolveResult.getElement();
          if (resolved instanceof PsiTypeParameter) {
            PsiType newType = resolveResult.getSubstitutor().putAll(substitutor).substitute((PsiTypeParameter)resolved);
            if (newType instanceof PsiCapturedWildcardType) {
              newType = ((PsiCapturedWildcardType)newType).getUpperBound();
            }
            if (newType instanceof PsiWildcardType) {
              newType = ((PsiWildcardType)newType).getBound();
            }
            if (newType == null) {
              newType = PsiType.getJavaLangObject(resolved.getManager(), resolved.getResolveScope());
            }
            try {
              replacement.put(typeElement, factory.createTypeElement(newType));
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    });
    for (PsiElement element : replacement.keySet()) {
      if (element.isValid()) {
        element.replace(replacement.get(element));
      }
    }
  }

  private static PsiElement replaceDiamondWithInferredTypesIfNeeded(PsiExpression initializer, PsiElement ref) {
    if (initializer instanceof PsiNewExpression) {
      final PsiDiamondType diamondType = PsiDiamondType.getDiamondType((PsiNewExpression)initializer);
      if (diamondType != null) {
        final PsiDiamondType.DiamondInferenceResult inferenceResult = diamondType.resolveInferredTypes();
        if (inferenceResult.getErrorMessage() == null) {
          final PsiElement copy = ref.copy();
          final PsiElement parent = ref.replace(initializer);
          final PsiDiamondType.DiamondInferenceResult result = PsiDiamondTypeImpl.resolveInferredTypes((PsiNewExpression)initializer, parent);
          ref = parent.replace(copy);
          if (!result.equals(inferenceResult)) {
            final String inferredTypeText = StringUtil.join(inferenceResult.getTypes(),
                                                            psiType -> psiType.getCanonicalText(), ", ");
            final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)initializer).getClassOrAnonymousClassReference();
            final PsiNewExpression expandedDiamond = (PsiNewExpression)JavaPsiFacade.getElementFactory(initializer.getProject())
              .createExpressionFromText("new " + Objects.requireNonNull(classReference).getReferenceName() + "<" + inferredTypeText + ">()", initializer);
            PsiNewExpression newExpression = (PsiNewExpression)initializer.copy();
            Objects.requireNonNull(newExpression.getClassOrAnonymousClassReference()).replace(Objects.requireNonNull(expandedDiamond.getClassReference()));
            return ref.replace(newExpression);
          }
        }
      }
    }
    return ref != initializer ? ref.replace(initializer) : initializer;
  }

  public static void solveVariableNameConflicts(final PsiElement scope,
                                                final PsiElement placeToInsert,
                                                final PsiElement renameScope) throws IncorrectOperationException {
    if (scope instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)scope;
      String name = var.getName();
      String oldName = name;
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(scope.getProject());
      while (true) {
        String newName = codeStyleManager.suggestUniqueVariableName(name, placeToInsert, true);
        if (newName.equals(name)) break;
        name = newName;
        newName = codeStyleManager.suggestUniqueVariableName(name, var, true);
        if (newName.equals(name)) break;
        name = newName;
      }
      if (!name.equals(oldName)) {
        RefactoringUtil.renameVariableReferences(var, name, new LocalSearchScope(renameScope), true);
        var.getNameIdentifier().replace(JavaPsiFacade.getElementFactory(scope.getProject()).createIdentifier(name));
      }
    }

    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      solveVariableNameConflicts(child, placeToInsert, renameScope);
    }
  }

  public static boolean isChainingConstructor(PsiMethod constructor) {
    return CommonJavaRefactoringUtil.getChainedConstructor(constructor) != null;
  }

  /**
   * Extracts all references from initializer and checks whether
   * referenced variables are changed after variable initialization and before last usage of variable.
   * If so, referenced value change returned with appropriate error message.
   *
   * @param conflicts map for found conflicts
   * @param initializer variable initializer
   */
  public static void checkChangedBeforeLastAccessConflicts(@NotNull MultiMap<PsiElement, String> conflicts,
                                                           @NotNull PsiExpression initializer,
                                                           @NotNull PsiVariable variable) {

    Set<PsiVariable> referencedVars = VariableAccessUtils.collectUsedVariables(initializer);
    if (referencedVars.isEmpty()) return;

    PsiElement scope = PsiUtil.getTopLevelEnclosingCodeBlock(initializer, null);
    if (scope == null) return;

    ControlFlow flow = createControlFlow(scope);
    if (flow == null) return;

    int start = flow.getEndOffset(initializer);
    if (start < 0) return;

    Map<PsiElement, PsiVariable> writePlaces = ControlFlowUtil.getWritesBeforeReads(flow, referencedVars, Collections.singleton(variable), start);

    String readVarName = variable.getName();
    for (Map.Entry<PsiElement, PsiVariable> writePlaceEntry : writePlaces.entrySet()) {
      String message = JavaRefactoringBundle.message("variable.0.is.changed.before.last.access", writePlaceEntry.getValue().getName(), readVarName);
      conflicts.putValue(writePlaceEntry.getKey(), message);
    }
  }

  @Nullable
  private static ControlFlow createControlFlow(@NotNull PsiElement scope) {
    ControlFlowFactory factory = ControlFlowFactory.getInstance(scope.getProject());
    ControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();

    try {
      return factory.getControlFlow(scope, policy);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
  }

  /**
   * Extracts side effects from return statements, replacing them with simple {@code return;} or {@code continue;}
   * while preserving semantics.
   *
   * @param method method to process
   * @param replaceWithContinue if true, returns will be replaced with {@code continue}.
   */
  public static void extractReturnValues(PsiMethod method, boolean replaceWithContinue) {
    PsiCodeBlock block = Objects.requireNonNull(method.getBody());
    PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(method);
    for (PsiReturnStatement returnStatement : returnStatements) {
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        List<PsiExpression> sideEffects = !singleReturnMethod(method) || !PsiUtil.isStatement(returnValue) 
                                          ? SideEffectChecker.extractSideEffectExpressions(returnValue) 
                                          : Collections.singletonList(returnValue);
        CommentTracker ct = new CommentTracker();
        sideEffects.forEach(ct::markUnchanged);
        PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, returnValue);
        ct.delete(returnValue);
        if (statements.length > 0) {
          PsiStatement lastAdded = BlockUtils.addBefore(returnStatement, statements);
          // Could be wrapped into {}, so returnStatement might be non-physical anymore
          returnStatement = Objects.requireNonNull(PsiTreeUtil.getNextSiblingOfType(lastAdded, PsiReturnStatement.class));
        }
        ct.insertCommentsBefore(returnStatement);
      }
      if (ControlFlowUtils.blockCompletesWithStatement(block, returnStatement)) {
        new CommentTracker().deleteAndRestoreComments(returnStatement);
      }
      else if (replaceWithContinue) {
        new CommentTracker().replaceAndRestoreComments(returnStatement, "continue;");
      }
    }
  }

  private static boolean singleReturnMethod(PsiMethod method) {
    int statementCount = Objects.requireNonNull(method.getBody()).getStatementCount();
    if (!method.hasModifierProperty(PsiModifier.STATIC) && PsiTreeUtil.getContextOfType(method, PsiClass.class) != null) { //this declaration
      statementCount--;
    }
    return statementCount <= 1;
  }

  public static PsiExpression inlineInitializer(PsiVariable variable, PsiExpression initializer, PsiJavaCodeReferenceElement ref) {
    Project project = variable.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (initializer instanceof PsiThisExpression && ((PsiThisExpression)initializer).getQualifier() == null) {
      final PsiClass varThisClass = RefactoringChangeUtil.getThisClass(variable);
      if (varThisClass != null && varThisClass != RefactoringChangeUtil.getThisClass(ref)) {
        initializer = factory.createExpressionFromText(varThisClass.getName() + ".this", variable);
      }
    }

    PsiExpression expr = inlineVariable(variable, initializer, ref);

    CommonJavaRefactoringUtil.tryToInlineArrayCreationForVarargs(expr);

    //Q: move the following code to some util? (addition to inline?)
    if (expr instanceof PsiThisExpression) {
      if (expr.getParent() instanceof PsiReferenceExpression) {
        PsiReferenceExpression refExpr = (PsiReferenceExpression)expr.getParent();
        PsiElement refElement = refExpr.resolve();
        PsiExpression exprCopy = (PsiExpression)refExpr.copy();
        refExpr = (PsiReferenceExpression)refExpr.replace(factory.createExpressionFromText(
          Objects.requireNonNull(refExpr.getReferenceName()), null));
        if (refElement != null) {
          PsiElement newRefElement = refExpr.resolve();
          if (!refElement.equals(newRefElement)) {
            // change back
            refExpr.replace(exprCopy);
          }
        }
      }
    }

    if (expr instanceof PsiLiteralExpression && PsiType.BOOLEAN.equals(expr.getType())) {
      Boolean value = tryCast(((PsiLiteralExpression)expr).getValue(), Boolean.class);
      if (value != null) {
        SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expr, value);
        if (fix.isAvailable()) {
          fix.invoke(project, expr.getContainingFile(), expr, expr);
        }
      }
    }
    return initializer;
  }

  public static boolean canInlineParameterOrThisVariable(PsiLocalVariable variable) {
    PsiElement block = PsiUtil.getVariableCodeBlock(variable, null);
    if (block == null) return false;
    List<PsiReferenceExpression> refs = VariableAccessUtils.getVariableReferences(variable, block);
    boolean isAccessedForWriting = false;
    for (PsiReferenceExpression refElement : refs) {
      if (PsiUtil.isAccessedForWriting(refElement)) {
        isAccessedForWriting = true;
      }
    }

    PsiExpression initializer = variable.getInitializer();
    return canInlineParameterOrThisVariable(variable.getProject(), initializer, false, false,
                                            refs.size(), isAccessedForWriting);
  }

  private static boolean canInlineParameterOrThisVariable(Project project,
                                                          PsiExpression initializer,
                                                          boolean shouldBeFinal,
                                                          boolean strictlyFinal,
                                                          int accessCount,
                                                          boolean isAccessedForWriting) {
    if (strictlyFinal) {
      class CanAllLocalsBeDeclaredFinal extends JavaRecursiveElementWalkingVisitor {
        private boolean success = true;

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          final PsiElement psiElement = expression.resolve();
          if (psiElement instanceof PsiLocalVariable || psiElement instanceof PsiParameter) {
            if (!CommonJavaRefactoringUtil.canBeDeclaredFinal((PsiVariable)psiElement)) {
              success = false;
            }
          }
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (success) {
            super.visitElement(element);
          }
        }
      }

      final CanAllLocalsBeDeclaredFinal canAllLocalsBeDeclaredFinal = new CanAllLocalsBeDeclaredFinal();
      initializer.accept(canAllLocalsBeDeclaredFinal);
      if (!canAllLocalsBeDeclaredFinal.success) return false;
    }
    if (initializer instanceof PsiFunctionalExpression) return accessCount <= 1;
    if (initializer instanceof PsiReferenceExpression) {
      PsiVariable refVar = (PsiVariable)((PsiReferenceExpression)initializer).resolve();
      if (refVar == null) {
        return !isAccessedForWriting;
      }
      if (refVar instanceof PsiField) {
        if (isAccessedForWriting) return false;
        if (refVar.hasModifierProperty(PsiModifier.VOLATILE)) return accessCount <= 1;
        /*
        PsiField field = (PsiField)refVar;
        if (isFieldNonModifiable(field)){
          return true;
        }
        //TODO: other cases
        return false;
        */
        return true; //TODO: "suspicious" places to review by user!
      }
      else {
        if (isAccessedForWriting) {
          if (refVar.hasModifierProperty(PsiModifier.FINAL) || shouldBeFinal) return false;
          PsiReference[] refs = ReferencesSearch.search(refVar, GlobalSearchScope.projectScope(project), false)
            .toArray(PsiReference.EMPTY_ARRAY);
          return refs.length == 1; //TODO: control flow
        }
        else {
          if (shouldBeFinal) {
            return refVar.hasModifierProperty(PsiModifier.FINAL) || CommonJavaRefactoringUtil.canBeDeclaredFinal(refVar);
          }
          return true;
        }
      }
    }
    else if (isAccessedForWriting) {
      return false;
    }
    else if (initializer instanceof PsiCallExpression) {
      if (accessCount != 1) return false;//don't allow deleting probable side effects or multiply those side effects
      if (initializer instanceof PsiNewExpression) {
        final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
        if (arrayInitializer != null) {
          for (PsiExpression expression : arrayInitializer.getInitializers()) {
            if (!canInlineParameterOrThisVariable(project, expression, shouldBeFinal, strictlyFinal, accessCount, false)) {
              return false;
            }
          }
          return true;
        }
      }
      final PsiExpressionList argumentList = ((PsiCallExpression)initializer).getArgumentList();
      if (argumentList == null) return false;
      final PsiExpression[] expressions = argumentList.getExpressions();
      for (PsiExpression expression : expressions) {
        if (!canInlineParameterOrThisVariable(project, expression, shouldBeFinal, strictlyFinal, accessCount, false)) {
          return false;
        }
      }
      return true; //TODO: "suspicious" places to review by user!
    }
    else if (initializer instanceof PsiLiteralExpression) {
      return true;
    }
    else if (initializer instanceof PsiPrefixExpression &&
             ((PsiPrefixExpression)initializer).getOperand() instanceof PsiLiteralExpression) {
      return true;
    }
    else if (initializer instanceof PsiArrayAccessExpression) {
      final PsiExpression arrayExpression = ((PsiArrayAccessExpression)initializer).getArrayExpression();
      final PsiExpression indexExpression = ((PsiArrayAccessExpression)initializer).getIndexExpression();
      return canInlineParameterOrThisVariable(project, arrayExpression, shouldBeFinal, strictlyFinal, accessCount, false) &&
             canInlineParameterOrThisVariable(project, indexExpression, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiParenthesizedExpression) {
      PsiExpression expr = ((PsiParenthesizedExpression)initializer).getExpression();
      return expr == null || canInlineParameterOrThisVariable(project, expr, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)initializer).getOperand();
      return operand != null && canInlineParameterOrThisVariable(project, operand, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression binExpr = (PsiPolyadicExpression)initializer;
      for (PsiExpression op : binExpr.getOperands()) {
        if (!canInlineParameterOrThisVariable(project, op, shouldBeFinal, strictlyFinal, accessCount, false)) return false;
      }
      return true;
    }
    else if (initializer instanceof PsiClassObjectAccessExpression) {
      return true;
    }
    else if (initializer instanceof PsiThisExpression) {
      return true;
    }
    else {
      return initializer instanceof PsiSuperExpression;
    }
  }

  /**
   * Try to inline local variable which was generated during method inlining (e.g. to hold a parameter or this reference)
   *
   * @param variable      variable to inline
   * @param strictlyFinal whether the variable is referenced in the places where final variable is required
   */
  public static void tryInlineGeneratedLocal(PsiLocalVariable variable, boolean strictlyFinal) throws IncorrectOperationException {
    PsiElement scope = PsiUtil.getVariableCodeBlock(variable, null);
    if (scope == null) return;
    List<PsiReferenceExpression> refs = VariableAccessUtils.getVariableReferences(variable, scope);
    PsiReferenceExpression firstRef = ContainerUtil.getFirstItem(refs);

    PsiExpression initializer = variable.getInitializer();
    if (firstRef == null) {
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)variable.getParent();
      if (initializer != null) {
        List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(initializer);
        for (PsiStatement statement : StatementExtractor.generateStatements(sideEffects, initializer)) {
          declaration.getParent().addBefore(statement, declaration);
        }
      }
      declaration.delete();
      return;
    }


    boolean isAccessedForWriting = false;
    for (PsiReferenceExpression refElement : refs) {
      if (PsiUtil.isAccessedForWriting(refElement)) {
        isAccessedForWriting = true;
      }
    }

    boolean shouldBeFinal = variable.hasModifierProperty(PsiModifier.FINAL) && strictlyFinal;
    Project project = variable.getProject();
    if (canInlineParameterOrThisVariable(project, initializer, shouldBeFinal, strictlyFinal, refs.size(), isAccessedForWriting)) {
      if (shouldBeFinal) {
        declareUsedLocalsFinal(initializer, true);
      }
      for (PsiJavaCodeReferenceElement ref : refs) {
        initializer = inlineInitializer(variable, initializer, ref);
      }
      variable.getParent().delete();
    }
  }

  private static void declareUsedLocalsFinal(PsiElement expr, boolean strictlyFinal) throws IncorrectOperationException {
    if (expr instanceof PsiReferenceExpression) {
      PsiElement refElement = ((PsiReferenceExpression)expr).resolve();
      if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
        if (strictlyFinal || CommonJavaRefactoringUtil.canBeDeclaredFinal((PsiVariable)refElement)) {
          PsiUtil.setModifierProperty((PsiVariable)refElement, PsiModifier.FINAL, true);
        }
      }
    }
    PsiElement[] children = expr.getChildren();
    for (PsiElement child : children) {
      declareUsedLocalsFinal(child, strictlyFinal);
    }
  }

  /**
   * Try to inline the result variable after method inlining
   *
   * @param resultVar   variable to inline
   * @param resultUsage variable usage
   */
  public static void tryInlineResultVariable(@NotNull PsiLocalVariable resultVar, @NotNull PsiReferenceExpression resultUsage)
    throws IncorrectOperationException {
    PsiElement context = PsiUtil.getVariableCodeBlock(resultVar, null);
    if (context == null) return;
    List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(resultVar, context);
    if (resultVar.getInitializer() == null) {
      PsiAssignmentExpression assignment = null;
      for (PsiReferenceExpression ref : references) {
        if (ref.getParent() instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)ref.getParent()).getLExpression().equals(ref)) {
          if (assignment != null) {
            assignment = null;
            break;
          }
          else {
            assignment = (PsiAssignmentExpression)ref.getParent();
          }
        }
      }

      if (assignment != null) {
        inlineSingleAssignment(resultVar, assignment, resultUsage);
        return;
      }
    }
    tryReplaceWithTarget(resultVar, resultUsage, context, references);
  }

  /**
   * If result of the method is an initializer of another var, try to reuse that var to store the result.
   */
  private static void tryReplaceWithTarget(@NotNull PsiLocalVariable variable,
                                           @NotNull PsiReferenceExpression usage,
                                           PsiElement context,
                                           List<? extends PsiReferenceExpression> references) {
    PsiLocalVariable target = tryCast(PsiUtil.skipParenthesizedExprUp(usage.getParent()), PsiLocalVariable.class);
    if (target == null) return;
    String name = target.getName();
    if (!target.getType().equals(variable.getType())) return;
    PsiDeclarationStatement declaration = tryCast(target.getParent(), PsiDeclarationStatement.class);
    if (declaration == null || declaration.getDeclaredElements().length != 1) return;
    PsiModifierList modifiers = target.getModifierList();
    if (modifiers != null && modifiers.getAnnotations().length != 0) return;
    boolean effectivelyFinal = HighlightControlFlowUtil.isEffectivelyFinal(variable, context, null);
    if (!effectivelyFinal && !VariableAccessUtils.canUseAsNonFinal(target)) return;

    for (PsiReferenceExpression reference : references) {
      ExpressionUtils.bindReferenceTo(reference, name);
    }
    if (effectivelyFinal && target.hasModifierProperty(PsiModifier.FINAL)) {
      PsiModifierList modifierList = variable.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
      }
    }
    variable.setName(name);
    new CommentTracker().deleteAndRestoreComments(declaration);
  }

  private static void inlineSingleAssignment(@NotNull PsiVariable resultVar,
                                             @NotNull PsiAssignmentExpression assignment,
                                             @NotNull PsiReferenceExpression resultUsage) {
    LOG.assertTrue(assignment.getParent() instanceof PsiExpressionStatement);
    // SCR3175 fixed: inline only if declaration and assignment is in the same code block.
    if (!(assignment.getParent().getParent() == resultVar.getParent().getParent())) return;
    String name = Objects.requireNonNull(resultVar.getName());
    PsiDeclarationStatement declaration = JavaPsiFacade.getElementFactory(resultVar.getProject())
      .createVariableDeclarationStatement(name, resultVar.getType(), assignment.getRExpression());
    declaration = (PsiDeclarationStatement)assignment.getParent().replace(declaration);
    resultVar.getParent().delete();
    resultVar = (PsiVariable)declaration.getDeclaredElements()[0];

    PsiElement parentStatement = CommonJavaRefactoringUtil.getParentStatement(resultUsage, true);
    PsiElement next = declaration.getNextSibling();
    boolean canInline = false;
    while (true) {
      if (next == null) break;
      if (next.equals(parentStatement)) {
        canInline = true;
        break;
      }
      if (next instanceof PsiStatement) break;
      next = next.getNextSibling();
    }

    if (canInline) {
      inlineVariable(resultVar, resultVar.getInitializer(), resultUsage);
      declaration.delete();
    }
  }

  public enum TailCallType {
    None(null),
    Simple((methodCopy, callSite, returnType) -> {
      extractReturnValues(methodCopy, false);
      return null;
    }),
    Continue((methodCopy, callSite, returnType) -> {
      extractReturnValues(methodCopy, true);
      return null;
    }),
    Invert((methodCopy, callSite, returnType) -> {
      for (PsiReturnStatement statement : PsiUtil.findReturnStatements(methodCopy)) {
        PsiExpression value = statement.getReturnValue();
        if (value != null) {
          CommentTracker ct = new CommentTracker();
          ct.replaceAndRestoreComments(value, BoolUtils.getNegatedExpressionText(value, ct));
        }
      }
      return null;
    }),
    Return((methodCopy, callSite, returnType) -> null);

    @Nullable
    private final InlineTransformer myTransformer;

    TailCallType(@Nullable InlineTransformer transformer) {
      myTransformer = transformer;
    }

    @Nullable
    public InlineTransformer getTransformer() {
      return myTransformer;
    }
  }
}
