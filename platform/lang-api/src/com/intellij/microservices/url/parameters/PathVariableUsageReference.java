package com.intellij.microservices.url.parameters;

import com.intellij.codeInsight.highlighting.HighlightedReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

public class PathVariableUsageReference extends PsiPolyVariantReferenceBase<PsiElement>
  implements ResolvingHint, HighlightedReference {

  private final PathVariableDefinitionsSearcher mySearcher;

  public PathVariableUsageReference(@NotNull PsiElement host, PathVariableDefinitionsSearcher searcher) {
    super(host, false);
    mySearcher = searcher;
  }

  public PathVariableUsageReference(@NotNull PsiElement host, @NotNull TextRange rangeInElement, PathVariableDefinitionsSearcher searcher) {
    super(host, rangeInElement);
    mySearcher = searcher;
  }

  @Override
  public Object @NotNull [] getVariants() {
    return mySearcher.getPathVariables(getElement()).toArray(new PathVariablePsiElement[0]);
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    final String variableName = getValue();
    PathVariablePsiElement merge = PathVariablePsiElement.merge(
      mySearcher.getPathVariables(getElement())
        .filter(o -> variableName.equals(o.getName()))
        .map(v -> v.navigatingToDeclaration())
        .toList()
    );
    if (merge == null) return ResolveResult.EMPTY_ARRAY;
    return PsiElementResolveResult.createResults(merge);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return element instanceof PathVariablePsiElement && super.isReferenceTo(element);
  }

  @Override
  public boolean canResolveTo(@NotNull Class<? extends PsiElement> elementClass) {
    return ReflectionUtil.isAssignable(PathVariablePsiElement.class, elementClass);
  }
}