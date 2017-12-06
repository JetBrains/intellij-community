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
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
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
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ControlFlowUtils.getInitializerUsageStatus;
import static com.siyeh.ig.psiutils.Java8MigrationUtils.MapCheckCondition.fromConditional;
import static com.siyeh.ig.psiutils.Java8MigrationUtils.extractLambdaCandidate;

/**
 * @author Tagir Valeev
 */
class CollectMigration extends BaseStreamApiMigration {
  private static final Logger LOG = Logger.getInstance(CollectMigration.class);

  static final Map<String, String> INTERMEDIATE_STEPS = EntryStream.of(
    CommonClassNames.JAVA_UTIL_ARRAY_LIST, "",
    "java.util.LinkedList", "",
    CommonClassNames.JAVA_UTIL_HASH_SET, ".distinct()",
    "java.util.LinkedHashSet", ".distinct()",
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
    String stream = tb.generate(ct) + terminal.generateIntermediate(ct) + terminal.generateTerminal(ct);
    PsiElement toReplace = terminal.getElementToReplace();
    PsiElement result;
    if (toReplace != null) {
      result = ct.replace(toReplace, stream);
      removeLoop(ct, loopStatement);
    }
    else {
      PsiVariable variable = terminal.getTargetVariable();
      LOG.assertTrue(variable != null);
      result = replaceInitializer(loopStatement, variable, variable.getInitializer(), stream, terminal.getStatus(), ct);
    }
    terminal.cleanUp();
    return result;
  }

