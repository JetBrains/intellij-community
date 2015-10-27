/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.codeInspection.java18StreamApi.PseudoLambdaReplaceTemplate;
import com.intellij.codeInspection.java18StreamApi.StreamApiConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class GuavaFluentIterableConversionRule extends BaseGuavaTypeConversionRule {
  private static final Logger LOG = Logger.getInstance(GuavaFluentIterableConversionRule.class);
  private static final Map<String, TypeConversionDescriptorFactory> DESCRIPTORS_MAP =
    new HashMap<String, TypeConversionDescriptorFactory>();

  public static final Set<String> CHAIN_HEAD_METHODS = ContainerUtil.newHashSet("from", "of");
  public static final String FLUENT_ITERABLE = "com.google.common.collect.FluentIterable";
  public static final String STREAM_COLLECT_TO_LIST = "$it$.collect(java.util.stream.Collectors.toList())";

  static class TypeConversionDescriptorFactory {
    private final String myStringToReplace;
    private final String myReplaceByString;
    private final boolean myWithLambdaParameter;
    private final boolean myChainedMethod;
    private final boolean myFluentIterableReturnType;

    public TypeConversionDescriptorFactory(String stringToReplace, String replaceByString, boolean withLambdaParameter) {
      this(stringToReplace, replaceByString, withLambdaParameter, false, false);
    }

    public TypeConversionDescriptorFactory(@NonNls final String stringToReplace,
                                           @NonNls final String replaceByString,
                                           boolean withLambdaParameter,
                                           boolean chainedMethod,
                                           boolean fluentIterableReturnType) {
      myStringToReplace = stringToReplace;
      myReplaceByString = replaceByString;
      myWithLambdaParameter = withLambdaParameter;
      myChainedMethod = chainedMethod;
      myFluentIterableReturnType = fluentIterableReturnType;
    }

    public TypeConversionDescriptor create() {
      return myWithLambdaParameter ? new LambdaParametersTypeConversionDescriptor(myStringToReplace, myReplaceByString)
                                   : new TypeConversionDescriptor(myStringToReplace, myReplaceByString);
    }

    public boolean isChainedMethod() {
      return myChainedMethod;
    }

    public boolean isFluentIterableReturnType() {
      return myFluentIterableReturnType;
    }
  }

  static {
    DESCRIPTORS_MAP.put("of", new TypeConversionDescriptorFactory("FluentIterable.of($arr$)", "java.util.Arrays.stream($arr$)", false, true, true));

    DESCRIPTORS_MAP.put("isEmpty", new TypeConversionDescriptorFactory("$q$.isEmpty()", "$q$.findAny().isPresent()", false));
    DESCRIPTORS_MAP.put("skip", new TypeConversionDescriptorFactory("$q$.skip($p$)", "$q$.skip($p$)", false, true, true));
    DESCRIPTORS_MAP.put("limit", new TypeConversionDescriptorFactory("$q$.limit($p$)", "$q$.limit($p$)", false, true, true));
    DESCRIPTORS_MAP.put("first", new TypeConversionDescriptorFactory("$q$.first()", "$q$.findFirst()", false, true, false));
    DESCRIPTORS_MAP.put("transform", new TypeConversionDescriptorFactory("$q$.transform($params$)", "$q$.map($params$)", true, true, true));

    DESCRIPTORS_MAP.put("allMatch", new TypeConversionDescriptorFactory("$it$.allMatch($c$)", "$it$." + StreamApiConstants.ALL_MATCH + "($c$)", true));
    DESCRIPTORS_MAP.put("anyMatch", new TypeConversionDescriptorFactory("$it$.anyMatch($c$)", "$it$." + StreamApiConstants.ANY_MATCH + "($c$)", true));

    DESCRIPTORS_MAP.put("firstMatch", new TypeConversionDescriptorFactory("$it$.firstMatch($p$)", "$it$.filter($p$).findFirst()", true, true, false));
    DESCRIPTORS_MAP.put("size", new TypeConversionDescriptorFactory("$it$.size()", "(int) $it$.count()", false));

    DESCRIPTORS_MAP.put("toMap", new TypeConversionDescriptorFactory("$it$.toMap($f$)",
                                                              "$it$.collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity(), $f$))", true));
    DESCRIPTORS_MAP.put("toList", new TypeConversionDescriptorFactory("$it$.toList()", STREAM_COLLECT_TO_LIST, false));
    DESCRIPTORS_MAP.put("toSet", new TypeConversionDescriptorFactory("$it$.toSet()", "$it$.collect(java.util.stream.Collectors.toSet())", false));
    DESCRIPTORS_MAP.put("toSortedList", new TypeConversionDescriptorFactory("$it$.toSortedList($c$)", "$it$.sorted($c$).collect(java.util.stream.Collectors.toList())", false));
    DESCRIPTORS_MAP.put("toSortedSet", new TypeConversionDescriptorFactory("$it$.toSortedSet($c$)", "$it$.sorted($c$).collect(java.util.stream.Collectors.toSet())", false));
  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForMethod(@NotNull PsiType from,
                                                                 @NotNull PsiType to,
                                                                 @NotNull PsiMethod method,
                                                                 @NotNull String methodName,
                                                                 PsiExpression context,
                                                                 TypeMigrationLabeler labeler) {
    if (context instanceof PsiMethodCallExpression) {
      return buildCompoundDescriptor((PsiMethodCallExpression)context, to, labeler);
    }

    return getOneMethodDescriptor(methodName, method, from, to, context);
  }

  @Nullable
  private static TypeConversionDescriptorBase getOneMethodDescriptor(@NotNull String methodName,
                                                                     @NotNull PsiMethod method,
                                                                     @NotNull PsiType from,
                                                                     @Nullable PsiType to,
                                                                     @Nullable PsiExpression context) {
    TypeConversionDescriptor descriptorBase = null;
    PsiType conversionType = to;
    boolean needSpecifyType = true;
    if (methodName.equals("from")) {
      descriptorBase = new TypeConversionDescriptor("FluentIterable.from($it$)", null) {
        @Override
        public PsiExpression replace(PsiExpression expression) {
          PsiExpression argument =
            PseudoLambdaReplaceTemplate.replaceTypeParameters(((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0]);
          if (argument == null) {
            return expression;
          }
          boolean isCollection =
            InheritanceUtil.isInheritor(PsiTypesUtil.getPsiClass(argument.getType()), CommonClassNames.JAVA_UTIL_COLLECTION);
          setReplaceByString(isCollection ? "$it$.stream()" : "java.util.stream.StreamSupport.stream($it$.spliterator(), false)");
          return super.replace(expression);
        }
      };
    } else if (methodName.equals("filter")) {
      descriptorBase = FluentIterableConversionUtil.getFilterDescriptor(method);
    }
    else if (methodName.equals("transformAndConcat")) {
      descriptorBase = new FluentIterableConversionUtil.TransformAndConcatConversionRule();
    } else if (methodName.equals("toArray")) {
      descriptorBase = FluentIterableConversionUtil.getToArrayDescriptor(from, context);
      needSpecifyType = false;
    }
    else if (methodName.equals("copyInto")) {
      descriptorBase = new FluentIterableConversionUtil.CopyIntoDescriptor();
      needSpecifyType = false;
    }
    else if (methodName.equals("get")) {
      descriptorBase = new TypeConversionDescriptor("$it$.get($p$)", null) {
        @Override
        public PsiExpression replace(PsiExpression expression) {
          PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
          final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
          setReplaceByString("$it$.skip($p$).findFirst().get()");
          if (arguments.length == 1 && arguments[0] instanceof PsiLiteralExpression) {
            final Object value = ((PsiLiteralExpression)arguments[0]).getValue();
            if (value != null && value.equals(0)) {
              setReplaceByString("$it$.findFirst().get()");
            }
          }
          return super.replace(expression);
        }
      };
      needSpecifyType = false;
    }
    else if (methodName.equals("contains")) {
      descriptorBase = new TypeConversionDescriptor("$it$.contains($o$)", null) {
        @Override
        public PsiExpression replace(PsiExpression expression) {
          final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
          final PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
          LOG.assertTrue(qualifier != null);
          final PsiClassType qualifierType = (PsiClassType)qualifier.getType();
          LOG.assertTrue(qualifierType != null);
          final PsiType[] parameters = qualifierType.getParameters();

          final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
          final SuggestedNameInfo suggestedNameInfo = codeStyleManager
            .suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, parameters.length == 1 ? parameters[0] : null, false);
          final String suggestedName = codeStyleManager.suggestUniqueVariableName(suggestedNameInfo, expression, false).names[0];

          setReplaceByString("$it$.anyMatch(" + suggestedName + " -> java.util.Objects.equals(" + suggestedName + ", $o$))");

          return super.replace(expression);
        }
      };
      needSpecifyType = false;
    }
    else {
      final TypeConversionDescriptorFactory base = DESCRIPTORS_MAP.get(methodName);
      if (base != null) {
        final TypeConversionDescriptor descriptor = base.create();
        needSpecifyType = base.isChainedMethod();
        if (needSpecifyType && !base.isFluentIterableReturnType()) {
          conversionType =
            JavaPsiFacade.getElementFactory(method.getProject()).createTypeByFQClassName(GuavaOptionalConversionRule.JAVA_OPTIONAL);
        }
        descriptorBase = descriptor;
      }
    }
    if (descriptorBase != null) {
      if (needSpecifyType && conversionType != null) {
        descriptorBase.withConversionType(conversionType);
      }
      return descriptorBase;
    }
    return null;
  }

  @Nullable
  public static GuavaChainedConversionDescriptor buildCompoundDescriptor(PsiMethodCallExpression expression,
                                                                          PsiType to,
                                                                          TypeMigrationLabeler labeler) {
    List<TypeConversionDescriptorBase> methodDescriptors = new SmartList<TypeConversionDescriptorBase>();

    NotNullLazyValue<TypeConversionRule> optionalDescriptor = new NotNullLazyValue<TypeConversionRule>() {
      @NotNull
      @Override
      protected TypeConversionRule compute() {
        for (TypeConversionRule rule : TypeConversionRule.EP_NAME.getExtensions()) {
          if (rule instanceof GuavaOptionalConversionRule) {
            return rule;
          }
        }
        throw new RuntimeException("GuavaOptionalConversionRule extension is not found");
      }
    };

    PsiMethodCallExpression current = expression;
    while (true) {
      final PsiMethod method = current.resolveMethod();
      if (method == null) {
        break;
      }
      final String methodName = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        break;
      }
      TypeConversionDescriptorBase descriptor = null;
      if (FLUENT_ITERABLE.equals(containingClass.getQualifiedName())) {
        descriptor = getOneMethodDescriptor(methodName, method, current.getType(), null, current);
      }
      else if (GuavaOptionalConversionRule.GUAVA_OPTIONAL.equals(containingClass.getQualifiedName())) {
        descriptor = optionalDescriptor.getValue().findConversion(null, null, method, current.getMethodExpression(), labeler);
      }
      if (descriptor == null) {
        return null;
      }
      methodDescriptors.add(descriptor);
      final PsiExpression qualifier = current.getMethodExpression().getQualifierExpression();
      if (qualifier instanceof PsiMethodCallExpression) {
        current = (PsiMethodCallExpression)qualifier;
      }
      else if (method.hasModifierProperty(PsiModifier.STATIC)) {
        if (!CHAIN_HEAD_METHODS.contains(methodName)) {
          return null;
        }
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null || !FLUENT_ITERABLE.equals(aClass.getQualifiedName())) {
          return null;
        }
        break;
      }
      else {
        return null;
      }
    }

    return new GuavaChainedConversionDescriptor(methodDescriptors, to);
  }

  private static class GuavaChainedConversionDescriptor extends TypeConversionDescriptorBase {
    private final List<TypeConversionDescriptorBase> myMethodDescriptors;
    private final PsiType myToType;

    private GuavaChainedConversionDescriptor(List<TypeConversionDescriptorBase> descriptors, PsiType to) {
      myMethodDescriptors = new ArrayList<TypeConversionDescriptorBase>(descriptors);
      Collections.reverse(myMethodDescriptors);
      myToType = to;
    }

    @Override
    public PsiExpression replace(PsiExpression expression) throws IncorrectOperationException {
      Stack<PsiMethodCallExpression> methodChainStack = new Stack<PsiMethodCallExpression>();
      PsiMethodCallExpression current = (PsiMethodCallExpression) expression;
      while (current != null) {
        methodChainStack.add(current);
        final PsiExpression qualifier = current.getMethodExpression().getQualifierExpression();
        current = qualifier instanceof PsiMethodCallExpression ? (PsiMethodCallExpression)qualifier : null;
      }

      if (methodChainStack.size() != myMethodDescriptors.size()) {
        return expression;
      }

      PsiExpression converted = null;

      for (TypeConversionDescriptorBase descriptor : myMethodDescriptors) {
        final PsiMethodCallExpression toConvert = methodChainStack.pop();
        converted = descriptor.replace(toConvert);
      }

      return converted;
    }

    @Nullable
    @Override
    public PsiType conversionType() {
      return myToType;
    }
  }

  @NotNull
  @Override
  public String ruleFromClass() {
    return FLUENT_ITERABLE;
  }

  @NotNull
  @Override
  public String ruleToClass() {
    return StreamApiConstants.JAVA_UTIL_STREAM_STREAM;
  }
}
