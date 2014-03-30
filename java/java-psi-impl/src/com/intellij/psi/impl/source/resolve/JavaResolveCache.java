/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class JavaResolveCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.JavaResolveCache");

  private static final NotNullLazyKey<JavaResolveCache, Project> INSTANCE_KEY = ServiceManager.createLazyKey(JavaResolveCache.class);

  public static JavaResolveCache getInstance(Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  private final ConcurrentMap<PsiExpression, Reference<PsiType>> myCalculatedTypes = new ConcurrentWeakHashMap<PsiExpression, Reference<PsiType>>();

  private final Map<PsiVariable,Object> myVarToConstValueMapPhysical = new ConcurrentWeakHashMap<PsiVariable, Object>();
  private final Map<PsiVariable,Object> myVarToConstValueMapNonPhysical = new ConcurrentWeakHashMap<PsiVariable, Object>();

  private static final Object NULL = Key.create("NULL");

  public JavaResolveCache(@Nullable("can be null in com.intellij.core.JavaCoreApplicationEnvironment.JavaCoreApplicationEnvironment") MessageBus messageBus) {
    if (messageBus != null) {
      messageBus.connect().subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
        @Override
        public void beforePsiChanged(boolean isPhysical) {
          clearCaches(isPhysical);
        }

        @Override
        public void afterPsiChanged(boolean isPhysical) {
        }
      });
    }
  }

  private void clearCaches(boolean isPhysical) {
    myCalculatedTypes.clear();
    if (isPhysical) {
      myVarToConstValueMapPhysical.clear();
    }
    myVarToConstValueMapNonPhysical.clear();
  }

  @Nullable
  public <T extends PsiExpression> PsiType getType(@NotNull T expr, @NotNull Function<T, PsiType> f) {
    PsiType type = getCachedType(expr);
    if (type == null) {
      final RecursionGuard.StackStamp dStackStamp = PsiDiamondType.ourDiamondGuard.markStack();
      final RecursionGuard.StackStamp gStackStamp = PsiResolveHelper.ourGraphGuard.markStack();
      type = f.fun(expr);
      if (!dStackStamp.mayCacheNow() || !gStackStamp.mayCacheNow() || !MethodCandidateInfo.ourOverloadGuard.currentStack().isEmpty()) {
        return type;
      }
      if (type == null) type = TypeConversionUtil.NULL_TYPE;
      Reference<PsiType> ref = new SoftReference<PsiType>(type);
      myCalculatedTypes.put(expr, ref);

      if (type instanceof PsiClassReferenceType) {
        // convert reference-based class type to the PsiImmediateClassType, since the reference may become invalid
        PsiClassType.ClassResolveResult result = ((PsiClassReferenceType)type).resolveGenerics();
        PsiClass psiClass = result.getElement();
        type = psiClass == null
               ? type // for type with unresolved reference, leave it in the cache
                      // for clients still might be able to retrieve its getCanonicalText() from the reference text
               : new PsiImmediateClassType(psiClass, result.getSubstitutor(), ((PsiClassReferenceType)type).getLanguageLevel(), type.getAnnotations());
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

  private <T extends PsiExpression> PsiType getCachedType(T expr) {
    Reference<PsiType> reference = myCalculatedTypes.get(expr);
    return SoftReference.dereference(reference);
  }

  @Nullable
  public Object computeConstantValueWithCaching(@NotNull PsiVariable variable, @NotNull ConstValueComputer computer, Set<PsiVariable> visitedVars){
    boolean physical = variable.isPhysical();

    Map<PsiVariable, Object> map = physical ? myVarToConstValueMapPhysical : myVarToConstValueMapNonPhysical;
    Object cached = map.get(variable);
    if (cached == NULL) return null;
    if (cached != null) return cached;

    Object result = computer.execute(variable, visitedVars);
    map.put(variable, result == null ? NULL : result);
    return result;
  }

  public interface ConstValueComputer{
    Object execute(PsiVariable variable, Set<PsiVariable> visitedVars);
  }
}
