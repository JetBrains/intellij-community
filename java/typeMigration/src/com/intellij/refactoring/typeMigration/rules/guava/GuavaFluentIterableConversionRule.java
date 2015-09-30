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
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class GuavaFluentIterableConversionRule extends BaseGuavaTypeConversionRule {

  @Override
  protected void fillSimpleDescriptors(Map<String, TypeConversionDescriptorBase> descriptorsMap) {
    descriptorsMap.put("contains",
                       new TypeConversionDescriptor("$it$.contains($o$)", "$it$.anyMatch(e -> e != null && e.equals(%s))"));
    descriptorsMap.put("from", new TypeConversionDescriptor("FluentIterable.from($it$)", "$it$.stream()"));
    descriptorsMap.put("isEmpty", new TypeConversionDescriptor("$q$.isEmpty()", "$q$.findAny().isPresent()"));
    descriptorsMap.put("skip", new TypeConversionDescriptorBase());
    descriptorsMap.put("limit", new TypeConversionDescriptorBase());
    descriptorsMap.put("transform", new LambdaParametersTypeConversionDescription("$q$.transform($params$)", "$q$.map($params$)"));
    descriptorsMap.put("transformAndConcat",
                       new LambdaParametersTypeConversionDescription("$q$.transformAndConcat($params$)", "$q$.flatMap($params$)"));
    descriptorsMap.put("first", new TypeConversionDescriptor("$q$.first()", "$q$.findFirst()"));

    descriptorsMap.put("allMatch", new TypeConversionDescriptor("$it$.allMatch($c$)", "$it$." + StreamApiConstants.ALL_MATCH + "($c$)"));
    descriptorsMap.put("anyMatch", new TypeConversionDescriptor("$it$.anyMatch($c$)", "$it$." + StreamApiConstants.ANY_MATCH + "($c$)"));

    descriptorsMap.put("filter", new TypeConversionDescriptor("$it$.filter($p$)", "$it$." + StreamApiConstants.FILTER + "($p$)"));
    descriptorsMap.put("first", new TypeConversionDescriptor("$it$.first()", "$it$." + StreamApiConstants.FIND_FIRST + "()"));
    descriptorsMap.put("firstMatch", new TypeConversionDescriptor("$it$.firstMatch($p$)", "$it$.filter($p$).findFirst()"));
    descriptorsMap.put("get", new TypeConversionDescriptor("$it$.get($p$)", "$it$.collect(java.util.stream.Collectors.toList()).get($p$)"));
    descriptorsMap.put("size", new TypeConversionDescriptor("$it$.size()", "$it$.collect(java.util.stream.Collectors.toList()).size()"));

    descriptorsMap.put("toMap", new TypeConversionDescriptor("$it$.toMap($f$)",
                                                             "$it$.collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity(), $f$))"));
    descriptorsMap.put("toList", new TypeConversionDescriptor("$it$.toList()", "$it$.collect(java.util.stream.Collectors.toList())"));
    descriptorsMap.put("toSet", new TypeConversionDescriptor("$it$.toSet()", "$it$.collect(java.util.stream.Collectors.toSet())"));
    descriptorsMap.put("toSortedList", new TypeConversionDescriptor("$it$.toSortedList($c$)", "$it$.sorted($c$).collect(java.util.stream.Collectors.toList())"));
    descriptorsMap.put("toSortedSet", new TypeConversionDescriptor("$it$.toSortedSet($c$)", "$it$.sorted($c$).collect(java.util.stream.Collectors.toSet())"));

  }

  @NotNull
  @Override
  public String ruleFromClass() {
    return "com.google.common.collect.FluentIterable";
  }

  @NotNull
  @Override
  public String ruleToClass() {
    return StreamApiConstants.JAVA_UTIL_STREAM_STREAM;
  }
}
