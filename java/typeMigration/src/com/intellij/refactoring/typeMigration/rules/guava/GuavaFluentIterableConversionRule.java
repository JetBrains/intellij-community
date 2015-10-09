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
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class GuavaFluentIterableConversionRule extends BaseGuavaTypeConversionRule {
  private static final Map<String, TypeConversionDescriptorFactory> DESCRIPTORS_MAP =
    new HashMap<String, TypeConversionDescriptorFactory>();

  public static final String FLUENT_ITERABLE = "com.google.common.collect.FluentIterable";

  private static class TypeConversionDescriptorFactory {
    private final String myStringToReplace;
    private final String myReplaceByString;
    private final boolean myWithLambdaParameter;
    private final boolean myChainedMethod;

    public TypeConversionDescriptorFactory(String stringToReplace, String replaceByString, boolean withLambdaParameter) {
      this(stringToReplace, replaceByString, withLambdaParameter, false);
    }

    public TypeConversionDescriptorFactory(@NonNls final String stringToReplace,
                                           @NonNls final String replaceByString,
                                           boolean withLambdaParameter,
                                           boolean chainedMethod) {
      myStringToReplace = stringToReplace;
      myReplaceByString = replaceByString;
      myWithLambdaParameter = withLambdaParameter;
      myChainedMethod = chainedMethod;
    }

    public TypeConversionDescriptor create() {
      return myWithLambdaParameter ? new LambdaParametersTypeConversionDescriptor(myStringToReplace, myReplaceByString)
                                   : new TypeConversionDescriptor(myStringToReplace, myReplaceByString);
    }

    public boolean isChainedMethod() {
      return myChainedMethod;
    }
  }

  static {
    DESCRIPTORS_MAP.put("contains",
                        new TypeConversionDescriptorFactory("$it$.contains($o$)", "$it$.anyMatch(e -> e != null && e.equals($o$))", false));
    DESCRIPTORS_MAP.put("isEmpty", new TypeConversionDescriptorFactory("$q$.isEmpty()", "$q$.findAny().isPresent()", false));
    DESCRIPTORS_MAP.put("skip", new TypeConversionDescriptorFactory("$q$.skip($p$)", "$q$.skip($p$)", false, true));
    DESCRIPTORS_MAP.put("limit", new TypeConversionDescriptorFactory("$q$.limit($p$)", "$q$.limit($p$)", false, true));
    DESCRIPTORS_MAP.put("first", new TypeConversionDescriptorFactory("$q$.first()", "$q$.findFirst()", false));
    DESCRIPTORS_MAP.put("transform", new TypeConversionDescriptorFactory("$q$.transform($params$)", "$q$.map($params$)", true, true));

    DESCRIPTORS_MAP.put("allMatch", new TypeConversionDescriptorFactory("$it$.allMatch($c$)", "$it$." + StreamApiConstants.ALL_MATCH + "($c$)", true));
    DESCRIPTORS_MAP.put("anyMatch", new TypeConversionDescriptorFactory("$it$.anyMatch($c$)", "$it$." + StreamApiConstants.ANY_MATCH + "($c$)", true));

    DESCRIPTORS_MAP.put("first", new TypeConversionDescriptorFactory("$it$.first()", "$it$." + StreamApiConstants.FIND_FIRST + "()", false));
    DESCRIPTORS_MAP.put("firstMatch", new TypeConversionDescriptorFactory("$it$.firstMatch($p$)", "$it$.filter($p$).findFirst()", true));
    DESCRIPTORS_MAP.put("get", new TypeConversionDescriptorFactory("$it$.get($p$)", "$it$.collect(java.util.stream.Collectors.toList()).get($p$)", false));
    DESCRIPTORS_MAP.put("size", new TypeConversionDescriptorFactory("$it$.size()", "$it$.collect(java.util.stream.Collectors.toList()).size()", false));

    DESCRIPTORS_MAP.put("toMap", new TypeConversionDescriptorFactory("$it$.toMap($f$)",
                                                              "$it$.collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity(), $f$))", false));
    DESCRIPTORS_MAP.put("toList", new TypeConversionDescriptorFactory("$it$.toList()", "$it$.collect(java.util.stream.Collectors.toList())", false));
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

    return getOneMethodDescriptor(methodName, method, to, context);
  }

  @Nullable
  private TypeConversionDescriptorBase getOneMethodDescriptor(@NotNull String methodName,
                                                              @NotNull PsiMethod method,
                                                              @Nullable PsiType to,
                                                              @Nullable PsiExpression context) {
    TypeConversionDescriptor descriptorBase = null;
    boolean needSpecifyType = true;
    if (methodName.equals("from")) {
      descriptorBase = new TypeConversionDescriptor("FluentIterable.from($it$)", "$it$.stream()") {
        @Override
        public PsiExpression replace(PsiExpression expression) {
          PseudoLambdaReplaceTemplate.replaceTypeParameters(((PsiMethodCallExpression) expression).getArgumentList().getExpressions()[0]);
          return super.replace(expression);
        }
      };
    } else if (methodName.equals("filter")) {
      descriptorBase = FluentIterableConversionUtil.getFilterDescriptor(method);
    }
    else if (methodName.equals("transformAndConcat")) {
      descriptorBase = new FluentIterableConversionUtil.TransformAndConcatConversionRule();
    }
    else {
      final TypeConversionDescriptorFactory base = DESCRIPTORS_MAP.get(methodName);
      if (base != null) {
        final TypeConversionDescriptor descriptor = base.create();
        needSpecifyType = base.isChainedMethod();
        descriptorBase = descriptor;
      }
    }
    if (descriptorBase != null) {
      if (needSpecifyType && to != null) {
        descriptorBase.withConversionType(to);
      }
      return descriptorBase;
    }
    return null;
  }



  @Nullable
  private GuavaChainedConversionDescriptor buildCompoundDescriptor(PsiMethodCallExpression expression,
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
        descriptor = getOneMethodDescriptor(methodName, method, null, current);
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
      else if (qualifier instanceof PsiReferenceExpression) {
        if (!methodName.equals("from")) {
          return null;
        }
        final PsiElement maybeClass = ((PsiReferenceExpression)qualifier).resolve();
        if (!(maybeClass instanceof PsiClass)) {
          return null;
        }
        if (!FLUENT_ITERABLE.equals(((PsiClass)maybeClass).getQualifiedName())) {
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
      myMethodDescriptors = descriptors;
      myToType = to;
    }

    @Override
    public PsiExpression replace(PsiExpression expression) throws IncorrectOperationException {
      PsiMethodCallExpression toReturn = null;
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(expression.getProject());

      for (TypeConversionDescriptorBase descriptor : myMethodDescriptors) {
        final PsiExpression oldQualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
        final SmartPsiElementPointer<PsiExpression> qualifierRef = oldQualifier == null
                                                                   ? null
                                                                   : smartPointerManager.createSmartPsiElementPointer(oldQualifier);
        final PsiMethodCallExpression replaced = (PsiMethodCallExpression)descriptor.replace(methodCallExpression);
        if (toReturn == null) {
          toReturn = replaced;
        }

        final PsiExpression newQualifier = qualifierRef == null ? null : qualifierRef.getElement();
        if (newQualifier instanceof PsiMethodCallExpression) {
          methodCallExpression = (PsiMethodCallExpression)newQualifier;
        } else {
          return toReturn;
        }
      }
      return toReturn;
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
