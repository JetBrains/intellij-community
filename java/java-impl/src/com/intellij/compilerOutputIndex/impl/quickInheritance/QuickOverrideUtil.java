package com.intellij.compilerOutputIndex.impl.quickInheritance;

/**
 * @author Dmitry Batkovich
 */
public final class QuickOverrideUtil {

  private QuickOverrideUtil() {}

  public static boolean isMethodOverriden(final String classQName, final String methodName,
                                          final QuickInheritanceIndex quickInheritanceIndex,
                                          final QuickMethodsIndex quickMethodsIndex) {
    for (final String aSuper : quickInheritanceIndex.getSupers(classQName)) {
      if (quickMethodsIndex.getMethodsNames(aSuper).contains(methodName)) {
        return true;
      }
      if (isMethodOverriden(aSuper, methodName, quickInheritanceIndex, quickMethodsIndex)) {
        return true;
      }
    }
    return false;
  }
}
