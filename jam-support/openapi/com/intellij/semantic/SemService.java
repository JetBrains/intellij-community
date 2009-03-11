/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.semantic;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class SemService {
  private final ConcurrentFactoryMap<PsiElement, ConcurrentMap<Class, SemElement>> myCache = new ConcurrentFactoryMap<PsiElement, ConcurrentMap<Class, SemElement>>() {
    @Override
    protected ConcurrentMap<Class, SemElement> create(PsiElement key) {
      return new ConcurrentHashMap<Class, SemElement>();
    }
  };
  private final MultiMap<Class, NullableFunction<PsiElement, ? extends SemElement>> myProducers = new MultiMap<Class, NullableFunction<PsiElement, ? extends SemElement>>();

  protected SemService(Project project) {
    project.getMessageBus().connect().subscribe(ProjectTopics.MODIFICATION_TRACKER, new PsiModificationTracker.Listener() {
      public void modificationCountChanged() {
        ApplicationManager.getApplication().assertWriteAccessAllowed();
        myCache.clear();
      }
    });
    final SemRegistrar registrar = new SemRegistrar() {
      public <T extends SemElement, V extends PsiElement> void registerSemElementProvider(Class<T> key,
                                                                                   final ElementPattern<? extends V> place,
                                                                                   final NullableFunction<V, T> provider) {
        myProducers.putValue(key, new NullableFunction<PsiElement, SemElement>() {
          public SemElement fun(PsiElement element) {
            if (place.accepts(element)) {
              return provider.fun((V)element);
            }
            return null;
          }
        });
      }
    };
    for (SemContributor contributor : project.getExtensions(SemContributor.EP_NAME)) {
      contributor.registerSemProviders(registrar);
    }
  }

  public static SemService getSemService(Project p) {
    return ServiceManager.getService(p, SemService.class);
  }

  @NotNull
  public List<SemElement> getSemElements(@NotNull PsiElement psi) {
    List<SemElement> result = null;
    for (final Class aClass : myProducers.keySet()) {
      final SemElement semElement = getSemElement(aClass, psi);
      if (semElement != null) {
        if (result == null) result = new SmartList<SemElement>();
        result.add(semElement);
      }
    }
    return result == null ? Collections.<SemElement>emptyList() : result;
  }

  @Nullable
  public <T extends SemElement> T getSemElement(Class<T> c, @NotNull PsiElement psi) {
    final ConcurrentMap<Class, SemElement> map = myCache.get(psi);
    final T cached = (T) map.get(c);
    if (cached != null) {
      return cached;
    }

    final Collection<NullableFunction<PsiElement, ? extends SemElement>> producers = myProducers.get(c);
    if (producers.isEmpty()) {
      return null;
    }

    for (final NullableFunction<PsiElement, ? extends SemElement> producer : producers) {
      final SemElement element = producer.fun(psi);
      if (element != null) {
        return (T)ConcurrencyUtil.cacheOrGet(map, c, element);
      }
    }

    return null;
  }


}
