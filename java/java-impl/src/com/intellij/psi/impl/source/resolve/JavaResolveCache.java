/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class JavaResolveCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.JavaResolveCache");

  private static final NotNullLazyKey<JavaResolveCache, Project> INSTANCE_KEY = ServiceManager.createLazyKey(JavaResolveCache.class);

  public static JavaResolveCache getInstance(Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  private final ConcurrentMap<PsiExpression, PsiType> myCalculatedTypes = new ConcurrentWeakHashMap<PsiExpression, PsiType>();
  private static final Key<ResolveCache.MapPair<PsiVariable, Object>> VAR_TO_CONST_VALUE_MAP_KEY = Key.create("ResolveCache.VAR_TO_CONST_VALUE_MAP_KEY");

  private final Map<PsiVariable,Object> myVarToConstValueMap1;
  private final Map<PsiVariable,Object> myVarToConstValueMap2;

  private static final Object NULL = Key.create("NULL");
  public static final PsiType NULL_TYPE = new PsiEllipsisType(PsiType.NULL){
    public boolean isValid() {
      return true;
    }

    @NonNls
    public String getPresentableText() {
      return "FAKE TYPE";
    }
  };

  public JavaResolveCache(PsiManagerEx manager) {
    ResolveCache cache = manager.getResolveCache();

    myVarToConstValueMap1 = cache.getOrCreateWeakMap(VAR_TO_CONST_VALUE_MAP_KEY, true);
    myVarToConstValueMap2 = cache.getOrCreateWeakMap(VAR_TO_CONST_VALUE_MAP_KEY, false);

    final Runnable cleanuper = new Runnable() {
      public void run() {
        myCalculatedTypes.clear();
      }
    };

    cache.addRunnableToRunOnDropCaches(cleanuper);
    manager.registerRunnableToRunOnAnyChange(cleanuper);
  }

  public boolean isTypeCached(@NotNull PsiExpression expr) {
    return myCalculatedTypes.get(expr) != null;
  }

  @Nullable
  public <T extends PsiExpression> PsiType getType(@NotNull T expr, @NotNull Function<T, PsiType> f) {
    PsiType type = myCalculatedTypes.get(expr);
    if (type == null) {
      type = f.fun(expr);
      if (type == null) {
        type = NULL_TYPE;
      }
      type = ConcurrencyUtil.cacheOrGet(myCalculatedTypes, expr, type);
    }
    if (!type.isValid()) {
      LOG.error("Type is invalid: " + type+"; expr: '"+expr+"' is "+(expr.isValid() ? "valid":"invalid"));
    }
    return type == NULL_TYPE ? null : type;
  }

  @Nullable
  public Object computeConstantValueWithCaching(PsiVariable variable, ConstValueComputer computer, Set<PsiVariable> visitedVars){
    boolean physical = variable.isPhysical();

    Object cached = (physical ? myVarToConstValueMap1 : myVarToConstValueMap2).get(variable);
    if (cached == NULL) return null;
    if (cached != null) return cached;

    Object result = computer.execute(variable, visitedVars);

    (physical ? myVarToConstValueMap1 : myVarToConstValueMap2).put(variable, result != null ? result : NULL);

    return result;
  }

  public interface ConstValueComputer{
    Object execute(PsiVariable variable, Set<PsiVariable> visitedVars);
  }
}
