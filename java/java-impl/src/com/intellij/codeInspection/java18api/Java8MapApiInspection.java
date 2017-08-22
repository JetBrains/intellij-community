/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * @author Tagir Valeev
 */
public class Java8MapApiInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(Java8MapApiInspection.class);
  public static final String SHORT_NAME = "Java8MapApi";

  public boolean mySuggestMapGetOrDefault = true;
  public boolean mySuggestMapComputeIfAbsent = true;
  public boolean mySuggestMapPutIfAbsent = true;
  public boolean mySuggestMapMerge = true;
  public boolean myTreatGetNullAsContainsKey = false;
  public boolean mySideEffects = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Suggest conversion to Map.computeIfAbsent", "mySuggestMapComputeIfAbsent");
    panel.addCheckbox("Suggest conversion to Map.getOrDefault", "mySuggestMapGetOrDefault");
    panel.addCheckbox("Suggest conversion to Map.putIfAbsent", "mySuggestMapPutIfAbsent");
    panel.addCheckbox("Suggest conversion to Map.merge", "mySuggestMapMerge");
    panel.addCheckbox("Treat 'get(k) != null' the same as 'containsKey(k)' (may change semantics)", "myTreatGetNullAsContainsKey");
    panel.addCheckbox("Suggest replacement even if lambda may have side effects", "mySideEffects");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
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
          boolean informationLevel =
            !mySideEffects && SideEffectChecker.mayHaveSideEffects(presentValue, ex -> condition.extractGetCall(ex) != null);
          condition.register(holder, informationLevel, new ReplaceWithSingleMapOperation("merge", PsiTreeUtil
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
          if (putArgs.length != 2 || !condition.isKey(putArgs[0]) || !ExpressionUtils.isSimpleExpression(putArgs[1])) return;
          condition.register(holder, false, new ReplaceWithSingleMapOperation("putIfAbsent", getCall, putArgs[1], result));
        }
        if (mySuggestMapGetOrDefault && condition.isContainsKey() && ExpressionUtils.isSimpleExpression(noneExpression) &&
          condition.isMapValueType(noneExpression.getType())) {
          condition.register(holder, false, new ReplaceWithSingleMapOperation("getOrDefault", getCall, noneExpression, result));
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
          if (ExpressionUtils.isSimpleExpression(rValue) && condition.isValueReference(assignment.getLExpression()) &&
              !condition.isValueReference(rValue) && condition.isMapValueType(rValue.getType())) {
            condition.register(holder, false, ReplaceWithSingleMapOperation.fromIf("getOrDefault", condition, rValue));
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
            boolean informationLevel = !mySideEffects && SideEffectChecker.mayHaveSideEffects(lambdaCandidate);
            condition
              .register(holder, informationLevel, ReplaceWithSingleMapOperation.fromIf("computeIfAbsent", condition, lambdaCandidate));
          }
          if (lambdaCandidate == null) {
            PsiExpression expression = extractPutValue(condition, noneBranch);
            if(expression != null) {
              String replacement = null;
              boolean informationLevel = false;
              if (mySuggestMapPutIfAbsent && ExpressionUtils.isSimpleExpression(expression) && !condition.isValueReference(expression)) {
                replacement = "putIfAbsent";
              }
              else if (mySuggestMapComputeIfAbsent && !condition.hasVariable()) {
                informationLevel = !mySideEffects && SideEffectChecker.mayHaveSideEffects(expression);
                replacement = "computeIfAbsent";
              }
              if(replacement != null) {
                if(condition.hasVariable()) {
                  condition.register(holder, informationLevel, ReplaceWithSingleMapOperation.fromIf(replacement, condition, expression));
                } else {
                  PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
                  LOG.assertTrue(call != null);
                  condition
                    .register(holder, informationLevel, new ReplaceWithSingleMapOperation(replacement, call, expression, noneBranch));
                }
              }
            }
          }
        }
      }
    };
  }

  @Nullable
  static PsiExpression extractLambdaCandidate(MapCheckCondition condition, PsiStatement statement) {
    PsiAssignmentExpression assignment;
    PsiExpression putValue = extractPutValue(condition, statement);
    if(putValue != null) {
      // like map.put(key, val = new ArrayList<>());
      assignment = ExpressionUtils.getAssignment(putValue);
    }
    else {
      if (!(statement instanceof PsiBlockStatement)) return null;
      // like val = new ArrayList<>(); map.put(key, val);
      PsiStatement[] statements = ((PsiBlockStatement)statement).getCodeBlock().getStatements();
      if (statements.length != 2) return null;
      putValue = extractPutValue(condition, statements[1]);
      if (!condition.isValueReference(putValue)) return null;
      assignment = ExpressionUtils.getAssignment(statements[0]);
    }
    if (assignment == null) return null;
    PsiExpression lambdaCandidate = assignment.getRExpression();
    if (lambdaCandidate == null || !condition.isValueReference(assignment.getLExpression())) return null;
    if (!LambdaGenerationUtil.canBeUncheckedLambda(lambdaCandidate)) return null;
    return lambdaCandidate;
  }

  @Contract("null, _ -> null")
  static PsiMethodCallExpression extractMapMethodCall(PsiExpression expression, @NotNull String expectedName) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    if (!expectedName.equals(methodCallExpression.getMethodExpression().getReferenceName())) return null;
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return null;
    PsiMethod[] superMethods = method.findDeepestSuperMethods();
    if (superMethods.length == 0) {
      superMethods = new PsiMethod[]{method};
    }
    return StreamEx.of(superMethods).map(PsiMember::getContainingClass).nonNull().map(PsiClass::getQualifiedName)
      .has(CommonClassNames.JAVA_UTIL_MAP) ? methodCallExpression : null;
  }


  @Contract("_, null -> null")
  @Nullable
  private static PsiExpression extractPutValue(MapCheckCondition condition, PsiStatement statement) {
    if(!(statement instanceof PsiExpressionStatement)) return null;
    PsiMethodCallExpression putCall = extractMapMethodCall(((PsiExpressionStatement)statement).getExpression(), "put");
    if (putCall == null) return null;
    PsiExpression[] putArguments = putCall.getArgumentList().getExpressions();
    return putArguments.length == 2 &&
           condition.isMap(putCall.getMethodExpression().getQualifierExpression()) &&
           condition.isKey(putArguments[0]) ? putArguments[1] : null;
  }

  @Nullable
  @Contract("_, null -> null")
  static PsiMethodCallExpression tryExtractMapGetCall(PsiReferenceExpression target, PsiElement element) {
    if(element instanceof PsiDeclarationStatement) {
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)element;
      PsiElement[] elements = declaration.getDeclaredElements();
      if(elements.length > 0) {
        PsiElement lastDeclaration = elements[elements.length - 1];
        if(lastDeclaration instanceof PsiLocalVariable && target.isReferenceTo(lastDeclaration)) {
          PsiLocalVariable var = (PsiLocalVariable)lastDeclaration;
          return extractMapMethodCall(var.getInitializer(), "get");
        }
      }
    }
    PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(element);
    if(assignment != null) {
      PsiExpression lValue = assignment.getLExpression();
      if (lValue instanceof PsiReferenceExpression &&
          EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(target, lValue)) {
        return extractMapMethodCall(assignment.getRExpression(), "get");
      }
    }
    return null;
  }

  @Contract("null, _ -> null")
  static MapCheckCondition fromConditional(PsiElement conditional, boolean treatGetNullAsContainsKey) {
    if(conditional instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)conditional;
      return tryExtract(ifStatement.getCondition(), ifStatement, treatGetNullAsContainsKey);
    }
    if(conditional instanceof PsiConditionalExpression) {
      PsiConditionalExpression ternary = (PsiConditionalExpression)conditional;
      PsiElement parent = ternary.getParent().getParent();
      return tryExtract(ternary.getCondition(), parent instanceof PsiStatement ? (PsiStatement)parent : null, treatGetNullAsContainsKey);
    }
    return null;
  }

  @Nullable
  private static MapCheckCondition tryExtract(PsiExpression fullCondition, @Nullable PsiStatement statement, boolean treatGetNullAsContainsKey) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(fullCondition);
    boolean negated = false;
    while(condition != null && BoolUtils.isNegation(condition)) {
      negated ^= true;
      condition = BoolUtils.getNegated(condition);
    }
    if(condition == null) return null;
    PsiReferenceExpression valueReference = null;
    boolean containsKey = false;
    PsiMethodCallExpression call;
    if(condition instanceof PsiBinaryExpression) {
      negated ^= ((PsiBinaryExpression)condition).getOperationTokenType().equals(JavaTokenType.EQEQ);
      PsiExpression value = ExpressionUtils.getValueComparedWithNull((PsiBinaryExpression)condition);
      if(value instanceof PsiReferenceExpression && statement != null) {
        valueReference = (PsiReferenceExpression)value;
        PsiElement previous = PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement);
        call = tryExtractMapGetCall(valueReference, previous);
      } else {
        call = extractMapMethodCall(value, "get");
      }
    } else {
      call = extractMapMethodCall(condition, "containsKey");
      containsKey = true;
    }
    if(call == null) return null;
    PsiExpression mapExpression = call.getMethodExpression().getQualifierExpression();
    if(mapExpression == null) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if(args.length != 1) return null;
    PsiExpression keyExpression = args[0];
    return new MapCheckCondition(valueReference, mapExpression, keyExpression, fullCondition, negated, containsKey,
                                 treatGetNullAsContainsKey);
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
    return nameCandidate.toLowerCase(Locale.ENGLISH);
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

    @NotNull
    static ReplaceWithSingleMapOperation fromIf(String methodName, MapCheckCondition condition, PsiExpression value) {
      PsiMethodCallExpression call = condition.getCheckCall();
      PsiStatement result = PsiTreeUtil.getParentOfType(call, PsiStatement.class);
      LOG.assertTrue(result != null);
      return new ReplaceWithSingleMapOperation(methodName, call, value, result);
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

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement conditional = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiIfStatement.class, PsiConditionalExpression.class);
      if(conditional == null) return;
      MapCheckCondition condition = fromConditional(conditional, true);
      if(condition == null) return;
      PsiMethodCallExpression call = myCallPointer.getElement();
      if (call == null) return;
      PsiExpressionList argsList = call.getArgumentList();
      PsiExpression[] args = argsList.getExpressions();
      if(args.length == 0) return;
      if(myMethodName.equals("merge") && args.length != 2) return;
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
              refs = StreamEx.of(PsiTreeUtil.collectElementsOfType(value, PsiReferenceExpression.class))
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
        PsiExpression absentValue = args[1];
        String aVar = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("a", value, true);
        String bVar = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("b", value, true);
        for(PsiElement e : PsiTreeUtil.collectElements(value, e -> PsiEquivalenceUtil.areElementsEquivalent(e, absentValue))) {
          ct.replace(e, factory.createIdentifier(bVar));
        }
        for(PsiElement e : PsiTreeUtil.collectElements(value, e -> condition.extractGetCall(e) != null || condition.isValueReference(e))) {
          ct.replace(e, factory.createIdentifier(aVar));
        }
        replacement = factory.createExpressionFromText("("+aVar+","+bVar+") -> "+ct.text(value), value);
      } else {
        replacement = ct.markUnchanged(value);
      }
      PsiElement newArg;
      if(args.length == 2 && !myMethodName.equals("merge")) {
        newArg = ct.replace(args[1], replacement);
      } else {
        newArg = argsList.add(replacement);
      }
      if(newArg instanceof PsiLambdaExpression) {
        LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference((PsiLambdaExpression)newArg);
      }
      if(PsiTreeUtil.isAncestor(conditional, result, true)) {
        result = ct.replaceAndRestoreComments(conditional, ct.markUnchanged(result));
      } else {
        ct.deleteAndRestoreComments(conditional);
      }
      PsiVariable variable = condition.extractDeclaration();
      if (variable != null && !PsiTreeUtil.isAncestor(result, variable, true) && ReferencesSearch.search(variable).findFirst() == null) {
        new CommentTracker().deleteAndRestoreComments(variable);
      }
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }

  static class MapCheckCondition {
    private final @Nullable PsiReferenceExpression myValueReference;
    private final PsiExpression myMapExpression;
    private final PsiExpression myKeyExpression;
    private final PsiExpression myFullCondition;
    private final boolean myNegated;
    private final boolean myContainsKey;
    private final boolean myTreatGetNullAsContainsKey;

    private MapCheckCondition(@Nullable PsiReferenceExpression valueReference,
                              PsiExpression mapExpression,
                              PsiExpression keyExpression,
                              PsiExpression fullCondition,
                              boolean negated,
                              boolean containsKey,
                              boolean treatGetNullAsContainsKey) {
      myValueReference = valueReference;
      myMapExpression = mapExpression;
      myKeyExpression = keyExpression;
      myFullCondition = fullCondition;
      myNegated = negated;
      myContainsKey = containsKey;
      myTreatGetNullAsContainsKey = treatGetNullAsContainsKey;
    }

    boolean isContainsKey() {
      return myContainsKey || myTreatGetNullAsContainsKey;
    }

    boolean isGetNull() {
      return !myContainsKey || myTreatGetNullAsContainsKey;
    }

    @Contract("null -> false")
    boolean isMap(PsiElement element) {
      return element != null && PsiEquivalenceUtil.areElementsEquivalent(myMapExpression, element);
    }

    @Contract("null -> false")
    boolean isKey(PsiElement element) {
      return element != null && PsiEquivalenceUtil.areElementsEquivalent(myKeyExpression, element);
    }

    PsiMethodCallExpression extractGetCall(PsiElement element) {
      if(!(element instanceof PsiExpression)) return null;
      PsiMethodCallExpression getCall = extractMapMethodCall((PsiExpression)element, "get");
      if(getCall == null) return null;
      PsiExpression[] args = getCall.getArgumentList().getExpressions();
      return args.length == 1 && isKey(args[0]) && isMap(getCall.getMethodExpression().getQualifierExpression()) ? getCall : null;
    }

    @Contract("null -> false")
    boolean isValueReference(PsiElement element) {
      return element != null && myValueReference != null && PsiEquivalenceUtil.areElementsEquivalent(element, myValueReference);
    }

    <T extends PsiElement> T getExistsBranch(T thenBranch, T elseBranch) {
      return myNegated ? elseBranch : thenBranch;
    }

    <T extends PsiElement> T getNoneBranch(T thenBranch, T elseBranch) {
      return myNegated ? thenBranch : elseBranch;
    }

    PsiVariable extractDeclaration() {
      if(myValueReference == null) return null;
      return PsiTreeUtil.getParentOfType(myKeyExpression, PsiVariable.class, true, PsiStatement.class);
    }

    boolean hasVariable() {
      if(myValueReference == null) return false;
      PsiVariable var = extractDeclaration();
      // has variable, but it used only in condition
      return var == null || ReferencesSearch.search(var).findAll().size() != 1;
    }

    PsiMethodCallExpression getCheckCall() {
      return PsiTreeUtil.getParentOfType(myMapExpression, PsiMethodCallExpression.class);
    }

    public PsiExpression getFullCondition() {
      return myFullCondition;
    }

    public void register(ProblemsHolder holder, boolean informationLevel, ReplaceWithSingleMapOperation fix) {
      //noinspection DialogTitleCapitalization
      holder.registerProblem(getFullCondition(), QuickFixBundle.message("java.8.map.api.inspection.description", fix.myMethodName),
                             informationLevel ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix);
    }

    public boolean isMapValueType(@Nullable PsiType type) {
      if (type == null) return false;
      PsiType mapExpressionType = myMapExpression.getType();
      PsiType valueTypeParameter = PsiUtil.substituteTypeParameter(mapExpressionType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
      return valueTypeParameter != null && valueTypeParameter.isAssignableFrom(type);
    }
  }
}