  private static boolean isHashMap(PsiLocalVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    LOG.assertTrue(initializer != null);
    PsiClass initializerClass = PsiUtil.resolveClassInClassTypeOnly(initializer.getType());
    PsiClass varClass = PsiUtil.resolveClassInClassTypeOnly(variable.getType());
    return initializerClass != null &&
           varClass != null &&
           CommonClassNames.JAVA_UTIL_HASH_MAP.equals(initializerClass.getQualifiedName()) &&
           CommonClassNames.JAVA_UTIL_MAP.equals(varClass.getQualifiedName()) &&
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
    List<BiFunction<CollectTerminal, PsiElement, CollectTerminal>> wrappers =
      Arrays.asList(SortingTerminal::tryWrap, ToArrayTerminal::tryWrap, NewListTerminal::tryWrap);
    while (true) {
      CollectTerminal wrapped = null;
      for (BiFunction<CollectTerminal, PsiElement, CollectTerminal> wrapper : wrappers) {
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
    }
  }

  @Contract("null -> false")
  static boolean hasLambdaCompatibleEmptyInitializer(@Nullable PsiLocalVariable target) {
    return target != null &&
           ConstructionUtils.isEmptyCollectionInitializer(target.getInitializer()) &&
           LambdaGenerationUtil.canBeUncheckedLambda(target.getInitializer());
  }

  abstract static class CollectTerminal {
    private final PsiLocalVariable myTargetVariable;
    private final InitializerUsageStatus myStatus;
    private final PsiStatement myLoop;

    protected CollectTerminal(PsiLocalVariable variable, PsiStatement loop, InitializerUsageStatus status) {
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
      List<PsiElement> usedElements = usedElements().toList();
      return StreamEx.of(ReferencesSearch.search(myTargetVariable).findAll()).select(PsiReferenceExpression.class)
        .filter(ref -> usedElements.stream().noneMatch(allowedUsage -> PsiTreeUtil.isAncestor(allowedUsage, ref, false)));
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

    abstract String generateTerminal(CommentTracker ct);

    StreamEx<PsiElement> usedElements() {
      return StreamEx.ofNullable(myLoop);
    }

    StreamEx<String> fusedElements() {
      return StreamEx.empty();
    }

    public InitializerUsageStatus getStatus() { return myStatus; }

    void cleanUp() {}

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

    public String generateCollector(CommentTracker ct) {
      return getCollectionCollector(ct, myInitializer, myTargetType);
    }

    @Override
    public String generateTerminal(CommentTracker ct) {
      return ".collect(" + generateCollector(ct) + ")";
    }

    @Nullable
    static AddingTerminal tryExtract(TerminalBlock tb, PsiMethodCallExpression call) {
      if (!isCallOf(call, CommonClassNames.JAVA_UTIL_COLLECTION, "add")) return null;
      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) return null;
      PsiExpression count = tb.getCountExpression();
      PsiLocalVariable variable = extractQualifierVariable(tb, call);
      if (variable != null) {
        InitializerUsageStatus status = getInitializerUsageStatus(variable, tb.getStreamSourceStatement());
        AddingTerminal terminal = new AddingTerminal(variable, tb.getVariable(), call, tb.getStreamSourceStatement(), status);
        if (count == null) return terminal;
        // like "list.add(x); if(list.size() >= limit) break;"
        if (!(count instanceof PsiMethodCallExpression)) return null;
        PsiMethodCallExpression sizeCall = (PsiMethodCallExpression)count;
        PsiExpression sizeQualifier = sizeCall.getMethodExpression().getQualifierExpression();
        if (isCallOf(sizeCall, CommonClassNames.JAVA_UTIL_COLLECTION, "size") &&
            EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(sizeQualifier, qualifierExpression) &&
            InheritanceUtil.isInheritor(PsiUtil.resolveClassInClassTypeOnly(variable.getType()), CommonClassNames.JAVA_UTIL_LIST)) {
          return terminal;
        }
      }
      return null;
    }
  }

  @NotNull
  private static String getCollectionCollector(CommentTracker ct, PsiExpression initializer, PsiType type) {
    String collector;
    PsiType initializerType = initializer.getType();
    PsiClassType rawType = initializerType instanceof PsiClassType ? ((PsiClassType)initializerType).rawType() : null;
    PsiClassType rawVarType = type instanceof PsiClassType ? ((PsiClassType)type).rawType() : null;
    if (rawType != null && rawVarType != null &&
        rawType.equalsToText(CommonClassNames.JAVA_UTIL_ARRAY_LIST) &&
        (rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_LIST) || rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_COLLECTION)) &&
        !ConstructionUtils.isCustomizedEmptyCollectionInitializer(initializer)) {
      collector = "toList()";
    }
    else if (rawType != null && rawVarType != null &&
             rawType.equalsToText(CommonClassNames.JAVA_UTIL_HASH_SET) &&
             (rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_SET) || rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_COLLECTION)) &&
             !ConstructionUtils.isCustomizedEmptyCollectionInitializer(initializer)) {
      collector = "toSet()";
    }
    else {
      PsiExpression copy = JavaPsiFacade.getElementFactory(initializer.getProject())
        .createExpressionFromText(ct.text(initializer), initializer);
      if (copy instanceof PsiNewExpression) {
        PsiExpressionList argumentList = ((PsiNewExpression)copy).getArgumentList();
        if (argumentList != null) {
          PsiExpression arg = ArrayUtil.getFirstElement(argumentList.getExpressions());
          if (arg != null && !(arg.getType() instanceof PsiPrimitiveType)) {
            arg.delete();
          }
        }
      }
      collector = "toCollection(() -> " + copy.getText() + ")";
    }
    return CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + "." + collector;
  }

  static class AddingAllTerminal extends AddingTerminal {
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
      PsiType[] typeParameters = myAddAllCall.getMethodExpression().getTypeParameters();
      String generic = "";
      if(typeParameters.length == 1) {
        generic = "<"+typeParameters[0].getCanonicalText()+">";
      }
      String method = MethodCallUtils.isVarArgCall(myAddAllCall) ? CommonClassNames.JAVA_UTIL_STREAM_STREAM + "." + generic + "of"
                                                                 : CommonClassNames.JAVA_UTIL_ARRAYS + "." + generic + "stream";
      String lambda = myElement.getName() + "->" + method + "(" +
                      StreamEx.of(myAddAllCall.getArgumentList().getExpressions()).skip(1).map(ct::text).joining(",") + ")";
      return myElement.getType() instanceof PsiPrimitiveType ?
             ".mapToObj(" + lambda + ").flatMap("+ CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION+".identity())" :
             ".flatMap(" + lambda + ")";
    }

    @Nullable
    static AddingAllTerminal tryExtractAddAll(TerminalBlock tb, PsiMethodCallExpression call) {
      if(tb.getCountExpression() != null ||
         !MethodCallUtils.isCallToStaticMethod(call, CommonClassNames.JAVA_UTIL_COLLECTIONS, "addAll", 2)) {
        return null;
      }
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if(args.length < 2) return null;
      PsiReferenceExpression collectionReference = tryCast(args[0], PsiReferenceExpression.class);
      if (collectionReference == null || tb.dependsOn(collectionReference)) return null;
      PsiLocalVariable target = tryCast(collectionReference.resolve(), PsiLocalVariable.class);
      if (target == null || StreamEx.of(args).skip(1).anyMatch(arg -> VariableAccessUtils.variableIsUsed(target, arg))) return null;
      InitializerUsageStatus status = getInitializerUsageStatus(target, tb.getStreamSourceStatement());
      return new AddingAllTerminal(target, tb.getVariable(), call, tb.getStreamSourceStatement(), status);
    }
  }

  static class GroupingTerminal extends CollectTerminal {

    private final static CallMatcher LIST_ADD = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "add").parameterCount(1);
    private final static CallMatcher COMPUTE_IF_ABSENT = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "computeIfAbsent").parameterCount(2);

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
    public String generateTerminal(CommentTracker ct) {
      String downstreamCollector = myDownstream.generateCollector(ct);
      PsiVariable elementVariable = myDownstream.getElementVariable();
      if (!ExpressionUtils.isReferenceTo(myDownstream.getMapping(), myDownstream.getElementVariable())) {
        downstreamCollector = CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".mapping(" +
                              myDownstream.getElementVariable().getName() + "->" + ct.text(myDownstream.getMapping()) + "," +
                              downstreamCollector + ")";
      }
      StringBuilder builder = new StringBuilder();
      builder.append(".collect(" + CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".groupingBy(")
        .append(ct.lambdaText(elementVariable, myKeyExpression));
      PsiLocalVariable variable = Objects.requireNonNull(getTargetVariable());
      PsiExpression initializer = variable.getInitializer();
      LOG.assertTrue(initializer != null);
      if (!isHashMap(variable)) {
        builder.append(",()->").append(ct.text(initializer)).append(",").append(downstreamCollector);
      }
      else if (!(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + "." + "toList()").equals(downstreamCollector)) {
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
                                                         @NotNull PsiStatement[] statements,
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
          !InheritanceUtil.isInheritor(listVar.getType(), CommonClassNames.JAVA_UTIL_LIST)) {
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
      if (args.length != 2 || !(args[1] instanceof PsiLambdaExpression)) return null;
      PsiLambdaExpression lambda = (PsiLambdaExpression)args[1];
      PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (ConstructionUtils.isEmptyCollectionInitializer(body)) {
        PsiLocalVariable variable = extractQualifierVariable(tb, computeIfAbsentCall);
        if (hasLambdaCompatibleEmptyInitializer(variable)) {
          PsiType mapType = variable.getType();
          PsiType valueType = PsiUtil.substituteTypeParameter(mapType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
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
    public String generateTerminal(CommentTracker ct) {
      PsiExpression[] args = myMapUpdateCall.getArgumentList().getExpressions();
      LOG.assertTrue(args.length >= 2);
      String methodName = myMapUpdateCall.getMethodExpression().getReferenceName();
      LOG.assertTrue(methodName != null);
      Project project = myMapUpdateCall.getProject();
      String merger;
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      String aVar = codeStyleManager.suggestUniqueVariableName("a", myMapUpdateCall, true);
      String bVar = codeStyleManager.suggestUniqueVariableName("b", myMapUpdateCall, true);
      switch (methodName) {
        case "put":
          merger = "(" + aVar + "," + bVar + ")->" + bVar;
          break;
        case "putIfAbsent":
          merger = "(" + aVar + "," + bVar + ")->" + aVar;
          break;
        case "merge":
          LOG.assertTrue(args.length == 3);
          merger = ct.text(args[2]);
          break;
        default:
          return null;
      }
      StringBuilder collector = new StringBuilder(".collect(" + CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".toMap(");
      collector.append(ct.lambdaText(myElementVariable, args[0])).append(',')
        .append(ct.lambdaText(myElementVariable, args[1])).append(',')
        .append(merger);
      PsiLocalVariable variable = Objects.requireNonNull(getTargetVariable());
      PsiExpression initializer = variable.getInitializer();
      LOG.assertTrue(initializer != null);
      if (!isHashMap(variable)) {
        collector.append(",()->").append(ct.text(initializer));
      }
      collector.append("))");
      return collector.toString();
    }

    @Nullable
    static ToMapTerminal tryExtract(TerminalBlock tb, PsiMethodCallExpression call) {
      if (tb.getCountExpression() != null ||
          !isCallOf(call, CommonClassNames.JAVA_UTIL_MAP, "merge", "put", "putIfAbsent")) {
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
    public String generateTerminal(CommentTracker ct) {
      return myDownstream.generateTerminal(ct);
    }

    @Override
    StreamEx<PsiElement> usedElements() {
      return myDownstream.usedElements().append(myStatement);
    }

    @Override
    public void cleanUp() {
      myDownstream.cleanUp();
      myStatement.delete();
    }

    @Override
    StreamEx<String> fusedElements() {
      return myDownstream.fusedElements().append("'sort'");
    }

    @Nullable
    public static CollectTerminal tryWrap(CollectTerminal terminal, PsiElement element) {
      PsiVariable containerVariable = terminal.getTargetVariable();
      if (containerVariable == null || !(element instanceof PsiExpressionStatement)) return null;
      PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
      PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      if (!"sort".equals(methodExpression.getReferenceName())) return null;
      PsiMethod method = methodCall.resolveMethod();
      if (method == null) return null;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return null;
      PsiExpression containerExpression = null;
      PsiExpression comparatorExpression = null;
      if (CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(containingClass.getQualifiedName()) ||
          CommonClassNames.JAVA_UTIL_ARRAYS.equals(containingClass.getQualifiedName())) {
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
      else if (InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_LIST)) {
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
      return new SortingTerminal(terminal, (PsiExpressionStatement)element, comparatorExpression);
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
    public void cleanUp() {
      PsiLocalVariable variable = myUpstream.getTargetVariable();
      if (variable != null && myUpstream.getStatus() != ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE) {
        variable.delete();
      }
      myUpstream.cleanUp();
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
    public String generateTerminal(CommentTracker ct) {
      return ".toArray(" + mySupplier + ")";
    }

    @Override
    StreamEx<String> fusedElements() {
      return myUpstream.fusedElements().append("'toArray'");
    }

    @Contract("_, null -> null")
    @Nullable
    public static ToArrayTerminal tryWrap(CollectTerminal terminal, PsiElement element) {
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
      if (!(PsiUtil.skipParenthesizedExprUp(toArrayCandidate.getParent()) instanceof PsiReturnStatement) &&
          usages.stream().anyMatch(usage -> !PsiTreeUtil.isAncestor(toArrayCandidate, usage, false))) {
        return null;
      }
      PsiLocalVariable var = tryCast(PsiUtil.skipParenthesizedExprUp(toArrayCandidate.getParent()), PsiLocalVariable.class);
      String supplier = extractSupplier(toArrayCandidate, terminal);
      if (supplier == null) return null;
      return new ToArrayTerminal(terminal, var, intermediateSteps, toArrayCandidate, supplier);
    }

    @Nullable
    static String extractSupplier(PsiMethodCallExpression toArrayCandidate, CollectTerminal terminal) {
      // collection.toArray() or collection.toArray(new Type[0]) or collection.toArray(new Type[collection.size()]);
      PsiExpression[] args = toArrayCandidate.getArgumentList().getExpressions();
      if (args.length == 0) return "";
      if (args.length != 1 || !(args[0] instanceof PsiNewExpression)) return null;
      PsiNewExpression newArray = (PsiNewExpression)args[0];
      PsiType arrayType = newArray.getType();
      if (arrayType == null) return null;
      String name = arrayType.getCanonicalText();
      PsiExpression[] dimensions = newArray.getArrayDimensions();
      if (dimensions.length != 1) return null;
      if (ExpressionUtils.isZero(dimensions[0])) return name+"::new";
      if (!(dimensions[0] instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression maybeSizeCall = (PsiMethodCallExpression)dimensions[0];
      if (!isCallOf(maybeSizeCall, CommonClassNames.JAVA_UTIL_COLLECTION, "size")) return null;
      PsiExpression sizeQualifier = maybeSizeCall.getMethodExpression().getQualifierExpression();
      if (!terminal.isTargetReference(sizeQualifier)) return null;
      return name+"::new";
    }
  }

  static class NewListTerminal extends RecreateTerminal {
    private final PsiType myResultType;

    NewListTerminal(CollectTerminal upstream,
                    PsiLocalVariable variable,
                    String intermediate,
                    PsiNewExpression newListExpression,
                    PsiType resultType) {
      super(upstream, variable, intermediate, newListExpression);
      myResultType = resultType;
    }

    @Override
    public String generateTerminal(CommentTracker ct) {
      return ".collect(" + getCollectionCollector(ct, ct.markUnchanged(myCreateExpression), myResultType) + ")";
    }

    @Override
    StreamEx<String> fusedElements() {
      PsiJavaCodeReferenceElement reference = ((PsiNewExpression)myCreateExpression).getClassReference();
      return myUpstream.fusedElements().append(Objects.requireNonNull(reference).getReferenceName());
    }

    @Nullable
    public static NewListTerminal tryWrap(CollectTerminal terminal, PsiElement element) {
      if (terminal.getStatus() == ControlFlowUtils.InitializerUsageStatus.UNKNOWN) return null;
      String intermediateSteps = terminal.getIntermediateStepsFromCollection();
      if (intermediateSteps == null) return null;

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
      if (!(candidate instanceof PsiNewExpression)) return null;
      if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) return null;
      PsiNewExpression newExpression = (PsiNewExpression)candidate;
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) return null;
      PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 1 || !terminal.isTargetReference(args[0])) return null;
      return new NewListTerminal(terminal, var, intermediateSteps, newExpression, type);
    }
  }
}
