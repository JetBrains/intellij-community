/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Stack;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Denis Zhdanov
 * @since 9/18/12 11:11 AM
 */
public class JavaArrangementParseInfo {

  @NotNull private final List<JavaElementArrangementEntry> myEntries = new ArrayList<JavaElementArrangementEntry>();

  @NotNull private final Map<Pair<String/* property name */, String/* class name */>, JavaArrangementPropertyInfo> myProperties
    = new HashMap<Pair<String, String>, JavaArrangementPropertyInfo>();

  @NotNull private final List<JavaArrangementMethodDependencyInfo> myMethodDependencyRoots
    = new ArrayList<JavaArrangementMethodDependencyInfo>();

  @NotNull private final Map<PsiMethod /* anchor */, Set<PsiMethod /* dependencies */>> myMethodDependencies
    = new HashMap<PsiMethod, Set<PsiMethod>>();

  @NotNull private final Map<PsiMethod, JavaElementArrangementEntry> myMethodEntriesMap =
    new HashMap<PsiMethod, JavaElementArrangementEntry>();

  @NotNull private final Map<PsiClass, List<Pair<PsiMethod/*overridden*/, PsiMethod/*overriding*/>>> myOverriddenMethods
    = new LinkedHashMap<PsiClass, List<Pair<PsiMethod, PsiMethod>>>();

  @NotNull private final Set<PsiMethod> myTmpMethodDependencyRoots = new LinkedHashSet<PsiMethod>();
  @NotNull private final Set<PsiMethod> myDependentMethods         = new HashSet<PsiMethod>();


  private boolean myRebuildMethodDependencies;

  @NotNull
  public List<JavaElementArrangementEntry> getEntries() {
    return myEntries;
  }

  public void addEntry(@NotNull JavaElementArrangementEntry entry) {
    myEntries.add(entry);
  }

  @NotNull
  public Collection<JavaArrangementPropertyInfo> getProperties() {
    return myProperties.values();
  }

  /**
   * @return    list of method dependency roots, i.e. there is a possible case that particular method
   *            {@link JavaArrangementMethodDependencyInfo#getDependentMethodInfos() calls another method}, it calls other methods
   *            and so forth
   */
  @NotNull
  public List<JavaArrangementMethodDependencyInfo> getMethodDependencyRoots() {
    if (myRebuildMethodDependencies) {
      myMethodDependencyRoots.clear();
      Map<PsiMethod, JavaArrangementMethodDependencyInfo> cache = new HashMap<PsiMethod, JavaArrangementMethodDependencyInfo>();
      for (PsiMethod method : myTmpMethodDependencyRoots) {
        JavaArrangementMethodDependencyInfo info = buildMethodDependencyInfo(method, cache);
        if (info != null) {
          myMethodDependencyRoots.add(info);
        }
      }
      myRebuildMethodDependencies = false;
    }
    return myMethodDependencyRoots;
  }

  @Nullable
  private JavaArrangementMethodDependencyInfo buildMethodDependencyInfo(@NotNull final PsiMethod method,
                                                                        @NotNull Map<PsiMethod, JavaArrangementMethodDependencyInfo> cache)
  {
    JavaElementArrangementEntry entry = myMethodEntriesMap.get(method);
    if (entry == null) {
      return null;
    }
    JavaArrangementMethodDependencyInfo result = new JavaArrangementMethodDependencyInfo(entry);
    Stack<Pair<PsiMethod, JavaArrangementMethodDependencyInfo>> toProcess
      = new Stack<Pair<PsiMethod, JavaArrangementMethodDependencyInfo>>();
    toProcess.push(Pair.create(method, result));
    Set<PsiMethod> usedMethods = ContainerUtilRt.newHashSet();
    while (!toProcess.isEmpty()) {
      Pair<PsiMethod, JavaArrangementMethodDependencyInfo> pair = toProcess.pop();
      Set<PsiMethod> dependentMethods = myMethodDependencies.get(pair.first);
      if (dependentMethods == null) {
        continue;
      }
      usedMethods.add(pair.first);
      for (PsiMethod dependentMethod : dependentMethods) {
        if (usedMethods.contains(dependentMethod)) {
          // Prevent cyclic dependencies.
          return null;
        }
        JavaElementArrangementEntry dependentEntry = myMethodEntriesMap.get(dependentMethod);
        if (dependentEntry == null) {
          continue;
        }
        JavaArrangementMethodDependencyInfo dependentMethodInfo = cache.get(dependentMethod);
        if (dependentMethodInfo == null) {
          cache.put(dependentMethod, dependentMethodInfo = new JavaArrangementMethodDependencyInfo(dependentEntry));
        }
        Pair<PsiMethod, JavaArrangementMethodDependencyInfo> dependentPair = Pair.create(dependentMethod, dependentMethodInfo);
        pair.second.addDependentMethodInfo(dependentPair.second);
        toProcess.push(dependentPair);
      }
    }
    return result;
  }

