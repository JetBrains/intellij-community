// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/// Migration that takes a combination of `Math.min` and `Math.max` calls into a `Math.clamp` call
@NotNullByDefault
public final class MathClampMigrationInspection extends BaseInspection {
  private static final String MIN_FUNCTION = "min";
  private static final String MAX_FUNCTION = "max";

  /// In brave mode, the inspection shows up more often
  /// May lead to code that doesn't work at runtime
  public boolean braveMode = true;

  @Override
  public OptPane getOptionsPane() {
    return OptPane.pane(OptPane.checkbox("braveMode", JavaAnalysisBundle.message("math.clamp.migration.brave.mode")));
  }

  @Override
  public Set<JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.MATH_CLAMP_METHODS);
  }

  @Override
  protected String buildErrorString(Object @Nullable ... infos) {
    return CommonQuickFixBundle.message("fix.can.replace.with.x", "Math.clamp()");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MathClampMigrationVisitor();
  }

  @Override
  protected LocalQuickFix[] buildFixes(Object... infos) {
    @SuppressWarnings("unchecked")
    List<String> targets = (List<String>)infos[0];
    if (braveMode) {
      return new LocalQuickFix[]{new MathClampMigrationFix(true, targets), LocalQuickFix.from(
        new UpdateInspectionOptionFix(this, "braveMode", JavaAnalysisBundle.message("math.clamp.migration.do.not.report.ambiguous"),
                                      false))};
    }
    return new LocalQuickFix[]{new MathClampMigrationFix(false, targets)};
  }

  private class MathClampMigrationVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      InspectionResult inspectionResult = inspect(expression, braveMode);
      if (inspectionResult != null) {
        registerError(expression, ContainerUtil.map(inspectionResult.targets, expression1 -> expression1.getText()));
      }
    }
  }

  @NotNullByDefault
  private static class MathClampMigrationFix extends ModCommandQuickFix {
    private final boolean braveMode;
    private final List<String> potentialTargets;

    private MathClampMigrationFix(boolean braveMode, List<String> potentialTargets) {
      this.braveMode = braveMode;
      this.potentialTargets = potentialTargets;
    }

    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Math.clamp()");
    }

    @Override
    public ModCommand perform(Project project, ProblemDescriptor descriptor) {
      PsiMethodCallExpression expression = ObjectUtils.tryCast(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (expression == null) return ModCommand.nop();

      if (braveMode) {
        List<ModCommandAction> actions = new ArrayList<>();
        for (int i = 0; i < potentialTargets.size(); i++) {
          actions.add(new MathClampMigrationAction(expression, potentialTargets.get(i), i, braveMode));
        }
        return ModCommand.chooseAction(getFamilyName(), actions);
      }

      return ModCommand.chooseAction(getFamilyName(),
                                     new MathClampMigrationAction(expression, potentialTargets.getFirst(), 0, braveMode));
    }
  }

  /// Fix that replace the entire call with a newly made one, taking into account the comments
  @NotNullByDefault
  private static class MathClampMigrationAction extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
    private final String clampedTargetName;
    private final int targetIndex;
    private final boolean braveMode;

    private MathClampMigrationAction(PsiMethodCallExpression callExpression, String clampedTargetName, int targetIndex, boolean braveMode) {
      super(callExpression);
      this.clampedTargetName = clampedTargetName;
      this.targetIndex = targetIndex;
      this.braveMode = braveMode;
    }

    @Override
    protected @Nullable Presentation getPresentation(ActionContext context, PsiMethodCallExpression element) {
      return Presentation.of(JavaRefactoringBundle.message("0.as.target", clampedTargetName));
    }

    @Override
    protected void invoke(ActionContext context, PsiMethodCallExpression call, ModPsiUpdater updater) {
      InspectionResult inspectionResult = inspect(call, braveMode);
      if (inspectionResult == null) return;

      CommentTracker tracker = new CommentTracker();
      Couple<ClampInfo> sortedInfos = inspectionResult.infos;
      if (braveMode) {
        sortedInfos = sortInfosBraveMode(inspectionResult.infos, inspectionResult.targets.get(targetIndex),
                                         inspectionResult.topLevelMethod.equals(MIN_FUNCTION));
      }

      String newCall = buildClampCall(sortedInfos, inspectionResult.targets.get(targetIndex), tracker);
      tracker.replaceAndRestoreComments(call, newCall);
    }

    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Math.clamp()");
    }
  }

  /// Record registering the important information about arguments inside the expressionList
  ///
  /// @param values    The values of the expression, expected to be a constant value or a range.
  /// @param element   a reference towards the element. Might be a constant, a variable or a method call
  /// @param modifiers If the element references a [PsiVariable], its modifier list.
  /// @param children  If the argument is a method call we are interested in (min/max), the info about the method arguments. `null` otherwise
  @NotNullByDefault
  private record ClampInfo(DfType values, PsiExpression element, @Nullable PsiModifierList modifiers, Couple<ClampInfo> children) {
    public boolean hasChildren() {
      return !children.equals(Couple.getEmpty());
    }

    public Stream<ClampInfo> flatten() {
      return hasChildren() ? childrenAsStream().flatMap(ClampInfo::flatten) : Stream.of(this);
    }

    /// Utility function
    private Stream<ClampInfo> childrenAsStream() {
      return coupleAsStream(this.children);
    }

    static <T> Stream<T> coupleAsStream(Couple<T> couple) {
      return Stream.of(couple.first, couple.second);
    }
  }

  /// @return the string to be inserted by [MathClampMigrationFix]
  private static String buildClampCall(Couple<ClampInfo> infos, PsiElement target, CommentTracker tracker) {
    StringBuilder newCall =
      new StringBuilder().append("java.lang.Math.clamp(").append(textWithCommentsFromArgument(target, tracker)).append(',');

    for (ClampInfo info : infos) {
      printInfo(info, target, newCall, tracker);
    }

    return newCall.replace(newCall.length() - 1, newCall.length(), ")").toString();
  }

  /// Prints the content of the `info` element and its children, skipping over the target and the function containing it.
  private static void printInfo(ClampInfo info, PsiElement target, StringBuilder builder, CommentTracker tracker) {
    if (info.hasChildren()) {
      // I need to know whether the target is a direct children
      // If so, we only want to print the other child(ren)
      List<ClampInfo> nonTargetChildren = info.childrenAsStream().filter(info1 -> !info1.element.equals(target)).toList();
      if (nonTargetChildren.size() == 1) {
        printInfo(nonTargetChildren.getFirst(), target, builder, tracker);
      }
      else {
        builder.append(((PsiMethodCallExpression)info.element).getMethodExpression().getText()).append('(');
        for (ClampInfo child : info.children) {
          printInfo(child, target, builder, tracker);
        }
        builder.replace(builder.length() - 1, builder.length(), "),");
      }
      return;
    }


    builder.append(textWithCommentsFromArgument(info.element, tracker));
    builder.append(',');
  }

  /// @param isMin Whether the top level function was [Math#min] (or an equivalent)
  /// @return the `infos` in order of smaller to bigger ranges
  private static @Nullable Couple<ClampInfo> sortInfos(Couple<ClampInfo> infos, PsiElement target, boolean isMin) {
    DfType A = getRange(infos.getFirst(), target, !isMin);
    DfType B = getRange(infos.getSecond(), target, !isMin);

    DfType andType = A.meet(B);
    if (andType == DfType.TOP) return null;
    boolean isA = andType.isSuperType(A);
    boolean isB = andType.isSuperType(B);
    boolean isBottom = andType == DfType.BOTTOM;
    if (!(isA || isB || isBottom || andType instanceof DfConstantType<?>)) return null;
    if (isA && isB && !(andType instanceof DfConstantType<?>)) return null;

    ClampInfo maxInfo, minInfo;
    if (isA) {
      maxInfo = infos.getSecond();
      minInfo = infos.getFirst();
    }
    else if (isB) {
      maxInfo = infos.getFirst();
      minInfo = infos.getSecond();
    }
    else { // BOTTOM or const
      if (A.meetRelation(RelationType.GT, B) == DfType.BOTTOM) {
        // big one is the second one
        maxInfo = infos.getSecond();
        minInfo = infos.getFirst();
      }
      else {
        maxInfo = infos.getFirst();
        minInfo = infos.getSecond();
      }
    }

    return Couple.of(minInfo, maxInfo);
  }

  /// @param targetMin Whether we target the min or max range of DfType
  /// @return The DfType that corresponds to the element, if the call containing the target was not there
  private static DfType getRange(ClampInfo info, PsiElement target, boolean targetMin) {
    if (!info.hasChildren()) {
      return info.values;
    }

    DfType borderType = DfType.TOP;
    RelationType rangeRelation = targetMin ? RelationType.LE : RelationType.GE;

    for (ClampInfo childInfo : info.children) {
      assert childInfo != null;
      if (target.equals(childInfo.element)) continue; // We do not care about the range of the clamped value
      DfType currentType = getRange(childInfo, target, targetMin);

      borderType = borderType.meetRelation(rangeRelation, currentType) == DfType.BOTTOM ? currentType : childInfo.values;
    }

    return borderType;
  }

  /// Variant of [#sortInfos(List, PsiElement, boolean)] for brave mode. **Not Dfa aware**
  private static Couple<ClampInfo> sortInfosBraveMode(Couple<ClampInfo> infos, PsiElement target, boolean isMin) {
    boolean isTargetFound = infos.getFirst().flatten().anyMatch(info -> info.element.equals(target));
    ClampInfo maxInfo, minInfo;

    // The logic here is that in a max(min()) call, the bigger number should to be next to the target
    if (isTargetFound) {
      maxInfo = infos.getFirst();
      minInfo = infos.getSecond();
    }
    else {
      maxInfo = infos.getSecond();
      minInfo = infos.getFirst();
    }

    if (isMin) {
      ClampInfo swapInfo = maxInfo;
      maxInfo = minInfo;
      minInfo = swapInfo;
    }

    return Couple.of(minInfo, maxInfo);
  }

  /// Returns all possible targets that represent the variable that should be clamped.
  ///
  /// @param topLevelInfo     The information about the top level math call
  /// @param assignmentTarget The variable that gets assigned the result of the "clamp" operation, if any.
  private static List<PsiExpression> getPossibleTargets(Couple<ClampInfo> topLevelInfo, @Nullable PsiExpression assignmentTarget) {
    List<Condition<ClampInfo>> filters = List.of(info -> {
      //Side effect, if the intended target "ends up" being a constant-like, the inspection will bail out.
      if (info.values instanceof DfConstantType<?> constant && constant.getValue() != null) return false;
      return true;
    }, info -> {
      if (info.modifiers != null) {
        return !info.modifiers.hasModifierProperty(PsiModifier.STATIC);
      }
      return true;
    });

    List<ClampInfo> possibleTargets =
      ClampInfo.coupleAsStream(topLevelInfo)
        .filter(ClampInfo::hasChildren) // The target is forced to be a child and can never be at a top level
        .flatMap((info -> info.flatten())).distinct().toList();

    EquivalenceChecker checker = EquivalenceChecker.getCanonicalPsiEquivalence();
    for (Condition<ClampInfo> filter : filters) {
      possibleTargets = ContainerUtil.filter(possibleTargets, filter);

      // Happy path case, we reassign the value to itself, just clamped
      if (assignmentTarget != null && assignmentTarget.getText() != null) {
        for (ClampInfo info : possibleTargets) {
          if (checker.expressionsAreEquivalent(info.element, assignmentTarget)) {
            return List.of(info.element);
          }
        }
      }

      // Less happy path, attempt to figure out the identifier
      if (possibleTargets.isEmpty()) return List.of();
      if (possibleTargets.size() == 1) return List.of(possibleTargets.getFirst().element);
    }

    // More than one target with too much ambiguity 
    return ContainerUtil.map(possibleTargets, info -> info.element);
  }

  /// If the result of the `Math.max(Math.min())` operation is assigned to a variable, returns the corresponding [PsiVariable].
  /// `null` otherwise.
  private static @Nullable PsiExpression getAssignment(PsiMethodCallExpression expression) {
    if (!(PsiUtil.skipParenthesizedExprUp(expression.getParent()) instanceof PsiAssignmentExpression assignment)) return null;
    return assignment.getLExpression();
  }

  /// @return the name of the found function that takes 2 arguments, or null if it isn't available.
  private static @Nullable String findMathFunction(PsiExpression expression, String... functionsNames) {
    if (!(expression instanceof PsiMethodCallExpression callExpression)) return null;

    PsiElement nameElement = callExpression.getMethodExpression().getReferenceNameElement();
    if (nameElement == null) return null;
    PsiExpressionList argumentList = callExpression.getArgumentList();

    if (argumentList.getExpressionCount() != 2) return null;
    int methodIndex = ArrayUtil.indexOf(functionsNames, nameElement.getText());
    if (methodIndex == -1) return null;

    PsiMethod method = callExpression.resolveMethod();
    if (method == null) return null;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !CommonClassNames.JAVA_LANG_MATH.equals(containingClass.getQualifiedName())) return null;

    return functionsNames[methodIndex];
  }

  /// Collect the necessary information about arguments and math functions sub-calls
  private static @Nullable Couple<ClampInfo> collectClampInfo(PsiExpressionList list, String... subMethods) {
    CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(list);
    if (result == null) return null;
    PsiExpression[] arguments = list.getExpressions();
    return Couple.of(
      buildSingleClampInfo(result, arguments[0], subMethods),
      buildSingleClampInfo(result, arguments[1], subMethods)
    );
  }

  private static ClampInfo buildSingleClampInfo(CommonDataflow.DataflowResult result, PsiExpression argument, String... subMethods) {
    argument = Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(argument));
    boolean isMathFunction = findMathFunction(argument, subMethods) != null;
    if (isMathFunction) {
      return new ClampInfo(result.getDfType(argument), argument, null,
                           Objects.requireNonNull(collectClampInfo(((PsiMethodCallExpression)argument).getArgumentList(), subMethods)));
    }
    else {
      PsiModifierListOwner modifierListOwner =
        argument.getReference() != null ? ObjectUtils.tryCast(argument.getReference().resolve(), PsiModifierListOwner.class) : null;

      return new ClampInfo(result.getDfType(argument), argument, modifierListOwner != null ? modifierListOwner.getModifierList() : null,
                           Couple.getEmpty());
    }
  }

  /// Record storing the detection phase results, useful to produce the fix from it
  private record InspectionResult(Couple<ClampInfo> infos, List<PsiExpression> targets, String topLevelMethod) {
  }

  /// Perform the inspection detection from the `expression` call
  /// @return The inspection results. `null` if the inspection could not be completed
  private static @Nullable InspectionResult inspect(PsiMethodCallExpression expression, boolean braveMode) {
    String topLevelMethod = findMathFunction(expression, MIN_FUNCTION, MAX_FUNCTION);
    if (topLevelMethod == null) return null;

    String subMethod = topLevelMethod.equals(MIN_FUNCTION) ? MAX_FUNCTION : MIN_FUNCTION;

    // So we have either max or min call, check if we have another call within
    PsiExpressionList argumentExpressions = expression.getArgumentList();
    if (!ContainerUtil.exists(argumentExpressions.getExpressions(), expression1 -> PsiUtil.skipParenthesizedExprDown(
      expression1) instanceof PsiMethodCallExpression callExpression && (findMathFunction(callExpression, subMethod) != null))) {
      return null;
    }

    Couple<ClampInfo> infos = collectClampInfo(expression.getArgumentList(), subMethod);
    if (infos == null) return null;

    List<PsiExpression> targets = getPossibleTargets(infos, getAssignment(expression));
    if (targets.isEmpty()) return null;

    if (braveMode) {
      return new InspectionResult(infos, targets, topLevelMethod);
    }

    // full dfa verification
    if (targets.size() > 1) return null;

    infos = sortInfos(infos, targets.getFirst(), topLevelMethod.equals(MIN_FUNCTION));
    if (infos == null) return null;

    return new InspectionResult(infos, targets, topLevelMethod);
  }

  /// @return The expression with comments around it. `expression` is expected to be an argument inside a function call
  private static String textWithCommentsFromArgument(PsiElement expression, CommentTracker tracker) {
    PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(expression);
    PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(expression);
    if (prev != null && next != null) {
      return tracker.rangeText(prev.getNextSibling(), next.getPrevSibling());
    }
    return tracker.text(expression);
  }
}
