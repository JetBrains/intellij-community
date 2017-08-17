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
package com.intellij.codeInspection.streamToLoop;

import com.intellij.codeInspection.streamToLoop.StreamToLoopInspection.ResultKind;
import com.intellij.codeInspection.streamToLoop.StreamToLoopInspection.StreamToLoopReplacementContext;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Tagir Valeev
 */
abstract class TerminalOperation extends Operation {
  @Override
  final String wrap(StreamVariable inVar, StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
    return generate(inVar, context);
  }

  @Override
  final void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
    throw new IllegalStateException("Should not be called for terminal operation (tried to rename " + oldName + " -> " + newName + ")");
  }

  @Override
  final boolean changesVariable() {
    return true;
  }

  CollectorOperation asCollector() {
    return null;
  }

  abstract String generate(StreamVariable inVar, StreamToLoopReplacementContext context);

  @Nullable
  static TerminalOperation createTerminal(@NotNull String name, @NotNull PsiExpression[] args,
                                          @NotNull PsiType elementType, @NotNull PsiType resultType, boolean isVoid) {
    if(isVoid) {
      if ((name.equals("forEach") || name.equals("forEachOrdered")) && args.length == 1) {
        FunctionHelper fn = FunctionHelper.create(args[0], 1, true);
        return fn == null ? null : new ForEachTerminalOperation(fn);
      }
      return null;
    }
    if(name.equals("count") && args.length == 0) {
      return TemplateBasedOperation.counting();
    }
    if(name.equals("sum") && args.length == 0) {
      return TemplateBasedOperation.summing(resultType);
    }
    if(name.equals("average") && args.length == 0) {
      if(elementType.equals(PsiType.DOUBLE)) {
        return new AverageTerminalOperation(true, true);
      }
      else if(elementType.equals(PsiType.INT) || elementType.equals(PsiType.LONG)) {
        return new AverageTerminalOperation(false, true);
      }
    }
    if(name.equals("summaryStatistics") && args.length == 0) {
      return TemplateBasedOperation.summarizing(resultType);
    }
    if((name.equals("findFirst") || name.equals("findAny")) && args.length == 0) {
      PsiType optionalElementType = OptionalUtil.getOptionalElementType(resultType);
      return optionalElementType == null ? null : new FindTerminalOperation(optionalElementType);
    }
    if(name.equals("toList") && args.length == 0) {
      return ToCollectionTerminalOperation.toList(resultType);
    }
    if(name.equals("toSet") && args.length == 0) {
      return ToCollectionTerminalOperation.toSet(resultType);
    }
    if((name.equals("anyMatch") || name.equals("allMatch") || name.equals("noneMatch")) && args.length == 1) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new MatchTerminalOperation(fn, name);
    }
    if(name.equals("reduce")) {
      if(args.length == 2 || args.length == 3) {
        FunctionHelper fn = FunctionHelper.create(args[1], 2);
        if(fn != null) {
          return new ReduceTerminalOperation(args[0], fn, resultType);
        }
      }
      if(args.length == 1) {
        return ReduceToOptionalTerminalOperation.create(args[0], resultType);
      }
    }
    if(name.equals("toArray") && args.length < 2) {
      if(!(resultType instanceof PsiArrayType)) return null;
      PsiType componentType = ((PsiArrayType)resultType).getComponentType();
      if (componentType instanceof PsiPrimitiveType) {
        if (args.length == 0) return new ToPrimitiveArrayTerminalOperation(componentType);
      }
      else {
        FunctionHelper fn = null;
        if(args.length == 1) {
          fn = FunctionHelper.create(args[0], 1);
          if(fn == null) return null;
        }
        return new ToArrayTerminalOperation(elementType, fn);
      }
    }
    if ((name.equals("max") || name.equals("min")) && args.length < 2) {
      return MinMaxTerminalOperation.create(args.length == 1 ? args[0] : null, elementType, name.equals("max"));
    }
    if (name.equals("collect")) {
      if (args.length == 3) {
        FunctionHelper supplier = FunctionHelper.create(args[0], 0);
        if (supplier == null) return null;
        FunctionHelper accumulator = FunctionHelper.create(args[1], 2);
        if (accumulator == null) return null;
        return new ExplicitCollectTerminalOperation(supplier, accumulator);
      }
      if (args.length == 1) {
        return fromCollector(elementType, resultType, args[0]);
      }
    }
    return null;
  }

  @Contract("_, _, null -> null")
  @Nullable
  private static TerminalOperation fromCollector(@NotNull PsiType elementType, @NotNull PsiType resultType, PsiExpression expr) {
    if (!(expr instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression collectorCall = (PsiMethodCallExpression)expr;
    PsiExpression[] collectorArgs = collectorCall.getArgumentList().getExpressions();
    PsiMethod collector = collectorCall.resolveMethod();
    if (collector == null) return null;
    PsiClass collectorClass = collector.getContainingClass();
    if (collectorClass != null && CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS.equals(collectorClass.getQualifiedName())) {
      return fromCollector(elementType, resultType, collector, collectorArgs);
    }
    return null;
  }

  @Nullable
  private static TerminalOperation fromCollector(@NotNull PsiType elementType,
                                                 @NotNull PsiType resultType,
                                                 PsiMethod collector,
                                                 PsiExpression[] collectorArgs) {
    String collectorName = collector.getName();
    FunctionHelper fn;
    switch (collectorName) {
      case "toList":
        if (collectorArgs.length != 0) return null;
        return ToCollectionTerminalOperation.toList(resultType);
      case "toSet":
        if (collectorArgs.length != 0) return null;
        return ToCollectionTerminalOperation.toSet(resultType);
      case "toCollection":
        if (collectorArgs.length != 1) return null;
        fn = FunctionHelper.create(collectorArgs[0], 0);
        return fn == null ? null : new ToCollectionTerminalOperation(resultType, fn, null);
      case "toMap": {
        if (collectorArgs.length < 2 || collectorArgs.length > 4) return null;
        FunctionHelper key = FunctionHelper.create(collectorArgs[0], 1);
        FunctionHelper value = FunctionHelper.create(collectorArgs[1], 1);
        if(key == null || value == null) return null;
        PsiExpression merger = collectorArgs.length > 2 ? collectorArgs[2] : null;
        FunctionHelper supplier = collectorArgs.length == 4
                   ? FunctionHelper.create(collectorArgs[3], 0)
                   : FunctionHelper.newObjectSupplier(resultType, CommonClassNames.JAVA_UTIL_HASH_MAP);
        if(supplier == null) return null;
        return new ToMapTerminalOperation(key, value, merger, supplier, resultType);
      }
      case "reducing":
        switch (collectorArgs.length) {
          case 1:
            return ReduceToOptionalTerminalOperation.create(collectorArgs[0], resultType);
          case 2:
            fn = FunctionHelper.create(collectorArgs[1], 2);
            return fn == null ? null : new ReduceTerminalOperation(collectorArgs[0], fn, resultType);
          case 3:
            FunctionHelper mapper = FunctionHelper.create(collectorArgs[1], 1);
            fn = FunctionHelper.create(collectorArgs[2], 2);
            return fn == null || mapper == null
                   ? null
                   : new MappingTerminalOperation(mapper, new ReduceTerminalOperation(collectorArgs[0], fn, resultType));
        }
        return null;
      case "counting":
        if (collectorArgs.length != 0) return null;
        return TemplateBasedOperation.counting();
      case "summingInt":
      case "summingLong":
      case "summingDouble": {
        if (collectorArgs.length != 1) return null;
        fn = FunctionHelper.create(collectorArgs[0], 1);
        PsiPrimitiveType type = PsiPrimitiveType.getUnboxedType(resultType);
        return fn == null || type == null ? null : new InlineMappingTerminalOperation(fn, TemplateBasedOperation.summing(type));
      }
      case "summarizingInt":
      case "summarizingLong":
      case "summarizingDouble": {
        if (collectorArgs.length != 1) return null;
        fn = FunctionHelper.create(collectorArgs[0], 1);
        return fn == null ? null : new InlineMappingTerminalOperation(fn, TemplateBasedOperation.summarizing(resultType));
      }
      case "averagingInt":
      case "averagingLong":
      case "averagingDouble": {
        if (collectorArgs.length != 1) return null;
        fn = FunctionHelper.create(collectorArgs[0], 1);
        return fn == null
               ? null
               : new InlineMappingTerminalOperation(fn, new AverageTerminalOperation(collectorName.equals("averagingDouble"), false));
      }
      case "mapping": {
        if (collectorArgs.length != 2) return null;
        fn = FunctionHelper.create(collectorArgs[0], 1);
        if (fn == null) return null;
        TerminalOperation downstreamOp = fromCollector(fn.getResultType(), resultType, collectorArgs[1]);
        return downstreamOp == null ? null : new MappingTerminalOperation(fn, downstreamOp);
      }
      case "groupingBy":
      case "partitioningBy": {
        if (collectorArgs.length == 0 || collectorArgs.length > 3
            || collectorArgs.length == 3 && collectorName.equals("partitioningBy")) return null;
        fn = FunctionHelper.create(collectorArgs[0], 1);
        if (fn == null) return null;
        PsiType resultSubType = PsiUtil.substituteTypeParameter(resultType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
        if (resultSubType == null) return null;
        CollectorOperation downstreamCollector;
        if (collectorArgs.length == 1) {
          downstreamCollector = ToCollectionTerminalOperation.toList(resultSubType).asCollector();
        }
        else {
          PsiExpression downstream = collectorArgs[collectorArgs.length - 1];
          TerminalOperation downstreamOp = fromCollector(elementType, resultSubType, downstream);
          if (downstreamOp == null) return null;
          downstreamCollector = downstreamOp.asCollector();
        }
        if (downstreamCollector == null) return null;
        if (collectorName.equals("partitioningBy")) {
          return new PartitionByTerminalOperation(fn, resultType, downstreamCollector);
        }
        FunctionHelper supplier = collectorArgs.length == 3
                                  ? FunctionHelper.create(collectorArgs[1], 0)
                                  : FunctionHelper.newObjectSupplier(resultType, CommonClassNames.JAVA_UTIL_HASH_MAP);
        return new GroupByTerminalOperation(fn, supplier, resultType, downstreamCollector);
      }
      case "minBy":
      case "maxBy":
        if (collectorArgs.length != 1) return null;
        return MinMaxTerminalOperation.create(collectorArgs[0], elementType, collectorName.equals("maxBy"));
      case "joining":
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(collector.getProject());
        switch (collectorArgs.length) {
          case 0:
            return new TemplateBasedOperation("sb", factory.createTypeFromText(CommonClassNames.JAVA_LANG_STRING_BUILDER, collector),
                                                    "new " + CommonClassNames.JAVA_LANG_STRING_BUILDER + "()",
                                              "{acc}.append({item});",
                                              "{acc}.toString()");
          case 1:
          case 3:
            String initializer =
              "new java.util.StringJoiner(" + StreamEx.of(collectorArgs).map(PsiElement::getText).joining(",") + ")";
            return new TemplateBasedOperation("joiner", factory.createTypeFromText("java.util.StringJoiner", collector), initializer,
                                              "{acc}.add({item});", "{acc}.toString()");
        }
        return null;
    }
    return null;
  }

  /**
   * Eliminates &lt;? extends&gt; wildcards from type parameters which directly map to the supplied superclass
   * type parameters and performs downstream correction steps if necessary.
   *
   * @param type type to process
   * @param superClass superclass which type parameters should be corrected
   * @param downstreamCorrectors Map which keys are superclass type parameter names and values are functions to perform additional
   *                             superclass type parameter correction if necessary
   * @return the corrected type.
   */
  @NotNull
  static PsiType correctTypeParameters(PsiType type, String superClass, Map<String, Function<PsiType, PsiType>> downstreamCorrectors) {
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if(aClass == null) return type;

    PsiSubstitutor origSubstitutor = ((PsiClassType)type).resolveGenerics().getSubstitutor();
    PsiSubstitutor substitutor = origSubstitutor;
    Project project = aClass.getProject();
    PsiClass baseClass = JavaPsiFacade.getInstance(project).findClass(superClass, aClass.getResolveScope());
    if(baseClass == null) return type;
    PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(baseClass, aClass, PsiSubstitutor.EMPTY, null);
    if(superClassSubstitutor == null) {
      // inconsistent class hierarchy: probably something is not resolved
      superClassSubstitutor = PsiSubstitutor.EMPTY;
    }
    for (PsiTypeParameter baseParameter : baseClass.getTypeParameters()) {
      PsiClass substitution = PsiUtil.resolveClassInClassTypeOnly(superClassSubstitutor.substitute(baseParameter));
      if(substitution instanceof PsiTypeParameter) {
        PsiTypeParameter subClassParameter = (PsiTypeParameter)substitution;
        PsiType origType = origSubstitutor.substitute(subClassParameter);
        PsiType replacedType = GenericsUtil.eliminateWildcards(origType, false, true);
        replacedType = downstreamCorrectors.getOrDefault(subClassParameter.getName(), Function.identity()).apply(replacedType);
        if(replacedType != origType) {
          substitutor = substitutor.put(subClassParameter, replacedType);
        }
      }
    }
    return substitutor == origSubstitutor ? type : JavaPsiFacade.getElementFactory(project).createType(aClass, substitutor);
  }

  abstract static class AccumulatedOperation extends TerminalOperation {
    abstract String initAccumulator(StreamVariable inVar, StreamToLoopReplacementContext context);
    abstract String getAccumulatorUpdater(StreamVariable inVar, String acc);

    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      String acc = initAccumulator(inVar, context);
      return getAccumulatorUpdater(inVar, acc);
    }
  }

  static class ReduceTerminalOperation extends TerminalOperation {
    private PsiExpression myIdentity;
    private PsiType myType;
    private FunctionHelper myUpdater;

    public ReduceTerminalOperation(PsiExpression identity, FunctionHelper updater, PsiType type) {
      myIdentity = identity;
      myType = type;
      myUpdater = updater;
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myIdentity);
      myUpdater.registerReusedElements(consumer);
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      String accumulator = context.declareResult("acc", myType, myIdentity.getText(), ResultKind.NON_FINAL);
      myUpdater.transform(context, accumulator, inVar.getName());
      return accumulator + "=" + myUpdater.getText() + ";";
    }
  }

  static class ReduceToOptionalTerminalOperation extends TerminalOperation {
    private PsiType myType;
    private FunctionHelper myUpdater;

    public ReduceToOptionalTerminalOperation(FunctionHelper updater, PsiType type) {
      myType = type;
      myUpdater = updater;
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      myUpdater.registerReusedElements(consumer);
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      String seen = context.declare("seen", "boolean", "false");
      String accumulator = context.declareResult("acc", myType, myType instanceof PsiPrimitiveType ? "0" : "null", ResultKind.UNKNOWN);
      myUpdater.transform(context, accumulator, inVar.getName());
      context.setFinisher(new ConditionalExpression.Optional(myType, seen, accumulator));
      String ifClause = "if(!" + seen + ") {\n" +
                        seen + "=true;\n" +
                        accumulator + "=" + inVar + ";\n" +
                        "}";
      if(myUpdater.getText().equals(accumulator)) {
        return ifClause + "\n";
      }
      return ifClause + " else {\n" + accumulator + "=" + myUpdater.getText() + ";\n}\n";
    }

    @Nullable
    static ReduceToOptionalTerminalOperation create(PsiExpression arg, PsiType resultType) {
      PsiType optionalElementType = OptionalUtil.getOptionalElementType(resultType);
      FunctionHelper fn = FunctionHelper.create(arg, 2);
      if(fn != null && optionalElementType != null) {
        return new ReduceToOptionalTerminalOperation(fn, optionalElementType);
      }
      return null;
    }
  }

  static class ExplicitCollectTerminalOperation extends TerminalOperation {
    private final FunctionHelper mySupplier;
    private final FunctionHelper myAccumulator;

    public ExplicitCollectTerminalOperation(FunctionHelper supplier, FunctionHelper accumulator) {
      mySupplier = supplier;
      myAccumulator = accumulator;
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      mySupplier.registerReusedElements(consumer);
      myAccumulator.registerReusedElements(consumer);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      myAccumulator.preprocessVariable(context, inVar, 1);
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      mySupplier.transform(context);
      String candidate = mySupplier.suggestFinalOutputNames(context, myAccumulator.getParameterName(0), "acc").get(0);
      String acc = context.declareResult(candidate, mySupplier.getResultType(), mySupplier.getText(), ResultKind.FINAL);
      myAccumulator.transform(context, acc, inVar.getName());
      return myAccumulator.getStatementText();
    }
  }

  static class AverageTerminalOperation extends TerminalOperation {
    private final boolean myDoubleAccumulator;
    private final boolean myUseOptional;

    public AverageTerminalOperation(boolean doubleAccumulator, boolean useOptional) {
      myDoubleAccumulator = doubleAccumulator;
      myUseOptional = useOptional;
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      String sum = context.declareResult("sum", myDoubleAccumulator ? PsiType.DOUBLE : PsiType.LONG, "0", ResultKind.UNKNOWN);
      String count = context.declare("count", "long", "0");
      String seenCheck = count + ">0";
      String result = (myDoubleAccumulator ? "" : "(double)") + sum + "/" + count;
      ConditionalExpression conditionalExpression = myUseOptional ?
                                                    new ConditionalExpression.Optional(PsiType.DOUBLE, seenCheck, result) :
                                                    new ConditionalExpression.Plain(PsiType.DOUBLE, seenCheck, result, "0.0");
      context.setFinisher(conditionalExpression);
      return sum + "+=" + inVar + ";\n" + count + "++;\n";
    }
  }

  static class ToPrimitiveArrayTerminalOperation extends TerminalOperation {
    private PsiType myType;

    ToPrimitiveArrayTerminalOperation(PsiType type) {
      myType = type;
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      String arr =
        context.declareResult("arr", myType.createArrayType(), "new " + myType.getCanonicalText() + "[10]", ResultKind.NON_FINAL);
      String count = context.declare("count", "int", "0");
      context.addAfterStep(arr + "=java.util.Arrays.copyOfRange(" + arr + ",0," + count + ");\n");
      return "if(" + arr + ".length==" + count + ") " + arr + "=java.util.Arrays.copyOf(" + arr + "," + count + "*2);\n" +
             arr + "[" + count + "++]=" + inVar + ";\n";
    }
  }

  static class ToArrayTerminalOperation extends AccumulatedOperation {
    private final PsiType myType;
    private final FunctionHelper mySupplier;

    public ToArrayTerminalOperation(PsiType type, FunctionHelper supplier) {
      myType = type;
      mySupplier = supplier;
    }

    @Override
    String initAccumulator(StreamVariable inVar, StreamToLoopReplacementContext context) {
      String list =
        context.declareResult("list", context.createType(CommonClassNames.JAVA_UTIL_LIST + "<" + myType.getCanonicalText() + ">"),
                                          "new " + CommonClassNames.JAVA_UTIL_ARRAY_LIST + "<>()", ResultKind.UNKNOWN);
      String toArrayArg = "";
      if(mySupplier != null) {
        mySupplier.transform(context, "0");
        toArrayArg = mySupplier.getText();
      }
      context.setFinisher(list + ".toArray(" + toArrayArg + ")");
      return list;
    }

    @Override
    String getAccumulatorUpdater(StreamVariable inVar, String list) {
      return list+".add("+inVar+");\n";
    }
  }

  static class FindTerminalOperation extends TerminalOperation {
    private PsiType myType;

    public FindTerminalOperation(PsiType type) {
      myType = type;
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      return context.assignAndBreak(new ConditionalExpression.Optional(myType, "found", inVar.getName()));
    }
  }

  static class MatchTerminalOperation extends TerminalOperation {
    private final FunctionHelper myFn;
    private final boolean myDefaultValue, myNegatePredicate;

    public MatchTerminalOperation(FunctionHelper fn, String name) {
      myFn = fn;
      switch(name) {
        case "anyMatch":
          myDefaultValue = false;
          myNegatePredicate = false;
          break;
        case "allMatch":
          myDefaultValue = true;
          myNegatePredicate = true;
          break;
        case "noneMatch":
          myDefaultValue = true;
          myNegatePredicate = false;
          break;
        default:
          throw new IllegalArgumentException(name);
      }
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      myFn.registerReusedElements(consumer);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      myFn.preprocessVariable(context, inVar, 0);
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      myFn.transform(context, inVar.getName());
      String expression;
      if (myNegatePredicate) {
        expression = BoolUtils.getNegatedExpressionText(myFn.getExpression());
      }
      else {
        expression = myFn.getText();
      }
      return "if(" + expression + ") {\n" +
             context.assignAndBreak(new ConditionalExpression.Boolean("b", myDefaultValue)) +
             "}\n";
    }
  }

  interface CollectorOperation {
    // Non-trivial finishers are not supported
    default void transform(StreamToLoopReplacementContext context, String item) {}
    default void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {}
    default void registerReusedElements(Consumer<PsiElement> consumer) {}
    String getSupplier();
    String getAccumulatorUpdater(StreamVariable inVar, String acc);

    default String getMerger(StreamVariable inVar, String map, String key) {
      return null;
    }

    default PsiType correctReturnType(PsiType type) {return type;}
  }

  abstract static class CollectorBasedTerminalOperation extends AccumulatedOperation implements CollectorOperation {
    final PsiType myType;
    final Function<StreamToLoopReplacementContext, String> myAccNameSupplier;
    final FunctionHelper mySupplier;

    CollectorBasedTerminalOperation(PsiType type, Function<StreamToLoopReplacementContext, String> accNameSupplier,
                                    FunctionHelper accSupplier) {
      myType = type;
      myAccNameSupplier = accNameSupplier;
      mySupplier = accSupplier;
    }

    @Override
    String initAccumulator(StreamVariable inVar, StreamToLoopReplacementContext context) {
      transform(context, inVar.getName());
      PsiType resultType = correctReturnType(myType);
      return context.declareResult(myAccNameSupplier.apply(context), resultType, getSupplier(), ResultKind.FINAL);
    }

    @Override
    CollectorOperation asCollector() {
      return this;
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      mySupplier.registerReusedElements(consumer);
    }

    @Override
    public void transform(StreamToLoopReplacementContext context, String item) {
      mySupplier.transform(context);
    }

    @Override
    public String getSupplier() {
      return mySupplier.getText();
    }
  }

  static class TemplateBasedOperation extends AccumulatedOperation implements CollectorOperation {
    private String myAccName;
    private PsiType myAccType;
    private String myAccInitializer;
    private String myUpdateTemplate;
    private String myFinisherTemplate;

    /**
     * @param accName desired name for accumulator variable
     * @param accType type of accumulator variable
     * @param accInitializer initializer for accumulator variable
     * @param updateTemplate template to update accumulator. May contain {@code {acc}} - reference to accumulator variable
     *                       and {@code {item}} - reference to stream element.
     * @param finisherTemplate template to final result. May contain {@code {acc}} - reference to accumulator variable.
     *                         By default it's {@code "{acc}"}
     */
    TemplateBasedOperation(String accName, PsiType accType, String accInitializer, String updateTemplate, String finisherTemplate) {
      myAccName = accName;
      myAccType = accType;
      myAccInitializer = accInitializer;
      myUpdateTemplate = updateTemplate;
      myFinisherTemplate = finisherTemplate;
    }

    TemplateBasedOperation(String accName, PsiType accType, String accInitializer, String updateTemplate) {
      this(accName, accType, accInitializer, updateTemplate, "{acc}");
    }

    @Override
    String initAccumulator(StreamVariable inVar, StreamToLoopReplacementContext context) {
      ResultKind kind = myFinisherTemplate.equals("{acc}") ?
                        myAccType instanceof PsiPrimitiveType ? ResultKind.NON_FINAL : ResultKind.FINAL : ResultKind.UNKNOWN;
      String varName = context.declareResult(myAccName, myAccType, myAccInitializer, kind);
      context.setFinisher(myFinisherTemplate.replace("{acc}", varName));
      return varName;
    }

    @Override
    CollectorOperation asCollector() {
      return myFinisherTemplate.equals("{acc}") ? this : null;
    }

    @Override
    public String getSupplier() {
      return myAccInitializer;
    }

    @Override
    public String getAccumulatorUpdater(StreamVariable inVar, String acc) {
      return myUpdateTemplate.replace("{acc}", acc).replace("{item}", inVar.getName());
    }

    @Override
    public String getMerger(StreamVariable inVar, String map, String key) {
      if(!(myAccType instanceof PsiPrimitiveType)) return null;
      String boxedType = PsiTypesUtil.boxIfPossible(myAccType.getCanonicalText());
      String val = myUpdateTemplate.equals("{acc}++;") ? "1L" : "(" + myAccType.getCanonicalText() + ")" + inVar;
      String merger = boxedType + "::sum";
      return map + ".merge(" + key + "," + val + "," + merger + ");\n";
    }

    @NotNull
    static TemplateBasedOperation summing(PsiType type) {
      String defValue = type.equals(PsiType.DOUBLE) ? "0.0" : type.equals(PsiType.LONG) ? "0L" : "0";
      return new TemplateBasedOperation("sum", type, defValue, "{acc}+={item};");
    }

    @NotNull
    static TemplateBasedOperation summarizing(@NotNull PsiType resultType) {
      return new TemplateBasedOperation("stat", resultType, "new " + resultType.getCanonicalText() + "()",
                                        "{acc}.accept({item});");
    }

    @NotNull
    static TemplateBasedOperation counting() {
      return new TemplateBasedOperation("count", PsiType.LONG, "0L", "{acc}++;");
    }
  }

  static class ToCollectionTerminalOperation extends CollectorBasedTerminalOperation {
    private final boolean myList;

    public ToCollectionTerminalOperation(PsiType resultType, FunctionHelper fn, String desiredName) {
      super(resultType, context -> fn.suggestFinalOutputNames(context, desiredName, "collection").get(0), fn);
      myList = InheritanceUtil.isInheritor(resultType, CommonClassNames.JAVA_UTIL_LIST);
    }

    @Override
    public String getAccumulatorUpdater(StreamVariable inVar, String acc) {
      return acc + ".add(" + inVar + ");\n";
    }

    @Override
    public PsiType correctReturnType(PsiType type) {
      return correctTypeParameters(type, CommonClassNames.JAVA_UTIL_COLLECTION, Collections.emptyMap());
    }

    public boolean isList() {
      return myList;
    }

    @NotNull
    private static ToCollectionTerminalOperation toList(@NotNull PsiType resultType) {
      return new ToCollectionTerminalOperation(resultType,
                                               FunctionHelper.newObjectSupplier(resultType, CommonClassNames.JAVA_UTIL_ARRAY_LIST), "list");
    }

    @NotNull
    private static ToCollectionTerminalOperation toSet(@NotNull PsiType resultType) {
      return new ToCollectionTerminalOperation(resultType,
                                               FunctionHelper.newObjectSupplier(resultType, CommonClassNames.JAVA_UTIL_HASH_SET), "set");
    }
  }

  static class MinMaxTerminalOperation extends TerminalOperation {
    private PsiType myType;
    private String myTemplate;
    private @Nullable FunctionHelper myComparator;

    public MinMaxTerminalOperation(PsiType type, String template, @Nullable FunctionHelper comparator) {
      myType = type;
      myTemplate = template;
      myComparator = comparator;
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      if(myComparator != null) {
        myComparator.registerReusedElements(consumer);
      }
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      String seen = context.declare("seen", "boolean", "false");
      String best = context.declareResult("best", myType, myType instanceof PsiPrimitiveType ? "0" : "null", ResultKind.UNKNOWN);
      context.setFinisher(new ConditionalExpression.Optional(myType, seen, best));
      String comparePredicate;
      if(myComparator != null) {
        myComparator.transform(context, inVar.getName(), best);
        PsiExpression expression = myComparator.getExpression();
        int expressionPrecedence = ParenthesesUtils.getPrecedence(expression);
        String text = expressionPrecedence >= ParenthesesUtils.EQUALITY_PRECEDENCE ? "("+expression.getText()+")" : expression.getText();
        comparePredicate = myTemplate.replace("{comparator}", text);
      } else {
        comparePredicate = myTemplate.replace("{best}", best).replace("{item}", inVar.getName());
      }
      return "if(!" + seen + " || " + comparePredicate + ") {\n" +
             seen + "=true;\n" +
             best + "=" + inVar + ";\n}\n";
    }

    @Nullable
    static MinMaxTerminalOperation create(@Nullable PsiExpression comparator, PsiType elementType, boolean max) {
      String sign = max ? ">" : "<";
      if(comparator == null) {
        if (PsiType.INT.equals(elementType) || PsiType.LONG.equals(elementType)) {
          return new MinMaxTerminalOperation(elementType, "{item}" + sign + "{best}", null);
        }
        if (PsiType.DOUBLE.equals(elementType)) {
          return new MinMaxTerminalOperation(elementType, "java.lang.Double.compare({item},{best})" + sign + "0", null);
        }
      }
      else {
        FunctionHelper fn = FunctionHelper.create(comparator, 2);
        if(fn != null) {
          return new MinMaxTerminalOperation(elementType, "{comparator}"+sign+"0", fn);
        }
      }
      return null;
    }
  }

  static class ToMapTerminalOperation extends CollectorBasedTerminalOperation {
    private final FunctionHelper myKeyExtractor, myValueExtractor;
    private final PsiExpression myMerger;

    ToMapTerminalOperation(FunctionHelper keyExtractor,
                           FunctionHelper valueExtractor,
                           PsiExpression merger,
                           FunctionHelper supplier,
                           PsiType resultType) {
      super(resultType, context -> "map", supplier);
      myKeyExtractor = keyExtractor;
      myValueExtractor = valueExtractor;
      myMerger = merger;
    }

    @Override
    public PsiType correctReturnType(PsiType type) {
      return correctTypeParameters(type, CommonClassNames.JAVA_UTIL_MAP, Collections.emptyMap());
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      super.registerReusedElements(consumer);
      myKeyExtractor.registerReusedElements(consumer);
      myValueExtractor.registerReusedElements(consumer);
      if(myMerger != null) consumer.accept(myMerger);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      myKeyExtractor.preprocessVariable(context, inVar, 0);
      myValueExtractor.preprocessVariable(context, inVar, 0);
    }

    @Override
    public void transform(StreamToLoopReplacementContext context, String item) {
      super.transform(context, item);
      myKeyExtractor.transform(context, item);
      myValueExtractor.transform(context, item);
    }

    @Override
    public String getAccumulatorUpdater(StreamVariable inVar, String map) {
      if(myMerger == null) {
        return "if("+map+".put("+myKeyExtractor.getText()+","+myValueExtractor.getText()+")!=null) {\n"+
               "throw new java.lang.IllegalStateException(\"Duplicate key\");\n}\n";
      }
      if(myMerger instanceof PsiLambdaExpression) {
        PsiLambdaExpression lambda = (PsiLambdaExpression)myMerger;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if(parameters.length == 2) {
          PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
          if(body instanceof PsiReferenceExpression) {
            PsiReferenceExpression ref = (PsiReferenceExpression)body;
            if(ref.getQualifierExpression() == null) {
              // cannot use isReferenceTo here as lambda could be detached from PsiFile
              if (Objects.equals(parameters[0].getName(), ref.getReferenceName())) {
                // like (a, b) -> a
                return map + ".putIfAbsent(" + myKeyExtractor.getText() + "," + myValueExtractor.getText() + ");\n";
              }
              else if (Objects.equals(parameters[1].getName(), ref.getReferenceName())) {
                // like (a, b) -> b
                return map + ".put(" + myKeyExtractor.getText() + "," + myValueExtractor.getText() + ");\n";
              }
            }
          }
        }
      }
      return map+".merge("+myKeyExtractor.getText()+","+myValueExtractor.getText()+","+myMerger.getText()+");\n";
    }
  }

  static class GroupByTerminalOperation extends CollectorBasedTerminalOperation {
    private final CollectorOperation myCollector;
    private FunctionHelper myKeyExtractor;
    private String myKeyVar;

    public GroupByTerminalOperation(FunctionHelper keyExtractor, FunctionHelper supplier, PsiType resultType, CollectorOperation collector) {
      super(resultType, context -> "map", supplier);
      myKeyExtractor = keyExtractor;
      myCollector = collector;
    }

    @Override
    public PsiType correctReturnType(PsiType type) {
      return correctTypeParameters(type, CommonClassNames.JAVA_UTIL_MAP, Collections.singletonMap("V", myCollector::correctReturnType));
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      super.registerReusedElements(consumer);
      myKeyExtractor.registerReusedElements(consumer);
      myCollector.registerReusedElements(consumer);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      myKeyExtractor.preprocessVariable(context, inVar, 0);
      myCollector.preprocessVariables(context, inVar, outVar);
    }

    @Override
    public void transform(StreamToLoopReplacementContext context, String item) {
      super.transform(context, item);
      myKeyExtractor.transform(context, item);
      myCollector.transform(context, item);
      myKeyVar = context.registerVarName(Arrays.asList("k", "key"));
    }

    @Override
    public String getAccumulatorUpdater(StreamVariable inVar, String map) {
      String key = myKeyExtractor.getText();
      String merger = myCollector.getMerger(inVar, map, key);
      if (merger != null) return merger;
      String acc = map + ".computeIfAbsent(" + key + "," + myKeyVar + "->" + myCollector.getSupplier() + ")";
      return myCollector.getAccumulatorUpdater(inVar, acc);
    }
  }

  static class PartitionByTerminalOperation extends TerminalOperation {
    private final String myResultType;
    private final CollectorOperation myCollector;
    private FunctionHelper myPredicate;

    public PartitionByTerminalOperation(FunctionHelper predicate, PsiType resultType, CollectorOperation collector) {
      myPredicate = predicate;
      myResultType = resultType.getCanonicalText();
      myCollector = collector;
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      myPredicate.registerReusedElements(consumer);
      myCollector.registerReusedElements(consumer);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      myPredicate.preprocessVariable(context, inVar, 0);
      myCollector.preprocessVariables(context, inVar, outVar);
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      PsiType resultType = context.createType(myResultType);
      resultType = correctTypeParameters(resultType, CommonClassNames.JAVA_UTIL_MAP,
                                         Collections.singletonMap("V", myCollector::correctReturnType));
      String map = context.declareResult("map", resultType, "new java.util.HashMap<>()", ResultKind.FINAL);
      myPredicate.transform(context, inVar.getName());
      myCollector.transform(context, inVar.getName());
      context.addBeforeStep(map + ".put(false, " + myCollector.getSupplier() + ");");
      context.addBeforeStep(map + ".put(true, " + myCollector.getSupplier() + ");");
      String key = myPredicate.getText();
      String merger = myCollector.getMerger(inVar, map, key);
      if (merger != null) return merger;
      return myCollector.getAccumulatorUpdater(inVar, map + ".get(" + key + ")");
    }
  }

  abstract static class AbstractMappingTerminalOperation extends TerminalOperation implements CollectorOperation {
    final FunctionHelper myMapper;
    final TerminalOperation myDownstream;
    final CollectorOperation myDownstreamCollector;

    AbstractMappingTerminalOperation(FunctionHelper mapper, TerminalOperation downstream) {
      myMapper = mapper;
      myDownstream = downstream;
      myDownstreamCollector = downstream.asCollector();
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      myMapper.registerReusedElements(consumer);
      myDownstream.registerReusedElements(consumer);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      myMapper.preprocessVariable(context, inVar, 0);
    }

    @Override
    public PsiType correctReturnType(PsiType type) {
      return myDownstreamCollector.correctReturnType(type);
    }

    @Override
    CollectorOperation asCollector() {
      return myDownstreamCollector == null ? null : this;
    }

    @Override
    public String getSupplier() {
      return myDownstreamCollector.getSupplier();
    }
  }

  static class MappingTerminalOperation extends AbstractMappingTerminalOperation {
    private StreamVariable myVariable;

    MappingTerminalOperation(FunctionHelper mapper, TerminalOperation downstream) {
      super(mapper, downstream);
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      createVariable(context, inVar.getName());
      return myVariable.getDeclaration(myMapper.getText()) + myDownstream.generate(myVariable, context);
    }

    private void createVariable(StreamToLoopReplacementContext context, String item) {
      myMapper.transform(context, item);
      myVariable = new StreamVariable(myMapper.getResultType());
      myDownstream.preprocessVariables(context, myVariable, StreamVariable.STUB);
      myMapper.suggestOutputNames(context, myVariable);
      myVariable.register(context);
    }

    @Override
    public void transform(StreamToLoopReplacementContext context, String item) {
      createVariable(context, item);
      myDownstreamCollector.transform(context, myVariable.getName());
    }

    @Override
    public String getAccumulatorUpdater(StreamVariable inVar, String acc) {
      return myVariable.getDeclaration(myMapper.getText()) + myDownstreamCollector.getAccumulatorUpdater(myVariable, acc);
    }

    @Override
    public String getMerger(StreamVariable inVar, String map, String key) {
      String merger = myDownstreamCollector.getMerger(myVariable, map, key);
      return merger == null ? null : myVariable.getDeclaration(myMapper.getText()) + merger;
    }
  }

  static class InlineMappingTerminalOperation extends AbstractMappingTerminalOperation {
    InlineMappingTerminalOperation(FunctionHelper mapper, TerminalOperation downstream) {
      super(mapper, downstream);
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      myMapper.transform(context, inVar.getName());
      StreamVariable updatedVar = new StreamVariable(myMapper.getResultType(), myMapper.getText());
      return myDownstream.generate(updatedVar, context);
    }

    @Override
    public void transform(StreamToLoopReplacementContext context, String item) {
      myMapper.transform(context, item);
      myDownstreamCollector.transform(context, myMapper.getText());
    }

    @Override
    public String getAccumulatorUpdater(StreamVariable inVar, String acc) {
      return myDownstreamCollector.getAccumulatorUpdater(new StreamVariable(myMapper.getResultType(), myMapper.getText()), acc);
    }

    @Override
    public String getMerger(StreamVariable inVar, String map, String key) {
      return myDownstreamCollector.getMerger(new StreamVariable(myMapper.getResultType(), myMapper.getText()), map, key);
    }
  }

  static class ForEachTerminalOperation extends TerminalOperation {
    private FunctionHelper myFn;

    public ForEachTerminalOperation(FunctionHelper fn) {
      myFn = fn;
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      myFn.preprocessVariable(context, inVar, 0);
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      myFn.registerReusedElements(consumer);
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      myFn.transform(context, inVar.getName());
      return myFn.getStatementText();
    }
  }

  static class SortedTerminalOperation extends TerminalOperation {
    private final AccumulatedOperation myOrigin;
    @Nullable private final PsiExpression myComparator;

    SortedTerminalOperation(AccumulatedOperation origin, @Nullable PsiExpression comparator) {
      myOrigin = origin;
      myComparator = comparator;
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      myOrigin.registerReusedElements(consumer);
      if(myComparator != null) {
        consumer.accept(myComparator);
      }
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      myOrigin.preprocessVariables(context, inVar, outVar);
    }

    @Override
    String generate(StreamVariable inVar, StreamToLoopReplacementContext context) {
      String acc = myOrigin.initAccumulator(inVar, context);
      context.addAfterStep(acc + ".sort(" + (myComparator == null ? "null" : myComparator.getText()) + ");\n");
      return myOrigin.getAccumulatorUpdater(inVar, acc);
    }
  }
}
