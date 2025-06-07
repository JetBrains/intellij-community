package com.intellij.microservices.url.parameters;

import com.intellij.codeInsight.highlighting.HighlightedReference;
import com.intellij.microservices.HttpReferenceService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.ResolvingHint;
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
    return mySearcher.getPathVariables(getElement())
      .toArray(new PomTargetPsiElement[0]);
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    HttpReferenceService service = ApplicationManager.getApplication().getService(HttpReferenceService.class);
    return service.resolvePathVariableUsage(getValue(), getElement(), mySearcher);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    HttpReferenceService service = ApplicationManager.getApplication().getService(HttpReferenceService.class);
    return service.isReferenceToPathVariableDeclaration(element) && super.isReferenceTo(element);
  }

  @Override
  public boolean canResolveTo(@NotNull Class<? extends PsiElement> elementClass) {
    HttpReferenceService service = ApplicationManager.getApplication().getService(HttpReferenceService.class);
    return service.canResolveToPathVariableDeclaration(elementClass);
  }
}