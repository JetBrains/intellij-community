package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class CachedNotDeprecatedMethodsResolver {
  private final Map<MethodIncompleteSignature, PsiMethod[]> myResolveLocalCache = new HashMap<MethodIncompleteSignature, PsiMethod[]>();
  private final JavaPsiFacade myJavaPsiFacade;
  private final GlobalSearchScope myScope;

  public CachedNotDeprecatedMethodsResolver(final Project project, final GlobalSearchScope scope) {
    myScope = scope;
    myJavaPsiFacade = JavaPsiFacade.getInstance(project);
  }

  @NotNull
  public PsiMethod[] resolveNotDeprecated(@NotNull final MethodIncompleteSignature methodInvocation) {
    final PsiMethod[] cached = myResolveLocalCache.get(methodInvocation);
    if (cached != null) {
      return cached;
    }
    final PsiMethod[] methods = methodInvocation.resolveNotDeprecated(myJavaPsiFacade, myScope);
    myResolveLocalCache.put(methodInvocation, methods);
    return methods;
  }
}
