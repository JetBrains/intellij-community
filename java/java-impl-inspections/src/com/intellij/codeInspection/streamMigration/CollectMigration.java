// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import static com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.isCallOf;
import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ControlFlowUtils.getInitializerUsageStatus;
import static com.siyeh.ig.psiutils.Java8MigrationUtils.MapCheckCondition.fromConditional;
import static com.siyeh.ig.psiutils.Java8MigrationUtils.extractLambdaCandidate;

class CollectMigration extends BaseStreamApiMigration {
  private static final Logger LOG = Logger.getInstance(CollectMigration.class);

  static final Map<String, String> INTERMEDIATE_STEPS = EntryStream.of(
    JAVA_UTIL_ARRAY_LIST, "",
    JAVA_UTIL_LINKED_LIST, "",
    JAVA_UTIL_HASH_SET, ".distinct()",
    JAVA_UTIL_LINKED_HASH_SET, ".distinct()",
    "java.util.TreeSet", ".distinct().sorted()"
  ).toMap();

  protected CollectMigration(boolean shouldWarn, String methodName) {
    super(shouldWarn, methodName);
  }

  @Nullable
  static PsiType getAddedElementType(PsiMethodCallExpression call) {
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiMethod method = call.resolveMethod();
    if(method == null) return null;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if(parameters.length != 1) return null;
    return resolveResult.getSubstitutor().substitute(parameters[0].getType());
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiStatement loopStatement = tb.getStreamSourceStatement();
    CollectTerminal terminal = extractCollectTerminal(tb, null);
    if (terminal == null) return null;
    CommentTracker ct = new CommentTracker();
    String stream = tb.generate(ct) + terminal.generateIntermediate(ct) + terminal.generateTerminal(ct, false);
    PsiElement toReplace = terminal.getElementToReplace();
    PsiElement result;
    if (toReplace != null) {
      result = ct.replace(toReplace, stream);
      terminal.cleanUp(ct);
      removeLoop(ct, loopStatement);
    }
    else {
      PsiVariable variable = terminal.getTargetVariable();
      LOG.assertTrue(variable != null);
      terminal.cleanUp(ct);
      result = replaceInitializer(loopStatement, variable, variable.getInitializer(), stream, terminal.getStatus(), ct);
    }
    return result;
  }

  private static boolean isHashMap(PsiLocalVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    LOG.assertTrue(initializer != null);
    PsiClass initializerClass = PsiUtil.resolveClassInClassTypeOnly(initializer.getType());
    PsiClass varClass = PsiUtil.resolveClassInClassTypeOnly(variable.getType());
    return initializerClass != null &&
           varClass != null &&
           JAVA_UTIL_HASH_MAP.equals(initializerClass.getQualifiedName()) &&
           JAVA_UTIL_MAP.equals(varClass.getQualifiedName()) &&
           !ConstructionUtils.isCustomizedEmptyCollectionInitializer(initializer);
  }

  @Nullable
  static PsiLocalVariable extractQualifierVariable(TerminalBlock tb, PsiMethodCallExpression call) {
    PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
    if (!(qualifierExpression instanceof PsiReferenceExpression)) return null;
    PsiLocalVariable variable = tryCast(((PsiReferenceExpression)qualifierExpression).resolve(), PsiLocalVariable.class);
    if (variable == null) return null;
    if (tb.getVariable() != variable && VariableAccessUtils.variableIsUsed(variable, call.getArgumentList())) return null;
    return variable;
  }

  @Nullable
  static CollectTerminal extractCollectTerminal(@NotNull TerminalBlock tb, @Nullable List<PsiVariable> nonFinalVariables) {
    GroupingTerminal groupingTerminal = GroupingTerminal.tryExtract(tb, nonFinalVariables);
    if(groupingTerminal != null) return groupingTerminal;
    if(nonFinalVariables != null && !nonFinalVariables.isEmpty()) {
      return null;
    }
    PsiMethodCallExpression call;
    call = tb.getSingleMethodCall();
    if (call == null) return null;
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (tb.dependsOn(qualifierExpression)) return null;

    List<BiFunction<TerminalBlock, PsiMethodCallExpression, CollectTerminal>> extractors = Arrays
      .asList(AddingTerminal::tryExtract, GroupingTerminal::tryExtractJava8Style, ToMapTerminal::tryExtract, AddingAllTerminal::tryExtractAddAll);

    CollectTerminal terminal = StreamEx.of(extractors).map(extractor -> extractor.apply(tb, call)).nonNull().findFirst().orElse(null);
    if (terminal != null) {
      if (terminal.getStatus() == ControlFlowUtils.InitializerUsageStatus.UNKNOWN) return null;
      terminal = includePostStatements(terminal, PsiTreeUtil.skipWhitespacesAndCommentsForward(tb.getStreamSourceStatement()));
    }
    return terminal;
  }

  @NotNull
  static CollectTerminal includePostStatements(@NotNull CollectTerminal terminal, @Nullable PsiElement nextElement) {
    if (nextElement == null) return terminal;
    List<BiFunction<@NotNull CollectTerminal, @NotNull PsiElement, @Nullable CollectTerminal>> wrappers =
      Arrays.asList(SortingTerminal::tryWrap, ToArrayTerminal::tryWrap, NewListTerminal::tryWrap, UnmodifiableTerminal::tryWrap);
    while (true) {
      CollectTerminal wrapped = null;
      for (BiFunction<@NotNull CollectTerminal, @NotNull PsiElement, @Nullable CollectTerminal> wrapper : wrappers) {
        wrapped = wrapper.apply(terminal, nextElement);
        if (wrapped != null) {
          terminal = wrapped;
          break;
        }
      }
      if (wrapped == null) {
        return terminal;
      }
      nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(nextElement);
      if (nextElement == null) {
        return terminal;
      }
    }
  }

