// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.HardcodedMethodConstants.EQUALS_IGNORE_CASE;
import static com.siyeh.HardcodedMethodConstants.TO_STRING;
import static com.siyeh.InspectionGadgetsBundle.BUNDLE;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

public final class RedundantStringOperationInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  enum FixType {
    REPLACE_WITH_QUALIFIER,
    REPLACE_WITH_ARGUMENTS
  }

  public static final String CONTENT_EQUALS = "contentEquals";

  private static final CallMatcher BYTE_ARRAY_OUTPUT_STREAM_INTO_BYTE_ARRAY =
    exactInstanceCall(JAVA_IO_BYTE_ARRAY_OUTPUT_STREAM, "toByteArray").parameterCount(0);
  private static final CallMatcher STRING_TO_STRING = exactInstanceCall(JAVA_LANG_STRING, TO_STRING).parameterCount(0);
  private static final CallMatcher STRING_INTERN = exactInstanceCall(JAVA_LANG_STRING, "intern").parameterCount(0);
  private static final CallMatcher STRING_LENGTH = exactInstanceCall(JAVA_LANG_STRING, HardcodedMethodConstants.LENGTH).parameterCount(0);
  private static final CallMatcher STRING_SUBSTRING_ONE_ARG = exactInstanceCall(JAVA_LANG_STRING, "substring").parameterTypes("int");
  private static final CallMatcher STRING_BUILDER_SUBSTRING_ONE_ARG = exactInstanceCall(JAVA_LANG_ABSTRACT_STRING_BUILDER, "substring").parameterTypes("int");
  private static final CallMatcher STRING_SUBSTRING_TWO_ARG = exactInstanceCall(JAVA_LANG_STRING, "substring").parameterTypes("int", "int");
  private static final CallMatcher STRING_BUILDER_SUBSTRING_TWO_ARG = exactInstanceCall(JAVA_LANG_ABSTRACT_STRING_BUILDER, "substring").parameterTypes("int", "int");
  private static final CallMatcher STRING_SUBSTRING = anyOf(STRING_SUBSTRING_ONE_ARG, STRING_SUBSTRING_TWO_ARG);
  private static final CallMatcher SUBSTRING = anyOf(STRING_BUILDER_SUBSTRING_ONE_ARG, STRING_BUILDER_SUBSTRING_TWO_ARG,
                                                     STRING_SUBSTRING_ONE_ARG, STRING_SUBSTRING_TWO_ARG);
  private static final CallMatcher STRING_BUILDER_APPEND =
    instanceCall(JAVA_LANG_ABSTRACT_STRING_BUILDER, "append").parameterTypes(JAVA_LANG_STRING);
  private static final CallMatcher SINGLE_ARG_STRING_BUILDER_APPEND =
    instanceCall(JAVA_LANG_ABSTRACT_STRING_BUILDER, "append").parameterCount(1);
  private static final CallMatcher STRING_BUILDER_TO_STRING = instanceCall(JAVA_LANG_ABSTRACT_STRING_BUILDER, TO_STRING).parameterCount(0);
  private static final CallMatcher PRINTSTREAM_PRINTLN = instanceCall("java.io.PrintStream", "println")
    .parameterTypes(JAVA_LANG_STRING);
  private static final CallMatcher METHOD_WITH_REDUNDANT_ZERO_AS_SECOND_PARAMETER =
    exactInstanceCall(JAVA_LANG_STRING, "indexOf", "startsWith").parameterCount(2);
  private static final CallMatcher STRING_LAST_INDEX_OF = exactInstanceCall(JAVA_LANG_STRING, "lastIndexOf").parameterCount(2);
  private static final CallMatcher STRING_IS_EMPTY = exactInstanceCall(JAVA_LANG_STRING, "isEmpty").parameterCount(0);
  private static final CallMatcher CASE_CHANGE = exactInstanceCall(JAVA_LANG_STRING, "toUpperCase", "toLowerCase");
  private static final CallMatcher STRING_EQUALS = exactInstanceCall(JAVA_LANG_STRING, HardcodedMethodConstants.EQUALS).parameterTypes(JAVA_LANG_OBJECT);
  private static final CallMatcher STRING_EQUALS_IGNORE_CASE =
    exactInstanceCall(JAVA_LANG_STRING, "equalsIgnoreCase").parameterTypes(JAVA_LANG_STRING);
  private static final CallMatcher CHANGE_CASE = anyOf(exactInstanceCall(JAVA_LANG_STRING, "toLowerCase").parameterCount(0),
                                                       exactInstanceCall(JAVA_LANG_STRING, "toUpperCase").parameterCount(0));
  private static final CallMatcher STRING_VALUE_OF = staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("char[]");
  private static final CallMatcher STRING_STRIP =
    exactInstanceCall(JAVA_LANG_STRING, "strip", "stripLeading", "stripTrailing").parameterCount(0);
  private static final CallMatcher STRING_TO_CHAR_ARRAY = exactInstanceCall(JAVA_LANG_STRING, "toCharArray").parameterCount(0);

  public boolean ignoreStringConstructor = false;
  public boolean ignoreSingleArgSubstring = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreStringConstructor",
               InspectionGadgetsBundle.message("inspection.redundant.string.option.do.not.report.string.constructors")),
      checkbox("ignoreSingleArgSubstring",
               InspectionGadgetsBundle.message("inspection.redundant.string.option.do.not.report.single.argument.substring")));
  }

  @Override
  public @NotNull String getShortName() {
    return "StringOperationCanBeSimplified";
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RedundantStringOperationVisitor(holder, isOnTheFly, this);
  }

  private static class RedundantStringOperationVisitor extends JavaElementVisitor {
    private final CallMapper<ProblemDescriptor> myProcessors = new CallMapper<ProblemDescriptor>()
      .register(STRING_TO_STRING, call -> getProblem(call, "inspection.redundant.string.call.message"))
      .register(SUBSTRING, this::getSubstringProblem)
      .register(SUBSTRING, this::getAppendSubstringProblem)
      .register(STRING_BUILDER_APPEND, this::getAppendProblem)
      .register(STRING_BUILDER_TO_STRING, this::getStringBuilderToStringProblem)
      .register(STRING_INTERN, this::getInternProblem)
      .register(PRINTSTREAM_PRINTLN, call ->
        getRedundantArgumentProblem(getSingleEmptyStringArgument(call), "inspection.redundant.empty.string.argument.message"))
      .register(METHOD_WITH_REDUNDANT_ZERO_AS_SECOND_PARAMETER, this::getRedundantZeroAsSecondParameterProblem)
      .register(STRING_LAST_INDEX_OF, this::getLastIndexOfProblem)
      .register(STRING_IS_EMPTY, this::getRedundantCaseChangeProblem)
      .register(STRING_EQUALS, this::getRedundantSubstringEqualsProblem)
      .register(anyOf(STRING_EQUALS, STRING_EQUALS_IGNORE_CASE), this::getRedundantCaseEqualsProblem)
      .register(STRING_VALUE_OF, call -> getValueOfProblem(call.getArgumentList()))
      .register(STRING_IS_EMPTY, this::getStripIsEmptyProblem);
    private final InspectionManager myManager;
    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;
    private final RedundantStringOperationInspection myInspection;

    RedundantStringOperationVisitor(ProblemsHolder holder, boolean isOnTheFly, RedundantStringOperationInspection inspection) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
      myInspection = inspection;
      myManager = myHolder.getManager();
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      myProcessors.mapAll(call).forEach(myHolder::registerProblem);
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      PsiJavaCodeReferenceElement classRef = expression.getClassReference();
      ProblemDescriptor descriptor = null;
      if (ConstructionUtils.isReferenceTo(classRef, JAVA_LANG_STRING_BUILDER, JAVA_LANG_STRING_BUFFER)) {
        String key = "inspection.redundant.empty.string.argument.message";
        descriptor = getRedundantArgumentProblem(getSingleEmptyStringArgument(expression), key);
      }
      else if (ConstructionUtils.isReferenceTo(classRef, JAVA_LANG_STRING) && !myInspection.ignoreStringConstructor) {
        descriptor = getStringConstructorProblem(expression);
      }
      if (descriptor != null) {
        myHolder.registerProblem(descriptor);
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(expression.getQualifierExpression());
      if (!(qualifier instanceof PsiMethodCallExpression call) ||
          !"length".equals(expression.getReferenceName()) ||
          !STRING_TO_CHAR_ARRAY.test(call)) {
        return;
      }
      myHolder.registerProblem(Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement()),
                               InspectionGadgetsBundle.message("unnecessary.tostring.call.problem.descriptor"),
                               new RemoveToCharArrayCallFix());
    }

    @Override
    public void visitTemplateExpression(@NotNull PsiTemplateExpression element) {
      if (!PsiUtil.isAvailable(JavaFeature.STRING_TEMPLATES, element) || element.getLiteralExpression() == null) {
        return;
      }
      PsiExpression processor = element.getProcessor();
      if (!(PsiUtil.skipParenthesizedExprDown(processor) instanceof PsiReferenceExpression reference)) {
        return;
      }
      PsiElement target = reference.resolve();
      if (!(target instanceof PsiField field) || !"STR".equals(field.getName())) {
        return;
      }
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null || !JAVA_LANG_STRING_TEMPLATE.equals(containingClass.getQualifiedName())) {
        return;
      }
      myHolder.registerProblem(processor,
                               InspectionGadgetsBundle.message("inspection.redundant.string.fix.remove.str.processor.description"),
                               new RemoveStrTemplateProcessorFix());
    }

    private ProblemDescriptor getStringConstructorProblem(PsiNewExpression expression) {
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) return null;
      final PsiJavaCodeReferenceElement anchor = expression.getClassOrAnonymousClassReference();
      if (anchor == null) return null;
      if (argumentList.isEmpty()) {
        LocalQuickFix[] fixes = {
          new StringConstructorFix(true),
          LocalQuickFix.from(new UpdateInspectionOptionFix(
            myInspection, "ignoreStringConstructor",
            InspectionGadgetsBundle.message("inspection.redundant.string.option.do.not.report.string.constructors"), true))};
        return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                                 InspectionGadgetsBundle.message("inspection.redundant.string.constructor.message"),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly, fixes);
      }
      final PsiExpression[] args = argumentList.getExpressions();

      if (isNewStringFromByteArrayParams(args)) {
        PsiMethodCallExpression methodCall = getMethodCallExpression(args[0]);

        if (BYTE_ARRAY_OUTPUT_STREAM_INTO_BYTE_ARRAY.test(methodCall)) {
          final PsiElement qualifier = methodCall.getMethodExpression().getQualifier();
          if (qualifier == null) return null;

          String newExpressionText = qualifier.getText() + ".toString(" + (args.length == 2 ? args[1].getText() : "") + ")";

          return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                                   InspectionGadgetsBundle.message("inspection.byte.array.output.stream.to.string.message"),
                                                   ProblemHighlightType.WARNING, myIsOnTheFly,
                                                   new ByteArrayOutputStreamToStringFix(newExpressionText));
        }
      }
      if (argumentList.getExpressionCount() == 1) {
        final CharArrayCreationArgument charArrayCreationArgument = CharArrayCreationArgument.from(argumentList);
        if (charArrayCreationArgument != null) {
          LocalQuickFix[] fixes = {
            new ReplaceWithValueOfFix(),
            LocalQuickFix.from(new UpdateInspectionOptionFix(
              myInspection, "ignoreStringConstructor",
              InspectionGadgetsBundle.message("inspection.redundant.string.option.do.not.report.string.constructors"), true))};
          return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                                   JavaAnalysisBundle.message("inspection.can.be.replaced.with.message", "String.valueOf()"),
                                                   ProblemHighlightType.WARNING, myIsOnTheFly, fixes);
        }
        PsiExpression arg = argumentList.getExpressions()[0];
        if (TypeUtils.isJavaLangString(arg.getType()) &&
            (PsiUtil.isLanguageLevel7OrHigher(expression) || !STRING_SUBSTRING.matches(arg))) {
          LocalQuickFix[] fixes = {
            new StringConstructorFix(false),
            LocalQuickFix.from(new UpdateInspectionOptionFix(
              myInspection, "ignoreStringConstructor",
              InspectionGadgetsBundle.message("inspection.redundant.string.option.do.not.report.string.constructors"), true))};
          return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                                   InspectionGadgetsBundle.message("inspection.redundant.string.constructor.message"),
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly, fixes);
        }
      }
      else if (isNewStringCreatedFromEntireArray(args)) {
        LocalQuickFix fix = new RemoveRedundantOffsetAndLengthArgumentsFix(args[1], args[2]);
        return myManager.createProblemDescriptor(args[1], args[2],
                                                 InspectionGadgetsBundle.message("inspection.redundant.arguments.message"),
                                                 ProblemHighlightType.LIKE_UNUSED_SYMBOL, myIsOnTheFly, fix);
      }
      return null;
    }

    /**
     * Checks that a new string is created from an entire array
     *
     * @param args arguments passed to the string constructor
     *
     * @return {@code true} if a new string is created from an entire array and the constructor
     * call can be simplified by removing redundant arguments, otherwise - {@code false}
     *
     * @see String#String(byte[], int, int)
     * @see String#String(char[], int, int)
     * @see String#String(byte[], int, int, java.nio.charset.Charset)
     * @see String#String(byte[], int, int, String)
     */
    private static boolean isNewStringCreatedFromEntireArray(PsiExpression[] args) {
      if (args.length < 3 || !ExpressionUtils.isZero(args[1])) return false;
      PsiExpression arrayExpression = ExpressionUtils.getArrayFromLengthExpression(args[2]);
      EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
      if (!equivalence.expressionsAreEquivalent(args[0], arrayExpression)) return false;
      return args.length == 3 && (TypeUtils.typeEquals("byte[]", args[0].getType()) || TypeUtils.typeEquals("char[]", args[0].getType())) ||
             args.length == 4 &&
             TypeUtils.typeEquals("byte[]", args[0].getType()) &&
             (TypeUtils.isJavaLangString(args[3].getType()) || TypeUtils.typeEquals(JAVA_NIO_CHARSET_CHARSET, args[3].getType()));
    }

    private static boolean isNewStringFromByteArrayParams(PsiExpression[] args) {
      if (args.length == 0 || !TypeUtils.typeEquals("byte[]", args[0].getType())) {
        return false;
      }
      if (args.length == 1) return true;
      if (args.length == 2) {
        PsiType type = args[1].getType();
        final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(args[1]);
        return TypeUtils.isJavaLangString(type) ||
               (TypeUtils.typeEquals(JAVA_NIO_CHARSET_CHARSET, type) && languageLevel.isAtLeast(LanguageLevel.JDK_10));
      }
      return false;
    }

    private @Nullable ProblemDescriptor getRedundantCaseEqualsProblem(PsiMethodCallExpression call) {
      PsiExpression equalTo = PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
      if (equalTo == null) return null;
      //case: "foo".equals(s.toLowerCase())
      if (equalTo instanceof PsiMethodCallExpression equalsToCallExpression &&
          isChangeCaseCall(equalsToCallExpression, call.getMethodExpression().getQualifierExpression())) {
        PsiElement anchor = equalsToCallExpression.getMethodExpression().getReferenceNameElement();
        if (anchor == null) {
          return null;
        }
        return createChangeCaseProblem(equalsToCallExpression, anchor, RemoveRedundantChangeCaseFix.PlaceCaseEqualType.RIGHT);
      }

      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (qualifierCall == null) return null;
      PsiExpression receiver = qualifierCall.getMethodExpression().getQualifierExpression();
      if (receiver == null) return null;

      PsiElement anchor = qualifierCall.getMethodExpression().getReferenceNameElement();
      if (anchor == null) {
        return null;
      }
      if (isChangeCaseCall(qualifierCall, equalTo)) {
        //case: text1.toLowerCase().equals("test2")
        return createChangeCaseProblem(qualifierCall, anchor, RemoveRedundantChangeCaseFix.PlaceCaseEqualType.LEFT);
      }

      //case: text1.toLowerCase().equals(text2.toLowerCase())
      if (equalTo instanceof PsiMethodCallExpression secondCall && isEqualChangeCaseCall(qualifierCall, secondCall)) {
        return createChangeCaseProblem(secondCall, anchor, RemoveRedundantChangeCaseFix.PlaceCaseEqualType.BOTH);
      }

      return null;
    }

    private @Nullable ProblemDescriptor createChangeCaseProblem(PsiMethodCallExpression equalsToCallExpression,
                                                                PsiElement anchor,
                                                                RemoveRedundantChangeCaseFix.PlaceCaseEqualType type) {
      String nameMethod = equalsToCallExpression.getMethodExpression().getReferenceName();
      if (nameMethod == null) {
        return null;
      }
      return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                               InspectionGadgetsBundle.message("inspection.x.call.can.be.replaced.with.y",
                                                                               EQUALS_IGNORE_CASE),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                               new RemoveRedundantChangeCaseFix(nameMethod, type));
    }


    private static boolean isChangeCaseCall(@NotNull PsiMethodCallExpression qualifierCall,
                                            @Nullable PsiExpression constant) {
      if (constant == null) return false;
      if (!CHANGE_CASE.test(qualifierCall)) return false;
      String constValue = tryCast(ExpressionUtils.computeConstantExpression(constant), String.class);
      if (constValue == null) return false;
      // Do not suggest incorrect fix for "HELLO".equals(s.toLowerCase())
      String methodName = qualifierCall.getMethodExpression().getReferenceName();
      String normalized = "toUpperCase".equals(methodName) ? constValue.toUpperCase(Locale.ROOT) : constValue.toLowerCase(Locale.ROOT);
      return constValue.equals(normalized);
    }

    private static boolean isEqualChangeCaseCall(@NotNull PsiMethodCallExpression qualifierCall,
                                                 @NotNull PsiMethodCallExpression secondCall) {
      return CHANGE_CASE.test(qualifierCall) &&
             CHANGE_CASE.test(secondCall) &&
             qualifierCall.getMethodExpression().getReferenceName() != null &&
             qualifierCall.getMethodExpression().getReferenceName().equals(secondCall.getMethodExpression().getReferenceName());
    }

    private ProblemDescriptor getRedundantSubstringEqualsProblem(PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (qualifierCall == null) return null;
      PsiExpression receiver = qualifierCall.getMethodExpression().getQualifierExpression();
      if (receiver == null) return null;
      if (STRING_SUBSTRING_TWO_ARG.test(qualifierCall)) {
        PsiExpression equalTo = call.getArgumentList().getExpressions()[0];
        PsiExpression[] args = qualifierCall.getArgumentList().getExpressions();
        boolean lengthMatches = lengthMatches(equalTo, args[0], args[1]);
        if (lengthMatches) {
          PsiElement anchor = qualifierCall.getMethodExpression().getReferenceNameElement();
          if (anchor != null) {
            if (equalTo instanceof PsiLiteralExpression literal &&
                literal.getValue() instanceof String str &&
                StringUtil.length(str) == 1) {
              return createSubstringToCharAtProblemDescriptor(call, anchor);
            }
            RemoveRedundantSubstringFix fix = new RemoveRedundantSubstringFix(isLengthOf(args[1], receiver) ? "endsWith" : "startsWith");
            return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                                     InspectionGadgetsBundle.message("inspection.redundant.string.call.message"),
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                                     fix);
          }
        }
      }
      if (STRING_SUBSTRING_ONE_ARG.test(qualifierCall)) {
        PsiExpression equalTo = call.getArgumentList().getExpressions()[0];
        PsiExpression from = qualifierCall.getArgumentList().getExpressions()[0];
        PsiExpression to = getLengthExpression(receiver, JavaPsiFacade.getElementFactory(myHolder.getProject()));
        boolean lengthMatches = lengthMatches(equalTo, from, to);
        if (lengthMatches) {
          PsiElement anchor = qualifierCall.getMethodExpression().getReferenceNameElement();
          if (anchor != null) {
            return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                                     InspectionGadgetsBundle.message("inspection.redundant.string.call.message"),
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                                     new RemoveRedundantSubstringFix("endsWith"));
          }
        }
      }
      return null;
    }

    /**
     * This method is meant to generate a {@link ProblemDescriptor} for a call like
     * <pre>
     * stringValue.substring(i, i + 1).equals("_")
     * </pre>
     * which can be replaced with a more readable version
     * <pre>
     *   stringValue.charAt(i) == '_'
     * </pre>
     * <hr />
     * <p>
     * <strong>IMPORTANT:</strong> this method is meant to be called only from
     * {@link RedundantStringOperationVisitor#getRedundantSubstringEqualsProblem(PsiMethodCallExpression)}
     * </p>
     *
     * @param call the original expression to generate a {@link ProblemDescriptor} for
     * @return generated instance of {@link ProblemDescriptor}
     */
    private @NotNull ProblemDescriptor createSubstringToCharAtProblemDescriptor(@NotNull PsiMethodCallExpression call, @NotNull PsiElement anchor) {
      final String converted = SubstringToCharAtQuickFix.getTargetString(call, PsiElement::getText);
      assert converted != null : "Message cannot be null";

      return myManager.createProblemDescriptor(anchor,
                                               InspectionGadgetsBundle.message("inspection.x.call.can.be.replaced.with.y", "charAt"),
                                               new SubstringToCharAtQuickFix(getOutermostEquals(call).getText(), converted, true),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
    }

    private static boolean isNegated(final @NotNull PsiExpression start, boolean negated) {
      final PsiExpression negation = BoolUtils.findNegation(start);
      if (negation == null) return negated;
      else return isNegated(negation, !negated);
    }

    private static PsiExpression getOutermostEquals(final @NotNull PsiExpression start) {
      final PsiExpression negation = BoolUtils.findNegation(start);
      if (negation == null) return start;
      else return getOutermostEquals(negation);
    }

    private boolean lengthMatches(PsiExpression equalTo, PsiExpression from, PsiExpression to) {
      if (!ExpressionUtils.hasStringType(equalTo)) return false;
      String str = tryCast(ExpressionUtils.computeConstantExpression(equalTo), String.class);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myHolder.getProject());
      if (str != null) {
        PsiExpression lengthExpression =
          factory.createExpressionFromText(String.valueOf(str.length()), equalTo);
        if (ExpressionUtils.isDifference(from, to, lengthExpression)) return true;
      }
      PsiExpression lengthExpression = getLengthExpression(equalTo, factory);
      return ExpressionUtils.isDifference(from, to, lengthExpression);
    }

    private static PsiExpression getLengthExpression(PsiExpression string, PsiElementFactory factory) {
      return factory
            .createExpressionFromText(ParenthesesUtils.getText(string, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + ".length()", string);
    }

    private @Nullable ProblemDescriptor getRedundantCaseChangeProblem(PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (CASE_CHANGE.test(qualifierCall)) {
        return getProblem(qualifierCall, "inspection.redundant.string.call.message");
      }
      return null;
    }

    private @Nullable ProblemDescriptor getStripIsEmptyProblem(PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (!STRING_STRIP.test(qualifierCall)) return null;
      PsiElement anchor = qualifierCall.getMethodExpression().getReferenceNameElement();
      if (anchor == null) return null;
      String message = InspectionGadgetsBundle.message("inspection.x.call.can.be.replaced.with.y", "isBlank");
      return myManager.createProblemDescriptor(anchor, (TextRange)null, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                               new StripIsEmptyToIsBlankFix());
    }

    private @Nullable ProblemDescriptor getAppendProblem(PsiMethodCallExpression call) {
      return getSingleEmptyStringArgument(call) != null ? getProblem(call, "inspection.redundant.string.call.message") : null;
    }

    private @Nullable ProblemDescriptor getStringBuilderToStringProblem(final @NotNull PsiMethodCallExpression call) {
      final ProblemDescriptor descriptor = getRedundantStringBuilderToStringProblem(call);
      if (descriptor != null) return descriptor;
      return getUseContentEqualsProblem(call);
    }

    private @Nullable ProblemDescriptor getRedundantStringBuilderToStringProblem(final @NotNull PsiMethodCallExpression call) {
      final PsiMethodCallExpression substringCall = PsiTreeUtil.getParentOfType(call, PsiMethodCallExpression.class);
      if (substringCall == null) return null;

      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(substringCall.getMethodExpression().getQualifierExpression());
      if (qualifier != call || !STRING_SUBSTRING.test(substringCall)) return null;

      return getProblem(call, "inspection.redundant.string.call.message");
    }

    private @Nullable ProblemDescriptor getUseContentEqualsProblem(final @NotNull PsiMethodCallExpression call) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
      if (parent instanceof PsiExpressionList list &&
          list.getExpressionCount() == 1 &&
          parent.getParent() instanceof PsiMethodCallExpression parentCall &&
          STRING_EQUALS.test(parentCall)) {
        PsiElement nameElement = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());
        final String message = InspectionGadgetsBundle.message("inspection.x.call.can.be.replaced.with.y", CONTENT_EQUALS);
        return myManager.createProblemDescriptor(nameElement, message, new UseContentEqualsFix(),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
      }
      return null;
    }

    private @Nullable ProblemDescriptor getInternProblem(PsiMethodCallExpression call) {
      return PsiUtil.isConstantExpression(call.getMethodExpression().getQualifierExpression())
             ? getProblem(call, "inspection.redundant.string.intern.on.constant.message")
             : null;
    }

    private @Nullable ProblemDescriptor getLastIndexOfProblem(PsiMethodCallExpression call) {
      PsiExpression secondArg = call.getArgumentList().getExpressions()[1];
      PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(secondArg);
      // s.lastIndexOf(..., s.length()) or s.lastIndexOf(..., s.length() - 1)
      if (stripped instanceof PsiBinaryExpression binOp && binOp.getOperationTokenType() == JavaTokenType.MINUS &&
          ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(binOp.getROperand()), 1)) {
        stripped = binOp.getLOperand();
      }
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (!isLengthOf(stripped, qualifier)) return null;
      return getRedundantArgumentProblem(secondArg, "inspection.redundant.string.length.argument.message");
    }

    private @Nullable ProblemDescriptor getRedundantZeroAsSecondParameterProblem(PsiMethodCallExpression call) {
      PsiExpression secondArg = call.getArgumentList().getExpressions()[1];
      if (ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(secondArg), 0)) {
        return getRedundantArgumentProblem(secondArg, "inspection.redundant.zero.argument.message");
      }
      return null;
    }

    private @Nullable ProblemDescriptor getRedundantArgumentProblem(@Nullable PsiExpression argument,
                                                                    @NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
      if (argument == null) return null;
      LocalQuickFix fix = LocalQuickFix.from(new DeleteElementFix(
        argument, InspectionGadgetsBundle.message("inspection.redundant.string.remove.argument.fix.name")));
      return myManager.createProblemDescriptor(argument,
                                               InspectionGadgetsBundle.message(key),
                                               myIsOnTheFly,
                                               new LocalQuickFix[]{fix},
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    private static @Nullable PsiExpression getSingleEmptyStringArgument(PsiCall call) {
      PsiExpressionList argList = call.getArgumentList();
      if (argList == null) return null;
      PsiExpression[] args = argList.getExpressions();
      if (args.length != 1) return null;
      return ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(args[0]), "") ? args[0] : null;
    }

    private @Nullable ProblemDescriptor getAppendSubstringProblem(PsiMethodCallExpression call) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      PsiExpression stringExpression = call.getMethodExpression().getQualifierExpression();
      if (args.length == 1) {
        if (myInspection.ignoreSingleArgSubstring ||
            ExpressionUtils.isZero(args[0]) ||
            isLengthOf(args[0], stringExpression) ||
            !(PsiUtil.deparenthesizeExpression(stringExpression) instanceof PsiReferenceExpression)) {
          return null;
        }
      }
      else if (args.length == 2) {
        if (ExpressionUtils.isZero(args[0]) && isLengthOf(args[1], stringExpression) || isBetterWithCharAt(call)) {
          return null;
        }
      }
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
      if (parent instanceof PsiExpressionList list && list.getExpressionCount() == 1 &&
          parent.getParent() instanceof PsiMethodCallExpression parentCall && SINGLE_ARG_STRING_BUILDER_APPEND.test(parentCall)) {
        PsiElement nameElement = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());
        return myManager.createProblemDescriptor(nameElement,
                                                 InspectionGadgetsBundle.message("inspection.redundant.string.call.message"),
                                                 new RemoveRedundantStringCallFix(nameElement.getText(), FixType.REPLACE_WITH_ARGUMENTS),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
      }
      return null;
    }

    private @Nullable ProblemDescriptor getSubstringProblem(PsiMethodCallExpression call) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      PsiExpression stringExpression = call.getMethodExpression().getQualifierExpression();
      if (args.length == 1) {
        if (ExpressionUtils.isZero(args[0])) {
          return getProblem(call, "inspection.redundant.string.call.message");
        } else if (isLengthOf(args[0], stringExpression)) {
          PsiElement anchor = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());
          return myManager.createProblemDescriptor(anchor,
                                                   InspectionGadgetsBundle.message("inspection.redundant.string.call.message"),
                                                   new SubstringToEmptyStringFix(),
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
        }
        return null;
      }
      // args.length == 2
      if (isLengthOf(args[1], stringExpression)) {
        if (ExpressionUtils.isZero(args[0])) {
          return getProblem(call, "inspection.redundant.string.call.message");
        }
        LocalQuickFix fix = LocalQuickFix.from(new DeleteElementFix(
          args[1], InspectionGadgetsBundle.message("inspection.redundant.string.remove.argument.fix.name")));
        return myManager.createProblemDescriptor(args[1],
                                                 InspectionGadgetsBundle.message("inspection.redundant.string.length.argument.message"),
                                                 fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
      }

      if (isBetterWithCharAt(call)) {
        final String converted = String.format("%s.charAt(%s)", Objects.requireNonNull(stringExpression).getText(), args[0].getText());
        PsiElement anchor = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());
        return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                                 InspectionGadgetsBundle.message("inspection.x.call.can.be.replaced.with.y", "charAt"),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                                 new SubstringToCharAtQuickFix(call.getText(), converted, false));
      }
      return null;
    }

    /**
     * This method examines the passed {@link PsiMethodCallExpression} that contains {@link String#substring(int, int)}
     * to decide if it should be altered with {@link String#charAt(int)}<br/>
     * <pre>
     * args[1] - args[0] == 1
     *    yes          no
     *    |             |
     *    |             |- return false
     *    |
     *    |-ExpressionUtils.isConversionToStringNecessary(call, false) == true
     *                    yes                  no
     *                     |                   |- return true
     *                     |- return false
     * </pre>
     * The method checks if the difference between the arguments of {@link String#substring(int, int)} is equal to "1"
     * and if so it checks if the expression should be converted to string according to its surroundings.
     * If the expression is not required to be converted to string explicitly then the expression is a good candidate
     * to be converted with {@link String#charAt(int)}
     *
     * @param call an expression to examine
     * @return true if the expression is a good candidate to be converted with {@link String#charAt(int)}, otherwise - false
     */
    private static boolean isBetterWithCharAt(final @NotNull PsiMethodCallExpression call) {
      final PsiExpression[] args = call.getArgumentList().getExpressions();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(call.getProject());

      final PsiExpression one = factory.createExpressionFromText("1", null);
      final boolean diffByOne = ExpressionUtils.isDifference(args[0], args[1], one);
      if (!diffByOne) return false;

      return !ExpressionUtils.isConversionToStringNecessary(call, false);
    }

    private static boolean isLengthOf(PsiExpression stringLengthCandidate, PsiExpression stringExpression) {
      PsiMethodCallExpression argCall = tryCast(PsiUtil.skipParenthesizedExprDown(stringLengthCandidate), PsiMethodCallExpression.class);
      return STRING_LENGTH.test(argCall) &&
             EquivalenceChecker.getCanonicalPsiEquivalence()
               .expressionsAreEquivalent(stringExpression, argCall.getMethodExpression().getQualifierExpression());
    }

    private ProblemDescriptor getProblem(PsiMethodCallExpression call, @NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
      PsiElement anchor = call.getMethodExpression().getReferenceNameElement();
      if (anchor == null) return null;
      return myManager.createProblemDescriptor(anchor, (TextRange)null, InspectionGadgetsBundle.message(key),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                               new RemoveRedundantStringCallFix(anchor.getText(), FixType.REPLACE_WITH_QUALIFIER));
    }

    private @Nullable ProblemDescriptor getValueOfProblem(@NotNull PsiExpressionList argList) {
      final CharArrayCreationArgument charArrayCreationArgument = CharArrayCreationArgument.from(argList);
      if (charArrayCreationArgument == null) return null;
      return myManager.createProblemDescriptor(charArrayCreationArgument.newExpression,
                                               new TextRange(0, charArrayCreationArgument.arrayInitializer.getStartOffsetInParent()),
                                               InspectionGadgetsBundle.message("inspection.redundant.string.new.array.message"),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                               myIsOnTheFly,
                                               new UnwrapArrayInitializerFix(charArrayCreationArgument.initializer.getText()));
    }

    /**
     * An instance of {@link LocalQuickFix} for problems that can be solved by replacing
     * {@link String#substring(int, int)} with {@link String#charAt(int)} or
     * {@code stringValue.substring(i, i + 1).equals("_")} with {@code stringValue.charAt(i) == '_'}
     */
    private static class SubstringToCharAtQuickFix extends PsiUpdateModCommandQuickFix {
      private final @NotNull String myText;
      private final @NotNull String myConverted;
      private final boolean myEquality;

      SubstringToCharAtQuickFix(final @NotNull String text, final @NotNull String converted, boolean equality) {
        myText = text;
        myConverted = converted;
        myEquality = equality;
      }

      @Override
      public @IntentionName @NotNull String getName() {
        return CommonQuickFixBundle.message("fix.replace.x.with.y", myText, myConverted);
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return CommonQuickFixBundle.message("fix.replace.x.with.y", "substring()", "charAt()");
      }

      @Override
      protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
        if (myEquality) {
          applyEqualityFix(element);
        }
        else {
          PsiMethodCallExpression call = tryCast(element.getParent().getParent(), PsiMethodCallExpression.class);
          if (call == null) return;
          PsiExpression[] args = call.getArgumentList().getExpressions();
          if (args.length != 2) return;
          ExpressionUtils.bindCallTo(call, "charAt");
          new CommentTracker().deleteAndRestoreComments(args[1]);
        }
      }

      private static void applyEqualityFix(@NotNull PsiElement startElement) {
        final PsiElement element = startElement.getParent().getParent();
        final PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        if (call == null) return;

        final CommentTracker ct = new CommentTracker();
        final String convertTo = getTargetString(call, ct::text);
        if (convertTo == null) return;

        ct.replaceAndRestoreComments(getOutermostEquals(call), convertTo);
      }

      private static @NonNls @Nullable String getTargetString(final @NotNull PsiMethodCallExpression call,
                                                              @NotNull Function<@NotNull PsiElement, @NotNull String> textExtractor) {
        final PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
        if (qualifierCall == null) return null;

        final PsiExpression receiver = qualifierCall.getMethodExpression().getQualifierExpression();
        if (receiver == null) return null;

        final PsiExpression[] args = qualifierCall.getArgumentList().getExpressions();
        if (args.length != 2) return null;

        final PsiLiteralExpression equalTo = (PsiLiteralExpression)call.getArgumentList().getExpressions()[0];
        final String equalToValue = PsiLiteralUtil.charLiteralString(equalTo);
        return String.format("%s.charAt(%s) %s %s",
                             textExtractor.apply(receiver),
                             textExtractor.apply(args[0]),
                             isNegated(call, false) ? "!=" : "==",
                             equalToValue
        );
      }
    }
  }

  private static void useMethodInsteadOfRedundantCall(String methodToUse, PsiMethodCallExpression redundantCall) {
    PsiMethodCallExpression equalsCall = PsiTreeUtil.getParentOfType(redundantCall, PsiMethodCallExpression.class);
    if (equalsCall == null) return;
    PsiExpression qualifierBeforeChangeCase = ExpressionUtils.getEffectiveQualifier(redundantCall.getMethodExpression());
    if (qualifierBeforeChangeCase == null) return;
    CommentTracker ct = new CommentTracker();
    ct.replaceAndRestoreComments(redundantCall, qualifierBeforeChangeCase);
    ExpressionUtils.bindCallTo(equalsCall, methodToUse);
  }

  private static class UseContentEqualsFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("use.contentequals");
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
      return InspectionGadgetsBundle.message("remove.redundant.string.fix.text", CONTENT_EQUALS, TO_STRING);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression changeCaseCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (changeCaseCall == null) return;
      useMethodInsteadOfRedundantCall(CONTENT_EQUALS, changeCaseCall);
    }
  }

  private static class RemoveRedundantChangeCaseFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull String caseRedundant;
    private final @NotNull PlaceCaseEqualType myPlaceCaseEqualType;

    private enum PlaceCaseEqualType {
      LEFT, RIGHT, BOTH
    }

    RemoveRedundantChangeCaseFix(@NotNull String caseRedundant, @NotNull PlaceCaseEqualType placeCaseEqualType) {
      this.caseRedundant = caseRedundant;
      this.myPlaceCaseEqualType = placeCaseEqualType;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
      return InspectionGadgetsBundle.message("remove.redundant.string.fix.text", EQUALS_IGNORE_CASE, caseRedundant);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("use.equalsignorecase.for.case.insensitive.comparison");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression changeCaseCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (changeCaseCall == null) return;

      if (myPlaceCaseEqualType == PlaceCaseEqualType.RIGHT) {
        useMethodInsteadOfRedundantCall(EQUALS_IGNORE_CASE, changeCaseCall);
        return;
      }

      fixLeftAndBothChangeCase(changeCaseCall);
    }

    private void fixLeftAndBothChangeCase(PsiMethodCallExpression changeCaseCall) {
      PsiExpression qualifierBeforeChangeCase = changeCaseCall.getMethodExpression().getQualifierExpression();
      if (qualifierBeforeChangeCase == null) return;
      PsiMethodCallExpression equalsCall = ExpressionUtils.getCallForQualifier(changeCaseCall);
      if (equalsCall == null) return;
      if (myPlaceCaseEqualType == PlaceCaseEqualType.BOTH) {
        PsiExpression secondChangeCaseCall = PsiUtil.skipParenthesizedExprDown(equalsCall.getArgumentList().getExpressions()[0]);
        if (secondChangeCaseCall == null) return;
        PsiExpression secondQualifierBeforeChangeCase =
          ExpressionUtils.getEffectiveQualifier(((PsiMethodCallExpression)secondChangeCaseCall).getMethodExpression());
        if (secondQualifierBeforeChangeCase == null) return;
        CommentTracker ct = new CommentTracker();
        ct.replaceAndRestoreComments(secondChangeCaseCall, secondQualifierBeforeChangeCase);
      }
      ExpressionUtils.bindCallTo(equalsCall, EQUALS_IGNORE_CASE);
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(changeCaseCall, qualifierBeforeChangeCase);
    }
  }

  private static class RemoveRedundantSubstringFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull String myBindCallName;

    RemoveRedundantSubstringFix(@NotNull @NonNls String bindCallName) {
      myBindCallName = bindCallName;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
      final @NonNls String methodName = "substring";
      return InspectionGadgetsBundle.message("remove.redundant.string.fix.text", myBindCallName, methodName);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.redundant.substring.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression substringCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (substringCall == null) return;
      PsiExpression stringExpr = substringCall.getMethodExpression().getQualifierExpression();
      if (stringExpr == null) return;
      PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(substringCall);
      if (nextCall == null) return;
      PsiExpression[] args = substringCall.getArgumentList().getExpressions();
      if (args.length == 0) return;
      CommentTracker ct = new CommentTracker();
      if (!"endsWith".equals(myBindCallName) && !ExpressionUtils.isZero(args[0])) {
        nextCall.getArgumentList().add(ct.markUnchanged(args[0]));
      }
      ExpressionUtils.bindCallTo(nextCall, myBindCallName);
      ct.replaceAndRestoreComments(substringCall, stringExpr);
    }
  }

  private static class StripIsEmptyToIsBlankFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getName() {
      return InspectionGadgetsBundle.message("remove.redundant.string.fix.text", "isBlank", "strip");
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("use.isblank.to.check.if.string.is.whitespace.or.empty");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression stripCall = PsiTreeUtil.getNonStrictParentOfType(element, PsiMethodCallExpression.class);
      if (stripCall == null) return;
      PsiMethodCallExpression isEmptyCall = ExpressionUtils.getCallForQualifier(stripCall);
      if (isEmptyCall == null) return;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(stripCall.getMethodExpression());
      if (qualifier == null) return;
      new CommentTracker().replaceAndRestoreComments(stripCall, qualifier);
      ExpressionUtils.bindCallTo(isEmptyCall, "isBlank");
    }
  }

  private static class RemoveRedundantStringCallFix extends PsiUpdateModCommandQuickFix {
    private final FixType myFixType;
    private final String myToRemove;

    RemoveRedundantStringCallFix(String toRemove, FixType fixType) {
      myToRemove = toRemove;
      myFixType = fixType;
    }

    @Override
    public @Nls @NotNull String getName() {
      return InspectionGadgetsBundle.message("inspection.remove.redundant.call.fix.name", myToRemove);
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.string.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getNonStrictParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
      if (qualifier == null) return;
      CommentTracker ct = new CommentTracker();
      switch (myFixType) {
        case REPLACE_WITH_QUALIFIER -> {
          PsiExpression result = (PsiExpression)ct.replaceAndRestoreComments(call, qualifier);
          if (result.getParent() instanceof PsiExpressionStatement expr) {
            extractSideEffects(result, expr);
          }
        }
        case REPLACE_WITH_ARGUMENTS -> {
          PsiExpressionList list = tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiExpressionList.class);
          if (list == null) return;
          PsiExpression[] expressions = call.getArgumentList().getExpressions();
          for (PsiExpression arg : expressions) {
            list.add(ct.markUnchanged(arg));
          }
          if (expressions.length == 1) {
            PsiExpression expression =
              JavaPsiFacade.getElementFactory(project).createExpressionFromText(qualifier.getText() + ".length()", call);
            list.add(expression);
          }
          ct.replaceAndRestoreComments(call, qualifier);
        }
      }
    }

    private static void extractSideEffects(PsiExpression result, PsiStatement statement) {
      List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(result);
      if (Objects.equals(Collections.singletonList(result), sideEffects)) return;

      PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, result);
      if (statements.length > 0) {
        PsiStatement lastAdded = BlockUtils.addBefore(statement, statements);
        statement = Objects.requireNonNull(PsiTreeUtil.getNextSiblingOfType(lastAdded, PsiStatement.class));
      }
      statement.delete();
    }
  }

  private static final class StringConstructorFix extends PsiUpdateModCommandQuickFix {
    private final @IntentionName String myName;

    private StringConstructorFix(boolean noArguments) {
      myName = noArguments
               ? InspectionGadgetsBundle.message("inspection.redundant.string.replace.with.empty.fix.name")
               : InspectionGadgetsBundle.message("inspection.redundant.string.replace.with.arg.fix.name");
    }

    @Override
    public @NotNull String getName() {
      return myName;
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiNewExpression expression = tryCast(element.getParent(), PsiNewExpression.class);
      if (expression == null) return;
      final PsiExpressionList argList = expression.getArgumentList();
      if (argList == null) return;
      final PsiExpression[] args = argList.getExpressions();
      CommentTracker commentTracker = new CommentTracker();
      final String argText = (args.length == 1) ? commentTracker.text(args[0]) : "\"\"";

      PsiReplacementUtil.replaceExpression(expression, argText, commentTracker);
    }
  }

  private static @Nullable PsiMethodCallExpression getMethodCallExpression(PsiExpression expression) {
    PsiExpression resolvedExpression = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.resolveExpression(expression));
    return tryCast(resolvedExpression, PsiMethodCallExpression.class);
  }

  private static final class ByteArrayOutputStreamToStringFix extends PsiUpdateModCommandQuickFix {
    private final String myText;

    private ByteArrayOutputStreamToStringFix(String text) {
      myText = text;
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myText);
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "toString()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiNewExpression expression = tryCast(element.getParent(), PsiNewExpression.class);
      if (expression == null) return;

      final PsiExpressionList args = expression.getArgumentList();
      if (args == null) return;

      final PsiExpression[] params = args.getExpressions();
      if (!(params.length == 1 || params.length == 2)) return;

      PsiMethodCallExpression resolvedExpression = getMethodCallExpression(params[0]);
      if (resolvedExpression == null) return;

      final PsiElement qualifier = resolvedExpression.getMethodExpression().getQualifier();
      if (qualifier == null) return;

      CommentTracker ct = new CommentTracker();
      String newText = ct.text(qualifier) + ".toString(" + (params.length == 2 ? ct.text(params[1]) : "") + ")";

      PsiElement parent = tryCast(PsiUtil.skipParenthesizedExprUp(resolvedExpression.getParent()), PsiLocalVariable.class);
      if (parent != null) ct.delete(parent);

      ct.replaceAndRestoreComments(expression, newText);
    }
  }

  private static final class SubstringToEmptyStringFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("inspection.redundant.string.replace.with.empty.fix.name");
    }

    @Override
    public @NotNull String getFamilyName() {
      return getName();
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiMethodCallExpression expression = tryCast(element.getParent().getParent(), PsiMethodCallExpression.class);
      if (expression == null) return;
      new CommentTracker().replaceAndRestoreComments(expression, "\"\"");
    }
  }

  private static final class ReplaceWithValueOfFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "String.valueOf()");
    }

    @Override
    public @NotNull String getFamilyName() {
      return getName();
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiNewExpression expression = tryCast(element.getParent(), PsiNewExpression.class);
      if (expression == null) return;
      final CharArrayCreationArgument charArrayCreationArgument = CharArrayCreationArgument.from(expression.getArgumentList());
      if (charArrayCreationArgument == null) return;
      CommentTracker ct = new CommentTracker();
      final String replacementText = "String.valueOf(" + ct.text(charArrayCreationArgument.initializer) + ")";
      PsiReplacementUtil.replaceExpression(expression, replacementText, ct);
    }
  }

  private static final class UnwrapArrayInitializerFix extends PsiUpdateModCommandQuickFix {
    private final String myInitializerText;

    private UnwrapArrayInitializerFix(String initializerText) {
      myInitializerText = initializerText;
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.unwrap", myInitializerText);
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("unwrap.array.initializer.fix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiNewExpression expression = tryCast(element, PsiNewExpression.class);
      if (expression == null) return;
      final PsiArrayInitializerExpression initializer = expression.getArrayInitializer();
      if (initializer == null || initializer.getInitializers().length != 1) return;
      PsiReplacementUtil.replaceExpression(expression, initializer.getInitializers()[0].getText(), new CommentTracker());
    }
  }

  private static final class CharArrayCreationArgument {
    @NotNull PsiNewExpression newExpression;
    @NotNull PsiArrayInitializerExpression arrayInitializer;
    @NotNull PsiExpression initializer;

    private CharArrayCreationArgument(@NotNull PsiNewExpression newExpression,
                                      @NotNull PsiArrayInitializerExpression arrayInitializer,
                                      @NotNull PsiExpression initializer) {
      this.newExpression = newExpression;
      this.arrayInitializer = arrayInitializer;
      this.initializer = initializer;
    }

    private static @Nullable CharArrayCreationArgument from(@Nullable PsiExpressionList argList) {
      if (argList == null || argList.getExpressionCount() != 1) return null;
      final PsiExpression arg = PsiUtil.skipParenthesizedExprDown(argList.getExpressions()[0]);
      final PsiNewExpression newExpression = tryCast(arg, PsiNewExpression.class);
      if (newExpression == null) return null;
      final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
      if (arrayInitializer == null || !TypeUtils.typeEquals("char[]", newExpression.getType())) return null;
      final PsiExpression[] initializers = arrayInitializer.getInitializers();
      if (initializers.length != 1) return null;
      final PsiExpression initializer = initializers[0];
      final PsiType type = initializer.getType();
      if (!PsiTypes.charType().equals(type) && !TypeUtils.typeEquals(JAVA_LANG_CHARACTER, type)) return null;
      return new CharArrayCreationArgument(newExpression, arrayInitializer, initializer);
    }
  }

  private static final class RemoveRedundantOffsetAndLengthArgumentsFix extends LocalQuickFixOnPsiElement {

    RemoveRedundantOffsetAndLengthArgumentsFix(PsiElement argument1, PsiElement argument2) {
      super(argument1, argument2);
    }

    @Override
    public @NotNull String getText() {
      return getFamilyName();
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("remove.redundant.arguments.family");
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      new CommentTracker().delete(startElement, endElement);
    }
  }

  private static final class RemoveStrTemplateProcessorFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("remove.redundant.str.processor");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element.getParent() instanceof PsiTemplateExpression template) {
        final PsiLiteralExpression literal = template.getLiteralExpression();
        if (literal != null) {
          template.replace(literal);
        }
      }
    }
  }

  private static class RemoveToCharArrayCallFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.unnecessary.0.call.quickfix", "toCharArray");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression ref)) {
        return;
      }
      PsiExpression qualifier = ref.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression) || !(grandParent.getParent() instanceof PsiReferenceExpression toArrayCall)) {
        return;
      }
      new CommentTracker().replaceAndRestoreComments(toArrayCall, qualifier.getText() + ".length()");
    }
  }
}
