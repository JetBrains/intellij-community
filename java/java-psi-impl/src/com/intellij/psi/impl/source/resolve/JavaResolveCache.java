// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class JavaResolveCache {
  private static final Logger LOG = Logger.getInstance(JavaResolveCache.class);
  private static final NotNullLazyKey<JavaResolveCache, Project> INSTANCE_KEY = ServiceManager.createLazyKey(JavaResolveCache.class);

  public static JavaResolveCache getInstance(Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  private final AtomicReference<ConcurrentMap<PsiExpression, PsiType>> myCalculatedTypes = new AtomicReference<>();
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
    myCalculatedTypes.set(null);
    if (isPhysical) {
      myVarToConstValueMapPhysical.set(null);
    }
    myVarToConstValueMapNonPhysical.set(null);
  }

  @Nullable
  public <T extends PsiExpression> PsiType getType(@NotNull T expr, @NotNull Function<? super T, ? extends PsiType> f) {
    ConcurrentMap<PsiExpression, PsiType> map = myCalculatedTypes.get();
    if (map == null) map = ConcurrencyUtil.cacheOrGet(myCalculatedTypes, ContainerUtil.createConcurrentWeakKeySoftValueMap());

    final boolean prohibitCaching = MethodCandidateInfo.isOverloadCheck() && PsiPolyExpressionUtil.isPolyExpression(expr);
    PsiType type = prohibitCaching ? null : map.get(expr);
    if (type == null) {
      RecursionGuard.StackStamp dStackStamp = RecursionManager.markStack();
      type = f.fun(expr);
      if (prohibitCaching || !dStackStamp.mayCacheNow()) {
        return type;
      }

      if (type == null) type = TypeConversionUtil.NULL_TYPE;
      PsiType alreadyCached = map.put(expr, type);
      if (alreadyCached != null && !type.equals(alreadyCached)) {
        reportUnstableType(expr, type, alreadyCached);
      }

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

    return type == TypeConversionUtil.NULL_TYPE ? null : type;
  }

  private static <T extends PsiExpression> void reportUnstableType(@NotNull PsiExpression expr, @NotNull PsiType type, @NotNull PsiType alreadyCached) {
    PsiFile file = expr.getContainingFile();
    LOG.error("Different types returned for the same PSI " + expr.getTextRange() + " on different threads: "
              + type + " != " + alreadyCached,
              new Attachment(file.getName(), file.getText()));
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
