/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.inheritance.ImplicitSubclassProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class InheritanceUtil {

  private InheritanceUtil() {}

  public static ThreeState existsMutualSubclass(PsiClass class1, PsiClass class2, boolean avoidExpensiveProcessing) {
    if (class1 instanceof PsiTypeParameter) {
      final PsiClass[] superClasses = class1.getSupers();
      ThreeState result = ThreeState.YES;
      for (PsiClass superClass : superClasses) {
        ThreeState state = existsMutualSubclass(superClass, class2, avoidExpensiveProcessing);
        if (state != ThreeState.YES) {
          result = state;
          if (result == ThreeState.NO) {
            return result;
          }
        }
      }
      return result;
    }
    if (class2 instanceof PsiTypeParameter) {
      return existsMutualSubclass(class2, class1, avoidExpensiveProcessing);
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(class1.getQualifiedName())) {
      return ThreeState.YES;
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(class2.getQualifiedName())) {
      return ThreeState.YES;
    }
    if (class1.isInheritor(class2, true) || class2.isInheritor(class1, true) || Objects.equals(class1, class2)) {
      return ThreeState.YES;
    }
    final SearchScope scope = GlobalSearchScope.allScope(class1.getProject());
    String class1Name = class1.getName();
    String class2Name = class2.getName();
    if (class1Name == null || class2Name == null) {
      // One of classes is anonymous? No subclass is possible
      return ThreeState.NO;
    }
    if (class1.hasModifierProperty(PsiModifier.FINAL) || class2.hasModifierProperty(PsiModifier.FINAL)) return ThreeState.NO;
    if (LambdaUtil.isFunctionalClass(class1) || class1Name.length() < class2Name.length() ||
        (isJavaClass(class2) && !isJavaClass(class1))) {
      // Assume that it could be faster to search inheritors from non-functional interface or from class with a longer simple name
      // Also prefer searching inheritors from Java class over other JVM languages as Java is usually faster
      PsiClass tmp = class1;
      class1 = class2;
      class2 = tmp;
    }
    if (DeclarationSearchUtils.isTooExpensiveToSearch(class1, false)) {
      if (!DeclarationSearchUtils.isTooExpensiveToSearch(class2, false)) {
        PsiClass tmp = class1;
        class1 = class2;
        class2 = tmp;
      }
      else if (avoidExpensiveProcessing) {
        // class inheritor search is too expensive on common names
        return ThreeState.UNSURE;
      }
    }
    return doSearch(class1, class2, avoidExpensiveProcessing, scope);
  }

  private static boolean isJavaClass(PsiClass class1) {
    return class1 instanceof PsiClassImpl || class1 instanceof ClsClassImpl;
  }

  private static ThreeState doSearch(PsiClass class1, PsiClass class2, boolean avoidExpensiveProcessing, SearchScope scope) {
    final Query<PsiClass> search = ClassInheritorsSearch.search(class1, scope, true);
    var processor = new Processor<PsiClass>() {
      ThreeState result = ThreeState.NO;
      final AtomicInteger count = new AtomicInteger(0);

      @Override
      public boolean process(PsiClass inheritor) {
        if (inheritor.equals(class2) || inheritor.isInheritor(class2, true)) {
          result = ThreeState.YES;
          return false;
        }
        if (avoidExpensiveProcessing && count.incrementAndGet() > 20) {
          result = ThreeState.UNSURE;
          return false;
        }
        return true;
      }
    };
    search.forEach(processor);
    return processor.result;
  }

  public static boolean hasImplementation(@NotNull PsiClass aClass) {
    for (ImplicitSubclassProvider provider : ImplicitSubclassProvider.EP_NAME.getExtensionList()) {
      if (!provider.isApplicableTo(aClass)) {
        continue;
      }
      ImplicitSubclassProvider.SubclassingInfo info = provider.getSubclassingInfo(aClass);
      if (info != null && !info.isAbstract()) {
        return true;
      }
    }
    return ClassInheritorsSearch.search(aClass).anyMatch(inheritor -> !inheritor.isInterface() &&
                                                                      !inheritor.isAnnotationType() &&
                                                                      !inheritor.hasModifierProperty(PsiModifier.ABSTRACT))
           || aClass.isInterface() && FunctionalExpressionSearch.search(aClass).findFirst() != null;
  }

  public static boolean hasOneInheritor(final PsiClass aClass) {
    final CountingProcessor processor = new CountingProcessor(2);
    ProgressManager.getInstance().runProcess(
      (Runnable)() -> ClassInheritorsSearch.search(aClass, aClass.getUseScope(), false).forEach(processor), null);
    return processor.getCount() == 1;
  }

  private static class CountingProcessor implements Processor<PsiClass> {
    private final AtomicInteger myCount = new AtomicInteger(0);
    private final int myLimit;

    CountingProcessor(int limit) {
      myLimit = limit;
    }

    public int getCount() {
      return myCount.get();
    }

    @Override
    public boolean process(PsiClass aClass) {
      return myCount.incrementAndGet() < myLimit;
    }
  }
}