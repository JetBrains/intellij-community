/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import one.util.streamex.IntStreamEx;
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
  public boolean myTreatGetNullAsContainsKey = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Suggest conversion to Map.computeIfAbsent", "mySuggestMapComputeIfAbsent");
    panel.addCheckbox("Suggest conversion to Map.getOrDefault", "mySuggestMapGetOrDefault");
    panel.addCheckbox("Suggest conversion to Map.putIfAbsent", "mySuggestMapPutIfAbsent");
    panel.addCheckbox("Treat 'get(k) != null' the same as 'containsKey(k)' (may change semantics)", "myTreatGetNullAsContainsKey");
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
        MapCheckCondition condition = fromTernary(expression);
        if(condition == null || condition.hasVariable()) return;
        PsiExpression existsBranch = condition.getExistsBranch(expression.getThenExpression(), expression.getElseExpression());
        PsiExpression noneBranch = condition.getNoneBranch(expression.getThenExpression(), expression.getElseExpression());
        processGetPut(condition, expression, existsBranch, existsBranch, noneBranch);
      }

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        MapCheckCondition condition = fromIfStatement(statement);
        if(condition == null) return;
        PsiStatement existsBranch = ControlFlowUtils.stripBraces(condition.getExistsBranch(statement.getThenBranch(), statement.getElseBranch()));
        PsiStatement noneBranch = ControlFlowUtils.stripBraces(condition.getNoneBranch(statement.getThenBranch(), statement.getElseBranch()));
        if(existsBranch == null) {
          processSingleBranch(statement, condition, noneBranch);
        } else {
          if(condition.hasVariable()) return;
          EquivalenceChecker.Decision decision =
            EquivalenceChecker.getCanonicalPsiEquivalence().statementsAreEquivalentDecision(noneBranch, existsBranch);

          processGetPut(condition, statement, existsBranch, decision.getRightDiff(), decision.getLeftDiff());
        }
      }

      private void processGetPut(MapCheckCondition condition, PsiElement toRemove, PsiElement result, PsiElement exists, PsiElement none) {
        if(!(exists instanceof PsiExpression)) return;
        PsiMethodCallExpression getCall = extractMapMethodCall((PsiExpression)exists, "get");
        if (getCall == null || !condition.isMap(getCall.getMethodExpression().getQualifierExpression())) return;
        PsiExpression[] getArgs = getCall.getArgumentList().getExpressions();
        if (getArgs.length != 1 || !condition.isKey(getArgs[0])) return;

        if(!(none instanceof PsiExpression)) return;
        PsiExpression noneExpression = (PsiExpression)none;
        PsiMethodCallExpression putCall = extractMapMethodCall(noneExpression, "put");
        if (mySuggestMapPutIfAbsent &&
            putCall != null &&
            condition.isGetNull() &&
            condition.isMap(putCall.getMethodExpression().getQualifierExpression())) {
          PsiExpression[] putArgs = putCall.getArgumentList().getExpressions();
          if (putArgs.length != 2 || !condition.isKey(putArgs[0]) || !ExpressionUtils.isSimpleExpression(putArgs[1])) return;
          condition.register(holder, new ReplaceWithSingleMapOperation("putIfAbsent", getCall, putArgs[1], toRemove, result));
        }
        if (mySuggestMapGetOrDefault && condition.isContainsKey() && ExpressionUtils.isSimpleExpression(noneExpression) &&
          !(getCall.getType() instanceof PsiCapturedWildcardType)) {
          condition.register(holder, new ReplaceWithSingleMapOperation("getOrDefault", getCall, noneExpression, toRemove,
                                                                       result));
        }
      }

      private void processSingleBranch(PsiIfStatement statement, MapCheckCondition condition, PsiStatement noneBranch) {
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(noneBranch);
        if(assignment != null && mySuggestMapGetOrDefault && condition.isContainsKey()) {
          /*
            value = map.get(key);
            if(value == null) {
              value = ...
            }
           */
          if (ExpressionUtils.isSimpleExpression(assignment.getRExpression()) &&
              condition.isValueReference(assignment.getLExpression()) &&
              !condition.isValueReference(assignment.getRExpression())) {
            condition
              .register(holder, ReplaceWithSingleMapOperation.fromIf("getOrDefault", condition, statement, assignment.getRExpression()));
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
            condition.register(holder, ReplaceWithSingleMapOperation.fromIf("computeIfAbsent", condition, statement, lambdaCandidate));
          }
          if (lambdaCandidate == null) {
            PsiExpression expression = extractPutValue(condition, noneBranch);
            if(expression != null) {
              String replacement = null;
              if (mySuggestMapPutIfAbsent && ExpressionUtils.isSimpleExpression(expression) && !condition.isValueReference(expression)) {
                replacement = "putIfAbsent";
              }
              else if (mySuggestMapComputeIfAbsent && !condition.hasVariable()) {
                replacement = "computeIfAbsent";
              }
              if(replacement != null) {
                if(condition.hasVariable()) {
                  condition.register(holder, ReplaceWithSingleMapOperation.fromIf(replacement, condition, statement, expression));
                } else {
                  PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
                  LOG.assertTrue(call != null);
                  condition.register(holder, new ReplaceWithSingleMapOperation(replacement, call, expression, statement, noneBranch));
                }
              }
            }
          }
        }
      }
    };
  }

  @Nullable
  static PsiExpression getValueComparedWithNull(PsiBinaryExpression binOp) {
    if(!binOp.getOperationTokenType().equals(JavaTokenType.EQEQ) &&
       !binOp.getOperationTokenType().equals(JavaTokenType.NE)) return null;
    PsiExpression left = binOp.getLOperand();
    PsiExpression right = binOp.getROperand();
    if(ExpressionUtils.isNullLiteral(right)) return left;
    if(ExpressionUtils.isNullLiteral(left)) return right;
    return null;
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

  @Nullable
  MapCheckCondition fromIfStatement(PsiIfStatement ifStatement) {
    return tryExtract(ifStatement.getCondition(), ifStatement);
  }

  @Nullable
  MapCheckCondition fromTernary(PsiConditionalExpression ternary) {
    PsiElement parent = ternary.getParent().getParent();
    return tryExtract(ternary.getCondition(), parent instanceof PsiStatement ? (PsiStatement)parent : null);
  }

  @Nullable
  private MapCheckCondition tryExtract(PsiExpression fullCondition, @Nullable PsiStatement statement) {
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
      PsiExpression value = getValueComparedWithNull((PsiBinaryExpression)condition);
      if(value instanceof PsiReferenceExpression && statement != null) {
        valueReference = (PsiReferenceExpression)value;
        PsiElement previous = PsiTreeUtil.skipSiblingsBackward(statement, PsiWhiteSpace.class, PsiComment.class);
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
    return new MapCheckCondition(valueReference, mapExpression, keyExpression, fullCondition, negated, containsKey);
  }

  private static class ReplaceWithSingleMapOperation implements LocalQuickFix {
    private final String myMethodName;
    private final SmartPsiElementPointer<PsiMethodCallExpression> myCallPointer;
    private final SmartPsiElementPointer<PsiExpression> myValuePointer;
    private final SmartPsiElementPointer<PsiElement> myRemovedPointer;
    private final SmartPsiElementPointer<PsiElement> myResultPointer;

    ReplaceWithSingleMapOperation(String methodName, PsiMethodCallExpression call, PsiExpression value, PsiElement removed, PsiElement result) {
      myMethodName = methodName;
      SmartPointerManager manager = SmartPointerManager.getInstance(value.getProject());
      myCallPointer = manager.createSmartPsiElementPointer(call);
      myValuePointer = manager.createSmartPsiElementPointer(value);
      myRemovedPointer = manager.createSmartPsiElementPointer(removed);
      myResultPointer = manager.createSmartPsiElementPointer(result);
    }

    @NotNull
    static ReplaceWithSingleMapOperation fromIf(String methodName, MapCheckCondition condition, PsiStatement ifStatement, PsiExpression value) {
      PsiMethodCallExpression call = condition.getCheckCall();
      PsiStatement result = PsiTreeUtil.getParentOfType(call, PsiStatement.class);
      LOG.assertTrue(result != null);
      return new ReplaceWithSingleMapOperation(methodName, call, value, ifStatement, result);
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
      PsiMethodCallExpression call = myCallPointer.getElement();
      if (call == null) return;
      PsiExpressionList argsList = call.getArgumentList();
      PsiExpression[] args = argsList.getExpressions();
      if(args.length == 0) return;
      PsiExpression value = myValuePointer.getElement();
      if (value == null) return;
      PsiElement removed = myRemovedPointer.getElement();
      if (removed == null) return;
      PsiElement result = myResultPointer.getElement();
      if(result == null) return;

      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CommentTracker ct = new CommentTracker();
      call.getMethodExpression().handleElementRename(myMethodName);
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
              PsiElement element = ((PsiReferenceExpression)key).resolve();
              refs = StreamEx.of(PsiTreeUtil.collectElementsOfType(value, PsiReferenceExpression.class))
                .filter(ref -> ref.getQualifierExpression() == null && ref.isReferenceTo(element)).toList();
              if (!refs.isEmpty()) {
                String name = ((PsiReferenceExpression)key).getReferenceName();
                // like "myVariableName" => "mvn"
                nameCandidate = IntStreamEx.ofChars(name).mapFirst(Character::toUpperCase).filter(Character::isUpperCase).charsToString()
                  .toLowerCase(Locale.ENGLISH);
                if (nameCandidate.isEmpty()) {
                  nameCandidate = "k";
                }
              }
            }
          }
        }
        String varName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(nameCandidate, value, true);
        for(PsiReferenceExpression ref : refs) {
          ref.handleElementRename(varName);
        }
        replacement = factory.createExpressionFromText(varName + " -> " + ct.text(value), value);
      } else {
        replacement = ct.markUnchanged(value);
      }
      if(args.length == 2) {
        ct.replace(args[1], replacement);
      } else {
        argsList.add(replacement);
      }
      PsiExpression expression = argsList.getExpressions()[1];
      if(expression instanceof PsiLambdaExpression) {
        LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference((PsiLambdaExpression)expression);
      }
      if(PsiTreeUtil.isAncestor(removed, result, true)) {
        result = ct.replaceAndRestoreComments(removed, ct.markUnchanged(result));
      } else {
        ct.deleteAndRestoreComments(removed);
      }
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }

  class MapCheckCondition {
    private final @Nullable PsiReferenceExpression myValueReference;
    private final PsiExpression myMapExpression;
    private final PsiExpression myKeyExpression;
    private final PsiExpression myFullCondition;
    private final boolean myNegated;
    private final boolean myContainsKey;

    private MapCheckCondition(@Nullable PsiReferenceExpression valueReference,
                              PsiExpression mapExpression,
                              PsiExpression keyExpression,
                              PsiExpression fullCondition,
                              boolean negated,
                              boolean containsKey) {
      myValueReference = valueReference;
      myMapExpression = mapExpression;
      myKeyExpression = keyExpression;
      myFullCondition = fullCondition;
      myNegated = negated;
      myContainsKey = containsKey;
    }

    boolean isContainsKey() {
      return myContainsKey || myTreatGetNullAsContainsKey;
    }

    boolean isGetNull() {
      return !myContainsKey || myTreatGetNullAsContainsKey;
    }

    @Contract("null -> false")
    boolean isMap(PsiExpression expression) {
      return expression != null && PsiEquivalenceUtil.areElementsEquivalent(myMapExpression, expression);
    }

    @Contract("null -> false")
    boolean isKey(PsiExpression expression) {
      return expression != null && PsiEquivalenceUtil.areElementsEquivalent(myKeyExpression, expression);
    }

    @Contract("null -> false")
    boolean isValueReference(PsiExpression expression) {
      return expression != null && myValueReference != null && PsiEquivalenceUtil.areElementsEquivalent(expression, myValueReference);
    }

    <T extends PsiElement> T getExistsBranch(T thenBranch, T elseBranch) {
      return myNegated ? elseBranch : thenBranch;
    }

    <T extends PsiElement> T getNoneBranch(T thenBranch, T elseBranch) {
      return myNegated ? thenBranch : elseBranch;
    }

    boolean hasVariable() {
      if(myValueReference == null) return false;
      PsiVariable var = PsiTreeUtil.getParentOfType(myKeyExpression, PsiVariable.class, true, PsiStatement.class);
      // has variable, but it used only in condition
      return var == null || ReferencesSearch.search(var).findAll().size() != 1;
    }

    PsiMethodCallExpression getCheckCall() {
      return PsiTreeUtil.getParentOfType(myMapExpression, PsiMethodCallExpression.class);
    }

    public PsiExpression getFullCondition() {
      return myFullCondition;
    }

    public void register(ProblemsHolder holder, LocalQuickFix fix) {
      //noinspection DialogTitleCapitalization
      holder.registerProblem(getFullCondition(), QuickFixBundle.message("java.8.map.api.inspection.description"), fix);
    }
  }
}