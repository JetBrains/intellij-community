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
import com.intellij.psi.*;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.messages.MessageBus;
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
  private final ConcurrentMap<PsiElement, PsiType> myCachedReferencesInPsiTypes = new ConcurrentWeakHashMap<PsiElement, PsiType>();

  private final Map<PsiVariable,Object> myVarToConstValueMapPhysical;
  private final Map<PsiVariable,Object> myVarToConstValueMapNonPhysical;

  private static final Object NULL = Key.create("NULL");

  public JavaResolveCache(MessageBus messageBus) {
    myVarToConstValueMapPhysical = new ConcurrentWeakHashMap<PsiVariable, Object>();
    myVarToConstValueMapNonPhysical = new ConcurrentWeakHashMap<PsiVariable, Object>();

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

  private void clearCaches(boolean isPhysical) {
    myCalculatedTypes.clear();
    myCachedReferencesInPsiTypes.clear();
    if (isPhysical) {
      myVarToConstValueMapPhysical.clear();
    }
    myVarToConstValueMapNonPhysical.clear();
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
        type = TypeConversionUtil.NULL_TYPE;
      }
      type = ConcurrencyUtil.cacheOrGet(myCalculatedTypes, expr, type);
    }
    if (!type.isValid()) {
      if (expr.isValid()) {
        PsiJavaCodeReferenceElement refInside = type instanceof PsiClassReferenceType ? ((PsiClassReferenceType)type).getReference() : null;
        String typeinfo = type + " (" + type.getClass() + ")" + (refInside == null ? "" : "; ref inside: "+refInside + " ("+refInside.getClass()+") valid:"+refInside.isValid());
        LOG.error("Type is invalid: " + typeinfo + "; expr: '" + expr + "' (" + expr.getClass() + ") is valid");
      }
      else {
        LOG.error("Expression: '"+expr+"' is invalid, must not be used for getType()");
      }
    }

    if (DebugUtil.DO_EXPENSIVE_CHECKS) {
      if (type instanceof PsiClassReferenceType) {
        PsiJavaCodeReferenceElement reference = ((PsiClassReferenceType)type).getReference();
        ConcurrencyUtil.cacheOrGet(myCachedReferencesInPsiTypes, reference, type);
        DebugUtil.trackInvalidation(reference, "Reference inside PsiClassReferenceType was invalidated", new Processor<PsiElement>() {
          @Override
          public boolean process(PsiElement element) {
            PsiType cached = myCalculatedTypes.get(element);
            if (cached != null) {
              LOG.error(element + " (inside ref) is invalid and yet it is still cached: " + cached);
            }
            PsiType cachedRef = myCachedReferencesInPsiTypes.get(element);
            if (cachedRef != null) {
              LOG.error(element + " (inside ref) is invalid and yet it is still cached in ref cache: " + cachedRef);
            }
            return true;
          }
        });

      }
      DebugUtil.trackInvalidation(expr, "Expression invalidated", new Processor<PsiElement>() {
        @Override
        public boolean process(PsiElement element) {
          PsiType cached = myCalculatedTypes.get(element);
          if (cached != null) {
            LOG.error(element + " is invalid and yet it is still cached: " + cached);
          }

          PsiType cachedRef = myCachedReferencesInPsiTypes.get(element);
          if (cachedRef != null) {
            LOG.error(element + " is invalid and yet it is still cached (inside PsiType): " + cachedRef);
          }
          return true;
        }
      });
    }

    return type == TypeConversionUtil.NULL_TYPE ? null : type;
  }

  @Nullable
  public Object computeConstantValueWithCaching(@NotNull PsiVariable variable, @NotNull ConstValueComputer computer, Set<PsiVariable> visitedVars){
    boolean physical = variable.isPhysical();

    Map<PsiVariable, Object> map = physical ? myVarToConstValueMapPhysical : myVarToConstValueMapNonPhysical;
    Object cached = map.get(variable);
    if (cached == NULL) return null;
    if (cached != null) return cached;

    Object result = computer.execute(variable, visitedVars);

    map.put(variable, result != null ? result : NULL);

    return result;
  }

  public interface ConstValueComputer{
    Object execute(PsiVariable variable, Set<PsiVariable> visitedVars);
  }
}
