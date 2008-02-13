package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author maxim
 */
abstract class NamedObjectProviderBinding implements ProviderBinding {
  private final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>> myNamesToProvidersMap = new ConcurrentHashMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>>(5);
  private final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>> myNamesToProvidersMapInsensitive = new ConcurrentHashMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>>(5);

  public void registerProvider(@NonNls String[] names, ElementPattern filter, boolean caseSensitive, PsiReferenceProvider provider, final double priority) {
    final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>> map = caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

    for (final String attributeName : names) {
      CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>> psiReferenceProviders = map.get(attributeName);

      if (psiReferenceProviders == null) {
        psiReferenceProviders = ConcurrencyUtil.cacheOrGet(map, caseSensitive ? attributeName : attributeName.toLowerCase(), new CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>());
      }

      psiReferenceProviders.add(new Trinity<PsiReferenceProvider, ElementPattern,Double>(provider, filter, priority));
    }
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> list) {
    String name = getName(position);
    if (name != null) {
      addMatchingProviders(position, myNamesToProvidersMap.get(name), list);
      addMatchingProviders(position, myNamesToProvidersMapInsensitive.get(name.toLowerCase()), list);
    }
  }

  public void unregisterProvider(final PsiReferenceProvider provider) {
    for (final CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern, Double>> list : myNamesToProvidersMap.values()) {
      for (final Trinity<PsiReferenceProvider, ElementPattern, Double> trinity : new ArrayList<Trinity<PsiReferenceProvider, ElementPattern, Double>>(list)) {
        if (trinity.first.equals(provider)) {
          list.remove(trinity);
        }
      }
    }
    for (final CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern, Double>> list : myNamesToProvidersMapInsensitive.values()) {
      for (final Trinity<PsiReferenceProvider, ElementPattern, Double> trinity : new ArrayList<Trinity<PsiReferenceProvider, ElementPattern, Double>>(list)) {
        if (trinity.first.equals(provider)) {
          list.remove(trinity);
        }
      }
    }
  }

  @Nullable
  abstract protected String getName(final PsiElement position);

  private static void addMatchingProviders(final PsiElement position,
                                           @Nullable final List<Trinity<PsiReferenceProvider,ElementPattern,Double>> providerList,
                                           final List<Trinity<PsiReferenceProvider, ProcessingContext,Double>> ret) {
    if (providerList == null) return;

    for(Trinity<PsiReferenceProvider,ElementPattern,Double> trinity:providerList) {
      final ProcessingContext context = new ProcessingContext();
      if (trinity.second.accepts(position, context)) {
        ret.add(Trinity.create(trinity.first, context, trinity.third));
      }
    }
  }
}