  @Contract("null -> false")
  static boolean hasLambdaCompatibleEmptyInitializer(@Nullable PsiLocalVariable target) {
    return target != null &&
           ConstructionUtils.isEmptyCollectionInitializer(target.getInitializer()) &&
           LambdaGenerationUtil.canBeUncheckedLambda(target.getInitializer());
  }

  abstract static class CollectTerminal {
    private final @Nullable PsiLocalVariable myTargetVariable;
    private final InitializerUsageStatus myStatus;
    private final PsiStatement myLoop;

    protected CollectTerminal(@Nullable PsiLocalVariable variable, PsiStatement loop, InitializerUsageStatus status) {
      myTargetVariable = variable;
      myLoop = loop;
      myStatus = status;
    }

    @Nullable
    PsiElement getElementToReplace() { return null; }

    String getMethodName() { return "collect"; }

    @Nullable
    PsiLocalVariable getTargetVariable() { return myTargetVariable; }

    abstract String generateIntermediate(CommentTracker ct);

    StreamEx<? extends PsiExpression> targetReferences() {
      if (myTargetVariable == null) return StreamEx.empty();
      List<PsiElement> usedElements = usedElements().toList();
      PsiElement block = PsiUtil.getVariableCodeBlock(myTargetVariable, null);
      return StreamEx.of(VariableAccessUtils.getVariableReferences(myTargetVariable, block))
        .filter(ref -> !ContainerUtil
          .exists(usedElements, allowedUsage -> PsiTreeUtil.isAncestor(allowedUsage, ref, false)));
    }

    boolean isTargetReference(PsiExpression expression) {
      return ExpressionUtils.isReferenceTo(expression, getTargetVariable());
    }

    String getIntermediateStepsFromCollection() {
      PsiLocalVariable variable = getTargetVariable();
      if (variable == null) return null;
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null) return null;
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(initializer.getType());
      if (aClass == null) return null;
      return INTERMEDIATE_STEPS.get(aClass.getQualifiedName());
    }

    /**
     * Generate terminal stream call starting with '.' (e.g. {@code ".collect(java.util.stream.Collectors.toList())"}
     * @param ct comment tracker to use
     * @param strictMode if true, toList/toSet collectors will not be used to replace ArrayList/HashSet
     * @return generated call
     */
    abstract String generateTerminal(CommentTracker ct, boolean strictMode);

    StreamEx<PsiElement> usedElements() {
      return StreamEx.ofNullable(myLoop);
    }

    StreamEx<String> fusedElements() {
      return StreamEx.empty();
    }

    public InitializerUsageStatus getStatus() { return myStatus; }

    void cleanUp(CommentTracker ct) {}

