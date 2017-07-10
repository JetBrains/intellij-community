/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.createConcurrentWeakKeySoftValueMap;

public class JvmFacadeImpl implements JvmFacade {

  private static final Logger LOG = Logger.getInstance(JvmFacadeImpl.class);

  private final DumbService myDumbService;
  private final JavaPsiFacadeImpl myJavaPsiFacade;
  private final JvmElementProvider[] myElementProviders;
  private final Map<GlobalSearchScope, Map<String, List<JvmClass>>> myClassCache = createConcurrentWeakKeySoftValueMap();

  public JvmFacadeImpl(@NotNull Project project, MessageBus bus) {
    myDumbService = DumbService.getInstance(project);
    myJavaPsiFacade = (JavaPsiFacadeImpl)JavaPsiFacade.getInstance(project);
    myElementProviders = JvmElementProvider.EP_NAME.getExtensions(project);
    if (bus != null) {
      bus.connect().subscribe(PsiModificationTracker.TOPIC, () -> myClassCache.clear());
    }
  }

  @Override
  @NotNull
  public List<? extends JvmClass> findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return findClasses(qualifiedName, scope, true);
  }

  @NotNull
  public List<? extends JvmClass> findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope, boolean askJavaFacade) {
    if (myDumbService.isDumb() || !askJavaFacade) {
      return doFindClasses(qualifiedName, scope, askJavaFacade); // don't cache
    }
    Map<String, List<JvmClass>> map = myClassCache.computeIfAbsent(scope, s -> ContainerUtil.createConcurrentWeakValueMap());
    return map.computeIfAbsent(qualifiedName, fqn -> doFindClasses(fqn, scope, askJavaFacade));
  }


  @NotNull
  private List<JvmClass> doFindClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope, boolean askJavaFacade) {
    return sortByScope(doGetClassesFromProviders(qualifiedName, scope, askJavaFacade), scope);
  }

  @NotNull
  private List<JvmClass> doGetClassesFromProviders(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope, boolean askJavaFacade) {
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
    if (askJavaFacade) {
      PsiClass[] javaClasses = myJavaPsiFacade.findClasses(qualifiedName, scope, false);
      if (javaClasses.length != 0) {
        if (result == null) {
          result = ContainerUtil.newArrayList(javaClasses);
        }
        else {
          ContainerUtil.addAll(result, javaClasses);
        }
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  @NotNull
  private static List<JvmClass> sortByScope(@NotNull List<JvmClass> classes, @NotNull GlobalSearchScope scope) {
    if (classes.size() == 1) return classes;
    classes.sort(JvmClassUtil.createScopeComparator(scope));
    return classes;
  }

  private static void assertNotNullClasses(@NotNull JvmElementProvider provider, @NotNull List<? extends JvmClass> classes) {
    for (JvmClass jvmClass : classes) {
      LOG.assertTrue(jvmClass != null, "Provider " + provider + "returned null JvmClass");
    }
  }

  @NotNull
  private List<JvmElementProvider> filteredProviders() {
    return myDumbService.filterByDumbAwareness(myElementProviders);
  }
}
