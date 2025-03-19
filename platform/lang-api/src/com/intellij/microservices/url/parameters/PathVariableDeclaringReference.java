package com.intellij.microservices.url.parameters;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.microservices.HttpReferenceService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolvingHint;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a reference to the element in code where the Path Variable is declared
 * EXAMPLE: in {@code "/users/{id}/delete"} it is {@code "{id}"}
 * <p>
 * in many cases it is interchangeable with {@link PathVariablePsiElement} and {@link PathVariablePomTarget}
 *
 * @see PathVariablePsiElement
 * @see PathVariablePomTarget
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
  public PomTargetPsiElement resolve() {
    HttpReferenceService service = ApplicationManager.getApplication().getService(HttpReferenceService.class);
    return service.resolvePathVariableDeclaration(getValue(), getElement(), getRangeInElement(), myVariableUsagesProvider);
  }

  @Override
  public boolean canResolveTo(@NotNull Class<? extends PsiElement> elementClass) {
    HttpReferenceService service = ApplicationManager.getApplication().getService(HttpReferenceService.class);
    return service.canResolveToPathVariableDeclaration(elementClass);
  }
}
