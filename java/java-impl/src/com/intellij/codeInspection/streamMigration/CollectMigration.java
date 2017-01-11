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

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.isCallOf;

/**
 * @author Tagir Valeev
 */
class CollectMigration extends BaseStreamApiMigration {
  private static final Logger LOG = Logger.getInstance(CollectMigration.class);

  protected CollectMigration(String methodName) {
    super(methodName);
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
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    PsiLoopStatement loopStatement = tb.getMainLoop();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiMethodCallExpression call = tb.getSingleMethodCall();
    if (call == null) return null;
    CollectTerminal terminal = extractCollectTerminal(tb);
    if (terminal == null) return null;
    PsiVariable variable = terminal.getTargetVariable();
    LOG.assertTrue(variable != null);
    String stream = tb.generate() + terminal.generateIntermediate() + terminal.generateTerminal();
    InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(variable, loopStatement);
    if (status == InitializerUsageStatus.UNKNOWN) return null;
    PsiElement toReplace = terminal.getElementToReplace();
    restoreComments(loopStatement, body);
    PsiElement result;
    if (toReplace != null) {
      result = toReplace.replace(factory.createExpressionFromText(stream, toReplace));
      removeLoop(loopStatement);
      if (status != InitializerUsageStatus.AT_WANTED_PLACE) {
        variable.delete();
      }
    }
    else {
      result = replaceInitializer(loopStatement, variable, variable.getInitializer(), stream, status);
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
           CommonClassNames.JAVA_UTIL_MAP.equals(varClass.getQualifiedName());
  }

  @Nullable
  static PsiLocalVariable extractQualifierVariable(TerminalBlock tb, PsiMethodCallExpression call) {
    PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
    if (!(qualifierExpression instanceof PsiReferenceExpression)) return null;
    PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
    if (!(resolve instanceof PsiLocalVariable)) return null;
    PsiLocalVariable variable = (PsiLocalVariable)resolve;
    if (tb.getVariable() != variable && VariableAccessUtils.variableIsUsed(variable, call.getArgumentList())) return null;
    return variable;
  }

  @Nullable
  static CollectTerminal extractCollectTerminal(TerminalBlock tb) {
    PsiMethodCallExpression call = tb.getSingleMethodCall();
    if (call == null) return null;
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (tb.dependsOn(qualifierExpression)) return null;

    List<BiFunction<TerminalBlock, PsiMethodCallExpression, CollectTerminal>> extractors =
      Arrays.asList(AddingTerminal::tryExtract, GroupingTerminal::tryExtract, ToMapTerminal::tryExtract);

    CollectTerminal terminal = StreamEx.of(extractors).map(extractor -> extractor.apply(tb, call)).nonNull().findFirst().orElse(null);
    if (terminal != null) {
      PsiVariable variable = terminal.getTargetVariable();
      if (variable == null || variable.getInitializer() == null) return null;
      terminal = includePostStatements(terminal, tb.getMainLoop());
    }
    return terminal;
  }

  static CollectTerminal includePostStatements(CollectTerminal terminal, PsiLoopStatement loop) {
    List<BiFunction<CollectTerminal, PsiElement, CollectTerminal>> wrappers =
      Arrays.asList(SortingTerminal::tryWrap, (t, e) -> ToArrayTerminal.tryWrap(t, loop, e));
    PsiElement nextStatement = loop;
    while (true) {
      nextStatement = PsiTreeUtil.skipSiblingsForward(nextStatement, PsiComment.class, PsiWhiteSpace.class);
      CollectTerminal wrapped = null;
      for (BiFunction<CollectTerminal, PsiElement, CollectTerminal> wrapper : wrappers) {
        wrapped = wrapper.apply(terminal, nextStatement);
        if (wrapped != null) {
          terminal = wrapped;
          break;
        }
      }
      if (wrapped == null) {
        return terminal;
      }
    }
  }

  interface CollectTerminal {
    @Nullable
    default PsiElement getElementToReplace() { return null; }

    default String getMethodName() { return "collect"; }

    @Nullable
    PsiVariable getTargetVariable();

    default String generateIntermediate() { return ""; }

    String generateTerminal();

    default void cleanUp() {}

    default boolean isTrivial() {
      return generateIntermediate().isEmpty();
    }
  }

  static class AddingTerminal implements CollectTerminal {
    private @Nullable PsiVariable myTarget;
    private final PsiType myTargetType;
    private final PsiExpression myInitializer;
    private final PsiVariable myElement;
    private final PsiMethodCallExpression myAddCall;

    AddingTerminal(@NotNull PsiVariable target,
                   PsiVariable element,
                   PsiMethodCallExpression addCall) {
      this(target.getType(), target.getInitializer(), element, addCall);
      if (myInitializer instanceof PsiNewExpression) {
        final PsiExpressionList argumentList = ((PsiNewExpression)myInitializer).getArgumentList();
        if (argumentList != null && argumentList.getExpressions().length == 0) {
          myTarget = target;
        }
      }
    }

    AddingTerminal(@NotNull PsiType targetType,
                   PsiExpression initializer,
                   PsiVariable element,
                   PsiMethodCallExpression addCall) {
      myTarget = null;
      myTargetType = targetType;
      myInitializer = initializer;
      myElement = element;
      myAddCall = addCall;
    }

    @Override
    @Nullable
    public PsiVariable getTargetVariable() {
      return myTarget;
    }

    PsiVariable getElementVariable() {
      return myElement;
    }

    PsiExpression getMapping() {
      return myAddCall.getArgumentList().getExpressions()[0];
    }

    @Override
    public String generateIntermediate() {
      PsiType addedType = getAddedElementType(myAddCall);
      PsiExpression mapping = getMapping();
      if (addedType == null) addedType = mapping.getType();
      return new MapOp(mapping, myElement, addedType).createReplacement();
    }

    public String generateCollector() {
      String collector;
      PsiType initializerType = myInitializer.getType();
      PsiClassType rawType = initializerType instanceof PsiClassType ? ((PsiClassType)initializerType).rawType() : null;
      PsiClassType rawVarType = myTargetType instanceof PsiClassType ? ((PsiClassType)myTargetType).rawType() : null;
      if (rawType != null && rawVarType != null &&
          rawType.equalsToText(CommonClassNames.JAVA_UTIL_ARRAY_LIST) &&
          (rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_LIST) || rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_COLLECTION))) {
        collector = "toList()";
      }
      else if (rawType != null && rawVarType != null &&
               rawType.equalsToText(CommonClassNames.JAVA_UTIL_HASH_SET) &&
               (rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_SET) ||
                rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_COLLECTION))) {
        collector = "toSet()";
      }
      else {
        collector = "toCollection(() -> " + myInitializer.getText() + ")";
      }
      return CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + "." + collector;
    }

    @Override
    public String generateTerminal() {
      return ".collect(" + generateCollector() + ")";
    }

    @Nullable
    static AddingTerminal tryExtract(TerminalBlock tb, PsiMethodCallExpression call) {
      if (!isCallOf(call, CommonClassNames.JAVA_UTIL_COLLECTION, "add")) return null;
      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) return null;
      PsiExpression count = tb.getCountExpression();
      PsiLocalVariable variable = extractQualifierVariable(tb, call);
      if (variable != null) {
        AddingTerminal terminal = new AddingTerminal(variable, tb.getVariable(), call);
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

  static class GroupingTerminal implements CollectTerminal {
    private final AddingTerminal myDownstream;
    private final PsiLocalVariable myTarget;
    private final PsiExpression myKeyExpression;

    GroupingTerminal(AddingTerminal downstream, PsiLocalVariable target, PsiExpression expression) {
      myDownstream = downstream;
      myTarget = target;
      myKeyExpression = expression;
    }

    @Override
    public boolean isTrivial() {
      return false;
    }

    @Nullable
    @Override
    public PsiVariable getTargetVariable() {
      return myTarget;
    }

    @Override
    public String generateTerminal() {
      String downstreamCollector = myDownstream.generateCollector();
      PsiVariable elementVariable = myDownstream.getElementVariable();
      if (!ExpressionUtils.isReferenceTo(myDownstream.getMapping(), myDownstream.getElementVariable())) {
        downstreamCollector = CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".mapping(" +
                              myDownstream.getElementVariable().getName() + "->" + myDownstream.getMapping().getText() + "," +
                              downstreamCollector + ")";
      }
      StringBuilder builder = new StringBuilder();
      builder.append(".collect(" + CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".groupingBy(")
        .append(LambdaUtil.createLambda(elementVariable, myKeyExpression));
      PsiExpression initializer = myTarget.getInitializer();
      LOG.assertTrue(initializer != null);
      if (!isHashMap(myTarget)) {
        builder.append(",()->").append(initializer.getText()).append(",").append(downstreamCollector);
      }
      else if (!(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + "." + "toList()").equals(downstreamCollector)) {
        builder.append(",").append(downstreamCollector);
      }
      builder.append("))");
      return builder.toString();
    }

    @Nullable
    public static GroupingTerminal tryExtract(TerminalBlock tb, PsiMethodCallExpression call) {
      if (!isCallOf(call, CommonClassNames.JAVA_UTIL_COLLECTION, "add")) return null;
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();

      if (qualifierExpression instanceof PsiMethodCallExpression && tb.getCountExpression() == null) {
        PsiMethodCallExpression qualifierCall = (PsiMethodCallExpression)qualifierExpression;
        if (isCallOf(qualifierCall, CommonClassNames.JAVA_UTIL_MAP, "computeIfAbsent")) {
          PsiExpression[] args = qualifierCall.getArgumentList().getExpressions();
          if (args.length != 2 || !(args[1] instanceof PsiLambdaExpression)) return null;
          PsiLambdaExpression lambda = (PsiLambdaExpression)args[1];
          PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
          if (!(body instanceof PsiNewExpression)) return null;
          PsiExpressionList ctorArgs = ((PsiNewExpression)body).getArgumentList();
          if (ctorArgs != null && ctorArgs.getExpressions().length == 0) {
            PsiLocalVariable variable = extractQualifierVariable(tb, qualifierCall);
            if (variable != null && variable.getInitializer() instanceof PsiNewExpression) {
              PsiType mapType = variable.getType();
              PsiType valueType = PsiUtil.substituteTypeParameter(mapType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
              if (valueType == null) return null;
              AddingTerminal adding = new AddingTerminal(valueType, body, tb.getVariable(), call);
              return new GroupingTerminal(adding, variable, args[0]);
            }
          }
        }
      }
      return null;
    }
  }

  static class ToMapTerminal implements CollectTerminal {
    private final PsiMethodCallExpression myMapUpdateCall;
    private final PsiLocalVariable myTargetVariable;
    private final PsiVariable myElementVariable;

    ToMapTerminal(PsiMethodCallExpression call, PsiVariable elementVariable, PsiLocalVariable variable) {
      myMapUpdateCall = call;
      myTargetVariable = variable;
      myElementVariable = elementVariable;
    }

    @Nullable
    @Override
    public PsiVariable getTargetVariable() {
      return myTargetVariable;
    }

    @Override
    public String generateTerminal() {
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
          merger = args[2].getText();
          break;
        default:
          return null;
      }
      StringBuilder collector = new StringBuilder(".collect(" + CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".toMap(");
      collector.append(LambdaUtil.createLambda(myElementVariable, args[0])).append(',')
        .append(LambdaUtil.createLambda(myElementVariable, args[1])).append(',')
        .append(merger);
      PsiExpression initializer = myTargetVariable.getInitializer();
      LOG.assertTrue(initializer != null);
      if (!isHashMap(myTargetVariable)) {
        collector.append(",()->").append(initializer.getText());
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
      if (variable == null) return null;
      PsiExpression initializer = variable.getInitializer();
      if (!(initializer instanceof PsiNewExpression)) return null;
      PsiExpressionList argumentList = ((PsiNewExpression)initializer).getArgumentList();
      if (argumentList == null || argumentList.getExpressions().length != 0) return null;
      return new ToMapTerminal(call, tb.getVariable(), variable);
    }
  }

  static class SortingTerminal implements CollectTerminal {
    private final CollectTerminal myDownstream;
    private final PsiExpression myComparator;
    private final PsiStatement myStatement;

    SortingTerminal(CollectTerminal downstream, PsiStatement statement, PsiExpression comparator) {
      myDownstream = downstream;
      myStatement = statement;
      myComparator = comparator;
    }

    @Override
    public String getMethodName() {
      return myDownstream.getMethodName();
    }

    @Nullable
    @Override
    public PsiVariable getTargetVariable() {
      return myDownstream.getTargetVariable();
    }

    @Override
    public String generateIntermediate() {
      return myDownstream.generateIntermediate() + ".sorted("
             + (myComparator == null ? "" : myComparator.getText()) + ")";
    }

    @Override
    public String generateTerminal() {
      return myDownstream.generateTerminal();
    }

    @Override
    public void cleanUp() {
      myDownstream.cleanUp();
      myStatement.delete();
    }

    @Contract("null, _ -> null")
    public static CollectTerminal tryWrap(CollectTerminal terminal, PsiElement element) {
      PsiVariable list = terminal.getTargetVariable();
      if (list == null || !(element instanceof PsiExpressionStatement)) return null;
      PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
      PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      if (!"sort".equals(methodExpression.getReferenceName())) return null;
      PsiMethod method = methodCall.resolveMethod();
      if (method == null) return null;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return null;
      PsiExpression listExpression = null;
      PsiExpression comparatorExpression = null;
      if (CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(containingClass.getQualifiedName())) {
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length == 1) {
          listExpression = args[0];
        }
        else if (args.length == 2) {
          listExpression = args[0];
          comparatorExpression = args[1];
        }
        else {
          return null;
        }
      }
      else if (InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_LIST)) {
        listExpression = methodExpression.getQualifierExpression();
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length != 1) return null;
        comparatorExpression = args[0];
      }
      if (!(listExpression instanceof PsiReferenceExpression) || !((PsiReferenceExpression)listExpression).isReferenceTo(list)) {
        return null;
      }
      if (ExpressionUtils.isNullLiteral(comparatorExpression)) {
        comparatorExpression = null;
      }
      return new SortingTerminal(terminal, (PsiExpressionStatement)element, comparatorExpression);
    }
  }

  static class ToArrayTerminal implements CollectTerminal {
    static final Map<String, String> INTERMEDIATE_STEPS = EntryStream.of(
      CommonClassNames.JAVA_UTIL_ARRAY_LIST, "",
      "java.util.LinkedList", "",
      CommonClassNames.JAVA_UTIL_HASH_SET, ".distinct()",
      "java.util.LinkedHashSet", ".distinct()",
      "java.util.TreeSet", ".distinct().sorted()"
    ).toMap();

    private final CollectTerminal myUpstream;
    private final String myIntermediate;
    private final PsiMethodCallExpression myToArrayExpression;
    private final String mySupplier;

    ToArrayTerminal(CollectTerminal upstream,
                    String intermediate,
                    String supplier,
                    PsiMethodCallExpression toArrayExpression) {
      myUpstream = upstream;
      mySupplier = supplier;
      myIntermediate = intermediate;
      myToArrayExpression = toArrayExpression;
    }

    @Override
    public boolean isTrivial() {
      return false;
    }

    @Override
    public String getMethodName() {
      return "toArray";
    }

    @Nullable
    @Override
    public PsiElement getElementToReplace() {
      return myToArrayExpression;
    }

    @Nullable
    @Override
    public PsiVariable getTargetVariable() {
      return myUpstream.getTargetVariable();
    }

    @Override
    public String generateIntermediate() {
      return myUpstream.generateIntermediate() + myIntermediate;
    }

    @Override
    public String generateTerminal() {
      return ".toArray(" + mySupplier + ")";
    }

    @Override
    public void cleanUp() {
      myUpstream.cleanUp();
    }

    @Contract("null, _, _ -> null")
    public static ToArrayTerminal tryWrap(CollectTerminal terminal, PsiLoopStatement loopStatement, PsiElement element) {
      PsiVariable collectionVariable = terminal.getTargetVariable();
      if (collectionVariable == null || StreamApiMigrationInspection.getInitializerUsageStatus(collectionVariable, loopStatement)
                                        == InitializerUsageStatus.UNKNOWN) {
        return null;
      }
      PsiExpression initializer = collectionVariable.getInitializer();
      if (initializer == null) return null;
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(initializer.getType());
      if (aClass == null) return null;
      String intermediateSteps = INTERMEDIATE_STEPS.get(aClass.getQualifiedName());
      if (intermediateSteps == null) return null;

      PsiMethodCallExpression toArrayExpression =
        extractToArrayExpression(element, loopStatement, collectionVariable);
      if (toArrayExpression == null) return null;
      PsiExpression[] args = toArrayExpression.getArgumentList().getExpressions();
      String supplier;
      if (args.length == 0) {
        supplier = "";
      }
      else {
        if (args.length != 1 || !(args[0] instanceof PsiNewExpression)) return null;
        PsiNewExpression newArray = (PsiNewExpression)args[0];
        PsiType arrayType = newArray.getType();
        if (arrayType == null) return null;
        String name = arrayType.getCanonicalText();
        supplier = name + "::new";
      }
      return new ToArrayTerminal(terminal, intermediateSteps, supplier, toArrayExpression);
    }

    @Nullable
    static PsiMethodCallExpression extractToArrayExpression(PsiElement nextElement, PsiLoopStatement statement, PsiVariable collectionVariable) {
      // return collection.toArray() or collection.toArray(new Type[0]) or collection.toArray(new Type[collection.size()]);
      PsiExpression toArrayCandidate;
      if (nextElement instanceof PsiReturnStatement) {
        toArrayCandidate = ((PsiReturnStatement)nextElement).getReturnValue();
      }
      else {
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(nextElement);
        if (assignment != null) {
          toArrayCandidate = assignment.getRExpression();
        }
        else if (nextElement instanceof PsiDeclarationStatement) {
          PsiElement[] elements = ((PsiDeclarationStatement)nextElement).getDeclaredElements();
          if (elements.length == 1 && elements[0] instanceof PsiLocalVariable) {
            toArrayCandidate = ((PsiLocalVariable)elements[0]).getInitializer();
          }
          else {
            return null;
          }
        }
        else {
          return null;
        }
      }
      if (!(toArrayCandidate instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression call = (PsiMethodCallExpression)toArrayCandidate;
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      if (!"toArray".equals(methodExpression.getReferenceName())) return null;
      if (!ExpressionUtils.isReferenceTo(methodExpression.getQualifierExpression(), collectionVariable)) return null;

      if (!(nextElement instanceof PsiReturnStatement) && !ReferencesSearch.search(collectionVariable)
        .forEach(ref ->
                   ref.getElement() == collectionVariable || PsiTreeUtil.isAncestor(statement, ref.getElement(), false) ||
                   PsiTreeUtil.isAncestor(toArrayCandidate, ref.getElement(), false)
        )) {
        return null;
      }

      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length == 0) return call;
      if (args.length != 1 || !(args[0] instanceof PsiNewExpression)) return null;
      PsiNewExpression newArray = (PsiNewExpression)args[0];
      PsiExpression[] dimensions = newArray.getArrayDimensions();
      if (dimensions.length != 1) return null;
      if (ExpressionUtils.isLiteral(dimensions[0], 0)) return call;
      if (!(dimensions[0] instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression maybeSizeCall = (PsiMethodCallExpression)dimensions[0];
      if (!isCallOf(maybeSizeCall, CommonClassNames.JAVA_UTIL_COLLECTION, "size")) return null;
      PsiExpression sizeQualifier = maybeSizeCall.getMethodExpression().getQualifierExpression();
      if (!ExpressionUtils.isReferenceTo(sizeQualifier, collectionVariable)) return null;
      return call;
    }
  }
}
