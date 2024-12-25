package com.intellij.microservices.url.parameters;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolvingHint;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a reference to the element in code where the Path Variable is declared
 * EXAMPLE: in {@code "/users/{id}/delete"} it is {@code "{id}"}
 * <p>
 * in many cases it is interchangeable with {@link PathVariablePsiElement} and {@link PathVariablePsiElement.PathVariablePomTarget}
 *
 * @see PathVariablePsiElement
 * @see PathVariablePsiElement.PathVariablePomTarget
 */
public final class PathVariableDeclaringReference extends PsiReferenceBase<PsiElement> implements ResolvingHint {
  private final PathVariableUsagesProvider myVariableUsagesProvider;

  public PathVariableDeclaringReference(@NotNull PsiElement host,
                                        @NotNull TextRange range,
                                        PathVariableUsagesProvider variableUsagesProvider) {
    super(host, range, false);
    myVariableUsagesProvider = variableUsagesProvider;
  }

  @Override
  public Object @NotNull [] getVariants() {
    return ContainerUtil.collect(myVariableUsagesProvider.getCompletionVariantsForDeclaration(getElement()).iterator())
      .toArray(LookupElement.EMPTY_ARRAY);
  }

  @Override
  public String toString() {
    return "PathVariableReference(" + getValue() + ", " + getRangeInElement() + ")";
  }

  @Override
  public PathVariablePsiElement resolve() {
    return PathVariablePsiElement.create(getValue(), getElement(), getRangeInElement(), myVariableUsagesProvider);
  }

  @Override
  public boolean canResolveTo(@NotNull Class<? extends PsiElement> elementClass) {
    return ReflectionUtil.isAssignable(PathVariablePsiElement.class, elementClass);
  }
}
