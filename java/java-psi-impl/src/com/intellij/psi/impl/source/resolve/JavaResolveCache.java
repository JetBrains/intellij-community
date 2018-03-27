/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.psi.*;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class JavaResolveCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.JavaResolveCache");

  private static final NotNullLazyKey<JavaResolveCache, Project> INSTANCE_KEY = ServiceManager.createLazyKey(JavaResolveCache.class);

  public static JavaResolveCache getInstance(Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  private final AtomicReference<ConcurrentMap<PsiExpression, PsiType>> myCalculatedTypes = new AtomicReference<>();
  private final AtomicReference<Map<PsiVariable,Object>> myVarToConstValueMapPhysical = new AtomicReference<>();
  private final AtomicReference<Map<PsiVariable,Object>> myVarToConstValueMapNonPhysical = new AtomicReference<>();

  private static final Object NULL = Key.create("NULL");

  public JavaResolveCache(@Nullable("can be null in com.intellij.core.JavaCoreApplicationEnvironment.JavaCoreApplicationEnvironment") MessageBus messageBus) {
    if (messageBus != null) {
      messageBus.connect().subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener.Adapter() {
        @Override
        public void beforePsiChanged(boolean isPhysical) {
          clearCaches(isPhysical);
        }
      });
    }
  }

  private void clearCaches(boolean isPhysical) {
    myCalculatedTypes.set(null);
    if (isPhysical) {
      myVarToConstValueMapPhysical.set(null);
    }
    myVarToConstValueMapNonPhysical.set(null);
  }

  @Nullable
  public <T extends PsiExpression> PsiType getType(@NotNull T expr, @NotNull Function<T, PsiType> f) {
    final boolean isOverloadCheck = MethodCandidateInfo.isOverloadCheck() || LambdaUtil.isLambdaParameterCheck();
    final boolean polyExpression = PsiPolyExpressionUtil.isPolyExpression(expr);

    ConcurrentMap<PsiExpression, PsiType> map = myCalculatedTypes.get();
    if (map == null) map = ConcurrencyUtil.cacheOrGet(myCalculatedTypes, ContainerUtil.createConcurrentWeakKeySoftValueMap());

    PsiType type = isOverloadCheck && polyExpression ? null : map.get(expr);
    if (type == null) {
      final RecursionGuard.StackStamp dStackStamp = PsiDiamondType.ourDiamondGuard.markStack();
      type = f.fun(expr);
      if (!dStackStamp.mayCacheNow()) {
        return type;
      }

      //cache standalone expression types as they do not depend on the context
      if (isOverloadCheck && polyExpression) {
        return type;
      }

      if (type == null) type = TypeConversionUtil.NULL_TYPE;
      map.put(expr, type);

      if (type instanceof PsiClassReferenceType) {
        // convert reference-based class type to the PsiImmediateClassType, since the reference may become invalid
        PsiClassType.ClassResolveResult result = ((PsiClassReferenceType)type).resolveGenerics();
        PsiClass psiClass = result.getElement();
        type = psiClass == null
               ? type // for type with unresolved reference, leave it in the cache
                      // for clients still might be able to retrieve its getCanonicalText() from the reference text
               : new PsiImmediateClassType(psiClass, result.getSubstitutor(), ((PsiClassReferenceType)type).getLanguageLevel(), type.getAnnotationProvider());
      }
    }

    if (!type.isValid()) {
      if (expr.isValid()) {
        PsiJavaCodeReferenceElement refInside = type instanceof PsiClassReferenceType ? ((PsiClassReferenceType)type).getReference() : null;
        @NonNls String typeinfo = type + " (" + type.getClass() + ")" + (refInside == null ? "" : "; ref inside: "+refInside + " ("+refInside.getClass()+") valid:"+refInside.isValid());
        LOG.error("Type is invalid: " + typeinfo + "; expr: '" + expr + "' (" + expr.getClass() + ") is valid");
      }
      else {
        LOG.error("Expression: '"+expr+"' is invalid, must not be used for getType()");
      }
    }

    return type == TypeConversionUtil.NULL_TYPE ? null : type;
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

  public interface ConstValueComputer{
    Object execute(@NotNull PsiVariable variable, Set<PsiVariable> visitedVars);
  }
}
