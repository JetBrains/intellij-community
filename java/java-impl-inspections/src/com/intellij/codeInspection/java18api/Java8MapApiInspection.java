// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.java.JavaBundle;
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

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.siyeh.ig.psiutils.Java8MigrationUtils.*;
import static com.siyeh.ig.psiutils.Java8MigrationUtils.MapCheckCondition.fromConditional;

public class Java8MapApiInspection extends AbstractBaseJavaLocalInspectionTool {
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

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(JavaBundle.message("checkbox.suggest.conversion.to.map.computeifabsent"), "mySuggestMapComputeIfAbsent");
    panel.addCheckbox(JavaBundle.message("checkbox.suggest.conversion.to.map.getordefault"), "mySuggestMapGetOrDefault");
    panel.addCheckbox(JavaBundle.message("checkbox.suggest.conversion.to.map.putifabsent"), "mySuggestMapPutIfAbsent");
    panel.addCheckbox(JavaBundle.message("checkbox.suggest.conversion.to.map.merge"), "mySuggestMapMerge");
    panel.addCheckbox(JavaBundle.message("checkbox.suggest.conversion.to.map.replaceall"), "mySuggestMapReplaceAll");
    panel.addCheckbox(JavaBundle.message("checkbox.treat.get.k.null.the.same.as.containskey.k.may.change.semantics"), "myTreatGetNullAsContainsKey");
    panel.addCheckbox(JavaBundle.message("checkbox.suggest.replacement.even.if.lambda.may.have.side.effects"), "mySideEffects");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!JavaFeature.ADVANCED_COLLECTIONS_API.isFeatureSupported(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitConditionalExpression(PsiConditionalExpression expression) {
        MapCheckCondition condition = fromConditional(expression, myTreatGetNullAsContainsKey);
        if(condition == null || condition.hasVariable()) return;
        PsiExpression existsBranch = condition.getExistsBranch(expression.getThenExpression(), expression.getElseExpression());
        PsiExpression noneBranch = condition.getNoneBranch(expression.getThenExpression(), expression.getElseExpression());
        processGetPut(condition, existsBranch, existsBranch, noneBranch);
      }

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
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
          EquivalenceChecker.Match match = EquivalenceChecker.getCanonicalPsiEquivalence().statementsMatch(noneBranch, existsBranch);

          processGetPut(condition, existsBranch, match.getRightDiff(), match.getLeftDiff());
        }
      }

      @Override
      public void visitForeachStatement(PsiForeachStatement statement) {
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

      private boolean hasMapUsages(@NotNull MapLoopCondition condition, @Nullable PsiExpression value) {
        return !VariableAccessUtils.getVariableReferences(condition.getMap(), value).stream()
          .map(ExpressionUtils::getCallForQualifier)
          .allMatch(call -> condition.isValueAccess(call));
      }

      private boolean isUsedAsReference(@NotNull PsiElement value, @NotNull MapLoopCondition condition) {
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

        if(!(none instanceof PsiExpression)) return;
        PsiExpression noneExpression = (PsiExpression)none;
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

  private static class ReplaceWithSingleMapOperation implements LocalQuickFix {
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
    public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
      PsiMethodCallExpression call = myCallPointer.getElement();
      PsiExpression value = myValuePointer.getElement();
      PsiElement result = myResultPointer.getElement();
      if (call == null || value == null || result == null) return null;
      return new ReplaceWithSingleMapOperation(myMethodName, PsiTreeUtil.findSameElementInCopy(call, target),
                                               PsiTreeUtil.findSameElementInCopy(value, target), 
                                               PsiTreeUtil.findSameElementInCopy(result, target));
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement outerElement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiIfStatement.class,
                                                            PsiConditionalExpression.class, PsiForeachStatement.class);
      if (outerElement == null) return;
      MapCondition condition = outerElement instanceof PsiForeachStatement ?
                               MapLoopCondition.create((PsiForeachStatement)outerElement) :
                               fromConditional(outerElement, true);
      if(condition == null) return;
      PsiMethodCallExpression call = myCallPointer.getElement();
      if (call == null) return;
      PsiExpressionList argsList = call.getArgumentList();
      PsiExpression[] args = argsList.getExpressions();
      if(args.length == 0) return;
      if ((myMethodName.equals("merge") || myMethodName.equals("replaceAll")) && args.length != 2) return;
      PsiExpression value = myValuePointer.getElement();
      if (value == null) return;
      PsiElement result = myResultPointer.getElement();
      if(result == null) return;

      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CommentTracker ct = new CommentTracker();
      ExpressionUtils.bindCallTo(call, myMethodName);
      PsiExpression replacement;
      if(myMethodName.equals("computeIfAbsent")) {
        PsiExpression key = args[0];
        List<PsiReferenceExpression> refs = Collections.emptyList();
        String nameCandidate = "k";
        if(key instanceof PsiReferenceExpression && ((PsiReferenceExpression)key).getQualifier() == null) {
          // try to use lambda parameter if key is simple reference and has the same type as map keys
          PsiMethod method = call.resolveMethod();
          if(method != null) {
            PsiType argType = method.getParameterList().getParameters()[0].getType();
            PsiType mapKeyType = call.resolveMethodGenerics().getSubstitutor().substitute(argType);
            PsiType keyType = key.getType();

            if(mapKeyType != null && keyType != null && keyType.isAssignableFrom(mapKeyType)) {
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
        for(PsiReferenceExpression ref : refs) {
          ExpressionUtils.bindReferenceTo(ref, varName);
        }
        replacement = factory.createExpressionFromText(varName + " -> " + ct.text(value), value);
      } else if (myMethodName.equals("merge")) {
        MapCheckCondition checkCondition = ObjectUtils.tryCast(condition, MapCheckCondition.class);
        if (checkCondition == null) return;
        PsiExpression absentValue = args[1];
        String aVar = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("a", value, true);
        String bVar = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("b", value, true);
        for(PsiElement e : PsiTreeUtil.collectElements(value, e -> PsiEquivalenceUtil.areElementsEquivalent(e, absentValue))) {
          ct.replace(e, factory.createIdentifier(bVar));
        }
        for (PsiElement e : PsiTreeUtil
          .collectElements(value, e -> checkCondition.extractGetCall(e) != null || checkCondition.isValueReference(e))) {
          ct.replace(e, factory.createIdentifier(aVar));
        }
        replacement = factory.createExpressionFromText("("+aVar+","+bVar+") -> "+ct.text(value), value);
      }
      else if (myMethodName.equals("replaceAll")) {
        MapLoopCondition loopCondition = ObjectUtils.tryCast(condition, MapLoopCondition.class);
        if (loopCondition == null) return;
        String kVar = suggestKeyName(loopCondition, value);
        String vVar = new VariableNameGenerator(value, VariableKind.PARAMETER).byName("v", "value").generate(true);
        replacement = createLambdaForLoopReplacement(factory, kVar, vVar, loopCondition, value, ct);
        ct.delete(args);
      }
      else {
        replacement = ct.markUnchanged(value);
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

  private static void register(MapCheckCondition condition, ProblemsHolder holder, boolean informationLevel, ReplaceWithSingleMapOperation fix) {
    if (informationLevel && !holder.isOnTheFly()) return;
    holder.registerProblem(condition.getFullCondition(), QuickFixBundle.message("java.8.map.api.inspection.description", fix.myMethodName),
                           informationLevel ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix);
  }

}