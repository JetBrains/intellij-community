// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.util.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallHandler;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.intellij.codeInspection.util.OptionalUtil.*;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_OPTIONAL;
import static com.intellij.util.ObjectUtils.tryCast;


public class SimplifyOptionalCallChainsInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher OPTIONAL_OR_ELSE =
    CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "orElse").parameterCount(1);
  private static final CallMatcher OPTIONAL_GET =
    CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "get").parameterCount(0);
  private static final CallMatcher OPTIONAL_OR_ELSE_GET =
    CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "orElseGet").parameterCount(1);
  private static final CallMatcher OPTIONAL_MAP =
    CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "map").parameterCount(1);
  private static final CallMatcher OPTIONAL_OF_NULLABLE =
    CallMatcher.staticCall(JAVA_UTIL_OPTIONAL, "ofNullable").parameterCount(1);
  private static final CallMatcher OPTIONAL_OF_OF_NULLABLE =
    CallMatcher.staticCall(JAVA_UTIL_OPTIONAL, "ofNullable", "of").parameterCount(1);
  private static final CallMatcher OPTIONAL_IS_PRESENT =
    CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(JAVA_UTIL_OPTIONAL, "isPresent").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_INT, "isPresent").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_LONG, "isPresent").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_DOUBLE, "isPresent").parameterCount(0)
    );
  private static final CallMatcher OPTIONAL_IF_PRESENT =
    CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(JAVA_UTIL_OPTIONAL, "ifPresent").parameterCount(1),
      CallMatcher.exactInstanceCall(OPTIONAL_INT, "ifPresent").parameterCount(1),
      CallMatcher.exactInstanceCall(OPTIONAL_LONG, "ifPresent").parameterCount(1),
      CallMatcher.exactInstanceCall(OPTIONAL_DOUBLE, "ifPresent").parameterCount(1)
    );
  private static final CallMatcher OPTIONAL_IS_EMPTY =
    CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(JAVA_UTIL_OPTIONAL, "isEmpty").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_INT, "isEmpty").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_LONG, "isEmpty").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_DOUBLE, "isEmpty").parameterCount(0)
    );


  private static final CallMapper<OptionalSimplificationFix> ourMapper;

  static {
    List<ChainSimplificationCase<?>> cases = Arrays.asList(
      new IfPresentFoldedCase(),
      new MapUnwrappingCase(),
      new OrElseNonNullCase(OrElseType.OrElse),
      new OrElseNonNullCase(OrElseType.OrElseGet),
      new FlipPresentOrEmptyCase(true),
      new FlipPresentOrEmptyCase(false),
      new OrElseReturnCase(OrElseType.OrElse),
      new OrElseReturnCase(OrElseType.OrElseGet),
      new RewrappingCase(RewrappingCase.Type.OptionalGet),
      new RewrappingCase(RewrappingCase.Type.OrElseNull),
      new MapOrElseCase(OrElseType.OrElseGet),
      new MapOrElseCase(OrElseType.OrElse),
      new OptionalOfNullableOrElseNullCase(),
      new OptionalOfNullableStringCase()
    );
    ourMapper = new CallMapper<>();
    for (ChainSimplificationCase<?> theCase : cases) {
      CallHandler<OptionalSimplificationFix> handler = CallHandler.of(theCase.getMatcher(), theCase);
      ourMapper.register(handler);
    }
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    LanguageLevel level = PsiUtil.getLanguageLevel(holder.getFile());
    if (level.isLessThan(LanguageLevel.JDK_1_8)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new OptionalChainVisitor(level) {
      @Override
      protected void handleSimplification(@NotNull PsiMethodCallExpression call, @NotNull OptionalSimplificationFix fix) {
        PsiElement element = call.getMethodExpression().getReferenceNameElement();
        holder.registerProblem(element != null ? element : call, fix.getDescription(), fix);
      }
    };
  }

  @Nullable
  private static <T> OptionalSimplificationFix getFix(PsiMethodCallExpression call, ChainSimplificationCase<T> inspection) {
    T context = inspection.extractContext(call.getProject(), call);
    if (context == null) return null;
    String name = inspection.getName(context);
    String description = inspection.getDescription(context);
    return new OptionalSimplificationFix(inspection, name, description);
  }

  private static <T> void handleSimplification(ChainSimplificationCase<T> inspection, Project project, PsiMethodCallExpression call) {
    if (!inspection.getMatcher().matches(call)) return;
    T context = inspection.extractContext(project, call);
    if (context != null) {
      inspection.apply(project, call, context);
    }
  }


  @Nullable
  private static PsiLambdaExpression getLambda(PsiExpression initializer) {
    PsiExpression expression = PsiUtil.skipParenthesizedExprDown(initializer);
    if (expression instanceof PsiLambdaExpression) {
      return (PsiLambdaExpression)expression;
    }
    if (expression instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)expression;
      PsiLambdaExpression lambda = LambdaRefactoringUtil.createLambda(methodRef, true);
      if (lambda != null) {
        LambdaUtil.specifyLambdaParameterTypes(methodRef.getFunctionalInterfaceType(), lambda);
        return lambda;
      }
    }
    return null;
  }

  /**
   * @return argument expression in case of absence of optional value
   */
  private static PsiExpression getOrElseArgument(PsiMethodCallExpression call, OrElseType type) {
    if (type == OrElseType.OrElse) {
      return call.getArgumentList().getExpressions()[0];
    }
    if (type == OrElseType.OrElseGet) {
      PsiLambdaExpression lambda = getLambda(call.getArgumentList().getExpressions()[0]);
      if (lambda == null || !lambda.getParameterList().isEmpty()) return null;
      return LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
    }
    return null;
  }

  private static CallMatcher getMatcherByType(OrElseType type) {
    if (type == OrElseType.OrElse) {
      return OPTIONAL_OR_ELSE;
    }
    if (type == OrElseType.OrElseGet) {
      return OPTIONAL_OR_ELSE_GET;
    }
    throw new IllegalStateException();
  }

  /*
    if(optValue != null) {return optValue;} else {return "default";}
    or
    return optValue == null? "default" : optValue;
     */
  @Nullable
  private static PsiExpression extractConditionalDefaultValue(@NotNull PsiStatement statement, @NotNull PsiVariable optValue) {
    if (statement instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)statement;
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;
      PsiExpression thenExpr = getReturnExpression(ifStatement.getThenBranch());
      PsiExpression elseExpr = getReturnExpression(ifStatement.getElseBranch());
      if (thenExpr == null || elseExpr == null) return null;
      return extractConditionalDefaultValue(thenExpr, elseExpr, condition, optValue);
    }
    else if (statement instanceof PsiReturnStatement) {
      PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
      PsiConditionalExpression ternary = tryCast(PsiUtil.skipParenthesizedExprDown(returnValue), PsiConditionalExpression.class);
      if (ternary == null) return null;
      PsiExpression thenExpression = ternary.getThenExpression();
      PsiExpression elseExpression = ternary.getElseExpression();
      if (thenExpression == null || elseExpression == null) return null;
      return extractConditionalDefaultValue(thenExpression, elseExpression, ternary.getCondition(), optValue);
    }
    return null;
  }

  @Contract("null -> null")
  @Nullable
  private static PsiExpression getReturnExpression(@Nullable PsiStatement block) {
    if (block == null) return null;
    PsiStatement statement = ControlFlowUtils.stripBraces(block);
    PsiReturnStatement returnStatement = tryCast(statement, PsiReturnStatement.class);
    if (returnStatement == null) return null;
    return returnStatement.getReturnValue();
  }

  @Nullable
  private static PsiExpression extractConditionalDefaultValue(@NotNull PsiExpression thenExpr,
                                                              @NotNull PsiExpression elseExpr,
                                                              @NotNull PsiExpression condition,
                                                              @NotNull PsiVariable optValue) {
    PsiVariable nullChecked = ExpressionUtils.getVariableFromNullComparison(condition, true);
    boolean inverted = false;
    if (nullChecked == null) {
      nullChecked = ExpressionUtils.getVariableFromNullComparison(condition, false);
      if (nullChecked == null) return null;
      inverted = true;
    }
    if (!nullChecked.equals(optValue) || !ExpressionUtils.isReferenceTo(inverted ? thenExpr : elseExpr, optValue)) return null;
    PsiExpression defaultExpression = inverted ? elseExpr : thenExpr;
    if (VariableAccessUtils.variableIsUsed(optValue, defaultExpression)) return null;
    return defaultExpression;
  }

  /**
   * Optional.orElse and Optional.orElseGet have similar semantics and can be handled together.
   * This enum represents what kind of method we are handling now.
   */
  private enum OrElseType {
    OrElse,
    OrElseGet
  }

  /**
   * Stateless component, that can suggest simplification for call chain
   *
   * @param <C> context of the simplification
   */
  private interface ChainSimplificationCase<C> extends Function<PsiMethodCallExpression, OptionalSimplificationFix> {
    @Override
    default OptionalSimplificationFix apply(PsiMethodCallExpression expression) {
      return getFix(expression, this);
    }

    @NotNull
    @IntentionName
    String getName(@NotNull C context);

    @NotNull
    @InspectionMessage
    String getDescription(@NotNull C context);

    /**
     * Gathers context for handling simplification
     * Called only if call matches to matcher returned by getMatcher call.
     */
    @Nullable
    C extractContext(@NotNull Project project, @NotNull PsiMethodCallExpression call);

    void apply(@NotNull Project project, @NotNull PsiMethodCallExpression call, @NotNull C context);

    default boolean isAvailable(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      return extractContext(project, call) != null;
    }

    @NotNull
    CallMatcher getMatcher();

    default boolean isAppropriateLanguageLevel(@NotNull LanguageLevel level) {
      return true;
    }
  }

  private static abstract class OptionalChainVisitor extends JavaElementVisitor {
    private final LanguageLevel myLevel;

    private OptionalChainVisitor(LanguageLevel level) {
      myLevel = level;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      Optional<OptionalSimplificationFix> fix = ourMapper
        .mapAll(expression)
        .filter(f -> f.myInspection.isAppropriateLanguageLevel(myLevel))
        .findAny();
      if (fix.isEmpty()) return;
      handleSimplification(expression, fix.get());
    }

    protected abstract void handleSimplification(@NotNull PsiMethodCallExpression call, @NotNull OptionalSimplificationFix fix);
  }

  private static final class MapOrElseCase extends BasicSimplificationInspection {
    private final OrElseType myType;

    private MapOrElseCase(OrElseType type) { myType = type; }

    @Nullable
    @Override
    public StringReplacement extractContext(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      PsiExpression falseArg = getOrElseArgument(call, myType);
      if (falseArg == null) return null;
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (!OPTIONAL_MAP.test(qualifierCall)) return null;
      PsiLambdaExpression lambda = getLambda(qualifierCall.getArgumentList().getExpressions()[0]);
      if (lambda == null) return null;
      PsiExpression trueArg = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (trueArg == null) return null;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return null;
      PsiExpression qualifier = qualifierCall.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return null;
      String opt = qualifier.getText();
      PsiParameter parameter = parameters[0];
      boolean useOrElseGet = myType == OrElseType.OrElseGet;
      String proposed = OptionalRefactoringUtil.generateOptionalUnwrap(opt, parameter, trueArg, falseArg, call.getType(), useOrElseGet);
      String canonicalOrElse;
      if (useOrElseGet && !ExpressionUtils.isSafelyRecomputableExpression(falseArg)) {
        canonicalOrElse = ".orElseGet(() -> " + falseArg.getText() + ")";
      }
      else {
        canonicalOrElse = ".orElse(" + falseArg.getText() + ")";
      }
      String canonical = opt + ".map(" + LambdaUtil.createLambda(parameter, trueArg) + ")" + canonicalOrElse;
      if (proposed.length() < canonical.length()) {
        String displayCode;
        if (proposed.equals(opt)) {
          displayCode = "";
        }
        else if (opt.length() > 10) {
          // should be a parseable expression
          opt = "(($))";
          String template =
            OptionalRefactoringUtil.generateOptionalUnwrap(opt, parameter, trueArg, falseArg, call.getType(), useOrElseGet);
          displayCode =
            PsiExpressionTrimRenderer
              .render(JavaPsiFacade.getElementFactory(parameter.getProject()).createExpressionFromText(template, call));
          displayCode = displayCode.replaceFirst(Pattern.quote(opt), "..");
        }
        else {
          displayCode =
            PsiExpressionTrimRenderer
              .render(JavaPsiFacade.getElementFactory(parameter.getProject()).createExpressionFromText(proposed, call));
        }
        String message = displayCode.isEmpty()
                         ? JavaBundle.message("simplify.optional.chain.inspection.remove.redundant.steps.from.optional.chain")
                         : JavaBundle.message("simplify.optional.chain.inspection.to.x", displayCode);
        String description = JavaBundle.message("simplify.optional.chain.inspection.map.or.else.description");
        return new StringReplacement(proposed, message, description);
      }
      return null;
    }

    @NotNull
    @Override
    public CallMatcher getMatcher() {
      return getMatcherByType(myType);
    }
  }

  static class OptionalSimplificationFix implements LocalQuickFix {
    @SafeFieldForPreview
    private final @NotNull ChainSimplificationCase<?> myInspection;
    private final @IntentionFamilyName String myName;
    private final @InspectionMessage String myDescription;

    OptionalSimplificationFix(@NotNull ChainSimplificationCase<?> inspection,
                              @IntentionFamilyName String name,
                              @InspectionMessage String description) {
      myInspection = inspection;
      myName = name;
      myDescription = description;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return myName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class, false);
      //PsiMethodCallExpression call = tryCast(descriptor.getStartElement(), PsiMethodCallExpression.class);
      handleSimplification(myInspection, project, call);
    }

    @InspectionMessage String getDescription() {
      return myDescription;
    }
  }

  private abstract static class BasicSimplificationInspection
    implements ChainSimplificationCase<BasicSimplificationInspection.StringReplacement> {
    @NotNull
    @Override
    public String getName(@NotNull StringReplacement context) {
      return context.myMessage;
    }

    @NotNull
    @Override
    public String getDescription(@NotNull StringReplacement context) {
      return context.myDescription;
    }


    @Override
    public void apply(@NotNull Project project, @NotNull PsiMethodCallExpression call, @NotNull StringReplacement context) {
      PsiExpression replacementExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(context.myReplacement, call);
      PsiElement result = call.replace(replacementExpression);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(result);
    }

    protected static class StringReplacement {
      private final @NonNls String myReplacement;
      private final @IntentionName String myMessage;
      private final @InspectionMessage String myDescription;

      StringReplacement(@NonNls String replacement, @IntentionName String message, @InspectionMessage String description) {
        myReplacement = replacement;
        myMessage = message;
        myDescription = description;
      }
    }
  }

  private static final class RewrappingCase implements ChainSimplificationCase<RewrappingCase.Context> {
    private final CallMatcher myWrapper;
    private final Type myType;

    private RewrappingCase(Type type) {
      myType = type;
      if (myType == Type.OrElseNull) {
        myWrapper = OPTIONAL_OF_NULLABLE;
      }
      else {
        myWrapper = OPTIONAL_OF_OF_NULLABLE;
      }
    }

    @NotNull
    @Override
    public String getName(@NotNull Context context) {
      return JavaBundle.message("simplify.optional.chain.inspection.optional.rewrapping.name");
    }

    @NotNull
    @Override
    public String getDescription(@NotNull Context context) {
      return JavaBundle.message("simplify.optional.chain.inspection.optional.rewrapping.description");
    }

    @Nullable
    @Override
    public Context extractContext(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
      if (!(parent instanceof PsiExpressionList)) return null;
      PsiMethodCallExpression parentCall = tryCast(parent.getParent(), PsiMethodCallExpression.class);
      if (!myWrapper.test(parentCall)) return null;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null ||
          !EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(qualifier.getType(), parentCall.getType())) {
        return null;
      }
      String name = call.getMethodExpression().getReferenceName();
      if ("get".equals(name)) {
        DfType dfType = SpecialField.OPTIONAL_VALUE.getFromQualifier(CommonDataflow.getDfType(qualifier));
        if (dfType.isSuperType(DfTypes.NULL)) return null;
      }
      else if ("orElse".equals(name)) {
        if (!ExpressionUtils.isNullLiteral(call.getArgumentList().getExpressions()[0])) return null;
      }
      return new Context(qualifier, parentCall);
    }

    @Override
    public void apply(@NotNull Project project, @NotNull PsiMethodCallExpression call, @NotNull Context context) {
      PsiElement result = context.myCallToReplace.replace(context.myQualifier);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(result);
    }

    @NotNull
    @Override
    public CallMatcher getMatcher() {
      if (myType == Type.OptionalGet) {
        return OPTIONAL_GET;
      }
      return OPTIONAL_OR_ELSE;
    }

    private static final class Context {
      private final PsiExpression myQualifier;
      private final PsiExpression myCallToReplace;

      private Context(PsiExpression qualifier, PsiExpression callToReplace) {
        myQualifier = qualifier;
        myCallToReplace = callToReplace;
      }
    }

    enum Type {
      OrElseNull,
      OptionalGet
    }
  }

  private static final class OrElseReturnCase implements ChainSimplificationCase<OrElseReturnCase.Context> {
    public static final String OR_ELSE = "orElse";
    public static final String OR_ELSE_GET = "orElseGet";
    private final OrElseType myType;

    private OrElseReturnCase(OrElseType type) { myType = type; }

    @NotNull
    @Override
    public String getName(@NotNull Context context) {
      String method = context.myIsSimple ? OR_ELSE : OR_ELSE_GET;
      return JavaBundle.message("simplify.optional.chain.inspection.or.else.return.fix.name", method,
                                PsiExpressionTrimRenderer.render(context.myDefaultExpression));
    }

    @NotNull
    @Override
    public String getDescription(@NotNull Context context) {
      return JavaBundle.message("simplify.optional.chain.inspection.or.else.return.fix.description");
    }

    @Nullable
    @Override
    public Context extractContext(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      PsiExpression falseArg = getOrElseArgument(call, myType);
      if (!ExpressionUtils.isNullLiteral(falseArg)) return null;
      PsiLocalVariable returnVar = PsiTreeUtil.getParentOfType(call, PsiLocalVariable.class, true);
      if (returnVar == null) return null;
      PsiStatement nextStatement =
        tryCast(PsiTreeUtil.skipWhitespacesForward(returnVar.getParent()), PsiStatement.class);
      if (nextStatement == null) return null;
      PsiExpression defaultValue = extractConditionalDefaultValue(nextStatement, returnVar);
      boolean isSimple = ExpressionUtils.isSafelyRecomputableExpression(defaultValue);
      if (defaultValue == null || (!isSimple && !LambdaGenerationUtil.canBeUncheckedLambda(defaultValue))) return null;
      PsiType type = defaultValue.getType();
      PsiType methodCallReturnValue = call.getMethodExpression().getType();
      if (type == null || methodCallReturnValue == null || !methodCallReturnValue.isAssignableFrom(type)) return null;
      return new Context(call, defaultValue, nextStatement, isSimple);
    }

    @Override
    public void apply(@NotNull Project project, @NotNull PsiMethodCallExpression call, @NotNull Context context) {
      PsiExpression receiver = context.myOrElseCall.getMethodExpression().getQualifierExpression();
      if (receiver == null) return;
      String methodWithArg = context.myIsSimple
                             ? ".orElse(" + context.myDefaultExpression.getText() + ")"
                             : ".orElseGet(()->" + context.myDefaultExpression.getText() + ")";
      String expressionText;
      expressionText = receiver.getText() + methodWithArg;
      PsiStatement finalStatement =
        JavaPsiFacade.getElementFactory(project).createStatementFromText("return " + expressionText + ";", receiver);
      PsiStatement current = PsiTreeUtil.getParentOfType(context.myOrElseCall, PsiStatement.class, false);
      if (current == null) return;
      PsiElement result = new CommentTracker().replaceAndRestoreComments(current, finalStatement);
      new CommentTracker().deleteAndRestoreComments(context.myNextStatement);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    }

    @NotNull
    @Override
    public CallMatcher getMatcher() {
      if (myType == OrElseType.OrElse) {
        return OPTIONAL_OR_ELSE;
      }
      return OPTIONAL_OR_ELSE_GET;
    }

    private static final class Context {
      @NotNull private final PsiMethodCallExpression myOrElseCall;
      @NotNull private final PsiExpression myDefaultExpression;
      @NotNull private final PsiStatement myNextStatement;
      private final boolean myIsSimple;

      private Context(@NotNull PsiMethodCallExpression call,
                      @NotNull PsiExpression defaultExpression,
                      @NotNull PsiStatement nextStatement, boolean simple) {
        myOrElseCall = call;
        myDefaultExpression = defaultExpression;
        myNextStatement = nextStatement;
        myIsSimple = simple;
      }
    }
  }

  private static final class FlipPresentOrEmptyCase implements ChainSimplificationCase<FlipPresentOrEmptyCase.Context> {
    // Type of the inspection (may be either present or empty)
    private final boolean myIsPresent;

    private FlipPresentOrEmptyCase(boolean present) { myIsPresent = present; }

    @NotNull
    @Override
    public String getName(@NotNull Context context) {
      return CommonQuickFixBundle.message("fix.replace.with.x", context.myReplacement + "()");
    }

    @NotNull
    @Override
    public String getDescription(@NotNull Context context) {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", context.myReplacement + "()");
    }

    @Nullable
    @Override
    public Context extractContext(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      if (!BoolUtils.isNegated(call)) return null;
      PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
      if (nameElement == null) return null;
      if (myIsPresent) {
        return new Context("isEmpty");
      }
      return new Context("isPresent");
    }

    @Override
    public boolean isAppropriateLanguageLevel(@NotNull LanguageLevel level) {
      return level.isAtLeast(LanguageLevel.JDK_11);
    }

    @Override
    public void apply(@NotNull Project project, @NotNull PsiMethodCallExpression call, @NotNull Context context) {
      PsiPrefixExpression negation = tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiPrefixExpression.class);
      if (negation == null || BoolUtils.getNegated(negation) != call) return;
      ExpressionUtils.bindCallTo(call, context.myReplacement);
      new CommentTracker().replaceAndRestoreComments(negation, call);
    }

    @NotNull
    @Override
    public CallMatcher getMatcher() {
      if (myIsPresent) {
        return OPTIONAL_IS_PRESENT;
      }
      return OPTIONAL_IS_EMPTY;
    }

    private static final class Context {
      private final String myReplacement;

      private Context(String replacement) { myReplacement = replacement; }
    }
  }

  private static final class OrElseNonNullCase implements ChainSimplificationCase<OrElseNonNullCase.Context> {
    private final OrElseType myType;

    private OrElseNonNullCase(OrElseType type) { myType = type; }

    @NotNull
    @Override
    public String getName(@NotNull Context context) {
      return JavaBundle.message("simplify.optional.chain.inspection.or.else.non.null.fix.name");
    }

    @NotNull
    @Override
    public String getDescription(@NotNull Context context) {
      return JavaBundle.message("simplify.optional.chain.inspection.or.else.non.null.fix.description");
    }

    @Nullable
    @Override
    public Context extractContext(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      PsiExpression orElseArgument = getOrElseArgument(call, myType);
      if (!ExpressionUtils.isNullLiteral(orElseArgument)) return null;
      PsiLocalVariable returnVar = tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiLocalVariable.class);
      if (returnVar == null) return null;
      PsiStatement statement = PsiTreeUtil.getParentOfType(returnVar, PsiStatement.class, true);
      if (statement == null) return null;
      PsiStatement nextStatement =
        tryCast(PsiTreeUtil.skipWhitespacesForward(returnVar.getParent()), PsiStatement.class);
      if (nextStatement == null) return null;
      PsiExpression lambdaExpr = extractMappingExpression(nextStatement, returnVar);
      if (!LambdaGenerationUtil.canBeUncheckedLambda(lambdaExpr)) return null;
      if (!ReferencesSearch.search(returnVar).allMatch(reference ->
                                                         PsiTreeUtil.isAncestor(statement, reference.getElement(), false) ||
                                                         PsiTreeUtil.isAncestor(nextStatement, reference.getElement(), false))) {
        return null;
      }
      return new Context(lambdaExpr, nextStatement, statement, returnVar, call);
    }

    @Override
    public void apply(@NotNull Project project, @NotNull PsiMethodCallExpression call, @NotNull Context context) {
      PsiExpression receiver = context.myOrElseCall.getMethodExpression().getQualifierExpression();
      if (receiver == null) return;
      String statementText = receiver.getText() + ".ifPresent(" + LambdaUtil.createLambda(context.myVariable, context.myAction) + ");";
      PsiStatement finalStatement = JavaPsiFacade.getElementFactory(project).createStatementFromText(statementText, context.myStatement);
      PsiElement result = context.myStatement.replace(finalStatement);
      context.myConditionStatement.delete();
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    }

    @NotNull
    @Override
    public CallMatcher getMatcher() {
      return getMatcherByType(myType);
    }


    /**
     * if(optValue != null) {
     * System.out.println(optValue);
     * }
     **/
    @Nullable
    private static PsiExpression extractMappingExpression(@NotNull PsiStatement statement, @NotNull PsiVariable optValue) {
      PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
      if (ifStatement == null) return null;
      if (ifStatement.getElseBranch() != null) return null;
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;
      if (ExpressionUtils.getVariableFromNullComparison(condition, false) != optValue) return null;

      PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      PsiExpressionStatement expressionStatement = tryCast(thenStatement, PsiExpressionStatement.class);
      if (expressionStatement == null) return null;
      return expressionStatement.getExpression();
    }

    private static final class Context {
      private final @NotNull PsiExpression myAction;
      private final @NotNull PsiStatement myConditionStatement;
      private final @NotNull PsiStatement myStatement;
      private final @NotNull PsiVariable myVariable;
      private final @NotNull PsiMethodCallExpression myOrElseCall;

      private Context(@NotNull PsiExpression action,
                      @NotNull PsiStatement conditionStatement,
                      @NotNull PsiStatement statement,
                      @NotNull PsiVariable variable,
                      @NotNull PsiMethodCallExpression call) {
        myAction = action;
        myConditionStatement = conditionStatement;
        myStatement = statement;
        myVariable = variable;
        myOrElseCall = call;
      }
    }
  }


  /**
   * Converts
   * <pre>opt.map(a -> a.getOptional().orElse(null)).ifPresent(System.out::println);</pre>
   * into
   * <pre>opt.flatMap(Fra::getOptional).ifPresent(System.out::println);</pre>
   */
  private static class MapUnwrappingCase implements ChainSimplificationCase<MapUnwrappingCase.Context> {
    @NotNull
    @Override
    public String getName(@NotNull Context context) {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", "map()", "flatMap()");
    }

    @NotNull
    @Override
    public String getDescription(@NotNull Context context) {
      return InspectionGadgetsBundle.message("fix.replace.map.with.flat.map.description");
    }

    @Nullable
    @Override
    public Context extractContext(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      PsiLambdaExpression lambda = getLambda(call.getArgumentList().getExpressions()[0]);
      if (lambda == null) return null;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return null;
      PsiParameter mapLambdaParameter = parameters[0];
      PsiExpression argument = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      PsiMethodCallExpression insideLambdaCall = tryCast(argument, PsiMethodCallExpression.class);
      if (insideLambdaCall == null) return null;
      PsiExpression optionalQualifier = insideLambdaCall.getMethodExpression().getQualifierExpression();
      if (optionalQualifier == null) return null;
      if (!OPTIONAL_OR_ELSE.test(insideLambdaCall)) {
        if (!OPTIONAL_GET.test(insideLambdaCall)) return null;
        PsiExpression qualifier = insideLambdaCall.getMethodExpression().getQualifierExpression();
        if (!isPresentOptional(qualifier)) return null;
        return new Context(optionalQualifier, call, mapLambdaParameter);
      }
      if (!ExpressionUtils.isNullLiteral(insideLambdaCall.getArgumentList().getExpressions()[0])) return null;
      return new Context(optionalQualifier, call, mapLambdaParameter);
    }

    @Override
    public void apply(@NotNull Project project, @NotNull PsiMethodCallExpression call, @NotNull Context context) {
      CommentTracker ct = new CommentTracker();
      String text = ct.text(context.myMapLambdaParameter) + " ->" + ct.text(context.myOptionalExpression);
      PsiExpression qualifier = context.myMapCall.getMethodExpression().getQualifierExpression();
      String callReplacement = Objects.requireNonNull(qualifier).getText() + ".flatMap(" + text + ")";
      PsiElement result = ct.replaceAndRestoreComments(context.myMapCall, callReplacement);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    }

    @NotNull
    @Override
    public CallMatcher getMatcher() {
      return OPTIONAL_MAP;
    }

    private static boolean isPresentOptional(PsiExpression optionalExpression) {
      return !SpecialField.OPTIONAL_VALUE.getFromQualifier(CommonDataflow.getDfType(optionalExpression)).isSuperType(DfTypes.NULL);
    }

    private static final class Context {
      private final PsiExpression myOptionalExpression;
      private final PsiMethodCallExpression myMapCall;
      private final PsiParameter myMapLambdaParameter;

      private Context(PsiExpression expression, PsiMethodCallExpression call, PsiParameter parameter) {
        myOptionalExpression = expression;
        myMapCall = call;
        myMapLambdaParameter = parameter;
      }
    }
  }

  private static class IfPresentFoldedCase implements ChainSimplificationCase<IfPresentFoldedCase.Context> {
    @NotNull
    @Override
    public String getName(@NotNull Context context) {
      return InspectionGadgetsBundle.message("fix.eliminate.folded.if.present.name");
    }

    @NotNull
    @Override
    public String getDescription(@NotNull Context context) {
      return InspectionGadgetsBundle.message("fix.eliminate.folded.if.present.description");
    }

    @Nullable
    @Override
    public Context extractContext(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      PsiExpression outerIfPresentQualifier = call.getMethodExpression().getQualifierExpression();
      PsiMethodCallExpression qualifierCall = tryCast(outerIfPresentQualifier, PsiMethodCallExpression.class);

      PsiLambdaExpression outerIfPresentArgument = tryCast(call.getArgumentList().getExpressions()[0], PsiLambdaExpression.class);
      if (outerIfPresentArgument == null) return null;
      if (outerIfPresentArgument.getParameterList().getParametersCount() != 1) return null;
      PsiParameter outerParameter = outerIfPresentArgument.getParameterList().getParameters()[0];
      if (outerParameter == null) return null;
      String outerIfPresentParameterName = outerParameter.getName();
      PsiExpression outerIfPresentBodyExpr = LambdaUtil.extractSingleExpressionFromBody(outerIfPresentArgument.getBody());
      PsiMethodCallExpression outerIfPresentBody = tryCast(outerIfPresentBodyExpr, PsiMethodCallExpression.class);
      if (!OPTIONAL_IF_PRESENT.test(outerIfPresentBody)) return null;

      PsiExpression innerIfPresentQualifier = outerIfPresentBody.getMethodExpression().getQualifierExpression();
      PsiExpression nonTrivialQualifier =
        ExpressionUtils.isReferenceTo(innerIfPresentQualifier, outerParameter) ? null : innerIfPresentQualifier;
      PsiExpression innerIfPresentArgument = outerIfPresentBody.getArgumentList().getExpressions()[0];
      if (ReferencesSearch.search(outerParameter, new LocalSearchScope(innerIfPresentArgument)).findFirst() != null) return null;

      PsiMethodCallExpression mapBefore = null;
      if (OPTIONAL_MAP.test(qualifierCall)) {
        // case when map(Value::getOptional).ifPresent(p -> p.ifPresent(...))
        if (isOptionalTypeParameter(qualifierCall.getType())) {
          mapBefore = qualifierCall;
        }
      }
      return new Context(mapBefore, nonTrivialQualifier, outerIfPresentParameterName, innerIfPresentArgument);
    }

    private static boolean isOptionalTypeParameter(@Nullable PsiType type) {
      PsiClassType classType = tryCast(type, PsiClassType.class);
      if (classType == null) return false;
      if (classType.getParameterCount() != 1) return false;
      PsiType typeParameter = classType.getParameters()[0];
      PsiClass parameterClass = PsiUtil.resolveClassInClassTypeOnly(typeParameter);
      if (parameterClass == null) return false;
      return JAVA_UTIL_OPTIONAL.equals(parameterClass.getQualifiedName());
    }

    @Override
    public void apply(@NotNull Project project, @NotNull PsiMethodCallExpression call, @NotNull Context context) {
      PsiMethodCallExpression mapBefore = context.myMapBefore;
      CommentTracker ct = new CommentTracker();
      StringBuilder sb = new StringBuilder();
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      assert qualifier != null;
      sb.append(ct.text(qualifier)).append(".");
      if (mapBefore != null) {
        PsiExpression mapArgument = mapBefore.getArgumentList().getExpressions()[0];
        sb.append("flatMap(").append(ct.text(mapArgument)).append(").");
      }
      PsiExpression lambdaBodyAfter = context.myMapLambdaBodyAfter;
      if (lambdaBodyAfter != null) {
        sb.append("flatMap(").append(context.myOuterIfPresentVarName).append("->").append(ct.text(lambdaBodyAfter)).append(").");
      }
      sb.append("ifPresent(").append(ct.text(context.myInnerIfPresentArgument)).append(")");
      PsiElement result = ct.replaceAndRestoreComments(call, sb.toString());
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    }

    @NotNull
    @Override
    public CallMatcher getMatcher() {
      return OPTIONAL_IF_PRESENT;
    }

    static class Context {
      @Nullable PsiMethodCallExpression myMapBefore;
      @Nullable PsiExpression myMapLambdaBodyAfter;
      @NotNull String myOuterIfPresentVarName;
      @NotNull PsiExpression myInnerIfPresentArgument;

      Context(@Nullable PsiMethodCallExpression mapBefore,
              @Nullable PsiExpression mapLambdaBodyAfter,
              @NotNull String outerIfPresentVarName,
              @NotNull PsiExpression innerIfPresentArgument) {
        myMapBefore = mapBefore;
        myMapLambdaBodyAfter = mapLambdaBodyAfter;
        myOuterIfPresentVarName = outerIfPresentVarName;
        myInnerIfPresentArgument = innerIfPresentArgument;
      }
    }
  }

  private static class OptionalOfNullableOrElseNullCase implements ChainSimplificationCase<OptionalOfNullableOrElseNullCase.Context> {
    @Override
    public @NotNull String getName(@NotNull Context context) {
      return JavaBundle.message("simplify.optional.chain.inspection.fix.name.remove.redundant.optional.chain");
    }

    @Override
    public @NotNull String getDescription(@NotNull Context context) {
      return JavaBundle.message("simplify.optional.chain.inspection.fix.description.optional.chain.can.be.eliminated");
    }

    @Override
    public @Nullable OptionalOfNullableOrElseNullCase.Context extractContext(@NotNull Project project,
                                                                             @NotNull PsiMethodCallExpression call) {
      PsiMethodCallExpression outerCall = ExpressionUtils.getCallForQualifier(call);
      PsiExpression wrappingArgument = call.getArgumentList().getExpressions()[0];
      if (!OPTIONAL_OR_ELSE.test(outerCall)) return null;
      PsiExpression argument = outerCall.getArgumentList().getExpressions()[0];
      if (!ExpressionUtils.isNullLiteral(argument)) return null;
      return new Context(wrappingArgument, outerCall);
    }

    @Override
    public void apply(@NotNull Project project,
                      @NotNull PsiMethodCallExpression call,
                      @NotNull Context context) {
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(context.outerCall, context.wrappingArgument);
    }

    @Override
    public @NotNull CallMatcher getMatcher() {
      return OPTIONAL_OF_NULLABLE;
    }

    static class Context {
      final PsiExpression wrappingArgument;
      final PsiMethodCallExpression outerCall;

      Context(PsiExpression wrappingArgument, PsiMethodCallExpression outerCall) {
        this.wrappingArgument = wrappingArgument;
        this.outerCall = outerCall;
      }
    }
  }

  private static class OptionalOfNullableStringCase implements ChainSimplificationCase<OptionalOfNullableStringCase.Context> {
    @Override
    public @NotNull String getName(@NotNull Context context) {
      return JavaBundle.message("simplify.optional.chain.inspection.fix.description.replace.with.value.of.name");
    }

    @Override
    public @NotNull String getDescription(@NotNull Context context) {
      return JavaBundle.message("simplify.optional.chain.inspection.fix.description.replace.with.value.of.description");
    }

    @Override
    public @Nullable OptionalOfNullableStringCase.Context extractContext(@NotNull Project project,
                                                                         @NotNull PsiMethodCallExpression call) {
      PsiExpression arg = call.getArgumentList().getExpressions()[0];
      if (!TypeUtils.isJavaLangString(arg.getType())) return null;
      PsiMethodCallExpression maybeOrElse = ExpressionUtils.getCallForQualifier(call);
      if (!OPTIONAL_OR_ELSE.matches(maybeOrElse)) return null;
      PsiExpression orElseArgument = maybeOrElse.getArgumentList().getExpressions()[0];
      PsiLiteralExpression literal = ExpressionUtils.getLiteral(orElseArgument);
      if (literal == null || !"null".equals(literal.getValue())) return null;
      return new Context(arg, maybeOrElse);
    }

    @Override
    public void apply(@NotNull Project project,
                      @NotNull PsiMethodCallExpression call,
                      @NotNull Context context) {
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(context.orElseCall, "java.lang.String.valueOf(" + ct.text(context.argument) + ")");
    }

    @Override
    public @NotNull CallMatcher getMatcher() {
      return OPTIONAL_OF_NULLABLE;
    }

    static class Context {
      final PsiExpression argument;
      final PsiMethodCallExpression orElseCall;

      Context(PsiExpression argument, PsiMethodCallExpression orElseCall) {
        this.argument = argument;
        this.orElseCall = orElseCall;
      }
    }
  }
}