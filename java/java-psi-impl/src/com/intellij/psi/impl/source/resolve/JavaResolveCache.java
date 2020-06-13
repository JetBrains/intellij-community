// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class JavaResolveCache {
  private static final NotNullLazyKey<JavaResolveCache, Project> INSTANCE_KEY = ServiceManager.createLazyKey(JavaResolveCache.class);

  public static JavaResolveCache getInstance(Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  private final AtomicReference<Map<PsiVariable,Object>> myVarToConstValueMapPhysical = new AtomicReference<>();
  private final AtomicReference<Map<PsiVariable,Object>> myVarToConstValueMapNonPhysical = new AtomicReference<>();

  private static final Object NULL = Key.create("NULL");

  public JavaResolveCache(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        clearCaches(isPhysical);
      }
    });
  }

  private void clearCaches(boolean isPhysical) {
    if (isPhysical) {
      myVarToConstValueMapPhysical.set(null);
    }
    myVarToConstValueMapNonPhysical.set(null);
  }

  @Nullable
  public <T extends PsiExpression> PsiType getType(@NotNull T expr, @NotNull Function<? super T, ? extends PsiType> f) {
    boolean prohibitCaching = MethodCandidateInfo.isOverloadCheck() && PsiPolyExpressionUtil.isPolyExpression(expr);
    if (prohibitCaching) {
      return f.fun(expr);
    }

    return CachedValuesManager.getProjectPsiDependentCache(expr, param -> f.fun(param));
  }

  @Nullable
  public Object computeConstantValueWithCaching(@NotNull PsiVariable variable, @NotNull ConstValueComputer computer, Set<PsiVariable> visitedVars){
    boolean physical = variable.isPhysical();

    AtomicReference<Map<PsiVariable, Object>> ref = physical ? myVarToConstValueMapPhysical : myVarToConstValueMapNonPhysical;
    Map<PsiVariable, Object> map = ref.get();
    if (map == null) map = ConcurrencyUtil.cacheOrGet(ref, ContainerUtil.createConcurrentWeakMap());

    Object cached = map.get(variable);
    if (cached == NULL) return null;
    if (cached != null) return cached;

    Object result = computer.execute(variable, visitedVars);
    map.put(variable, result == null ? NULL : result);
    return result;
  }

  @FunctionalInterface
  public interface ConstValueComputer{
    Object execute(@NotNull PsiVariable variable, Set<PsiVariable> visitedVars);
  }
}