  public void registerGetter(@NotNull String propertyName, @NotNull String className, @NotNull JavaElementArrangementEntry entry) {
    getPropertyInfo(propertyName, className).setGetter(entry);
  }

  public void registerSetter(@NotNull String propertyName, @NotNull String className, @NotNull JavaElementArrangementEntry entry) {
    getPropertyInfo(propertyName, className).setSetter(entry);
  }

  @NotNull
  private JavaArrangementPropertyInfo getPropertyInfo(@NotNull String propertyName, @NotNull String className) {
    Pair<String, String> key = Pair.create(propertyName, className);
    JavaArrangementPropertyInfo propertyInfo = myProperties.get(key);
    if (propertyInfo == null) {
      myProperties.put(key, propertyInfo = new JavaArrangementPropertyInfo());
    }
    return propertyInfo;
  }

  public void onMethodEntryCreated(@NotNull PsiMethod method, @NotNull JavaElementArrangementEntry entry) {
    myMethodEntriesMap.put(method, entry);
  }

  public void onOverriddenMethod(@NotNull PsiMethod baseMethod, @NotNull PsiMethod overridingMethod) {
    PsiClass clazz = baseMethod.getContainingClass();
    if (clazz == null) {
      return;
    }
    List<Pair<PsiMethod, PsiMethod>> methods = myOverriddenMethods.get(clazz);
    if (methods == null) {
      myOverriddenMethods.put(clazz, methods = new ArrayList<Pair<PsiMethod, PsiMethod>>());
    }
    methods.add(Pair.create(baseMethod, overridingMethod));
  }

  @NotNull
  public List<JavaArrangementOverriddenMethodsInfo> getOverriddenMethods() {
    List<JavaArrangementOverriddenMethodsInfo> result = new ArrayList<JavaArrangementOverriddenMethodsInfo>();
    final TObjectIntHashMap<PsiMethod> weights = new TObjectIntHashMap<PsiMethod>();
    Comparator<Pair<PsiMethod, PsiMethod>> comparator = new Comparator<Pair<PsiMethod, PsiMethod>>() {
      @Override
      public int compare(Pair<PsiMethod, PsiMethod> o1, Pair<PsiMethod, PsiMethod> o2) {
        return weights.get(o1.first) - weights.get(o2.first);
      }
    };
    for (Map.Entry<PsiClass, List<Pair<PsiMethod, PsiMethod>>> entry : myOverriddenMethods.entrySet()) {
      JavaArrangementOverriddenMethodsInfo info = new JavaArrangementOverriddenMethodsInfo(entry.getKey().getName());
      weights.clear();
      int i = 0;
      for (PsiMethod method : entry.getKey().getMethods()) {
        weights.put(method, i++);
      }
      ContainerUtil.sort(entry.getValue(), comparator);
      for (Pair<PsiMethod, PsiMethod> pair : entry.getValue()) {
        JavaElementArrangementEntry overridingMethodEntry = myMethodEntriesMap.get(pair.second);
        if (overridingMethodEntry != null) {
          info.addMethodEntry(overridingMethodEntry);
        }
      }
      if (!info.getMethodEntries().isEmpty()) {
        result.add(info);
      }
    }
    
    return result;
  }

  /**
   * Is expected to be called when new method dependency is detected. Here given <code>'base method'</code> calls
   * <code>'dependent method'</code>.
   * 
   * @param baseMethod       method which calls another method
   * @param dependentMethod  method being called
   */
  public void registerDependency(@NotNull PsiMethod baseMethod, @NotNull PsiMethod dependentMethod) {
    myTmpMethodDependencyRoots.remove(dependentMethod);
    if (!myDependentMethods.contains(baseMethod)) {
      myTmpMethodDependencyRoots.add(baseMethod);
    }
    myDependentMethods.add(dependentMethod);
    Set<PsiMethod> methods = myMethodDependencies.get(baseMethod);
    if (methods == null) {
      myMethodDependencies.put(baseMethod, methods = new LinkedHashSet<PsiMethod>());
    }
    if (!methods.contains(dependentMethod)) {
      methods.add(dependentMethod);
    }
    myRebuildMethodDependencies = true;
  }
}
