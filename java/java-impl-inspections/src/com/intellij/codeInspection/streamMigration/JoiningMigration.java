// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;


import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil.isEffectivelyFinal;
import static com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.isCallOf;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_CHAR_SEQUENCE;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ControlFlowUtils.getInitializerUsageStatus;
import static com.siyeh.ig.psiutils.ExpressionUtils.resolveLocalVariable;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class JoiningMigration extends BaseStreamApiMigration {
  protected JoiningMigration(boolean shouldWarn) {
    super(shouldWarn, "collect");
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    JoiningTerminal terminal = extractTerminal(tb, null);
    if(terminal == null) return null;

    TerminalBlock block = terminal.getTerminalBlock();
    PsiStatement loopStatement = block.getStreamSourceStatement();
    CommentTracker ct = new CommentTracker();
    String stream = terminal.generateStreamCode(ct);
    PsiVariable builder = terminal.getBuilder();
    terminal.preCleanUp(ct);
    ControlFlowUtils.InitializerUsageStatus status = getInitializerUsageStatus(builder, loopStatement);
    if(builder instanceof PsiLocalVariable) {
      PsiElement result = replaceInitializer(loopStatement, builder, builder.getInitializer(), stream, status, ct);
      terminal.cleanUp((PsiLocalVariable)builder);
      JoiningTerminal.replaceUsages((PsiLocalVariable)terminal.getBuilder());
      return result;
    } else {
      return new CommentTracker().replaceAndRestoreComments(tb.getStreamSourceStatement(), builder.getName() + ".append(" +  stream + ");");
    }
  }

  @Nullable
  static JoiningTerminal extractTerminal(@NotNull TerminalBlock terminalBlock,
                                         @Nullable("when fix applied") List<PsiVariable> nonFinalVariables) {
    List<BiFunction<TerminalBlock, List<PsiVariable>, JoiningTerminal>> extractors = Arrays.asList(
      JoiningTerminal.CountedLoopJoiningTerminal::extractCountedLoopTerminal,
      JoiningTerminal.PlainJoiningTerminal::extractPlainJoining,
      JoiningTerminal.LengthBasedJoiningTerminal::extractLengthBasedTerminal,
      JoiningTerminal.BoolFlagJoiningTerminal::extractBoolFlagTerminal,
      JoiningTerminal.LengthTruncateJoiningTerminal::extractLengthTruncateTerminal,
      JoiningTerminal.DelimiterRewriteJoiningTerminal::extractDelimiterRewritingTerminal,
      JoiningTerminal.IndexBasedJoiningTerminal::extractIndexBasedTerminal
    );
    return StreamEx.of(extractors)
      .map(extractor -> extractor.apply(terminalBlock, nonFinalVariables))
      .nonNull()
      .findFirst()
      .orElse(null);
  }

  static class JoiningTerminal {
    private static final CallMatcher APPEND = CallMatcher.anyOf(
      CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING_BUILDER, "append").parameterCount(1),
      CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING_BUFFER, "append").parameterCount(1)
    );


    private static final CallMatcher LENGTH =
      CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER, "length").parameterCount(0);
    private static final CallMatcher EMPTY_LENGTH =
      CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER, "isEmpty").parameterCount(0);
    private static final CallMatcher SET_LENGTH =
      CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER, "setLength").parameterCount(1);


    private static final EquivalenceChecker ourEquivalence = EquivalenceChecker.getCanonicalPsiEquivalence();

    private final @NotNull TerminalBlock myTerminalBlock;
    private final @NotNull PsiVariable myBuilder;
    private final @NotNull PsiVariable myLoopVariable;
    private final @NotNull List<PsiExpression> myMainJoinParts;
    private final @NotNull List<PsiExpression> myPrefixJoinParts;
    private final @NotNull List<PsiExpression> mySuffixJoinParts;
    private final @NotNull List<PsiExpression> myDelimiterJoinParts;
    private final @Nullable PsiMethodCallExpression myBeforeLoopAppend;
    private final @Nullable PsiMethodCallExpression myAfterLoopAppend;

    @NotNull
    public TerminalBlock getTerminalBlock() {
      return myTerminalBlock;
    }

    @NotNull
    public PsiVariable getBuilder() {
      return myBuilder;
    }

    protected JoiningTerminal(@NotNull TerminalBlock block,
                              @NotNull PsiVariable targetBuilder,
                              @NotNull PsiVariable variable,
                              @NotNull List<PsiExpression> mainJoinParts,
                              @NotNull List<PsiExpression> prefixJoinParts,
                              @NotNull List<PsiExpression> suffixJoinParts,
                              @NotNull List<PsiExpression> delimiterJoinParts,
                              @Nullable PsiMethodCallExpression beforeLoopAppend,
                              @Nullable PsiMethodCallExpression afterLoopAppend) {
      myTerminalBlock = block;
      myBuilder = targetBuilder;
      myLoopVariable = variable;
      myMainJoinParts = mainJoinParts;
      myPrefixJoinParts = prefixJoinParts;
      mySuffixJoinParts = suffixJoinParts;
      myDelimiterJoinParts = delimiterJoinParts;
      myBeforeLoopAppend = beforeLoopAppend;
      myAfterLoopAppend = afterLoopAppend;
    }

    void cleanUp(@NotNull PsiLocalVariable target) {
      replaceInitializer(target);
      replaceUsages(target);
    }

    void preCleanUp(CommentTracker ct) {
      cleanUpCall(ct, myBeforeLoopAppend);
      cleanUpCall(ct, myAfterLoopAppend);
    }

    @NotNull
    String generateStreamCode(CommentTracker ct) {
      return myTerminalBlock.generate(ct) + generateIntermediate(ct) + generateTerminal(ct);
    }

    private static void replaceInitializer(@NotNull PsiLocalVariable target) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(target.getProject());
      target.getTypeElement().replace(factory.createTypeElementFromText(CommonClassNames.JAVA_LANG_STRING, target));
      PsiExpression initializer = target.getInitializer();
      String initialText = ConstructionUtils.getStringBuilderInitializerText(initializer);
      if (initialText != null) {
        initializer.replace(factory.createExpressionFromText("\"\"", target));
      }
    }

    private static boolean canBeMadeNonFinal(@NotNull PsiLocalVariable variable, @NotNull PsiStatement sourceStatement) {
      NavigatablePsiElement loopBound = PsiTreeUtil.getParentOfType(sourceStatement, PsiMember.class, PsiLambdaExpression.class);

      Predicate<PsiReference> referenceBoundPredicate;
      if (sourceStatement instanceof PsiLoopStatement) {
        referenceBoundPredicate =
          (reference) -> PsiTreeUtil.getParentOfType(reference.getElement(), PsiMember.class, PsiLambdaExpression.class) == loopBound;
      }
      else {
        referenceBoundPredicate = (reference) -> {
          PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(reference.getElement(), PsiLambdaExpression.class);
          return PsiTreeUtil.getParentOfType(lambda, PsiMember.class, PsiLambdaExpression.class) == loopBound;
        };
      }
      return ReferencesSearch.search(variable).allMatch(referenceBoundPredicate) && FinalUtils.canBeFinal(variable);
    }

    String generateTerminal(CommentTracker ct) {
      final String collectArguments;
      if (myDelimiterJoinParts.isEmpty() && myPrefixJoinParts.isEmpty() && mySuffixJoinParts.isEmpty()) {
        collectArguments = "";
      }
      else {
        String delimiter = myDelimiterJoinParts.isEmpty() ? "\"\"" : getExpressionText(ct, myDelimiterJoinParts);
        if (mySuffixJoinParts.isEmpty() && myPrefixJoinParts.isEmpty()) {
          collectArguments = delimiter;
        }
        else {
          String suffix = mySuffixJoinParts.isEmpty() ? "\"\"" : getExpressionText(ct, mySuffixJoinParts);
          String prefix = myPrefixJoinParts.isEmpty() ? "\"\"" : getExpressionText(ct, myPrefixJoinParts);
          collectArguments = delimiter + "," + prefix + "," + suffix;
        }
      }
      return ".collect(" + CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".joining(" + collectArguments + "))";
    }

    String generateIntermediate(CommentTracker ct) {
      if (TypeUtils.isJavaLangString(myLoopVariable.getType()) &&
          myMainJoinParts.size() == 1 &&
          myMainJoinParts.get(0) instanceof PsiReferenceExpression) {
        return "";
      }
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myLoopVariable.getProject());
      String joinTransformation = getExpressionText(ct, myMainJoinParts);
      PsiExpression mapping = elementFactory.createExpressionFromText(joinTransformation, myLoopVariable);
      return StreamRefactoringUtil.generateMapOperation(myLoopVariable, null, mapping);
    }


    private static void replaceUsages(PsiLocalVariable target) {
      Collection<PsiReference> usages = ReferencesSearch.search(target).findAll();
      for (PsiReference usage : usages) {
        PsiElement element = usage.getElement();
        if (element.isValid() && element instanceof PsiExpression) {
          PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier((PsiExpression)element);
          if (call != null && "toString".equals(call.getMethodExpression().getReferenceName())) {
            new CommentTracker().replaceAndRestoreComments(call, element);
          }
        }
      }
    }

    private static void cleanUpCall(CommentTracker ct, PsiMethodCallExpression call) {
      if (call != null) {
        if (call.getParent() instanceof PsiExpressionStatement) {
          ct.delete(call);
        }
        else {
          PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(call);
          PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
          if (nextCall != null && qualifier != null) {
            ct.replace(nextCall, qualifier);
          }
        }
      }
    }


    private static String getExpressionText(CommentTracker ct, @NotNull List<PsiExpression> joinParts) {
      StringJoiner joiner = new StringJoiner("+");
      int size = joinParts.size();
      for (int i = 0; i < joinParts.size(); i++) {
        PsiExpression joinPart = joinParts.get(i);
        String partText;
        if (i == 0) {
          boolean neighborIsString = false;
          if (joinParts.size() > 1) {
            PsiExpression second = joinParts.get(1);
            if (TypeUtils.isJavaLangString(second.getType())) {
              neighborIsString = true;
            }
          }
          partText = expressionToCharSequence(ct, joinPart, size, neighborIsString);
        }
        else {
          partText = expressionToCharSequence(ct, joinPart, size, true);
        }
        joiner.add(partText);
      }
      return joiner.toString();
    }

    @Nullable
    private static String computeConstant(@NotNull PsiExpression expression) {
      Object constantExpression = ExpressionUtils.computeConstantExpression(expression);
      if (constantExpression != null)  {
        return String.valueOf(constantExpression);
      } else {
        PsiLocalVariable variable = resolveLocalVariable(expression);
        if(variable == null) return null;
        PsiElement parent = variable.getParent();
        PsiExpression initializer = variable.getInitializer();
        if(parent == null || initializer == null) return null;
        if(!isEffectivelyFinal(variable, parent, null)) return null;
        Object initializerConstant = ExpressionUtils.computeConstantExpression(initializer);
        if(initializerConstant == null) return null;
        return String.valueOf(initializerConstant);
      }
    }

    @Nullable
    private static String computeConstant(@NotNull List<PsiExpression> joinParts) {
      StringBuilder sb = new StringBuilder();
      for (PsiExpression expression : joinParts) {
        String constant = computeConstant(expression);
        if(constant == null) return null;
        sb.append(constant);
      }
      return sb.toString();
    }

    @NotNull
    private static String expressionToCharSequence(CommentTracker ct,
                                                   @NotNull PsiExpression expression,
                                                   int expressionCount,
                                                   boolean neighborIsString) {
      PsiType type = expression.getType();
      if (expression instanceof PsiMethodCallExpression callExpression &&
          isCallOf(callExpression, CommonClassNames.JAVA_LANG_STRING, "charAt")) {
        PsiExpression qualifierExpression = callExpression.getMethodExpression().getQualifierExpression();
        PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
        if (expressions.length == 1) {
          PsiExpression first = expressions[0];
          if (qualifierExpression != null && ExpressionUtils.computeConstantExpression(first) instanceof Integer intValue) {
            String endIndex = String.valueOf(intValue + 1);
            return ct.text(qualifierExpression) + ".substring(" + ct.text(first) + "," + endIndex + ")";
          }
        }
      }
      if (!InheritanceUtil.isInheritor(type, JAVA_LANG_CHAR_SEQUENCE)) {
        if (!neighborIsString || (type instanceof PsiArrayType && ((PsiArrayType)type).getComponentType().equals(PsiTypes.charType()))) {
          PsiLiteralExpression literalExpression = tryCast(expression, PsiLiteralExpression.class);
          if (literalExpression != null) {
            Object value = literalExpression.getValue();
            if (value instanceof Character) {
              String text = ct.text(literalExpression);
              if ("'\"'".equals(text)) return "\"\\\"\"";
              return "\"" + text.substring(1, text.length() - 1) + "\"";
            }
          }
          return CommonClassNames.JAVA_LANG_STRING + ".valueOf(" + ct.text(expression) + ")";
        }
        if (ParenthesesUtils.getPrecedence(expression) > ParenthesesUtils.ADDITIVE_PRECEDENCE ||
            (expression.getType() instanceof PsiPrimitiveType &&
             ParenthesesUtils.getPrecedence(expression) == ParenthesesUtils.ADDITIVE_PRECEDENCE) ||
            expressionCount == 1) {
          return "(" + ct.text(expression) + ")";
        }
        return ct.text(expression);
      }
      String expressionText = ct.text(expression);
      if(ParenthesesUtils.getPrecedence(expression) > ParenthesesUtils.ADDITIVE_PRECEDENCE && expressionCount > 1) {
        expressionText = "(" + expressionText + ")";
      }
      return expressionText;
    }

    /**
     * from statement like sb.append(a).append(b) extracts sb
     */
    //
    @Nullable("when failed to extract")
    private static PsiVariable extractStringBuilder(@NotNull PsiStatement statement) {
      PsiExpressionStatement expressionStatement = tryCast(statement, PsiExpressionStatement.class);
      if (expressionStatement == null) return null;
      PsiMethodCallExpression methodCallExpression = tryCast(expressionStatement.getExpression(), PsiMethodCallExpression.class);
      if (methodCallExpression == null) return null;
      PsiMethodCallExpression currentExpression = methodCallExpression;
      while (APPEND.test(methodCallExpression)) {
        PsiExpression qualifierExpression = currentExpression.getMethodExpression().getQualifierExpression();
        PsiMethodCallExpression callerExpression = MethodCallUtils.getQualifierMethodCall(currentExpression);
        if (callerExpression == null) {
          PsiReferenceExpression refExpression = tryCast(qualifierExpression, PsiReferenceExpression.class);
          if(refExpression == null) return null;
          return tryCast(refExpression.resolve(), PsiVariable.class);
        }
        currentExpression = callerExpression;
      }
      return null;
    }

    @Nullable("when failed to extract")
    private static List<PsiExpression> extractJoinParts(@Nullable PsiExpression expression) {
      List<PsiExpression> joinParts = new SmartList<>();
      if (expression == null) return joinParts;
      return tryExtractJoinPart(expression, joinParts) ? joinParts : null;
    }

    /**
     * @param statements list of statements. Only appends expected inside.
     * @return list of joining expressions
     */
    @Nullable("when failed to extract")
    private static List<PsiExpression> extractJoinParts(@NotNull List<? extends PsiStatement> statements) {
      List<PsiExpression> joinParts = new ArrayList<>();
      for (PsiStatement statement : statements) {
        PsiExpressionStatement expressionStatement = tryCast(statement, PsiExpressionStatement.class);
        if (expressionStatement == null) return null;
        PsiExpression expression = expressionStatement.getExpression();
        if (!tryExtractJoinPart(expression, joinParts)) {
          return null;
        }
      }
      return joinParts;
    }

    /**
     * @param joinParts list to append joining parts into it
     * @return true on success
     */
    private static boolean tryExtractJoinPart(@NotNull PsiExpression expression,
                                              @NotNull List<PsiExpression> joinParts) {
      PsiMethodCallExpression methodCallExpression = tryCast(expression, PsiMethodCallExpression.class);
      if (methodCallExpression != null) {
        if (!APPEND.test(methodCallExpression)) return false;
        PsiExpression appendArgument = methodCallExpression.getArgumentList().getExpressions()[0];
        PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
        if (qualifierExpression == null) return false;
        PsiReferenceExpression referenceExpression = tryCast(qualifierExpression, PsiReferenceExpression.class);
        if (referenceExpression == null) { // assume expr like sb.append(a).append(b).append(c)
          if (!tryExtractJoinPart(qualifierExpression, joinParts)) return false;
        }
        if (!tryExtractConcatenationParts(appendArgument, joinParts)) return false;
        return true;
      }
      return false;
    }

    private static boolean tryExtractConcatenationParts(@NotNull PsiExpression expression, @NotNull List<PsiExpression> joinParts) {
      PsiType type = expression.getType();
      if (type == null) return false;
      if (!TypeUtils.isJavaLangString(type)) {
        joinParts.add(expression);
        return true;
      }
      PsiBinaryExpression binaryExpression = tryCast(expression, PsiBinaryExpression.class);
      if (binaryExpression != null) {
        if (binaryExpression.getOperationTokenType().equals(JavaTokenType.PLUS)) {
          PsiExpression lOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
          PsiExpression rOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
          if (lOperand == null || rOperand == null) return false;
          PsiType lOperandType = lOperand.getType();
          PsiType rOperandType = rOperand.getType();
          if (lOperandType == null || rOperandType == null) return false;
          if (!tryExtractConcatenationParts(lOperand, joinParts) ||
              !tryExtractConcatenationParts(rOperand, joinParts)) {
            return false;
          }
          return true;
        }
      }
      joinParts.add(expression);
      return true;
    }

    @Nullable("when failed to extract join parts from initializer statement")
    private static List<PsiExpression> extractStringBuilderInitializer(PsiExpression construction) {
      List<PsiExpression> joinParts = new ArrayList<>();
      PsiExpression expression = construction;
      PsiMethodCallExpression current = tryCast(construction, PsiMethodCallExpression.class);
      while(current != null) {
        if (APPEND.test(current)) {
          joinParts.add(current.getArgumentList().getExpressions()[0]);
        }
        else {
          return null;
        }
        expression = current.getMethodExpression().getQualifierExpression();
        current = MethodCallUtils.getQualifierMethodCall(current);
      }

      PsiNewExpression newExpression = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiNewExpression.class);
      if (newExpression == null) return null;
      final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
      if (classReference == null) return null;
      PsiClass aClass = tryCast(classReference.resolve(), PsiClass.class);
      if (aClass == null) return null;
      final String qualifiedName = aClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(qualifiedName) &&
          !CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(qualifiedName)) {
        return null;
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) return null;
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        if(arguments.length != 1) return null;
        final PsiExpression argument = arguments[0];
        final PsiType argumentType = argument.getType();
        if (!PsiTypes.intType().equals(argumentType)) {
          joinParts.add(argument);
        }
      }
      Collections.reverse(joinParts);
      return joinParts;
    }

    @Nullable
    private static AppendChain getCallAfterStatement(PsiStatement statement, PsiVariable receiver) {
      PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(statement);
      return getAppendCallExpression(receiver, next);
    }

    @Nullable
    private static AppendChain getCallBeforeStatement(@NotNull PsiStatement statement,
                                                      @NotNull PsiVariable receiver,
                                                      @NotNull List<PsiDeclarationStatement> declarationsToSkip) {
      PsiElement previous = PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement);
      PsiDeclarationStatement previousDeclaration = tryCast(previous, PsiDeclarationStatement.class);
      while (previousDeclaration != null && declarationsToSkip.contains(previousDeclaration)) {
        previous = PsiTreeUtil.skipWhitespacesAndCommentsBackward(previousDeclaration);
        previousDeclaration = tryCast(previous, PsiDeclarationStatement.class);
      }
      return getAppendCallExpression(receiver, previous);
    }

    @Contract("_, null -> null")
    @Nullable
    private static AppendChain getAppendCallExpression(@NotNull PsiVariable target,
                                                       @Nullable PsiElement element) {
      if (!(element instanceof PsiExpressionStatement exprStatement)) return null;
      if (!(exprStatement.getExpression() instanceof PsiMethodCallExpression topCall)) return null;
      PsiMethodCallExpression call = topCall;
      while (true) {
        if (!APPEND.test(call)) return null;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier instanceof PsiMethodCallExpression) {
          call = (PsiMethodCallExpression)qualifier;
        }
        else if (ExpressionUtils.isReferenceTo(qualifier, target)) {
          return new AppendChain(call, topCall);
        }
        else {
          return null;
        }
      }
    }

    private record AppendChain(@NotNull PsiMethodCallExpression first, @NotNull PsiMethodCallExpression outermost) {
    }

    private static boolean areReferencesAllowed(@NotNull List<PsiElement> refs,
                                                @NotNull Set<PsiMethodCallExpression> allowedReferencePlaces) {
      return StreamEx.of(refs).select(PsiExpression.class).allMatch(expression -> {
        PsiMethodCallExpression usage = ExpressionUtils.getCallForQualifier(expression);
        if (usage != null) {
          if (allowedReferencePlaces.contains(usage)) return true;
          PsiExpression[] usageArgs = usage.getArgumentList().getExpressions();
          String name = usage.getMethodExpression().getReferenceName();
          if (usageArgs.length == 0 && ("toString".equals(name) || "length".equals(name))) return true;
        }
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiPolyadicExpression &&
            ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.PLUS)) {
          return true;
        }
        if (parent instanceof PsiAssignmentExpression &&
            ((PsiAssignmentExpression)parent).getOperationTokenType().equals(JavaTokenType.PLUSEQ)) {
          return true;
        }
        return false;
      });
    }

    @Nullable
    private static PsiMethodCallExpression tryExtractCombinedToString(PsiMethodCallExpression afterLoopAppend,
                                                                      List<PsiElement> refs) {
      if (refs.size() == 1 && afterLoopAppend == null) { // case like return sb.append(postfix).toString();
        PsiMethodCallExpression usage = ExpressionUtils.getCallForQualifier((PsiExpression)refs.get(0));
        if (APPEND.test(usage)) {
          PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(usage);
          if (nextCall != null && "toString".equals(nextCall.getMethodExpression().getReferenceName())) {
            return usage;
          }
        }
      }
      return null;
    }


    private static boolean joinPartsAreEquivalent(@NotNull List<PsiExpression> joinParts1, @NotNull List<PsiExpression> joinParts2) {
      if (joinParts1.size() != joinParts2.size()) return false;
      for (int i = 0, size = joinParts1.size(); i < size; i++) {
        PsiExpression joinPart1 = joinParts1.get(i);
        PsiExpression joinPart2 = joinParts2.get(i);
        if (!ourEquivalence.expressionsAreEquivalent(joinPart1, joinPart2)) return false;
      }
      return true;
    }

    /**
     * Like: if(!sb.isEmpty()) => prefixLength == 0 or if(sb.length() > 2) => prefixLength == 2
     */
    @Nullable
    private static Integer extractConditionPrefixLength(@NotNull PsiExpression expression, PsiVariable targetBuilder) {
      Integer explicitLengthCondition = extractExplicitLengthCheck(expression, targetBuilder);
      if (explicitLengthCondition != null) return explicitLengthCondition;
      return extractEmptyLengthCheck(expression, targetBuilder);
    }

    @Nullable
    private static Integer extractEmptyLengthCheck(@NotNull PsiExpression expression, PsiVariable targetBuilder) {
      PsiMethodCallExpression maybeEmptyCall = tryCast(BoolUtils.getNegated(expression), PsiMethodCallExpression.class);
      if (!EMPTY_LENGTH.test(maybeEmptyCall)) return null; // extract call matcher
      if (!ExpressionUtils.isReferenceTo(maybeEmptyCall.getMethodExpression().getQualifierExpression(), targetBuilder)) return null;
      return 0;
    }

    @Nullable("when failed to extract length")
    private static Integer extractExplicitLengthCheck(@NotNull PsiExpression expression, PsiVariable targetBuilder) {
      PsiBinaryExpression condition = tryCast(expression, PsiBinaryExpression.class);
      if (condition == null) return null;
      PsiExpression rOperand = condition.getROperand();
      if (rOperand == null) return null;


      PsiExpression lOperand = condition.getLOperand();
      RelationType relation = DfaPsiUtil.getRelationByToken(condition.getOperationTokenType());
      if (relation == null) return null;
      int lSize = computeConstantIntExpression(lOperand);
      if (lSize >= 0) {
        return extractLength(rOperand, relation.getFlipped(), lSize, targetBuilder);
      }
      else {
        int rSize = computeConstantIntExpression(condition.getROperand());
        return rSize >= 0 ? extractLength(lOperand, relation, rSize, targetBuilder) : null;
      }
    }

    @Nullable
    private static Integer extractLength(PsiExpression rOperand,
                                         RelationType relation,
                                         int size,
                                         PsiVariable targetBuilder) {
      if (!isStringBuilderLengthCall(rOperand, targetBuilder)) return null;
      LongRangeSet rangeSet = LongRangeSet.point(size).fromRelation(relation);
      if (rangeSet == null || rangeSet.max() != Long.MAX_VALUE) return null;
      long min = rangeSet.min();
      return min > 0 ? (int)(min - 1) : null;
    }

    private static boolean isStringBuilderLengthCall(@NotNull PsiExpression expression, PsiVariable targetBuilder) {
      PsiMethodCallExpression methodCallExpression = tryCast(expression, PsiMethodCallExpression.class);
      return LENGTH.test(methodCallExpression) &&
             ExpressionUtils.isReferenceTo(methodCallExpression.getMethodExpression().getQualifierExpression(), targetBuilder);
    }

    /**
     * @param expression that is expected to be positive or 0
     * @return evaluated value or -1 when error
     */
    private static int computeConstantIntExpression(@NotNull PsiExpression expression) {
      Object constantExpression = ExpressionUtils.computeConstantExpression(expression);
      if (!(constantExpression instanceof Integer)) return -1;
      return (int)constantExpression;
    }

    private static class PrefixSuffixContext {
      private final @Nullable PsiMethodCallExpression myBeforeLoopStatement;
      private final @Nullable PsiMethodCallExpression myAfterLoopStatement;
      private final @NotNull List<PsiExpression> myPrefixJoinParts;
      private final @NotNull List<PsiExpression> mySuffixJoinParts;

      PrefixSuffixContext(@Nullable PsiMethodCallExpression beforeLoopStatement,
                                 @Nullable PsiMethodCallExpression afterLoopStatement,
                                 @NotNull List<PsiExpression> prefixJoinParts,
                                 @NotNull List<PsiExpression> suffixJoinParts) {
        myBeforeLoopStatement = beforeLoopStatement;
        myAfterLoopStatement = afterLoopStatement;
        myPrefixJoinParts = prefixJoinParts;
        mySuffixJoinParts = suffixJoinParts;
      }

      @Nullable
      public PsiMethodCallExpression getBeforeLoopStatement() {
        return myBeforeLoopStatement;
      }

      @Nullable
      public PsiMethodCallExpression getAfterLoopStatement() {
        return myAfterLoopStatement;
      }

      @NotNull
      public List<PsiExpression> getPrefixJoinParts() {
        return myPrefixJoinParts;
      }

      @NotNull
      public List<PsiExpression> getSuffixJoinParts() {
        return mySuffixJoinParts;
      }

      /**
       * @param finalAppendPredecessor        - statement, after and which expected suffix append (loop statement generally)
       * @param firstAppendSuccessor - statement before which expected prefix append and possibly some declarations used in loop
       * @param targetBuilder - string builder used
       * @param possibleVariablesBeforeLoop - variable, which declarations that can be before loop
       * @return prefix and suffix data
       */
      @Nullable
      static PrefixSuffixContext extractAndVerifyRefs(@NotNull PsiStatement finalAppendPredecessor,
                                                      @NotNull PsiStatement firstAppendSuccessor,
                                                      @NotNull PsiVariable targetBuilder,
                                                      @NotNull TerminalBlock terminalBlock,
                                                      @NotNull List<PsiLocalVariable> possibleVariablesBeforeLoop,
                                                      @NotNull Set<PsiMethodCallExpression> allowedReferencePlaces) {
        AppendChain afterLoopAppend = getCallAfterStatement(finalAppendPredecessor, targetBuilder);

        List<PsiDeclarationStatement> declarations = getDeclarations(possibleVariablesBeforeLoop);
        if(declarations == null) return null;
        AppendChain beforeLoopAppend = getCallBeforeStatement(firstAppendSuccessor, targetBuilder, declarations);
        List<PsiExpression> builderStrInitializers = null;
        PsiMethodCallExpression afterLoopAppendCall = afterLoopAppend != null ? afterLoopAppend.outermost : null;
        PsiMethodCallExpression beforeLoopAppendCall = beforeLoopAppend != null ? beforeLoopAppend.outermost : null;
        if(targetBuilder instanceof PsiLocalVariable) {
          if(!canBeMadeNonFinal((PsiLocalVariable)targetBuilder, terminalBlock.getStreamSourceStatement())) return null;

          List<PsiElement> refs = StreamEx.of(ReferencesSearch.search(targetBuilder).findAll())
            .map(PsiReference::getElement)
            .remove(e -> PsiTreeUtil.isAncestor(targetBuilder, e, false) ||
                         PsiTreeUtil.isAncestor(terminalBlock.getStreamSourceStatement(), e, false))
            .toList();
          if (afterLoopAppend != null) {
            allowedReferencePlaces.add(afterLoopAppend.first);
          }
          if (beforeLoopAppend != null) {
            allowedReferencePlaces.add(beforeLoopAppend.first);
          }

          boolean allowed = areReferencesAllowed(refs, allowedReferencePlaces);
          if (!allowed) {
            PsiMethodCallExpression newAfterLoopAppend = tryExtractCombinedToString(afterLoopAppendCall, refs);
            if (newAfterLoopAppend == null) return null;
            afterLoopAppendCall = newAfterLoopAppend;
          }
          builderStrInitializers = extractStringBuilderInitializer(targetBuilder.getInitializer());
          if(builderStrInitializers == null) return null;
        }
        List<PsiExpression> prefixJoinParts = extractJoinParts(beforeLoopAppendCall);
        if (prefixJoinParts == null) return null;
        if(builderStrInitializers != null) {
          prefixJoinParts.addAll(0, builderStrInitializers);
        }
        if (afterLoopAppend != null && VariableAccessUtils.variableIsUsed(targetBuilder, afterLoopAppendCall.getArgumentList())) {
          return null;
        }
        List<PsiExpression> suffixJoinParts = extractJoinParts(afterLoopAppendCall);
        if (suffixJoinParts == null) return null;
        return new PrefixSuffixContext(beforeLoopAppendCall, afterLoopAppendCall, prefixJoinParts, suffixJoinParts);
      }

      /**
       * @return list of declaration statements or null if error
       */
      @Nullable("when failed to get declaration of any var")
      static List<PsiDeclarationStatement> getDeclarations(@NotNull List<? extends PsiLocalVariable> variables) {
        List<PsiDeclarationStatement> list = new ArrayList<>();
        for (PsiLocalVariable var : variables) {
          PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(var, PsiDeclarationStatement.class);
          if(declarationStatement == null) return null;
          list.add(declarationStatement);
        }
        return list;
      }
    }


    /**
     * Joining without delimiter, but maybe with prefix and suffix
     */
    private static class PlainJoiningTerminal extends JoiningTerminal {
      protected PlainJoiningTerminal(@NotNull PsiVariable targetBuilder,
                                     @NotNull PsiVariable variable,
                                     @NotNull List<PsiExpression> mainJoinParts,
                                     @NotNull PrefixSuffixContext prefixSuffixContext,
                                     @NotNull TerminalBlock block) {
        super(block, targetBuilder, variable, mainJoinParts, prefixSuffixContext.getPrefixJoinParts(),
              prefixSuffixContext.getSuffixJoinParts(), emptyList(), prefixSuffixContext.getBeforeLoopStatement(),
              prefixSuffixContext.getAfterLoopStatement());
      }

      @Nullable
      static PlainJoiningTerminal extractPlainJoining(@NotNull TerminalBlock terminalBlock, @Nullable List<PsiVariable> nonFinalVariables) {
        if (nonFinalVariables != null && !nonFinalVariables.isEmpty()) return null;
        List<PsiStatement> statements = Arrays.asList(terminalBlock.getStatements());
        List<PsiExpression> mainJoinParts = extractJoinParts(statements);
        if (mainJoinParts == null || mainJoinParts.isEmpty()) return null;
        if (statements.isEmpty()) return null;
        PsiVariable targetBuilder = extractStringBuilder(statements.get(0));
        if (targetBuilder == null) return null;
        PsiStatement loop = terminalBlock.getStreamSourceStatement();
        PrefixSuffixContext context =
          PrefixSuffixContext.extractAndVerifyRefs(loop, loop, targetBuilder, terminalBlock, emptyList(), new HashSet<>(emptyList()));
        if (context == null) return null;
        return new PlainJoiningTerminal(targetBuilder, terminalBlock.getVariable(), mainJoinParts, context, terminalBlock);
      }
    }

    /**
     * if(sb.length() > prefixLength) sb.append(",");
     */
    private static class LengthBasedJoiningTerminal extends JoiningTerminal {

      protected LengthBasedJoiningTerminal(@NotNull PsiVariable targetBuilder,
                                           @NotNull PsiVariable variable,
                                           @NotNull List<PsiExpression> mainJoinParts,
                                           @NotNull PrefixSuffixContext prefixSuffixContext,
                                           @NotNull List<PsiExpression> delimiter,
                                           @NotNull TerminalBlock block) {
        super(block, targetBuilder, variable, mainJoinParts, prefixSuffixContext.getPrefixJoinParts(),
              prefixSuffixContext.getSuffixJoinParts(), delimiter, prefixSuffixContext.getBeforeLoopStatement(),
              prefixSuffixContext.getAfterLoopStatement());
      }

      @Nullable
      static LengthBasedJoiningTerminal extractLengthBasedTerminal(@NotNull TerminalBlock terminalBlock,
                                                                   @Nullable List<PsiVariable> nonFinalVariables) {
        if (nonFinalVariables != null && !nonFinalVariables.isEmpty()) return null;
        List<PsiStatement> statements = List.of(terminalBlock.getStatements());
        if (statements.size() < 2) return null;
        PsiIfStatement ifStatement = tryCast(statements.get(0), PsiIfStatement.class);
        if (ifStatement == null) return null;
        PsiExpression condition = ifStatement.getCondition();
        if (condition == null || ifStatement.getElseBranch() != null) return null;
        List<PsiExpression> delimiter = extractDelimiter(ifStatement);
        if (delimiter == null) return null;
        List<PsiStatement> withoutCondition = statements.subList(1, statements.size());
        PsiVariable targetBuilder = extractStringBuilder(withoutCondition.get(0));
        if(!(targetBuilder instanceof PsiLocalVariable)) return null;
        Integer conditionPrefixLength = extractConditionPrefixLength(condition, targetBuilder);
        if (conditionPrefixLength == null) return null;

        List<PsiExpression> mainJoinParts = extractJoinParts(withoutCondition);
        if (mainJoinParts == null) return null;
        PsiStatement loop = terminalBlock.getStreamSourceStatement();
        PrefixSuffixContext context =
          PrefixSuffixContext.extractAndVerifyRefs(loop, loop, targetBuilder, terminalBlock, emptyList(), new HashSet<>(emptyList()));
        if (context == null) return null;
        String prefix = computeConstant(context.getPrefixJoinParts());
        if (prefix == null || prefix.length() != conditionPrefixLength) return null;
        return new LengthBasedJoiningTerminal(targetBuilder, terminalBlock.getVariable(), mainJoinParts, context, delimiter, terminalBlock);
      }

      @Nullable
      private static List<PsiExpression> extractDelimiter(PsiIfStatement ifStatement) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        if (thenBranch == null) return null;
        List<PsiStatement> delimiterAppendStatements = Arrays.asList(ControlFlowUtils.unwrapBlock(thenBranch));
        List<PsiExpression> delimiterJoinParts = extractJoinParts(delimiterAppendStatements);
        if (delimiterJoinParts == null) return null;
        if(computeConstant(delimiterJoinParts) == null) return null;
        return delimiterJoinParts;
      }
    }

    /**
     * if(first) sb.append(mainPart) else sb.append(delimiter).append(",");
     */
    private static class BoolFlagJoiningTerminal extends JoiningTerminal {

      private final @NotNull PsiVariable myBoolVariable;

      protected BoolFlagJoiningTerminal(@NotNull PsiVariable targetBuilder,
                                        @NotNull PsiVariable variable,
                                        @NotNull List<PsiExpression> mainJoinParts,
                                        @NotNull PrefixSuffixContext prefixSuffixContext,
                                        @NotNull List<PsiExpression> delimiter,
                                        @NotNull PsiVariable boolVariable,
                                        @NotNull TerminalBlock block) {
        super(block, targetBuilder, variable, mainJoinParts, prefixSuffixContext.getPrefixJoinParts(),
              prefixSuffixContext.getSuffixJoinParts(), delimiter, prefixSuffixContext.getBeforeLoopStatement(),
              prefixSuffixContext.getAfterLoopStatement());
        this.myBoolVariable = boolVariable;
      }

      @Override
      void preCleanUp(CommentTracker ct) {
        super.preCleanUp(ct);
        ct.delete(myBoolVariable);
      }

      @Nullable
      static JoiningTerminal extractBoolFlagTerminal(@NotNull TerminalBlock terminalBlock,
                                                      @Nullable List<PsiVariable> nonFinalVariables) {
        if (nonFinalVariables != null && nonFinalVariables.size() != 1) return null;
        SpecialFirstIterationLoop specialFirstIterationLoop = SpecialFirstIterationLoop.BoolFlagLoop.extract(terminalBlock);
        if (specialFirstIterationLoop == null) return null;
        PsiLocalVariable boolVar = specialFirstIterationLoop.getVariable();
        if (boolVar == null) return null;
        if (nonFinalVariables != null && !nonFinalVariables.get(0).equals(boolVar)) return null;
        List<? extends PsiStatement> firstIterationStatements = specialFirstIterationLoop.getFirstIterationStatements();
        List<? extends PsiStatement> otherIterationStatements = specialFirstIterationLoop.getOtherIterationStatements();
        if (firstIterationStatements.isEmpty() || otherIterationStatements.isEmpty()) return null;

        List<PsiExpression> firstIterationJoinParts = extractJoinParts(firstIterationStatements);
        List<PsiExpression> otherIterationJoinParts = extractJoinParts(otherIterationStatements);

        if (firstIterationJoinParts == null || otherIterationJoinParts == null) return null;
        JoinData joinData = JoinData.extractLeftDelimiter(otherIterationJoinParts);
        if (!joinPartsAreEquivalent(joinData.getMainJoinParts(), firstIterationJoinParts)) return null;


        PsiVariable targetBuilder = extractStringBuilder(firstIterationStatements.get(0));
        if (targetBuilder == null) return null;
        PsiStatement loop = terminalBlock.getStreamSourceStatement();
        PrefixSuffixContext context =
          PrefixSuffixContext.extractAndVerifyRefs(loop, loop, targetBuilder, terminalBlock, singletonList(boolVar),
                                                   new HashSet<>(emptyList()));
        if (context == null) return null;

        return new BoolFlagJoiningTerminal(targetBuilder, terminalBlock.getVariable(), firstIterationJoinParts, context,
                                           joinData.getDelimiterJoinParts(), boolVar, terminalBlock);
      }
    }

    /**
     * for() ...
     * if(sb.length() > prefixLength) sb.seLength(sb.length() - delimiterSize)
     */
    private static class LengthTruncateJoiningTerminal extends JoiningTerminal {
      private final @NotNull PsiIfStatement myTruncateIfStatement;

      protected LengthTruncateJoiningTerminal(@NotNull PsiVariable targetBuilder,
                                              @NotNull PsiVariable variable,
                                              @NotNull List<PsiExpression> mainJoinParts,
                                              @NotNull PrefixSuffixContext prefixSuffixContext,
                                              @NotNull List<PsiExpression> delimiter,
                                              @NotNull PsiIfStatement truncateIfStatement,
                                              @NotNull TerminalBlock block) {
        super(block, targetBuilder, variable, mainJoinParts, prefixSuffixContext.getPrefixJoinParts(),
              prefixSuffixContext.getSuffixJoinParts(), delimiter, prefixSuffixContext.getBeforeLoopStatement(),
              prefixSuffixContext.getAfterLoopStatement());
        myTruncateIfStatement = truncateIfStatement;
      }

      @Override
      void preCleanUp(CommentTracker ct) {
        super.preCleanUp(ct);
        ct.delete(myTruncateIfStatement);
      }

      @Nullable
      static LengthTruncateJoiningTerminal extractLengthTruncateTerminal(@NotNull TerminalBlock terminalBlock,
                                                                         @Nullable List<PsiVariable> nonFinalVariables) {
        if (nonFinalVariables != null && !nonFinalVariables.isEmpty()) return null;
        List<PsiStatement> statements = Arrays.asList(terminalBlock.getStatements());
        if (statements.size() < 1) return null;
        PsiVariable targetBuilder = extractStringBuilder(statements.get(0));
        if(!(targetBuilder instanceof PsiLocalVariable)) return null;
        List<PsiExpression> joinParts = extractJoinParts(statements);
        if (joinParts == null) return null;
        JoinData joinData = JoinData.extractRightDelimiter(joinParts);
        List<PsiExpression> mainJoinParts = joinData.getMainJoinParts();
        List<PsiExpression> delimiterJoinParts = joinData.getDelimiterJoinParts();

        PsiStatement loop = terminalBlock.getStreamSourceStatement();

        PsiIfStatement ifStatement = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(loop), PsiIfStatement.class);
        if(ifStatement == null) return null;
        PsiExpression condition = ifStatement.getCondition();
        if (condition == null) return null;
        Integer conditionPrefixLength = extractConditionPrefixLength(condition, targetBuilder);
        if (conditionPrefixLength == null) return null;


        PsiMethodCallExpression truncateCall = extractTruncateCall(targetBuilder, ifStatement);
        if (truncateCall == null) return null;

        Integer truncateSize = tryExtractTruncationSize(targetBuilder, truncateCall);
        String delimiter = joinData.getDelimiter();
        if(delimiter == null) return null;
        if (truncateSize == null || truncateSize != delimiter.length()) return null;

        PrefixSuffixContext context =
          PrefixSuffixContext
            .extractAndVerifyRefs(ifStatement, loop, targetBuilder, terminalBlock, emptyList(), new HashSet<>(singletonList(truncateCall)));
        if (context == null) return null;


        String prefix = computeConstant(context.getPrefixJoinParts());
        if (prefix == null || prefix.length() != conditionPrefixLength) return null;
        PsiVariable loopVariable = terminalBlock.getVariable();
        return new LengthTruncateJoiningTerminal(targetBuilder, loopVariable, mainJoinParts, context, delimiterJoinParts, ifStatement,
                                                 terminalBlock);
      }

      @Nullable
      private static PsiMethodCallExpression extractTruncateCall(@NotNull PsiVariable targetBuilder, @NotNull PsiIfStatement ifStatement) {
        if (ifStatement.getElseBranch() != null) return null;
        PsiStatement block = ifStatement.getThenBranch();
        PsiStatement[] thenBranch = ControlFlowUtils.unwrapBlock(block);
        if (thenBranch.length != 1) return null;
        PsiExpressionStatement expressionStatement = tryCast(thenBranch[0], PsiExpressionStatement.class);
        if(expressionStatement == null) return null;
        PsiMethodCallExpression call = tryCast(expressionStatement.getExpression(), PsiMethodCallExpression.class);
        if (!SET_LENGTH.test(call)) return null;
        PsiLocalVariable localVariable = resolveLocalVariable(call.getMethodExpression().getQualifierExpression());

        if (!targetBuilder.equals(localVariable)) return null;
        return call;
      }

      @Nullable
      private static Integer tryExtractTruncationSize(@NotNull PsiVariable targetBuilder,
                                                      @NotNull PsiMethodCallExpression truncateCall) {
        PsiExpression[] expressions = truncateCall.getArgumentList().getExpressions();
        if (expressions.length == 0) return null;
        PsiExpression parameter = expressions[0];
        if (parameter == null) return null;
        PsiBinaryExpression binaryExpression = tryCast(parameter, PsiBinaryExpression.class);
        if (binaryExpression == null || !binaryExpression.getOperationTokenType().equals(JavaTokenType.MINUS)) return null;
        PsiExpression lOperand = binaryExpression.getLOperand();
        PsiExpression rOperand = binaryExpression.getROperand();
        if (rOperand == null) return null;
        Object constantExpression = ExpressionUtils.computeConstantExpression(rOperand);
        if (!(constantExpression instanceof Integer)) return null;
        int truncationSize = (int)constantExpression;

        PsiMethodCallExpression lengthCall = tryCast(lOperand, PsiMethodCallExpression.class);
        if (!LENGTH.test(lengthCall)) return null;
        PsiLocalVariable variable = resolveLocalVariable(lengthCall.getMethodExpression().getQualifierExpression());
        if (variable == null || !variable.equals(targetBuilder)) return null;
        return truncationSize;
      }
    }

    /**
     * String delimiter = "";
     * for() {
     *   sb.append(mainPart).append(delimiter);
     *   delimiter = ",";
     * }
     */
    private static class DelimiterRewriteJoiningTerminal extends JoiningTerminal {
      private final @NotNull PsiVariable myDelimiterVariable;

      protected DelimiterRewriteJoiningTerminal(@NotNull PsiVariable targetBuilder,
                                                @NotNull PsiVariable variable,
                                                @NotNull List<PsiExpression> mainJoinParts,
                                                @NotNull PrefixSuffixContext prefixSuffixContext,
                                                @NotNull List<PsiExpression> delimiter,
                                                @NotNull PsiVariable delimiterVariable,
                                                @NotNull TerminalBlock block) {
        super(block, targetBuilder, variable, mainJoinParts, prefixSuffixContext.getPrefixJoinParts(),
              prefixSuffixContext.getSuffixJoinParts(), delimiter, prefixSuffixContext.getBeforeLoopStatement(),
              prefixSuffixContext.getAfterLoopStatement());
        myDelimiterVariable = delimiterVariable;
      }

      @Override
      void preCleanUp(CommentTracker ct) {
        super.preCleanUp(ct);
        ct.delete(myDelimiterVariable);
      }

      @Nullable
      static DelimiterRewriteJoiningTerminal extractDelimiterRewritingTerminal(@NotNull TerminalBlock terminalBlock,
                                                                               @Nullable List<PsiVariable> nonFinalVariables) {
        if (nonFinalVariables != null && nonFinalVariables.size() != 1) return null;
        List<PsiStatement> statements = List.of(terminalBlock.getStatements());
        if (statements.size() < 2) return null;
        // TODO maybe not just last, but check if delimiter not used after assignment?
        PsiAssignmentExpression assignment = extractAssignment(statements.get(statements.size() - 1));
        if (assignment == null) return null;
        PsiLocalVariable delimiterVar = extractDelimiterVar(assignment);
        if (delimiterVar == null) return null;
        PsiExpression delimiter = extractDelimiter(assignment);
        if (delimiter == null) return null;
        List<PsiStatement> mainStatements = statements.subList(0, statements.size() - 1);
        List<PsiExpression> joinParts = extractJoinParts(mainStatements);
        if (joinParts == null || joinParts.isEmpty()) return null;

        if (isSeparator(delimiterVar, joinParts.get(0))) return null;
        joinParts.remove(0);
        if (ReferencesSearch.search(delimiterVar, new LocalSearchScope(terminalBlock.getStatements())).findAll().size() != 2) return null;

        PsiVariable targetBuilder = extractStringBuilder(mainStatements.get(0));
        if (targetBuilder == null) return null;

        PsiStatement loop = terminalBlock.getStreamSourceStatement();
        PrefixSuffixContext context =
          PrefixSuffixContext.extractAndVerifyRefs(loop, loop, targetBuilder, terminalBlock, singletonList(delimiterVar),
                                                   new HashSet<>(emptyList()));
        if (context == null) return null;
        PsiVariable variable = terminalBlock.getVariable();
        return new DelimiterRewriteJoiningTerminal(targetBuilder, variable, joinParts, context, singletonList(delimiter), delimiterVar,
                                                   terminalBlock);
      }

      private static boolean isSeparator(PsiLocalVariable delimiterVar, PsiExpression joinPart) {
        PsiLocalVariable maybeDelimiter = resolveLocalVariable(joinPart);
        if(maybeDelimiter == null || !maybeDelimiter.equals(delimiterVar)) return true;
        return false;
      }

      @Nullable
      private static PsiExpression extractDelimiter(@NotNull PsiAssignmentExpression assignmentExpression) {
        PsiExpression expression = assignmentExpression.getRExpression();
        if (expression == null) return null;
        Object constantExpression = ExpressionUtils.computeConstantExpression(expression);
        if (!(constantExpression instanceof String)) return null;
        return expression;
      }

      @Nullable
      private static PsiLocalVariable extractDelimiterVar(@NotNull PsiAssignmentExpression assignmentExpression) {
        PsiLocalVariable delimiterVar = resolveLocalVariable(assignmentExpression.getLExpression());
        if (delimiterVar == null) return null;
        PsiType delimiterVarType = delimiterVar.getType();
        if (!delimiterVarType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return null;
        PsiExpression initializer = delimiterVar.getInitializer();
        if (initializer == null) return null;
        Object constantExpression = ExpressionUtils.computeConstantExpression(initializer);
        if (!"".equals(constantExpression)) return null;
        return delimiterVar;
      }

      @Nullable
      private static PsiAssignmentExpression extractAssignment(@NotNull PsiStatement last) {
        PsiExpressionStatement expressionStatement = tryCast(last, PsiExpressionStatement.class);
        if (expressionStatement == null) return null;
        PsiAssignmentExpression assignment = tryCast(expressionStatement.getExpression(), PsiAssignmentExpression.class);
        if (assignment == null) return null;
        return assignment;
      }
    }

    /**
     * if(i > 0) append(",");
     */
    private static class IndexBasedJoiningTerminal extends JoiningTerminal {

      protected IndexBasedJoiningTerminal(@NotNull PsiVariable targetBuilder,
                                          @NotNull PsiVariable variable,
                                          @NotNull List<PsiExpression> mainJoinParts,
                                          @NotNull PrefixSuffixContext prefixSuffixContext,
                                          @NotNull List<PsiExpression> delimiter,
                                          @NotNull TerminalBlock block) {
        super(block, targetBuilder, variable, mainJoinParts, prefixSuffixContext.getPrefixJoinParts(),
              prefixSuffixContext.getSuffixJoinParts(), delimiter, prefixSuffixContext.getBeforeLoopStatement(),
              prefixSuffixContext.getAfterLoopStatement());
      }

      @Nullable
      static JoiningTerminal extractIndexBasedTerminal(@NotNull TerminalBlock terminalBlock,
                                                       @Nullable List<PsiVariable> nonFinalVariables) {
        if (nonFinalVariables != null && !nonFinalVariables.isEmpty()) return null;
        StreamApiMigrationInspection.CountingLoopSource countingLoopSource =
          terminalBlock.getLastOperation(StreamApiMigrationInspection.CountingLoopSource.class);
        if (countingLoopSource == null) return null;
        SpecialFirstIterationLoop specialFirstIterationLoop =
          SpecialFirstIterationLoop.IndexBasedLoop.extract(terminalBlock, countingLoopSource);
        if (specialFirstIterationLoop == null) return null;
        List<? extends PsiStatement> firstIterationStatements = specialFirstIterationLoop.getFirstIterationStatements();
        List<? extends PsiStatement> otherIterationStatements = specialFirstIterationLoop.getOtherIterationStatements();
        if (firstIterationStatements.isEmpty() || otherIterationStatements.isEmpty()) return null;

        int additionalPrefix = 0;
        while (additionalPrefix < firstIterationStatements.size()) {
          PsiStatement statement = firstIterationStatements.get(additionalPrefix);
          if (additionalPrefix >= otherIterationStatements.size() || statement != otherIterationStatements.get(additionalPrefix)) {
            break;
          }
          if (statement instanceof PsiExpressionStatement &&
              JoiningTerminal.tryExtractJoinPart(((PsiExpressionStatement)statement).getExpression(), new ArrayList<>())) {
            break;
          }
          additionalPrefix++;
        }
        if (additionalPrefix > 0) {
          TerminalBlock newBlock =
            TerminalBlock.fromStatements(countingLoopSource, firstIterationStatements.toArray(PsiStatement.EMPTY_ARRAY));
          int leftOver = firstIterationStatements.size() - additionalPrefix;
          while (newBlock != null && newBlock.getStatements().length < leftOver) {
            newBlock = newBlock.withoutLastOperation();
          }
          if (newBlock != null && newBlock.getStatements().length == leftOver) {
            terminalBlock = newBlock;
            firstIterationStatements = firstIterationStatements.subList(additionalPrefix, firstIterationStatements.size());
            otherIterationStatements = otherIterationStatements.subList(additionalPrefix, otherIterationStatements.size());
          }
        }

        List<PsiExpression> firstIterationJoinParts = extractJoinParts(firstIterationStatements);
        List<PsiExpression> otherIterationJoinParts = extractJoinParts(otherIterationStatements);

        if (firstIterationJoinParts == null || otherIterationJoinParts == null) return null;
        JoinData joinData = JoinData.extractLeftDelimiter(otherIterationJoinParts);
        if (!joinPartsAreEquivalent(joinData.getMainJoinParts(), firstIterationJoinParts)) return null;

        if (firstIterationStatements.isEmpty()) return null;
        PsiVariable targetBuilder = extractStringBuilder(firstIterationStatements.get(0));
        if (targetBuilder == null) return null;
        PsiStatement loop = terminalBlock.getStreamSourceStatement();
        PrefixSuffixContext context =
          PrefixSuffixContext.extractAndVerifyRefs(loop, loop, targetBuilder, terminalBlock, emptyList(), new HashSet<>(emptyList()));
        if (context == null) return null;
        return new IndexBasedJoiningTerminal(targetBuilder, terminalBlock.getVariable(), firstIterationJoinParts, context,
                                             joinData.getDelimiterJoinParts(), terminalBlock);
      }
    }


    /**
     * sb.append(elements[0]);
     * for(int i = 1; i < elements.length; i++) {
     * sb.append(delimiter).append(element[i]);
     * }
     */
    private static class CountedLoopJoiningTerminal extends JoiningTerminal {

      @NotNull private final StreamApiMigrationInspection.CountingLoopSource mySource;
      @NotNull private final PsiStatement myBeforeLoopAppend;

      protected CountedLoopJoiningTerminal(@NotNull PsiVariable targetBuilder,
                                           @NotNull PsiVariable variable,
                                           @NotNull List<PsiExpression> mainJoinParts,
                                           @NotNull PrefixSuffixContext prefixSuffixContext,
                                           @NotNull List<PsiExpression> delimiter,
                                           @NotNull TerminalBlock block,
                                           @NotNull StreamApiMigrationInspection.CountingLoopSource newSource,
                                           @NotNull PsiStatement beforeLoopAppendStatement) {
        super(block, targetBuilder, variable, mainJoinParts, prefixSuffixContext.getPrefixJoinParts(),
              prefixSuffixContext.getSuffixJoinParts(), delimiter, prefixSuffixContext.getBeforeLoopStatement(),
              prefixSuffixContext.getAfterLoopStatement());
        mySource = newSource;
        myBeforeLoopAppend = beforeLoopAppendStatement;
      }

      @Override
      void preCleanUp(CommentTracker ct) {
        super.preCleanUp(ct);
        ct.delete(myBeforeLoopAppend);
      }

      private static List<PsiExpression> copyReplacingVar(@NotNull List<PsiExpression> joinParts,
                                                          @NotNull PsiLocalVariable localVariable,
                                                          @NotNull PsiExpression replacement) {
        List<PsiExpression> copies = ContainerUtil.map(joinParts, expression -> (PsiExpression)expression.copy());
        for (PsiElement joinPart : copies) {
          ReferencesSearch.search(localVariable, new LocalSearchScope(joinPart)).forEach(reference -> {
            reference.getElement().replace(replacement);
          });
        }
        return copies;
      }

      @NotNull
      @Override
      String generateStreamCode(CommentTracker ct) {
        return mySource.createReplacement(ct) + generateIntermediate(ct) + generateTerminal(ct);
      }

      @Nullable
      static CountedLoopJoiningTerminal extractCountedLoopTerminal(@NotNull TerminalBlock terminalBlock,
                                                                   @Nullable List<PsiVariable> nonFinalVariables) {
        if (nonFinalVariables != null && !nonFinalVariables.isEmpty()) return null;
        StreamApiMigrationInspection.CountingLoopSource loopSource =
          terminalBlock.getLastOperation(StreamApiMigrationInspection.CountingLoopSource.class);
        if (loopSource == null) return null;
        PsiExpression initializer = loopSource.getVariable().getInitializer();
        Object constantExpression = ExpressionUtils.computeConstantExpression(initializer);
        if (!Integer.valueOf(1).equals(constantExpression)) return null;
        List<PsiStatement> statements = List.of(terminalBlock.getStatements());
        if (statements.isEmpty()) return null;
        List<PsiExpression> joinParts = extractJoinParts(statements);
        if (joinParts == null) return null;
        JoinData joinData = JoinData.extractLeftDelimiter(joinParts);
        List<PsiExpression> delimiterJoinParts = joinData.getDelimiterJoinParts();
        if (delimiterJoinParts.isEmpty()) return null;
        PsiStatement loop = terminalBlock.getStreamSourceStatement();
        PsiLocalVariable variable = tryCast(terminalBlock.getVariable(), PsiLocalVariable.class);
        if (variable == null) return null;
        PsiVariable targetBuilder = extractStringBuilder(statements.get(0));
        if (targetBuilder == null) return null;
        AppendChain beforeLoopAppend = JoiningTerminal.getCallBeforeStatement(loop, targetBuilder, emptyList());
        if (beforeLoopAppend == null) return null;
        PsiStatement beforeLoopAppendStatement = PsiTreeUtil.getParentOfType(beforeLoopAppend.outermost, PsiStatement.class);
        if (beforeLoopAppendStatement == null) return null;
        List<PsiExpression> firstIterationJoinParts = extractJoinParts(beforeLoopAppend.outermost);
        if (firstIterationJoinParts == null) return null;


        PsiElementFactory factory = JavaPsiFacade.getElementFactory(targetBuilder.getProject());
        PsiExpression expression = factory.createExpressionFromText("0", variable);
        List<PsiExpression> replacedMainJoinParts = copyReplacingVar(joinData.getMainJoinParts(), variable, expression);
        if (!joinPartsAreEquivalent(replacedMainJoinParts, firstIterationJoinParts)) return null;
        PrefixSuffixContext context =
          PrefixSuffixContext.extractAndVerifyRefs(loop, beforeLoopAppendStatement, targetBuilder, terminalBlock, emptyList(),
                                                   new HashSet<>(singletonList(beforeLoopAppend.outermost)));
        if (context == null) return null;

        StreamApiMigrationInspection.CountingLoopSource newSource = loopSource.withInitializer(expression);

        return new CountedLoopJoiningTerminal(targetBuilder, variable, joinData.getMainJoinParts(), context,
                                              delimiterJoinParts, terminalBlock, newSource, beforeLoopAppendStatement);
      }
    }

    private static class JoinData { // TODO confusing naming
      private final @Nullable String myDelimiter;
      private final @NotNull List<PsiExpression> myMainJoinParts;
      private final @NotNull List<PsiExpression> myDelimiterJoinParts;

      JoinData(@Nullable String delimiter,
                      @NotNull List<PsiExpression> mainJoinParts,
                      @NotNull List<PsiExpression> delimiterJoinParts) {
        myDelimiter = delimiter;
        myMainJoinParts = mainJoinParts;
        myDelimiterJoinParts = delimiterJoinParts;
      }

      @Nullable
      public String getDelimiter() {
        return myDelimiter;
      }

      @NotNull
      public List<PsiExpression> getMainJoinParts() {
        return myMainJoinParts;
      }

      @NotNull
      public List<PsiExpression> getDelimiterJoinParts() {
        return myDelimiterJoinParts;
      }

      @NotNull
      static JoinData extractLeftDelimiter(@NotNull List<PsiExpression> joinParts) {
        List<PsiExpression> delimiterJoinParts = new ArrayList<>();
        int firstNonConstant = -1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0, size = joinParts.size(); i < size; i++) {
          PsiExpression joinPart = joinParts.get(i);
          String constantExpression = computeConstant(joinPart);
          if (constantExpression == null) {
            firstNonConstant = i;
            break;
          }
          delimiterJoinParts.add(joinPart);
          sb.append(constantExpression);
        }
        String separator = sb.length() == 0 ? null : sb.toString();
        if (firstNonConstant != -1) {
          List<PsiExpression> mainJoinParts = joinParts.subList(firstNonConstant, joinParts.size());
          return new JoinData(separator, mainJoinParts, delimiterJoinParts);
        }
        return new JoinData(separator, emptyList(), delimiterJoinParts);
      }

      @NotNull
      static JoinData extractRightDelimiter(@NotNull List<PsiExpression> joinParts) {
        List<PsiExpression> delimiterJoinParts = new ArrayList<>();
        int firstNonConstant = -1;
        StringBuilder sb = new StringBuilder();
        for (int i = joinParts.size() - 1; i >= 0; i--) {
          PsiExpression joinPart = joinParts.get(i);
          String constantExpression = computeConstant(joinPart);
          if (constantExpression == null) {
            firstNonConstant = i;
            break;
          }
          sb.append(constantExpression);
          delimiterJoinParts.add(joinPart);
        }
        String separator = sb.length() == 0 ? null : sb.toString();
        if (firstNonConstant != -1) {
          List<PsiExpression> mainJoinParts = joinParts.subList(0, firstNonConstant + 1);
          return new JoinData(separator, mainJoinParts, delimiterJoinParts);
        }
        return new JoinData(separator, emptyList(), delimiterJoinParts);
      }
    }
  }
}
