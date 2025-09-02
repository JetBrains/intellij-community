// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class JavaArrangementParseInfo {

  private final List<JavaElementArrangementEntry> myEntries = new ArrayList<>();

  private final Map<Pair<String/* property name */, String/* class name */>, JavaArrangementPropertyInfo> myProperties = new HashMap<>();

  private final List<ArrangementEntryDependencyInfo> myMethodDependencyRoots = new ArrayList<>();
  private final Map<PsiMethod /* anchor */, Set<PsiMethod /* dependencies */>> myMethodDependencies = new HashMap<>();

  private final Map<PsiMethod, JavaElementArrangementEntry> myMethodEntriesMap = new HashMap<>();
  private final Map<PsiClass, List<OverriddenMethodPair>> myOverriddenMethods = new LinkedHashMap<>();

  private final Set<PsiMethod> myTmpMethodDependencyRoots = new LinkedHashSet<>();
  private final Set<PsiMethod> myDependentMethods = new HashSet<>();
  private boolean myRebuildMethodDependencies;

  private final HashMap<PsiField, JavaElementArrangementEntry> myFields = new LinkedHashMap<>();
  private final Map<PsiField, Set<PsiField>> myFieldDependencies = new HashMap<>();

  public @NotNull List<JavaElementArrangementEntry> getEntries() {
    return myEntries;
  }

  public void addEntry(@NotNull JavaElementArrangementEntry entry) {
    myEntries.add(entry);
  }

  public @NotNull Collection<JavaArrangementPropertyInfo> getProperties() {
    return myProperties.values();
  }

  /**
   * @return    list of method dependency roots, i.e. there is a possible case that particular method
   *            {@link ArrangementEntryDependencyInfo#getDependentEntriesInfos() calls another method}, it calls other methods
   *            and so forth
   */
  public @NotNull List<ArrangementEntryDependencyInfo> getMethodDependencyRoots() {
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

  private @Nullable ArrangementEntryDependencyInfo buildMethodDependencyInfo(final @NotNull PsiMethod method,
                                                                             @NotNull Map<PsiMethod, ArrangementEntryDependencyInfo> cache) {
    JavaElementArrangementEntry entry = myMethodEntriesMap.get(method);
    if (entry == null) {
      return null;
    }
    ArrangementEntryDependencyInfo result = new ArrangementEntryDependencyInfo(entry);
    Stack<Pair<PsiMethod, ArrangementEntryDependencyInfo>> toProcess
      = new Stack<>();
    toProcess.push(Pair.create(method, result));
    Set<PsiMethod> usedMethods = new HashSet<>();
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

  public void registerSetter(@NotNull String propertyName,
                             @NotNull String className,
                             @NotNull JavaElementArrangementEntry entry) {
    getPropertyInfo(propertyName, className).addSetter(entry);
  }

  private @NotNull JavaArrangementPropertyInfo getPropertyInfo(@NotNull String propertyName, @NotNull String className) {
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
    List<OverriddenMethodPair> methods = myOverriddenMethods.get(clazz);
    if (methods == null) {
      myOverriddenMethods.put(clazz, methods = new ArrayList<>());
    }
    methods.add(new OverriddenMethodPair(baseMethod, overridingMethod));
  }

  public @NotNull List<JavaArrangementOverriddenMethodsInfo> getOverriddenMethods() {
    List<JavaArrangementOverriddenMethodsInfo> result = new ArrayList<>();
    for (Map.Entry<PsiClass, List<OverriddenMethodPair>> entry: myOverriddenMethods.entrySet()) {
      String name = entry.getKey().getName();

      Map<PsiClass, List<OverriddenMethodPair>> groupedByClass = entry.getValue().stream()
             .collect(Collectors.groupingBy(pair -> pair.overriding.getContainingClass()));
      for (Map.Entry<PsiClass, List<OverriddenMethodPair>> listEntry: groupedByClass.entrySet()) {
        JavaArrangementOverriddenMethodsInfo info = new JavaArrangementOverriddenMethodsInfo(name);
        result.add(info);

        List<OverriddenMethodPair> value = ContainerUtil.sorted(listEntry.getValue(),
                Comparator.comparingInt(pair -> pair.overridden.getTextOffset()));
        for (OverriddenMethodPair methodPair: value) {
          JavaElementArrangementEntry methodEntry = myMethodEntriesMap.get(methodPair.overriding);
          info.addMethodEntry(methodEntry);
        }
      }
    }

    return result;
  }

  /**
   * Is expected to be called when new method dependency is detected. Here given {@code 'base method'} calls
   * {@code 'dependent method'}.
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
    methods.add(callee);
    myRebuildMethodDependencies = true;
  }

  public void registerFieldInitializationDependency(@NotNull PsiField fieldToInitialize, @NotNull PsiField usedInInitialization) {
    Set<PsiField> fields = myFieldDependencies.get(fieldToInitialize);
    if (fields == null) {
      fields = new HashSet<>();
      myFieldDependencies.put(fieldToInitialize, fields);
    }
    fields.add(usedInInitialization);
  }

  public @NotNull List<ArrangementEntryDependencyInfo> getFieldDependencyRoots() {
     return new FieldDependenciesManager(myFieldDependencies, myFields).getRoots();
  }

  public @NotNull Collection<JavaElementArrangementEntry> getFields() {
    return myFields.values();
  }

  private record OverriddenMethodPair(@NotNull PsiMethod overridden, @NotNull PsiMethod overriding) {
  }
}