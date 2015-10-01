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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class GuavaFluentIterableConversionRule extends BaseGuavaTypeConversionRule {
  private static final Map<String, TypeConversionDescriptorBase> DESCRIPTORS_MAP = new HashMap<String, TypeConversionDescriptorBase>();

  public static final String FLUENT_ITERABLE = "com.google.common.collect.FluentIterable";

  static {
    DESCRIPTORS_MAP.put("contains",
                        new TypeConversionDescriptor("$it$.contains($o$)", "$it$.anyMatch(e -> e != null && e.equals(%s))"));
    DESCRIPTORS_MAP.put("from", new TypeConversionDescriptor("FluentIterable.from($it$)", "$it$.stream()"));
    DESCRIPTORS_MAP.put("isEmpty", new TypeConversionDescriptor("$q$.isEmpty()", "$q$.findAny().isPresent()"));
    DESCRIPTORS_MAP.put("skip", new TypeConversionDescriptor("$q$.skip($p$)", "$q$.skip($p$)"));
    DESCRIPTORS_MAP.put("limit", new TypeConversionDescriptor("$q$.limit($p$)", "$q$.limit($p$)"));
    DESCRIPTORS_MAP.put("first", new TypeConversionDescriptor("$q$.first()", "$q$.findFirst()"));
    DESCRIPTORS_MAP.put("transform", new LambdaParametersTypeConversionDescription("$q$.transform($params$)", "$q$.map($params$)"));
    //TODO support
    //DESCRIPTORS_MAP.put("transformAndConcat", new TransformAndConcatDescriptorBase("$q$.transformAndConcat($params$)", "$q$.flatMap($params$)"));

    DESCRIPTORS_MAP.put("allMatch", new LambdaParametersTypeConversionDescription("$it$.allMatch($c$)", "$it$." + StreamApiConstants.ALL_MATCH + "($c$)"));
    DESCRIPTORS_MAP.put("anyMatch", new LambdaParametersTypeConversionDescription("$it$.anyMatch($c$)", "$it$." + StreamApiConstants.ANY_MATCH + "($c$)"));

    //TODO add another filter processor
    DESCRIPTORS_MAP.put("filter", new LambdaParametersTypeConversionDescription("$it$.filter($p$)", "$it$." + StreamApiConstants.FILTER + "($p$)"));
    DESCRIPTORS_MAP.put("first", new TypeConversionDescriptor("$it$.first()", "$it$." + StreamApiConstants.FIND_FIRST + "()"));
    DESCRIPTORS_MAP.put("firstMatch", new LambdaParametersTypeConversionDescription("$it$.firstMatch($p$)", "$it$.filter($p$).findFirst()"));
    DESCRIPTORS_MAP.put("get", new TypeConversionDescriptor("$it$.get($p$)", "$it$.collect(java.util.stream.Collectors.toList()).get($p$)"));
    DESCRIPTORS_MAP.put("size", new TypeConversionDescriptor("$it$.size()", "$it$.collect(java.util.stream.Collectors.toList()).size()"));

    DESCRIPTORS_MAP.put("toMap", new TypeConversionDescriptor("$it$.toMap($f$)",
                                                              "$it$.collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity(), $f$))"));
    DESCRIPTORS_MAP.put("toList", new TypeConversionDescriptor("$it$.toList()", "$it$.collect(java.util.stream.Collectors.toList())"));
    DESCRIPTORS_MAP.put("toSet", new TypeConversionDescriptor("$it$.toSet()", "$it$.collect(java.util.stream.Collectors.toSet())"));
    DESCRIPTORS_MAP.put("toSortedList", new TypeConversionDescriptor("$it$.toSortedList($c$)", "$it$.sorted($c$).collect(java.util.stream.Collectors.toList())"));
    DESCRIPTORS_MAP.put("toSortedSet", new TypeConversionDescriptor("$it$.toSortedSet($c$)", "$it$.sorted($c$).collect(java.util.stream.Collectors.toSet())"));

  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForMethod(@NotNull PsiType from,
                                                                 @NotNull PsiType to,
                                                                 @NotNull PsiMethod method,
                                                                 String methodName,
                                                                 PsiExpression context,
                                                                 TypeMigrationLabeler labeler) {
    final TypeConversionDescriptorBase base = DESCRIPTORS_MAP.get(methodName);
    return base instanceof TypeConversionDescriptor ? ((TypeConversionDescriptor)base).withConversionType(to) : null;
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