    boolean isTrivial() {
      return generateIntermediate(new CommentTracker()).isEmpty();
    }
  }

  static class AddingTerminal extends CollectTerminal {
    final PsiType myTargetType;
    final PsiExpression myInitializer;
    final PsiVariable myElement;
    final PsiMethodCallExpression myAddCall;

    AddingTerminal(@NotNull PsiLocalVariable target,
                   PsiVariable element,
                   PsiMethodCallExpression addCall,
                   PsiStatement loop,
                   InitializerUsageStatus status) {
      super(target, loop, hasLambdaCompatibleEmptyInitializer(target) ? status : ControlFlowUtils.InitializerUsageStatus.UNKNOWN);
      myTargetType = target.getType();
      myInitializer = target.getInitializer();
      myElement = element;
      myAddCall = addCall;
    }

    AddingTerminal(@NotNull PsiType targetType,
                   PsiExpression initializer,
                   PsiVariable element,
                   PsiMethodCallExpression addCall) {
      super(null, null, ControlFlowUtils.InitializerUsageStatus.UNKNOWN);
      myTargetType = targetType;
      myInitializer = initializer;
      myElement = element;
      myAddCall = addCall;
    }

    PsiVariable getElementVariable() {
      return myElement;
    }

    PsiExpression getMapping() {
      return myAddCall.getArgumentList().getExpressions()[0];
    }

    @Override
    public String generateIntermediate(CommentTracker ct) {
      PsiType addedType = getAddedElementType(myAddCall);
      PsiExpression mapping = getMapping();
      if (addedType == null) addedType = mapping.getType();
      return StreamRefactoringUtil.generateMapOperation(myElement, addedType, ct.markUnchanged(mapping));
    }

    String generateCollector(CommentTracker ct, boolean strictMode) {
      return getCollectionCollector(ct, myInitializer, myTargetType, strictMode);
    }

    @Override
    public String generateTerminal(CommentTracker ct, boolean strictMode) {
      return ".collect(" + generateCollector(ct, strictMode) + ")";
    }

    @Nullable
    static AddingTerminal tryExtract(TerminalBlock tb, PsiMethodCallExpression call) {
      if (!isCallOf(call, JAVA_UTIL_COLLECTION, "add")) return null;
      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) return null;
      PsiExpression count = tb.getCountExpression();
      PsiLocalVariable variable = extractQualifierVariable(tb, call);
      if (variable != null) {
        InitializerUsageStatus status = getInitializerUsageStatus(variable, tb.getStreamSourceStatement());
        AddingTerminal terminal = new AddingTerminal(variable, tb.getVariable(), call, tb.getStreamSourceStatement(), status);
        if (count == null) return terminal;
        // like "list.add(x); if(list.size() >= limit) break;"
        if (CollectionUtils.isCollectionOrMapSize(count, qualifierExpression) &&
            InheritanceUtil.isInheritor(PsiUtil.resolveClassInClassTypeOnly(variable.getType()), JAVA_UTIL_LIST)) {
          return terminal;
        }
      }
      return null;
    }
  }

  @NotNull
  private static String getCollectionCollector(CommentTracker ct, PsiExpression initializer, PsiType type, boolean strictMode) {
    String collector;
    PsiType initializerType = initializer.getType();
    PsiClassType rawType = initializerType instanceof PsiClassType ? ((PsiClassType)initializerType).rawType() : null;
    PsiClassType rawVarType = type instanceof PsiClassType ? ((PsiClassType)type).rawType() : null;
    if (!strictMode && rawType != null && rawVarType != null &&
        rawType.equalsToText(JAVA_UTIL_ARRAY_LIST) &&
        (rawVarType.equalsToText(JAVA_UTIL_LIST) || rawVarType.equalsToText(JAVA_UTIL_COLLECTION)) &&
        !ConstructionUtils.isCustomizedEmptyCollectionInitializer(initializer)) {
      collector = "toList()";
    }
    else if (!strictMode && rawType != null && rawVarType != null &&
             rawType.equalsToText(JAVA_UTIL_HASH_SET) &&
             (rawVarType.equalsToText(JAVA_UTIL_SET) || rawVarType.equalsToText(JAVA_UTIL_COLLECTION)) &&
             !ConstructionUtils.isCustomizedEmptyCollectionInitializer(initializer)) {
      collector = "toSet()";
    }
    else {
      PsiExpression copy = JavaPsiFacade.getElementFactory(initializer.getProject())
        .createExpressionFromText(ct.text(initializer), initializer);
      if (copy instanceof PsiCallExpression && ConstructionUtils.isPrepopulatedCollectionInitializer(copy)) {
        PsiExpressionList argumentList = ((PsiCallExpression)copy).getArgumentList();
        if (argumentList != null) {
          PsiExpression arg = ArrayUtil.getFirstElement(argumentList.getExpressions());
          if (arg != null && !(arg.getType() instanceof PsiPrimitiveType)) {
            arg.delete();
          }
        }
      }
      collector = "toCollection(() -> " + copy.getText() + ")";
    }
    return JAVA_UTIL_STREAM_COLLECTORS + "." + collector;
  }

  static class AddingAllTerminal extends AddingTerminal {
    public static final CallMatcher COLLECTIONS_ADD_ALL =
      CallMatcher.staticCall(JAVA_UTIL_COLLECTIONS, "addAll").parameterTypes(JAVA_UTIL_COLLECTION, "T...");
    public static final CallMatcher COLLECTION_ADD_ALL =
      CallMatcher.instanceCall(JAVA_UTIL_COLLECTION, "addAll").parameterTypes(JAVA_UTIL_COLLECTION);
    private final PsiMethodCallExpression myAddAllCall;

    AddingAllTerminal(PsiLocalVariable target,
                      PsiVariable element,
                      PsiMethodCallExpression addAllCall,
                      PsiStatement loop,
                      InitializerUsageStatus status) {
      super(target, element, null, loop, status);
      myAddAllCall = addAllCall;
    }

    @Override
    public String generateIntermediate(CommentTracker ct) {
      String nestedStreamSource;
      if (COLLECTION_ADD_ALL.test(myAddAllCall)) {
        // result.addAll(c)
        PsiExpression collection = myAddAllCall.getArgumentList().getExpressions()[0];
        if (myElement.getType() instanceof PsiPrimitiveType) {
          String collectionVariableName = new VariableNameGenerator(myAddAllCall, VariableKind.PARAMETER)
            .byExpression(collection)
            .byName("c", "col")
            .generate(true);
          return ".mapToObj(" + myElement.getName() + "->" + collection.getText() + ")" +
                 ".flatMap(" + collectionVariableName + "->" + collectionVariableName + ".stream())";
        } else {
          nestedStreamSource = ParenthesesUtils.getText(
            collection, ParenthesesUtils.POSTFIX_PRECEDENCE) + ".stream()";
          String lambda = myElement.getName() + "->" + nestedStreamSource;
          return ".flatMap(" + lambda + ")";
        }
      }
      else {// Collections.addAll(result, ...)
        PsiType[] typeParameters = myAddAllCall.getMethodExpression().getTypeParameters();
        String generic = "";
        if (typeParameters.length == 1) {
          generic = "<" + typeParameters[0].getCanonicalText() + ">";
        }
        String method = MethodCallUtils.isVarArgCall(myAddAllCall) ? JAVA_UTIL_STREAM_STREAM + "." + generic + "of"
                                                                   : JAVA_UTIL_ARRAYS + "." + generic + "stream";
        nestedStreamSource = StreamEx.of(myAddAllCall.getArgumentList().getExpressions()).skip(1).map(ct::text)
          .joining(",", method + "(", ")");
        String lambda = myElement.getName() + "->" + nestedStreamSource;
        return myElement.getType() instanceof PsiPrimitiveType ?
               ".mapToObj(" + lambda + ").flatMap(" + JAVA_UTIL_FUNCTION_FUNCTION + ".identity())" :
               ".flatMap(" + lambda + ")";
      }
    }

    @Nullable
    static AddingAllTerminal tryExtractAddAll(TerminalBlock tb, PsiMethodCallExpression call) {
      if (tb.getCountExpression() != null) return null;
      if (COLLECTIONS_ADD_ALL.test(call)) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length < 2) return null;
        return createAddAll(tb, call, args[0]);
      }
      if (COLLECTION_ADD_ALL.test(call)) {
        return createAddAll(tb, call, call.getMethodExpression().getQualifierExpression());
      }

      return null;
    }

    private static @Nullable AddingAllTerminal createAddAll(TerminalBlock tb,
                                                            PsiMethodCallExpression call,
                                                            PsiExpression targetExpr) {
      PsiReferenceExpression collectionReference = tryCast(PsiUtil.skipParenthesizedExprDown(targetExpr), PsiReferenceExpression.class);
      if (collectionReference == null || tb.dependsOn(collectionReference)) return null;
      PsiLocalVariable target = tryCast(collectionReference.resolve(), PsiLocalVariable.class);
      if (target == null) return null;
      if (ContainerUtil.or(call.getArgumentList().getExpressions(), 
                           arg -> arg != targetExpr && VariableAccessUtils.variableIsUsed(target, arg))) {
        return null;
      }
      InitializerUsageStatus status = getInitializerUsageStatus(target, tb.getStreamSourceStatement());
      return new AddingAllTerminal(target, tb.getVariable(), call, tb.getStreamSourceStatement(), status);
    }
  }

  static class GroupingTerminal extends CollectTerminal {

    private final static CallMatcher LIST_ADD = CallMatcher.instanceCall(JAVA_UTIL_LIST, "add").parameterCount(1);
    private final static CallMatcher COMPUTE_IF_ABSENT = CallMatcher.instanceCall(JAVA_UTIL_MAP, "computeIfAbsent").parameterCount(2);

    private final AddingTerminal myDownstream;
    private final PsiExpression myKeyExpression;

    GroupingTerminal(@NotNull AddingTerminal downstream,
                     @NotNull PsiLocalVariable target,
                     @NotNull PsiExpression expression,
                     @NotNull InitializerUsageStatus status) {
      super(target, null, status);
      myDownstream = downstream;
      myKeyExpression = expression;
    }

    @Override
    public boolean isTrivial() {
      return false;
    }

    @Override
    String generateIntermediate(CommentTracker ct) {
      return myDownstream.myElement.getType() instanceof PsiPrimitiveType ? ".boxed()" : "";
    }

    @Override
    public String generateTerminal(CommentTracker ct, boolean strictMode) {
      String downstreamCollector = myDownstream.generateCollector(ct, strictMode);
      PsiVariable elementVariable = myDownstream.getElementVariable();
      if (!ExpressionUtils.isReferenceTo(myDownstream.getMapping(), myDownstream.getElementVariable())) {
        downstreamCollector = JAVA_UTIL_STREAM_COLLECTORS + ".mapping(" +
                              myDownstream.getElementVariable().getName() + "->" + ct.text(myDownstream.getMapping()) + "," +
                              downstreamCollector + ")";
      }
      StringBuilder builder = new StringBuilder();
      builder.append(".collect(" + JAVA_UTIL_STREAM_COLLECTORS + ".groupingBy(")
        .append(ct.lambdaText(elementVariable, myKeyExpression));
      PsiLocalVariable variable = Objects.requireNonNull(getTargetVariable());
      PsiExpression initializer = variable.getInitializer();
      LOG.assertTrue(initializer != null);
      if (!isHashMap(variable)) {
        builder.append(",()->").append(ct.text(initializer)).append(",").append(downstreamCollector);
      }
      else if (!(JAVA_UTIL_STREAM_COLLECTORS + "." + "toList()").equals(downstreamCollector)) {
        builder.append(",").append(downstreamCollector);
      }
      builder.append("))");
      return builder.toString();
    }

    @Nullable
    static GroupingTerminal tryExtract(@NotNull TerminalBlock tb, @Nullable List<PsiVariable> nonFinalVariables) {
      PsiStatement[] statements = tb.getStatements();
      if (statements.length == 1) {
        if(nonFinalVariables != null && !nonFinalVariables.isEmpty()) return null;
        PsiMethodCallExpression call = tb.getSingleExpression(PsiMethodCallExpression.class);
        return tryExtractJava8Style(tb, call);
      }
      if (statements.length == 2) {
        if(nonFinalVariables != null && !nonFinalVariables.isEmpty()) return null;
        return tryExtractWithIntermediateVariable(tb);
      }
      return tryExtractJava7Style(tb, statements, nonFinalVariables);
    }

    /*
      List<String> tmp = map.get(s.length());
      if(tmp == null) {
          tmp = new ArrayList<>();
          map.put(s.length(), tmp);
      }
      tmp.add(s);
     */
    @Nullable
    private static GroupingTerminal tryExtractJava7Style(@NotNull TerminalBlock terminalBlock,
                                                         PsiStatement @NotNull [] statements,
                                                         @Nullable List<PsiVariable> nonFinalVariables) {
      if(nonFinalVariables != null && nonFinalVariables.size() != 1) return null;
      if (statements.length != 3) return null;
      PsiIfStatement ifStatement = tryCast(statements[1], PsiIfStatement.class);
      if(ifStatement == null) return null;
      Java8MigrationUtils.MapCheckCondition condition = fromConditional(ifStatement, false);
      if(condition == null || !condition.hasVariable()) return null;
      PsiStatement existsBranch = ControlFlowUtils.stripBraces(condition.getExistsBranch(ifStatement.getThenBranch(), ifStatement.getElseBranch()));
      PsiStatement noneBranch = ControlFlowUtils.stripBraces(condition.getNoneBranch(ifStatement.getThenBranch(), ifStatement.getElseBranch()));
      if(existsBranch != null) return null;
      PsiExpression lambdaCandidate = extractLambdaCandidate(condition, noneBranch);
      if(lambdaCandidate == null) return null;

      PsiExpressionStatement additionToListStatement = tryCast(statements[2], PsiExpressionStatement.class);
      if (additionToListStatement == null) return null;

      PsiReferenceExpression valueReference = condition.getValueReference();
      if(valueReference == null) return null;
      PsiLocalVariable listVar = tryCast(valueReference.resolve(), PsiLocalVariable.class);
      if (nonFinalVariables != null && !nonFinalVariables.get(0).equals(listVar) ||
          listVar == null ||
          !InheritanceUtil.isInheritor(listVar.getType(), JAVA_UTIL_LIST)) {
        return null;
      }
      PsiLocalVariable mapVariable = ExpressionUtils.resolveLocalVariable(condition.getMapExpression());
      if(mapVariable == null) return null;
      PsiMethodCallExpression addCall = extractAddMethod(terminalBlock, additionToListStatement);
      if(addCall == null) return null;
      InitializerUsageStatus status = getInitializerUsageStatus(mapVariable, terminalBlock.getStreamSourceStatement());
      AddingTerminal adding = new AddingTerminal(listVar.getType(), lambdaCandidate, terminalBlock.getVariable(), addCall);
      return new GroupingTerminal(adding, mapVariable, condition.getKeyExpression(), status);
    }

    @Nullable
    private static PsiMethodCallExpression extractAddMethod(@NotNull TerminalBlock terminalBlock,
                                                            @NotNull PsiExpressionStatement additionToListStatement) {
      PsiMethodCallExpression additionToList = tryCast(additionToListStatement.getExpression(), PsiMethodCallExpression.class);
      if (!LIST_ADD.test(additionToList)) return null;
      PsiExpression[] additionArgs = additionToList.getArgumentList().getExpressions();
      PsiExpression arg = additionArgs[0];
      PsiReferenceExpression referenceExpression = tryCast(arg, PsiReferenceExpression.class);
      if (referenceExpression == null) return null;
      PsiVariable savedVar = tryCast(referenceExpression.resolve(), PsiVariable.class);
      if(savedVar == null) return null;
      if (!savedVar.equals(terminalBlock.getVariable())) return null;
      return additionToList;
    }

    @Nullable
    public static GroupingTerminal tryExtractJava8Style(@NotNull TerminalBlock tb, @Nullable PsiMethodCallExpression call) {
      if(call == null) return null;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      return tryExtractJava8Style(tb, tryCast(qualifier, PsiMethodCallExpression.class), tryCast(call, PsiMethodCallExpression.class));
    }

    /*
    Map<Integer, List<String>> map = new HashMap<>();
    for (String s : list) {
        List<String> strings = map.computeIfAbsent(s.length(), k -> new ArrayList<>());
        strings.add(s);
    }
 */
    @Nullable
    private static GroupingTerminal tryExtractWithIntermediateVariable(@NotNull TerminalBlock terminalBlock) {
      PsiStatement[] statements = terminalBlock.getStatements();
      PsiDeclarationStatement assignmentStmt = tryCast(statements[0], PsiDeclarationStatement.class);
      if(assignmentStmt == null) return null;
      PsiElement[] elements = assignmentStmt.getDeclaredElements();
      if(elements.length != 1) return null;
      PsiLocalVariable variable = tryCast(elements[0], PsiLocalVariable.class);
      if(variable == null) return null;

      PsiExpressionStatement addStmt = tryCast(statements[1], PsiExpressionStatement.class);
      if(addStmt == null) return null;
      PsiMethodCallExpression maybeAddCall = tryCast(addStmt.getExpression(), PsiMethodCallExpression.class);
      if(maybeAddCall == null) return null;
      PsiExpression qualifier = maybeAddCall.getMethodExpression().getQualifierExpression();
      if(!ExpressionUtils.isReferenceTo(qualifier, variable)) return null;

      return tryExtractJava8Style(terminalBlock, tryCast(variable.getInitializer(), PsiMethodCallExpression.class), maybeAddCall);
    }

    @Nullable
    public static GroupingTerminal tryExtractJava8Style(@NotNull TerminalBlock tb,
                                                        @Nullable PsiMethodCallExpression computeIfAbsentCall,
                                                        @Nullable PsiMethodCallExpression addCall) {
      if (!LIST_ADD.test(addCall) || !COMPUTE_IF_ABSENT.test(computeIfAbsentCall)) return null;
      PsiExpression[] args = computeIfAbsentCall.getArgumentList().getExpressions();
      if (args.length != 2 || !(args[1] instanceof PsiLambdaExpression lambda)) return null;
      PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (ConstructionUtils.isEmptyCollectionInitializer(body)) {
        PsiLocalVariable variable = extractQualifierVariable(tb, computeIfAbsentCall);
        if (hasLambdaCompatibleEmptyInitializer(variable)) {
          PsiType mapType = variable.getType();
          PsiType valueType = PsiUtil.substituteTypeParameter(mapType, JAVA_UTIL_MAP, 1, false);
          if (valueType == null) return null;
          AddingTerminal adding = new AddingTerminal(valueType, body, tb.getVariable(), addCall);
          InitializerUsageStatus status = getInitializerUsageStatus(variable, tb.getStreamSourceStatement());
          return new GroupingTerminal(adding, variable, args[0], status);
        }
      }
      return null;
    }
  }

  static class ToMapTerminal extends CollectTerminal {
    private final PsiMethodCallExpression myMapUpdateCall;
    private final PsiVariable myElementVariable;

    ToMapTerminal(PsiMethodCallExpression call,
                  PsiVariable elementVariable,
                  PsiLocalVariable variable,
                  PsiStatement loop,
                  InitializerUsageStatus status) {
      super(variable, loop, status);
      myMapUpdateCall = call;
      myElementVariable = elementVariable;
    }

    @Override
    String generateIntermediate(CommentTracker ct) {
      return myElementVariable.getType() instanceof PsiPrimitiveType ? ".boxed()" : "";
    }

    @Override
    public String generateTerminal(CommentTracker ct, boolean strictMode) {
      return generateTerminal(ct, "toMap");
    }

    public String generateTerminal(CommentTracker ct, final String collectorName) {
      PsiExpression[] args = myMapUpdateCall.getArgumentList().getExpressions();
      LOG.assertTrue(args.length >= 2);
      String methodName = myMapUpdateCall.getMethodExpression().getReferenceName();
      LOG.assertTrue(methodName != null);
      Project project = myMapUpdateCall.getProject();
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      String aVar = codeStyleManager.suggestUniqueVariableName("a", myMapUpdateCall, true);
      String bVar = codeStyleManager.suggestUniqueVariableName("b", myMapUpdateCall, true);
      String merger = switch (methodName) {
        case "put" -> "(" + aVar + "," + bVar + ")->" + bVar;
        case "putIfAbsent" -> "(" + aVar + "," + bVar + ")->" + aVar;
        case "merge" -> {
          LOG.assertTrue(args.length == 3);
          yield ct.text(args[2]);
        }
        default -> null;
      };
      if (merger == null) return null;
      StringBuilder collector = new StringBuilder(".collect(" + JAVA_UTIL_STREAM_COLLECTORS + "." + collectorName + "(");
      collector.append(ct.lambdaText(myElementVariable, args[0])).append(',')
        .append(ct.lambdaText(myElementVariable, args[1])).append(',')
        .append(merger);
      PsiLocalVariable variable = Objects.requireNonNull(getTargetVariable());
      PsiExpression initializer = variable.getInitializer();
      LOG.assertTrue(initializer != null);
      if ("toMap".equals(collectorName) && !isHashMap(variable)) {
        collector.append(",()->").append(ct.text(initializer));
      }
      collector.append("))");
      return collector.toString();
    }

    @Nullable
    static ToMapTerminal tryExtract(TerminalBlock tb, PsiMethodCallExpression call) {
      if (tb.getCountExpression() != null ||
          !isCallOf(call, JAVA_UTIL_MAP, "merge", "put", "putIfAbsent")) {
        return null;
      }
      PsiLocalVariable variable = extractQualifierVariable(tb, call);
      if (!hasLambdaCompatibleEmptyInitializer(variable)) return null;
      InitializerUsageStatus status = getInitializerUsageStatus(variable, tb.getStreamSourceStatement());
      return new ToMapTerminal(call, tb.getVariable(), variable, tb.getStreamSourceStatement(), status);
    }
  }
  static class SortingTerminal extends CollectTerminal {
    private final CollectTerminal myDownstream;
    private final PsiExpression myComparator;
    private final PsiStatement myStatement;

    SortingTerminal(CollectTerminal downstream, PsiStatement statement, PsiExpression comparator) {
      super(downstream.getTargetVariable(), null, downstream.getStatus());
      myDownstream = downstream;
      myStatement = statement;
      myComparator = comparator;
    }

    @Override
    public String getMethodName() {
      return myDownstream.getMethodName();
    }

    @Override
    public String generateIntermediate(CommentTracker ct) {
      return myDownstream.generateIntermediate(ct) + ".sorted("
             + (myComparator == null ? "" : ct.text(myComparator)) + ")";
    }

    @Override
    public String generateTerminal(CommentTracker ct, boolean strictMode) {
      return myDownstream.generateTerminal(ct, strictMode);
    }

    @Override
    StreamEx<PsiElement> usedElements() {
      return myDownstream.usedElements().append(myStatement);
    }

    @Override
    public void cleanUp(CommentTracker ct) {
      myDownstream.cleanUp(ct);
      ct.delete(myStatement);
    }

    @Override
    StreamEx<String> fusedElements() {
      return myDownstream.fusedElements().append("'sort'");
    }

    @Nullable
    public static CollectTerminal tryWrap(@NotNull CollectTerminal terminal, @NotNull PsiElement element) {
      PsiVariable containerVariable = terminal.getTargetVariable();
      if (containerVariable == null || !(element instanceof PsiExpressionStatement expressionStatement)) return null;
      if (!(expressionStatement.getExpression() instanceof PsiMethodCallExpression methodCall)) return null;
      PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      if (!"sort".equals(methodExpression.getReferenceName())) return null;
      PsiMethod method = methodCall.resolveMethod();
      if (method == null) return null;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return null;
      PsiExpression containerExpression = null;
      PsiExpression comparatorExpression = null;
      if (JAVA_UTIL_COLLECTIONS.equals(containingClass.getQualifiedName()) ||
          JAVA_UTIL_ARRAYS.equals(containingClass.getQualifiedName())) {
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length == 1) {
          containerExpression = args[0];
        }
        else if (args.length == 2) {
          containerExpression = args[0];
          comparatorExpression = args[1];
        }
        else {
          return null;
        }
      }
      else if (InheritanceUtil.isInheritor(containingClass, JAVA_UTIL_LIST)) {
        containerExpression = methodExpression.getQualifierExpression();
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length != 1) return null;
        comparatorExpression = args[0];
      }
      if (!(containerExpression instanceof PsiReferenceExpression) || !terminal.isTargetReference(containerExpression)) {
        return null;
      }
      if (ExpressionUtils.isNullLiteral(comparatorExpression)) {
        comparatorExpression = null;
      }
      return new SortingTerminal(terminal, expressionStatement, comparatorExpression);
    }
  }

  static abstract class RecreateTerminal extends CollectTerminal {
    final CollectTerminal myUpstream;
    private final String myIntermediate;
    final PsiExpression myCreateExpression;

    RecreateTerminal(CollectTerminal upstream, PsiLocalVariable variable, String intermediate, PsiExpression createExpression) {
      super(variable, null, ControlFlowUtils.InitializerUsageStatus.DECLARED_JUST_BEFORE);
      myUpstream = upstream;
      myIntermediate = intermediate;
      myCreateExpression = createExpression;
    }

    @Override
    public boolean isTrivial() {
      return false;
    }

    @Nullable
    @Override
    public PsiElement getElementToReplace() {
      return getTargetVariable() == null ? myCreateExpression : null;
    }

    @Override
    public String generateIntermediate(CommentTracker ct) {
      return myUpstream.generateIntermediate(ct) + myIntermediate;
    }

    @Override
    public void cleanUp(CommentTracker ct) {
      PsiLocalVariable variable = myUpstream.getTargetVariable();
      if (variable != null && myUpstream.getStatus() != ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE) {
        ct.delete(variable);
      }
      myUpstream.cleanUp(ct);
    }
  }

  static class ToArrayTerminal extends RecreateTerminal {
    private final String mySupplier;

    ToArrayTerminal(CollectTerminal upstream,
                    PsiLocalVariable variable,
                    String intermediate,
                    PsiMethodCallExpression toArrayExpression,
                    String supplier) {
      super(upstream, variable, intermediate, toArrayExpression);
      mySupplier = supplier;
    }

    @Override
    public String getMethodName() {
      return "toArray";
    }

    @Override
    public String generateTerminal(CommentTracker ct, boolean strictMode) {
      return ".toArray(" + mySupplier + ")";
    }

    @Override
    StreamEx<String> fusedElements() {
      return myUpstream.fusedElements().append("'toArray'");
    }

    @Nullable
    public static ToArrayTerminal tryWrap(@NotNull CollectTerminal terminal, @NotNull PsiElement element) {
      if (terminal.getStatus() == ControlFlowUtils.InitializerUsageStatus.UNKNOWN) return null;
      if (!(element instanceof PsiExpressionStatement) && !(element instanceof PsiDeclarationStatement)
          && !(element instanceof PsiReturnStatement)) {
        return null;
      }
      String intermediateSteps = terminal.getIntermediateStepsFromCollection();
      if (intermediateSteps == null) return null;

      List<? extends PsiExpression> usages = terminal.targetReferences().toList();
      if (usages.isEmpty()) return null;
      PsiMethodCallExpression toArrayCandidate = StreamEx.of(usages)
        .map(usage -> ExpressionUtils.getCallForQualifier(tryCast(usage, PsiExpression.class)))
        .nonNull().findFirst().orElse(null);
      if (toArrayCandidate == null) return null;
      PsiReferenceExpression methodExpression = toArrayCandidate.getMethodExpression();
      if (!"toArray".equals(methodExpression.getReferenceName())) return null;
      /* We want to allow reusing the same empty collection in another branch of code after return.
       * However, in this case, return should be on the same level as the stream itself.
       * See beforeToArrayInBranch.java and beforeToArrayReusedCollection.java tests.
       */
      if ((!(PsiUtil.skipParenthesizedExprUp(toArrayCandidate.getParent()) instanceof PsiReturnStatement stmt) 
           || stmt.getParent() != element.getParent()) &&
          ContainerUtil.exists(usages, usage -> !PsiTreeUtil.isAncestor(toArrayCandidate, usage, false))) {
        return null;
      }
      PsiLocalVariable var = tryCast(PsiUtil.skipParenthesizedExprUp(toArrayCandidate.getParent()), PsiLocalVariable.class);
      String supplier = extractSupplier(toArrayCandidate);
      if (supplier == null) return null;
      return new ToArrayTerminal(terminal, var, intermediateSteps, toArrayCandidate, supplier);
    }

    @Nullable
    static String extractSupplier(PsiMethodCallExpression toArrayCandidate) {
      // collection.toArray() or collection.toArray(new Type[0]) or collection.toArray(new Type[collection.size()]);
      PsiExpression[] args = toArrayCandidate.getArgumentList().getExpressions();
      if (args.length == 0) return "";
      if (args.length != 1 || !(args[0] instanceof PsiNewExpression newArray)) return null;
      PsiType arrayType = newArray.getType();
      if (arrayType == null) return null;
      String name = arrayType.getCanonicalText();
      PsiExpression[] dimensions = newArray.getArrayDimensions();
      if (dimensions.length != 1) return null;
      PsiExpression qualifier = toArrayCandidate.getMethodExpression().getQualifierExpression();
      if (ExpressionUtils.isZero(dimensions[0]) ||
          (qualifier != null && CollectionUtils.isCollectionOrMapSize(dimensions[0], qualifier))) {
        return name + "::new";
      }
      return null;
    }
  }

  static class NewListTerminal extends RecreateTerminal {
    private final PsiType myResultType;

    NewListTerminal(CollectTerminal upstream,
                    PsiLocalVariable variable,
                    String intermediate,
                    PsiCallExpression newListExpression,
                    PsiType resultType) {
      super(upstream, variable, intermediate, newListExpression);
      myResultType = resultType;
    }

    @Override
    public String generateTerminal(CommentTracker ct, boolean strictMode) {
      return ".collect(" + getCollectionCollector(ct, ct.markUnchanged(myCreateExpression), myResultType, strictMode) + ")";
    }

    @Override
    StreamEx<String> fusedElements() {
      if (myCreateExpression instanceof PsiNewExpression) {
        PsiJavaCodeReferenceElement reference = ((PsiNewExpression)myCreateExpression).getClassReference();
        return myUpstream.fusedElements().append(Objects.requireNonNull(reference).getReferenceName());
      }
      return myUpstream.fusedElements().append(((PsiMethodCallExpression)myCreateExpression).getMethodExpression().getReferenceName());
    }

    @Nullable
    public static NewListTerminal tryWrap(@NotNull CollectTerminal terminal, @NotNull PsiElement element) {
      if (terminal.getStatus() == ControlFlowUtils.InitializerUsageStatus.UNKNOWN) return null;
      String intermediateSteps = terminal.getIntermediateStepsFromCollection();
      if (intermediateSteps == null) return null;

      WrapperCandidate candidate = WrapperCandidate.tryExtract(terminal, element);
      if (candidate == null) return null;
      if (!(candidate.myCandidate instanceof PsiCallExpression callExpression)) return null;
      PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(candidate.myCandidate.getType());
      if (!InheritanceUtil.isInheritor(targetClass, JAVA_UTIL_COLLECTION)) return null;
      if (!ConstructionUtils.isPrepopulatedCollectionInitializer(callExpression)) return null;
      if (JAVA_UTIL_HASH_SET.equals(targetClass.getQualifiedName()) && intermediateSteps.equals(".distinct()")) {
        intermediateSteps = "";
      }
      PsiExpressionList argumentList = callExpression.getArgumentList();
      if (argumentList == null) return null;
      PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 1 || !terminal.isTargetReference(args[0])) return null;
      return new NewListTerminal(terminal, candidate.myVar, intermediateSteps, callExpression, candidate.myType);
    }
  }

  static class UnmodifiableTerminal extends RecreateTerminal {
    private static final Map<String, String> TYPE_TO_UNMODIFIABLE_WRAPPER = EntryStream.of(
      JAVA_UTIL_ARRAY_LIST, "toUnmodifiableList",
      JAVA_UTIL_LINKED_LIST, "toUnmodifiableList",
      JAVA_UTIL_HASH_SET, "toUnmodifiableSet",
      JAVA_UTIL_HASH_MAP, "toUnmodifiableMap"
    ).toMap();

    private static final CallMatcher UNMODIFIABLE_WRAPPER = CallMatcher.staticCall(
      JAVA_UTIL_COLLECTIONS,
      "unmodifiableList", "unmodifiableSet", "unmodifiableCollection", "unmodifiableMap").parameterCount(1);

    private static final CallMatcher STREAM_COLLECT =
      CallMatcher.instanceCall(JAVA_UTIL_STREAM_STREAM, "collect").parameterTypes("java.util.stream.Collector");
    private static final CallMatcher TO_LIST =
      CallMatcher.staticCall(JAVA_UTIL_STREAM_COLLECTORS, "toList").parameterCount(0);
    private static final CallMatcher TO_SET =
      CallMatcher.staticCall(JAVA_UTIL_STREAM_COLLECTORS, "toSet").parameterCount(0);

    private final String myUnmodifiableCollector;

    UnmodifiableTerminal(CollectTerminal upstream,
                         PsiLocalVariable variable,
                         PsiMethodCallExpression unmodifiableExpression,
                         String unmodifiableCollector) {
      super(upstream, variable, "", unmodifiableExpression);
      myUnmodifiableCollector = unmodifiableCollector;
    }

    @Override
    public String generateTerminal(CommentTracker ct, boolean strictMode) {
      if (myUpstream instanceof ToMapTerminal) {
        return ((ToMapTerminal)myUpstream).generateTerminal(ct, myUnmodifiableCollector);
      }
      return ".collect(" + JAVA_UTIL_STREAM_COLLECTORS + "." + myUnmodifiableCollector + "())";
    }

    @Override
    StreamEx<String> fusedElements() {
      String reference = ((PsiMethodCallExpression)myCreateExpression).getMethodExpression().getReferenceName();
      return myUpstream.fusedElements().append(reference);
    }

    @Nullable
    public static UnmodifiableTerminal tryWrap(@NotNull CollectTerminal terminal, @NotNull PsiElement element) {
      if (PsiUtil.getLanguageLevel(element).isLessThan(LanguageLevel.JDK_10)) return null;
      if (terminal.getStatus() == ControlFlowUtils.InitializerUsageStatus.UNKNOWN) return null;

      WrapperCandidate candidate = WrapperCandidate.tryExtract(terminal, element);
      if (candidate == null) return null;
      if (!(candidate.myCandidate instanceof PsiMethodCallExpression wrapCall)) return null;
      if (!UNMODIFIABLE_WRAPPER.test(wrapCall)) return null;
      PsiExpression arg = wrapCall.getArgumentList().getExpressions()[0];
      if (!terminal.isTargetReference(arg)) return null;
      if (terminal.getTargetVariable() != null) {
        arg = terminal.getTargetVariable().getInitializer();
      }
      if (arg == null) return null;
      PsiClassType targetType = tryCast(arg.getType(), PsiClassType.class);
      if (targetType == null) return null;
      String collector = TYPE_TO_UNMODIFIABLE_WRAPPER.get(targetType.rawType().getCanonicalText());
      if (collector == null) {
        if (!(arg instanceof PsiMethodCallExpression argCall)) return null;
        if (!STREAM_COLLECT.test(argCall)) return null;
        PsiExpression previousCollector = argCall.getArgumentList().getExpressions()[0];
        if (TO_LIST.matches(previousCollector)) {
          collector = "toUnmodifiableList";
        } else if (TO_SET.matches(previousCollector)) {
          collector = "toUnmodifiableSet";
        } else {
          return null;
        }
      }
      if (terminal instanceof AddingTerminal) {
        if (terminal instanceof AddingAllTerminal) return null;
        Nullability nullability = NullabilityUtil.getExpressionNullability(((AddingTerminal)terminal).getMapping(), true);
        // Null is not allowed in unmodifiable list/set
        if (nullability == Nullability.NULLABLE) return null;
      }
      return new UnmodifiableTerminal(terminal, candidate.myVar, wrapCall, collector);
    }
  }

  private static class WrapperCandidate {
    private final PsiExpression myCandidate;
    private final PsiType myType;
    private final PsiLocalVariable myVar;

    WrapperCandidate(PsiExpression candidate, PsiType type, PsiLocalVariable var) {
      myCandidate = candidate;
      myType = type;
      myVar = var;
    }

    @Nullable
    static WrapperCandidate tryExtract(CollectTerminal terminal, PsiElement element) {
      PsiExpression candidate;
      PsiType type;
      PsiLocalVariable var = null;
      if (element instanceof PsiReturnStatement) {
        candidate = ((PsiReturnStatement)element).getReturnValue();
        type = PsiTypesUtil.getMethodReturnType(element);
      }
      else {
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(element);
        if (assignment != null) {
          candidate = assignment.getRExpression();
          type = assignment.getType();
        }
        else if (element instanceof PsiDeclarationStatement) {
          PsiElement[] elements = ((PsiDeclarationStatement)element).getDeclaredElements();
          if (elements.length != 1 || !(elements[0] instanceof PsiLocalVariable)) return null;
          var = (PsiLocalVariable)elements[0];
          candidate = var.getInitializer();
          type = var.getType();
        }
        else {
          return null;
        }
        if (candidate != null && terminal.targetReferences().anyMatch(ref -> !PsiTreeUtil.isAncestor(element, ref, false))) {
          return null;
        }
      }
      return new WrapperCandidate(candidate, type, var);
    }
  }
}
