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

import com.intellij.codeInspection.java18StreamApi.StreamApiConstants;
import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      return myWithLambdaParameter ? new LambdaParametersTypeConversionDescription(myStringToReplace, myReplaceByString)
                                   : new TypeConversionDescriptor(myStringToReplace, myReplaceByString);
    }

    public boolean isChainedMethod() {
      return myChainedMethod;
    }
  }

  static {
    DESCRIPTORS_MAP.put("contains",
                        new TypeConversionDescriptorFactory("$it$.contains($o$)", "$it$.anyMatch(e -> e != null && e.equals($o$))", false));
    DESCRIPTORS_MAP.put("from", new TypeConversionDescriptorFactory("FluentIterable.from($it$)", "$it$.stream()", false, true));
    DESCRIPTORS_MAP.put("isEmpty", new TypeConversionDescriptorFactory("$q$.isEmpty()", "$q$.findAny().isPresent()", false));
    DESCRIPTORS_MAP.put("skip", new TypeConversionDescriptorFactory("$q$.skip($p$)", "$q$.skip($p$)", false, true));
    DESCRIPTORS_MAP.put("limit", new TypeConversionDescriptorFactory("$q$.limit($p$)", "$q$.limit($p$)", false, true));
    DESCRIPTORS_MAP.put("first", new TypeConversionDescriptorFactory("$q$.first()", "$q$.findFirst()", false));
    DESCRIPTORS_MAP.put("transform", new TypeConversionDescriptorFactory("$q$.transform($params$)", "$q$.map($params$)", true, true));
    //TODO support
    //DESCRIPTORS_MAP.put("transformAndConcat", new TransformAndConcatDescriptorBase("$q$.transformAndConcat($params$)", "$q$.flatMap($params$)"));

    DESCRIPTORS_MAP.put("allMatch", new TypeConversionDescriptorFactory("$it$.allMatch($c$)", "$it$." + StreamApiConstants.ALL_MATCH + "($c$)", true));
    DESCRIPTORS_MAP.put("anyMatch", new TypeConversionDescriptorFactory("$it$.anyMatch($c$)", "$it$." + StreamApiConstants.ANY_MATCH + "($c$)", true));

    //TODO add another filter processor
    DESCRIPTORS_MAP.put("filter", new TypeConversionDescriptorFactory("$it$.filter($p$)", "$it$." + StreamApiConstants.FILTER + "($p$)", true, true));
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
                                                                 String methodName,
                                                                 PsiExpression context,
                                                                 TypeMigrationLabeler labeler) {
    final TypeConversionDescriptorFactory base = DESCRIPTORS_MAP.get(methodName);
    if (base != null) {
      final TypeConversionDescriptor descriptor = base.create();
      if (base.isChainedMethod()) {
        descriptor.withConversionType(to);
      }
      return descriptor;
    }
    else {
      return null;
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
