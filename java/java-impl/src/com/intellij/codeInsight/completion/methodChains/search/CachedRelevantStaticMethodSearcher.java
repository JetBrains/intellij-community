package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.codeInsight.completion.methodChains.ChainCompletionStringUtil;
import com.intellij.codeInsight.completion.methodChains.completion.context.ChainCompletionContext;
import com.intellij.codeInsight.completion.methodChains.completion.context.ContextRelevantStaticMethod;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.compilerOutputIndex.impl.MethodsUsageIndex;
import com.intellij.compilerOutputIndex.impl.UsageIndexValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class CachedRelevantStaticMethodSearcher {
  private final HashMap<MethodIncompleteSignature, PsiMethod> myCachedResolveResults = new HashMap<MethodIncompleteSignature, PsiMethod>();
  private final MethodsUsageIndex myIndex;
  private final JavaPsiFacade myJavaPsiFacade;
  private final GlobalSearchScope myAllScope;
  private final GlobalSearchScope myResolveScope;

  public CachedRelevantStaticMethodSearcher(final Project project, final GlobalSearchScope resolveScope) {
    myIndex = MethodsUsageIndex.getInstance(project);
    myJavaPsiFacade = JavaPsiFacade.getInstance(project);
    myAllScope = GlobalSearchScope.allScope(project);
    myResolveScope = resolveScope;
  }

  @NotNull
  public List<ContextRelevantStaticMethod> getRelevantStaticMethods(final String resultQualifiedClassName,
                                                                    final int minOccurrence,
                                                                    final ChainCompletionContext completionContext) {
    if (resultQualifiedClassName == null ||
        ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(resultQualifiedClassName) ||
        completionContext.getTargetQName().equals(resultQualifiedClassName)) {
      return Collections.emptyList();
    }
    final TreeSet<UsageIndexValue> indexValues = myIndex.getValues(resultQualifiedClassName);
    if (indexValues != null) {
      int occurrences = 0;
      final List<ContextRelevantStaticMethod> relevantMethods = new ArrayList<ContextRelevantStaticMethod>();
      for (final UsageIndexValue indexValue : extractStaticMethods(indexValues)) {
        final MethodIncompleteSignature methodInvocation = indexValue.getMethodIncompleteSignature();
        final PsiMethod method;
        if (myCachedResolveResults.containsKey(methodInvocation)) {
          method = myCachedResolveResults.get(methodInvocation);
        }
        else {
          final PsiMethod[] methods = methodInvocation.resolveNotDeprecated(myJavaPsiFacade, myAllScope);
          method = MethodChainsSearchUtil
            .getMethodWithMinNotPrimitiveParameters(methods, Collections.singleton(completionContext.getTargetQName()));
          myCachedResolveResults.put(methodInvocation, method);
          if (method == null) {
            return Collections.emptyList();
          }
        }
        if (method == null) {
          return Collections.emptyList();
        }
        if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
          if (isMethodValid(method, completionContext, resultQualifiedClassName)) {
            occurrences += indexValue.getOccurrences();
            if (myResolveScope.contains(method.getContainingFile().getVirtualFile())) {
              relevantMethods.add(new ContextRelevantStaticMethod(method, null));
            }
            if (occurrences >= minOccurrence) {
              return relevantMethods;
            }
          }
        }
      }
    }
    return Collections.emptyList();
  }

  private static List<UsageIndexValue> extractStaticMethods(final TreeSet<UsageIndexValue> indexValues) {
    final List<UsageIndexValue> relevantStaticMethods = new SmartList<UsageIndexValue>();
    for (final UsageIndexValue indexValue : indexValues) {
      if (indexValue.getMethodIncompleteSignature().isStatic()) {
        relevantStaticMethods.add(indexValue);
      }
    }
    return relevantStaticMethods;
  }

  private static boolean isMethodValid(final @Nullable PsiMethod method,
                                       final ChainCompletionContext completionContext,
                                       final String targetTypeShortName) {
    if (method == null) return false;
    for (final PsiParameter parameter : method.getParameterList().getParameters()) {
      final PsiType type = parameter.getType();
      final String shortClassName = typeAsString(type);
      if (targetTypeShortName.equals(shortClassName)) return false;
      if (!ChainCompletionStringUtil.isShortNamePrimitiveOrArrayOfPrimitives(shortClassName) &&
          !completionContext.contains(type.getCanonicalText())) {
        return false;
      }
    }
    return true;
  }


  @Nullable
  public static String typeAsString(final PsiType type) {
    if (type instanceof PsiClassType)
      return ((PsiClassType) type).getClassName();
    else if (type instanceof PsiPrimitiveType)
      return type.getCanonicalText();
    else if (type instanceof PsiArrayType) {
      final String componentTypeAsString = typeAsString(((PsiArrayType) type).getComponentType());
      if (componentTypeAsString == null) return null;
      return String.format("%s[]", componentTypeAsString);
    }
    return null;
  }
}
