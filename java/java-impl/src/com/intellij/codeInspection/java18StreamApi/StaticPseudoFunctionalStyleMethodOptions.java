// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodOptions {
  private final List<PipelineElement> myElements;

  public StaticPseudoFunctionalStyleMethodOptions() {
    myElements = new ArrayList<>();
    restoreDefault(myElements);
  }

  private static void restoreDefault(final List<? super PipelineElement> elements) {
    elements.clear();
    String guavaIterables = "com.google.common.collect.Iterables";
    elements.add(new PipelineElement(guavaIterables, "transform", PseudoLambdaReplaceTemplate.MAP));
    elements.add(new PipelineElement(guavaIterables, "filter", PseudoLambdaReplaceTemplate.FILTER));
    elements.add(new PipelineElement(guavaIterables, "find", PseudoLambdaReplaceTemplate.FIND));
    elements.add(new PipelineElement(guavaIterables, "all", PseudoLambdaReplaceTemplate.ALL_MATCH));
    elements.add(new PipelineElement(guavaIterables, "any", PseudoLambdaReplaceTemplate.ANY_MATCH));

    String guavaLists = "com.google.common.collect.Lists";
    elements.add(new PipelineElement(guavaLists, "transform", PseudoLambdaReplaceTemplate.MAP));
  }

  public @Unmodifiable @NotNull Collection<PipelineElement> findElementsByMethodName(final @NotNull String methodName) {
    return ContainerUtil.filter(myElements, element -> methodName.equals(element.methodName()));
  }

  public record PipelineElement(@NotNull String handlerClass,
                                @NotNull String methodName,
                                @NotNull PseudoLambdaReplaceTemplate template) {
  }
}
