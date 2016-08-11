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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Stack;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 9/18/12 11:11 AM
 */
public class JavaArrangementParseInfo {

  private final List<JavaElementArrangementEntry> myEntries = new ArrayList<>();

  private final Map<Pair<String/* property name */, String/* class name */>, JavaArrangementPropertyInfo> myProperties = new HashMap<>();

  private final List<ArrangementEntryDependencyInfo> myMethodDependencyRoots = new ArrayList<>();
  private final Map<PsiMethod /* anchor */, Set<PsiMethod /* dependencies */>> myMethodDependencies = new HashMap<>();

  private final Map<PsiMethod, JavaElementArrangementEntry> myMethodEntriesMap = new HashMap<>();
  private final Map<PsiClass, List<Pair<PsiMethod/*overridden*/, PsiMethod/*overriding*/>>> myOverriddenMethods = new LinkedHashMap<>();

  private final Set<PsiMethod> myTmpMethodDependencyRoots = new LinkedHashSet<>();
  private final Set<PsiMethod> myDependentMethods = new HashSet<>();
  private boolean myRebuildMethodDependencies;

  private final HashMap<PsiField, JavaElementArrangementEntry> myFields = ContainerUtil.newLinkedHashMap();
  private final Map<PsiField, Set<PsiField>> myFieldDependencies = ContainerUtil.newHashMap();

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
   *            {@link ArrangementEntryDependencyInfo#getDependentEntriesInfos() calls another method}, it calls other methods
   *            and so forth
   */
  @NotNull
  public List<ArrangementEntryDependencyInfo> getMethodDependencyRoots() {
    if (myRebuildMethodDependencies) {
      myMethodDependencyRoots.clear();
      Map<PsiMethod, ArrangementEntryDependencyInfo> cache = new HashMap<>();
      for (PsiMethod method : myTmpMethodDependencyRoots) {
        ArrangementEntryDependencyInfo info = buildMethodDependencyInfo(method, cache);
        if (info != null) {
          myMethodDependencyRoots.add(info);
        }
      }
      myRebuildMethodDependencies = false;
    }
    return myMethodDependencyRoots;
  }

  @Nullable
  private ArrangementEntryDependencyInfo buildMethodDependencyInfo(@NotNull final PsiMethod method,
                                                                   @NotNull Map<PsiMethod, ArrangementEntryDependencyInfo> cache) {
    JavaElementArrangementEntry entry = myMethodEntriesMap.get(method);
    if (entry == null) {
      return null;
    }
    ArrangementEntryDependencyInfo result = new ArrangementEntryDependencyInfo(entry);
    Stack<Pair<PsiMethod, ArrangementEntryDependencyInfo>> toProcess
      = new Stack<>();
    toProcess.push(Pair.create(method, result));
    Set<PsiMethod> usedMethods = ContainerUtilRt.newHashSet();
    while (!toProcess.isEmpty()) {
      Pair<PsiMethod, ArrangementEntryDependencyInfo> pair = toProcess.pop();
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
        ArrangementEntryDependencyInfo dependentMethodInfo = cache.get(dependentMethod);
        if (dependentMethodInfo == null) {
          cache.put(dependentMethod, dependentMethodInfo = new ArrangementEntryDependencyInfo(dependentEntry));
        }
        Pair<PsiMethod, ArrangementEntryDependencyInfo> dependentPair = Pair.create(dependentMethod, dependentMethodInfo);
        pair.second.addDependentEntryInfo(dependentPair.second);
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

  public void onFieldEntryCreated(@NotNull PsiField field, @NotNull JavaElementArrangementEntry entry) {
    myFields.put(field, entry);
  }

  public void onOverriddenMethod(@NotNull PsiMethod baseMethod, @NotNull PsiMethod overridingMethod) {
    PsiClass clazz = baseMethod.getContainingClass();
    if (clazz == null) {
      return;
    }
    List<Pair<PsiMethod, PsiMethod>> methods = myOverriddenMethods.get(clazz);
    if (methods == null) {
      myOverriddenMethods.put(clazz, methods = new ArrayList<>());
    }
    methods.add(Pair.create(baseMethod, overridingMethod));
  }

  @NotNull
  public List<JavaArrangementOverriddenMethodsInfo> getOverriddenMethods() {
    List<JavaArrangementOverriddenMethodsInfo> result = new ArrayList<>();
    final TObjectIntHashMap<PsiMethod> weights = new TObjectIntHashMap<>();
    Comparator<Pair<PsiMethod, PsiMethod>> comparator = (o1, o2) -> weights.get(o1.first) - weights.get(o2.first);
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
   */
  public void registerMethodCallDependency(@NotNull PsiMethod caller, @NotNull PsiMethod callee) {
    myTmpMethodDependencyRoots.remove(callee);
    if (!myDependentMethods.contains(caller)) {
      myTmpMethodDependencyRoots.add(caller);
    }
    myDependentMethods.add(callee);
    Set<PsiMethod> methods = myMethodDependencies.get(caller);
    if (methods == null) {
      myMethodDependencies.put(caller, methods = new LinkedHashSet<>());
    }
    if (!methods.contains(callee)) {
      methods.add(callee);
    }
    myRebuildMethodDependencies = true;
  }

  public void registerFieldInitializationDependency(@NotNull PsiField fieldToInitialize, @NotNull PsiField usedInInitialization) {
    Set<PsiField> fields = myFieldDependencies.get(fieldToInitialize);
    if (fields == null) {
      fields = ContainerUtil.newHashSet();
      myFieldDependencies.put(fieldToInitialize, fields);
    }
    fields.add(usedInInitialization);
  }

  @NotNull
  public List<ArrangementEntryDependencyInfo> getFieldDependencyRoots() {
     return new FieldDependenciesManager(myFieldDependencies, myFields).getRoots();
  }

  @NotNull
  public Collection<JavaElementArrangementEntry> getFields() {
    return myFields.values();
  }
}