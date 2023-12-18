// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.siyeh.ig.psiutils.EquivalenceChecker.getCanonicalPsiEquivalence;
import static com.siyeh.ig.psiutils.Java8MigrationUtils.*;
import static com.siyeh.ig.psiutils.Java8MigrationUtils.MapCheckCondition.fromConditional;

public final class Java8MapApiInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(Java8MapApiInspection.class);
  public static final String SHORT_NAME = "Java8MapApi";
  private static final CallMatcher KEY_VALUE_GET_METHODS =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP_ENTRY, "getKey", "getValue").parameterCount(0);

  @SuppressWarnings("PublicField")
  public boolean mySuggestMapGetOrDefault = true;
  @SuppressWarnings("PublicField")
  public boolean mySuggestMapComputeIfAbsent = true;
  @SuppressWarnings("PublicField")
  public boolean mySuggestMapPutIfAbsent = true;
  @SuppressWarnings("PublicField")
  public boolean mySuggestMapMerge = true;
  @SuppressWarnings("PublicField")
  public boolean mySuggestMapReplaceAll = true;
  @SuppressWarnings("PublicField")
  public boolean myTreatGetNullAsContainsKey = false;
  @SuppressWarnings("PublicField")
  public boolean mySideEffects = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("mySuggestMapComputeIfAbsent", JavaBundle.message("checkbox.suggest.conversion.to.map.computeifabsent")),
      checkbox("mySuggestMapGetOrDefault", JavaBundle.message("checkbox.suggest.conversion.to.map.getordefault")),
      checkbox("mySuggestMapPutIfAbsent", JavaBundle.message("checkbox.suggest.conversion.to.map.putifabsent")),
      checkbox("mySuggestMapMerge", JavaBundle.message("checkbox.suggest.conversion.to.map.merge")),
      checkbox("mySuggestMapReplaceAll", JavaBundle.message("checkbox.suggest.conversion.to.map.replaceall")),
      checkbox("myTreatGetNullAsContainsKey",
               JavaBundle.message("checkbox.treat.get.k.null.the.same.as.containskey.k.may.change.semantics")),
      checkbox("mySideEffects", JavaBundle.message("checkbox.suggest.replacement.even.if.lambda.may.have.side.effects")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!JavaFeature.ADVANCED_COLLECTIONS_API.isFeatureSupported(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
        MapCheckCondition condition = fromConditional(expression, myTreatGetNullAsContainsKey);
        if(condition == null || condition.hasVariable()) return;
        PsiExpression existsBranch = condition.getExistsBranch(expression.getThenExpression(), expression.getElseExpression());
        PsiExpression noneBranch = condition.getNoneBranch(expression.getThenExpression(), expression.getElseExpression());
        processGetPut(condition, existsBranch, existsBranch, noneBranch);
      }
      @Override
      public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
        PsiExpression expression = variable.getInitializer();
        PsiMethodCallExpression getCall = extractMapMethodCall(expression, "get");
        if (getCall == null) return;

        List<PsiReferenceExpression> references = VariableAccessUtils
          .getVariableReferences(variable, PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class));

        if (references.isEmpty()) return;

        PsiMethodCallExpression commonPutCall = findPutMethodParent(references.get(0).getElement());

        if (commonPutCall == null || !isCommonPutCallForAllReferences(references, commonPutCall)) return;

        PsiExpression getCallQualifierExpression = getCall.getMethodExpression().getQualifierExpression();
        PsiExpression putCallQualifierExpression = commonPutCall.getMethodExpression().getQualifierExpression();

        EquivalenceChecker equivalenceChecker = getCanonicalPsiEquivalence();

        if (! equivalenceChecker.expressionsAreEquivalent(getCallQualifierExpression, putCallQualifierExpression)) return;

        PsiStatement variableDeclarationStatement = PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
        PsiElement nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(variableDeclarationStatement);
        if (! (nextSibling instanceof PsiStatement)) return;
        PsiExpressionStatement putCallStatement = ObjectUtils.tryCast(commonPutCall.getParent(), PsiExpressionStatement.class);

        if (nextSibling != putCallStatement) return;

        PsiExpression[] getArgs = getCall.getArgumentList().getExpressions();
        PsiExpression[] putArgs = commonPutCall.getArgumentList().getExpressions();

        if (getArgs.length != 1 || putArgs.length != 2 ||
            ! equivalenceChecker.expressionsAreEquivalent(getArgs[0], putArgs[0])) return;

        PsiExpression value = putArgs[1];
        if (LambdaGenerationUtil.canBeUncheckedLambda(value)) {
          GetPutToComputeFix fix = new GetPutToComputeFix(variable);
          holder.registerProblem(commonPutCall,
                                 QuickFixBundle.message("java.8.map.api.inspection.description", "compute"), fix);
        }

      }

      @Override
      public void visitIfStatement(@NotNull PsiIfStatement statement) {
        MapCheckCondition condition = fromConditional(statement, myTreatGetNullAsContainsKey);
        if(condition == null) return;
        PsiStatement existsBranch = ControlFlowUtils.stripBraces(condition.getExistsBranch(statement.getThenBranch(), statement.getElseBranch()));
        PsiStatement noneBranch = ControlFlowUtils.stripBraces(condition.getNoneBranch(statement.getThenBranch(), statement.getElseBranch()));
        if(existsBranch == null) {
          processSingleBranch(condition, noneBranch);
        } else {
          if(mySuggestMapMerge && condition.isGetNull()) {
            processMerge(condition, existsBranch, noneBranch);
          }
          if(condition.hasVariable()) return;
          EquivalenceChecker.Match match = getCanonicalPsiEquivalence().statementsMatch(noneBranch, existsBranch);

          processGetPut(condition, existsBranch, match.getRightDiff(), match.getLeftDiff());
        }
      }

      @Override
      public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
        if (!mySuggestMapReplaceAll) return;
        MapLoopCondition condition = MapLoopCondition.create(statement);
        if (condition == null) return;
        PsiMethodCallExpression putCall = condition.extractPut(statement);
        if (putCall == null) return;
        PsiExpression[] args = putCall.getArgumentList().getExpressions();
        if (args.length != 2) return;
        PsiExpression key = args[0];
        if (!condition.isKeyAccess(key)) return;
        PsiExpression value = args[1];
        if (condition.isEntrySet() && isUsedAsReference(value, condition)) return;
        if (hasMapUsages(condition, value)) return;
        if (!LambdaGenerationUtil.canBeUncheckedLambda(value, variable -> condition.getMap().equals(variable))) return;

        ReplaceWithSingleMapOperation fix = ReplaceWithSingleMapOperation.create("replaceAll", putCall, value);
        holder.registerProblem(statement.getFirstChild(),
                               QuickFixBundle.message("java.8.map.api.inspection.description", fix.myMethodName), fix);
      }

      private static PsiMethodCallExpression findPutMethodParent(PsiElement element) {
        while (element != null && !(element instanceof PsiMethod)) {
          if (element instanceof PsiExpression expression) {
            PsiMethodCallExpression putCall = extractMapMethodCall(expression, "put");
            if (putCall != null) return putCall;
          }
          element = element.getParent();
        }
        return null;
      }

      private static boolean isCommonPutCallForAllReferences(List<PsiReferenceExpression> references, PsiMethodCallExpression commonPutCall) {
        for (PsiReferenceExpression reference : references) {
          PsiMethodCallExpression putCall = findPutMethodParent(reference);
          if (putCall != commonPutCall) {
            return false;
          }
        }
        return true;
      }

      private static boolean hasMapUsages(@NotNull MapLoopCondition condition, @Nullable PsiExpression value) {
        return !VariableAccessUtils.getVariableReferences(condition.getMap(), value).stream()
          .map(ExpressionUtils::getCallForQualifier)
          .allMatch(call -> call != null && condition.isValueAccess(call));
      }

      private static boolean isUsedAsReference(@NotNull PsiElement value, @NotNull MapLoopCondition condition) {
        return !VariableAccessUtils.getVariableReferences(condition.getIterParam(), value).stream()
          .map(ExpressionUtils::getCallForQualifier)
          .allMatch(KEY_VALUE_GET_METHODS);
      }

      private void processMerge(MapCheckCondition condition,
                                PsiStatement existsBranch,
                                PsiStatement noneBranch) {
        if(noneBranch instanceof PsiExpressionStatement && existsBranch instanceof PsiExpressionStatement) {
          PsiExpression absentValue = extractPutValue(condition, noneBranch);
          if (absentValue == null) return;
          PsiExpression presentValue = extractPutValue(condition, existsBranch);
          if (presentValue == null || !LambdaGenerationUtil.canBeUncheckedLambda(presentValue)) return;
          // absentValue should not refer map
          if (!PsiTreeUtil.processElements(absentValue, e -> !condition.isMap(e))) return;
          boolean hasVariable = condition.hasVariable();
          if (hasVariable && PsiTreeUtil.collectElements(presentValue, condition::isValueReference).length == 0) return;
          PsiElement[] mapRefs = PsiTreeUtil.collectElements(presentValue, condition::isMap);
          if(hasVariable ^ mapRefs.length == 0) return;
          for(PsiElement mapRef : mapRefs) {
            PsiElement parent = mapRef.getParent();
            if (!(parent instanceof PsiReferenceExpression) || condition.extractGetCall(parent.getParent()) == null) return;
          }
          if (PsiTreeUtil.collectElements(presentValue, e -> PsiEquivalenceUtil.areElementsEquivalent(e, absentValue)).length == 0) {
            return;
          }
          if (NullabilityUtil.getExpressionNullability(absentValue) == Nullability.NULLABLE ||
              NullabilityUtil.getExpressionNullability(presentValue) == Nullability.NULLABLE) {
            return;
          }
          boolean informationLevel =
            !mySideEffects && SideEffectChecker.mayHaveSideEffects(presentValue, ex -> condition.extractGetCall(ex) != null);
          register(condition, holder, informationLevel, new ReplaceWithSingleMapOperation("merge", PsiTreeUtil
            .getParentOfType(absentValue, PsiMethodCallExpression.class), presentValue, noneBranch));
        }
      }

      private void processGetPut(MapCheckCondition condition, PsiElement result, PsiElement exists, PsiElement none) {
        PsiMethodCallExpression getCall = condition.extractGetCall(exists);
        if(getCall == null) return;

        if(!(none instanceof PsiExpression noneExpression)) return;
        PsiMethodCallExpression putCall = extractMapMethodCall(noneExpression, "put");
        if (mySuggestMapPutIfAbsent &&
            putCall != null &&
            condition.isGetNull() &&
            condition.isMap(putCall.getMethodExpression().getQualifierExpression())) {
          PsiExpression[] putArgs = putCall.getArgumentList().getExpressions();
          if (putArgs.length != 2 || !condition.isKey(putArgs[0]) || !ExpressionUtils.isSafelyRecomputableExpression(putArgs[1])) return;
          register(condition, holder, false, new ReplaceWithSingleMapOperation("putIfAbsent", getCall, putArgs[1], result));
        }
        if (mySuggestMapGetOrDefault && condition.isContainsKey() && ExpressionUtils.isSafelyRecomputableExpression(noneExpression) &&
            condition.isMapValueType(noneExpression.getType())) {
          register(condition, holder, false, new ReplaceWithSingleMapOperation("getOrDefault", getCall, noneExpression, result));
        }
      }

      private void processSingleBranch(MapCheckCondition condition, PsiStatement noneBranch) {
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(noneBranch);
        if(assignment != null && mySuggestMapGetOrDefault && condition.isContainsKey()) {
          /*
            value = map.get(key);
            if(value == null) {
              value = ...
            }
           */
          PsiExpression rValue = assignment.getRExpression();
          if (ExpressionUtils.isSafelyRecomputableExpression(rValue) && condition.isValueReference(assignment.getLExpression()) &&
              !condition.isValueReference(rValue) && condition.isMapValueType(rValue.getType())) {
            register(condition, holder, false, ReplaceWithSingleMapOperation.fromIf("getOrDefault", condition, rValue));
          }
        } else if (condition.isGetNull()) {
          /*
            value = map.get(key);
            if(value == null) {
              value = ...
              map.put(key, value);
            }
           */
          PsiExpression lambdaCandidate = extractLambdaCandidate(condition, noneBranch);
          if (lambdaCandidate != null && mySuggestMapComputeIfAbsent) {
            if (NullabilityUtil.getExpressionNullability(lambdaCandidate) == Nullability.NULLABLE) return;
            boolean informationLevel = !mySideEffects && SideEffectChecker.mayHaveSideEffects(lambdaCandidate);
            register(condition, holder, informationLevel, ReplaceWithSingleMapOperation.fromIf("computeIfAbsent", condition, lambdaCandidate));
          }
          if (lambdaCandidate == null) {
            PsiExpression expression = extractPutValue(condition, noneBranch);
            if(expression != null) {
              String replacement = null;
              boolean informationLevel = false;
              if (mySuggestMapPutIfAbsent && ExpressionUtils.isSafelyRecomputableExpression(expression) && !condition.isValueReference(expression)) {
                replacement = "putIfAbsent";
              }
              else if (mySuggestMapComputeIfAbsent && !condition.hasVariable()) {
                informationLevel = !mySideEffects && SideEffectChecker.mayHaveSideEffects(expression);
                replacement = "computeIfAbsent";
              }
              if(replacement != null) {
                if(condition.hasVariable()) {
                  register(condition, holder, informationLevel, ReplaceWithSingleMapOperation.fromIf(replacement, condition, expression));
                } else {
                  PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
                  LOG.assertTrue(call != null);
                  register(condition, holder, informationLevel, new ReplaceWithSingleMapOperation(replacement, call, expression, noneBranch));
                }
              }
            }
          }
        }
      }
    };
  }


  @NotNull
  public static String getNameCandidate(String name) {
    // Either last uppercase letter (if it's not the last letter) or the first letter, removing leading underscores
    // token -> t
    // myAccessToken -> t
    // SQL -> s
    // __name -> n
    // __1 -> k
    String nameCandidate;
    name = name.replaceFirst("^[_\\d]+", "");
    if (name.isEmpty()) return "k";
    nameCandidate = name.substring(0, 1);
    for (int pos = name.length() - 1; pos > 0; pos--) {
      if (Character.isUpperCase(name.charAt(pos))) {
        if (pos != name.length() - 1) {
          nameCandidate = name.substring(pos, pos + 1);
        }
        break;
      }
    }
    return StringUtil.toLowerCase(nameCandidate);
  }

  private static class ReplaceWithSingleMapOperation extends PsiUpdateModCommandQuickFix {
    private final String myMethodName;
    private final SmartPsiElementPointer<PsiMethodCallExpression> myCallPointer;
    private final SmartPsiElementPointer<PsiExpression> myValuePointer;
    private final SmartPsiElementPointer<PsiElement> myResultPointer;

    ReplaceWithSingleMapOperation(String methodName, PsiMethodCallExpression call, PsiExpression value, PsiElement result) {
      myMethodName = methodName;
      SmartPointerManager manager = SmartPointerManager.getInstance(value.getProject());
      myCallPointer = manager.createSmartPsiElementPointer(call);
      myValuePointer = manager.createSmartPsiElementPointer(value);
      myResultPointer = manager.createSmartPsiElementPointer(result);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement outerElement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class,
                                                            PsiConditionalExpression.class, PsiForeachStatement.class);
      if (outerElement == null) return;
      MapCondition condition = outerElement instanceof PsiForeachStatement ?
                               MapLoopCondition.create((PsiForeachStatement)outerElement) :
                               fromConditional(outerElement, true);
      if(condition == null) return;
      PsiMethodCallExpression call = updater.getWritable(myCallPointer.getElement());
      if (call == null) return;
      PsiExpressionList argsList = call.getArgumentList();
      PsiExpression[] args = argsList.getExpressions();
      if(args.length == 0) return;
      if ((myMethodName.equals("merge") || myMethodName.equals("replaceAll")) && args.length != 2) return;
      PsiExpression value = updater.getWritable(myValuePointer.getElement());
      if (value == null) return;
      PsiElement result = updater.getWritable(myResultPointer.getElement());
      if(result == null) return;

      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CommentTracker ct = new CommentTracker();
      ExpressionUtils.bindCallTo(call, myMethodName);
      PsiExpression replacement;
      switch (myMethodName) {
        case "computeIfAbsent" -> {
          PsiExpression key = args[0];
          List<PsiReferenceExpression> refs = Collections.emptyList();
          String nameCandidate = "k";
          if (key instanceof PsiReferenceExpression && ((PsiReferenceExpression)key).getQualifier() == null) {
            // try to use lambda parameter if key is simple reference and has the same type as map keys
            PsiMethod method = call.resolveMethod();
            if (method != null) {
              PsiType argType = method.getParameterList().getParameters()[0].getType();
              PsiType mapKeyType = call.resolveMethodGenerics().getSubstitutor().substitute(argType);
              PsiType keyType = key.getType();

              if (mapKeyType != null && keyType != null && keyType.isAssignableFrom(mapKeyType)) {
                PsiElement target = ((PsiReferenceExpression)key).resolve();
                refs = target == null ? Collections.emptyList() :
                       StreamEx.of(PsiTreeUtil.collectElementsOfType(value, PsiReferenceExpression.class))
                         .filter(ref -> ref.getQualifierExpression() == null && ref.isReferenceTo(target)).toList();
                if (!refs.isEmpty()) {
                  nameCandidate = getNameCandidate(((PsiReferenceExpression)key).getReferenceName());
                }
              }
            }
          }
          String varName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(nameCandidate, value, true);
          for (PsiReferenceExpression ref : refs) {
            ExpressionUtils.bindReferenceTo(ref, varName);
          }
          replacement = factory.createExpressionFromText(varName + " -> " + ct.text(value), value);
        }
        case "merge" -> {
          MapCheckCondition checkCondition = ObjectUtils.tryCast(condition, MapCheckCondition.class);
          if (checkCondition == null) return;
          PsiExpression absentValue = args[1];
          String aVar = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("a", value, true);
          String bVar = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("b", value, true);
          for (PsiElement e : PsiTreeUtil.collectElements(value, e -> PsiEquivalenceUtil.areElementsEquivalent(e, absentValue))) {
            ct.replace(e, factory.createIdentifier(bVar));
          }
          for (PsiElement e : PsiTreeUtil
            .collectElements(value, e -> checkCondition.extractGetCall(e) != null || checkCondition.isValueReference(e))) {
            ct.replace(e, factory.createIdentifier(aVar));
          }
          replacement = factory.createExpressionFromText("(" + aVar + "," + bVar + ") -> " + ct.text(value), value);
        }
        case "replaceAll" -> {
          MapLoopCondition loopCondition = ObjectUtils.tryCast(condition, MapLoopCondition.class);
          if (loopCondition == null) return;
          String kVar = suggestKeyName(loopCondition, value);
          String vVar = new VariableNameGenerator(value, VariableKind.PARAMETER).byName("v", "value").generate(true);
          replacement = createLambdaForLoopReplacement(factory, kVar, vVar, loopCondition, value, ct);
          ct.delete(args);
        }
        default -> replacement = ct.markUnchanged(value);
      }
      PsiElement newArg;
      if (args.length == 2 && !myMethodName.equals("merge") && !myMethodName.equals("replaceAll")) {
        newArg = ct.replace(args[1], replacement);
      } else {
        newArg = argsList.add(replacement);
      }
      if(newArg instanceof PsiLambdaExpression) {
        LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference((PsiLambdaExpression)newArg);
      }
      if (PsiTreeUtil.isAncestor(outerElement, result, true)) {
        result = ct.replaceAndRestoreComments(outerElement, result);
      } else {
        ct.deleteAndRestoreComments(outerElement);
      }
      PsiVariable variable = condition instanceof MapCheckCondition ? ((MapCheckCondition)condition).extractDeclaration() : null;
      if (variable != null && !PsiTreeUtil.isAncestor(result, variable, true) && ReferencesSearch.search(variable).findFirst() == null) {
        new CommentTracker().deleteAndRestoreComments(variable);
      }
      CodeStyleManager.getInstance(project).reformat(result);
    }

    @NotNull
    private static String suggestKeyName(@NotNull MapLoopCondition loopCondition, @NotNull PsiElement value) {
      VariableNameGenerator generator = new VariableNameGenerator(value, VariableKind.PARAMETER);
      if (!loopCondition.isEntrySet()) {
        String origName = loopCondition.getIterParam().getName();
        String nameCandidate = getNameCandidate(origName);
        if (origName.equals(nameCandidate)) return nameCandidate;
        generator.byName(nameCandidate);
      }
      return generator.byName("k", "key").generate(true);
    }

    @NotNull
    private static PsiExpression createLambdaForLoopReplacement(@NotNull PsiElementFactory factory,
                                                                @NotNull String kVar,
                                                                @NotNull String vVar,
                                                                @NotNull MapLoopCondition loopCondition,
                                                                @NotNull PsiExpression value,
                                                                @NotNull CommentTracker tracker) {
      if (value instanceof PsiMethodCallExpression) {
        if (loopCondition.isKeyAccess(value)) return factory.createExpressionFromText("(" + kVar + "," + vVar + ") ->" + kVar, value);
        if (loopCondition.isValueAccess(value)) return factory.createExpressionFromText("(" + kVar + "," + vVar + ") ->" + vVar, value);
      }
      if (!loopCondition.isEntrySet()) {
        PsiParameter param = loopCondition.getIterParam();
        VariableAccessUtils.getVariableReferences(param, value).forEach(ref -> ExpressionUtils.bindReferenceTo(ref, kVar));
      }
      Collection<PsiMethodCallExpression> calls = PsiTreeUtil.collectElementsOfType(value, PsiMethodCallExpression.class);
      for (PsiMethodCallExpression call : calls) {
        if (loopCondition.isKeyAccess(call)) {
          tracker.replace(call, kVar);
        }
        else if (loopCondition.isValueAccess(call)) {
          tracker.replace(call, vVar);
        }
      }
      return factory.createExpressionFromText("(" + kVar + "," + vVar + ") ->" + tracker.text(value), value);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return QuickFixBundle.message("java.8.map.api.inspection.fix.text", myMethodName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("java.8.map.api.inspection.fix.family.name");
    }

    @NotNull
    static ReplaceWithSingleMapOperation fromIf(String methodName, MapCheckCondition condition, PsiExpression value) {
      PsiMethodCallExpression call = condition.getCheckCall();
      return create(methodName, call, value);
    }

    @NotNull
    static ReplaceWithSingleMapOperation create(String methodName, PsiMethodCallExpression call, PsiExpression value) {
      PsiStatement result = PsiTreeUtil.getParentOfType(call, PsiStatement.class);
      LOG.assertTrue(result != null);
      return new ReplaceWithSingleMapOperation(methodName, call, value, result);
    }
  }
  private static class GetPutToComputeFix extends PsiUpdateModCommandQuickFix {
    private final SmartPsiElementPointer<PsiLocalVariable> variablePointer;
    private GetPutToComputeFix(PsiLocalVariable variable) {
      variablePointer = SmartPointerManager.createPointer(variable);
    }

    @Override
    public @NotNull String getName() {
      return QuickFixBundle.message("java.8.map.api.inspection.fix.text", "compute");
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("java.8.map.api.inspection.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      CommentTracker commentTracker = new CommentTracker();
      PsiMethodCallExpression call = (PsiMethodCallExpression) element;
      PsiLocalVariable variable = updater.getWritable(variablePointer.getElement());
      if (variable == null) return;
      ExpressionUtils.bindCallTo(call, "compute");
      String variableName = variable.getName();

      PsiExpressionList argsList = call.getArgumentList();
      PsiExpression[] args = argsList.getExpressions();
      if(args.length != 2) return;
      PsiExpression exp = args[1];

      VariableNameGenerator generator = new VariableNameGenerator(call, VariableKind.PARAMETER);
      String keyName = generator.byName("k", "key").generate(true);

      String lambdaParameters = "(" + keyName + ", " + variableName + ")";
      String lambdaExpressionText = lambdaParameters + " -> " + commentTracker.text(exp);
      commentTracker.delete(variable);
      commentTracker.replaceExpressionAndRestoreComments(exp, lambdaExpressionText);
    }
  }

  private static void register(MapCheckCondition condition, ProblemsHolder holder, boolean informationLevel, ReplaceWithSingleMapOperation fix) {
    if (informationLevel && !holder.isOnTheFly()) return;
    holder.registerProblem(condition.getFullCondition(), QuickFixBundle.message("java.8.map.api.inspection.description", fix.myMethodName),
                           informationLevel ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix);
  }

}