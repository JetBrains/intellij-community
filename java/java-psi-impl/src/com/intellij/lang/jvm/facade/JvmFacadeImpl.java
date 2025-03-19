// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.facade;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.util.JvmClassUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JvmFacadeImpl implements JvmFacade {
  private static final Logger LOG = Logger.getInstance(JvmFacadeImpl.class);

  private final DumbService myDumbService;
  private final JavaPsiFacadeImpl myJavaPsiFacade;
  private final Map<GlobalSearchScope, Map<String, List<JvmClass>>> myClassCache = CollectionFactory.createConcurrentWeakKeySoftValueMap();

  public JvmFacadeImpl(@NotNull Project project) {
    myDumbService = DumbService.getInstance(project);
    myJavaPsiFacade = (JavaPsiFacadeImpl)JavaPsiFacade.getInstance(project);
    project.getMessageBus().simpleConnect().subscribe(PsiModificationTracker.TOPIC, () -> myClassCache.clear());
  }

  @Override
  public @Unmodifiable @NotNull List<? extends JvmClass> findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    Map<String, List<JvmClass>> map = myClassCache.computeIfAbsent(scope, s -> CollectionFactory.createConcurrentWeakValueMap());
    return map.computeIfAbsent(qualifiedName, fqn -> doFindClassesWithJavaFacade(fqn, scope));
  }

  private @Unmodifiable List<JvmClass> doFindClassesWithJavaFacade(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return sortByScope(findClassesWithJavaFacade(qualifiedName, scope), scope);
  }

  private @Unmodifiable List<JvmClass> findClassesWithJavaFacade(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    List<JvmClass> result = null;

    final List<JvmClass> ownClasses = findClassesWithoutJavaFacade(qualifiedName, scope);
    if (!ownClasses.isEmpty()) {
      result = new ArrayList<>(ownClasses);
    }

    final List<PsiClass> javaClasses = myJavaPsiFacade.findClassesWithoutJvmFacade(qualifiedName, scope);
    if (!javaClasses.isEmpty()) {
      if (result == null) {
        result = new ArrayList<>(javaClasses);
      }
      else {
        result.addAll(javaClasses);
      }
    }

    return result == null ? Collections.emptyList() : result;
  }

  public @Unmodifiable @NotNull List<JvmClass> findClassesWithoutJavaFacade(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    List<JvmClass> result = null;
    for (JvmElementProvider provider : filteredProviders()) {
      List<? extends JvmClass> providedClasses = provider.getClasses(qualifiedName, scope);
      if (providedClasses.isEmpty()) continue;
      assertNotNullClasses(provider, providedClasses);
      if (result == null) {
        result = new ArrayList<>(providedClasses);
      }
      else {
        result.addAll(providedClasses);
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  private static @Unmodifiable @NotNull List<JvmClass> sortByScope(@NotNull @Unmodifiable List<JvmClass> classes, @NotNull GlobalSearchScope scope) {
    if (classes.size() == 1) return classes;
    return ContainerUtil.sorted(classes, JvmClassUtil.createScopeComparator(scope));
  }

  private static void assertNotNullClasses(@NotNull JvmElementProvider provider, @NotNull List<? extends JvmClass> classes) {
    for (JvmClass jvmClass : classes) {
      LOG.assertTrue(jvmClass != null, "Provider " + provider + "returned null JvmClass");
    }
  }

  private @Unmodifiable @NotNull List<JvmElementProvider> filteredProviders() {
    return myDumbService.filterByDumbAwareness(JvmElementProvider.EP_NAME.getExtensionList(myJavaPsiFacade.getProject()));
  }
}
