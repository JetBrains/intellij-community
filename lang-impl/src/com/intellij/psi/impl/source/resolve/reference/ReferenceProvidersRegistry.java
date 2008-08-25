package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.codeInsight.completion.LegacyCompletionContributor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.*;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:13:45
 * To change this template use Options | File Templates.
 */
public class ReferenceProvidersRegistry implements PsiReferenceRegistrar {
  private final ConcurrentMap<Class,SimpleProviderBinding> myBindingsMap = new ConcurrentWeakHashMap<Class, SimpleProviderBinding>();
  private final ConcurrentMap<Class,NamedObjectProviderBinding> myNamedBindingsMap = new ConcurrentWeakHashMap<Class, NamedObjectProviderBinding>();
  private MultiMap<Class,Class> myKnownSupers;
  private boolean myInitialized;

  private static final Comparator<Trinity<PsiReferenceProvider,ProcessingContext,Double>> PRIORITY_COMPARATOR = new Comparator<Trinity<PsiReferenceProvider, ProcessingContext, Double>>() {
    public int compare(final Trinity<PsiReferenceProvider, ProcessingContext, Double> o1,
                       final Trinity<PsiReferenceProvider, ProcessingContext, Double> o2) {
      return o2.getThird().compareTo(o1.getThird());
    }
  };

  public static ReferenceProvidersRegistry getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ReferenceProvidersRegistry.class);
  }

  private ReferenceProvidersRegistry() {
  }

  /**
   * @deprecated {@see com.intellij.psi.PsiReferenceContributor
   */
  public void registerReferenceProvider(@Nullable ElementFilter elementFilter,
                                        @NotNull Class scope,
                                        @NotNull PsiReferenceProvider provider,
                                        double priority) {
    registerReferenceProvider(PlatformPatterns.psiElement(scope).and(new FilterPattern(elementFilter)), provider, priority);
  }


  public void registerReferenceProvider(@NotNull ElementPattern<? extends PsiElement> pattern, @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(pattern, provider, DEFAULT_PRIORITY);
  }
  public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern, @NotNull PsiReferenceProvider provider, double priority) {
    myKnownSupers = null;
    final Class scope = pattern.getCondition().getInitialCondition().getAcceptedClass();
    final PsiNamePatternCondition<?> nameCondition = ContainerUtil.findInstance(pattern.getCondition().getConditions(), PsiNamePatternCondition.class);
    if (nameCondition != null) {
      final ValuePatternCondition<String> valueCondition =
        ContainerUtil.findInstance(nameCondition.getNamePattern().getCondition().getConditions(), ValuePatternCondition.class);
      if (valueCondition != null) {
        final Collection<String> strings = valueCondition.getValues();
        registerNamedReferenceProvider(strings.toArray(new String[strings.size()]), new NamedObjectProviderBinding() {
          protected String getName(final PsiElement position) {
            return nameCondition.getPropertyValue(position);
          }
        }, scope, true, provider, priority, pattern);
        return;
      }

      final CaseInsensitiveValuePatternCondition ciCondition =
        ContainerUtil.findInstance(nameCondition.getNamePattern().getCondition().getConditions(), CaseInsensitiveValuePatternCondition.class);
      if (ciCondition != null) {
        registerNamedReferenceProvider(ciCondition.getValues(), new NamedObjectProviderBinding() {
          @Nullable
          protected String getName(final PsiElement position) {
            return nameCondition.getPropertyValue(position);
          }
        }, scope, false, provider, priority, pattern);
        return;
      }
    }


    while (true) {
      final SimpleProviderBinding providerBinding = myBindingsMap.get(scope);
      if (providerBinding != null) {
        providerBinding.registerProvider(provider, pattern, priority);
        return;
      }

      final SimpleProviderBinding binding = new SimpleProviderBinding();
      binding.registerProvider(provider, pattern, priority);
      if (myBindingsMap.putIfAbsent(scope, binding) == null) break;
    }
  }

  /**
   * @deprecated {@see com.intellij.psi.PsiReferenceContributor
   */
  public void registerReferenceProvider(@Nullable ElementFilter elementFilter,
                                        @NotNull Class scope,
                                        @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(elementFilter, scope, provider, DEFAULT_PRIORITY);
  }

  public void unregisterReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    final ProviderBinding providerBinding = myBindingsMap.get(scope);
    providerBinding.unregisterProvider(provider);
  }


  private void registerNamedReferenceProvider(final String[] names, final NamedObjectProviderBinding binding,
                                              final Class scopeClass,
                                              final boolean caseSensitive,
                                              final PsiReferenceProvider provider, final double priority, final ElementPattern pattern) {
    NamedObjectProviderBinding providerBinding = myNamedBindingsMap.get(scopeClass);

    if (providerBinding == null) {
      providerBinding = ConcurrencyUtil.cacheOrGet(myNamedBindingsMap, scopeClass, binding);
    }

    providerBinding.registerProvider(names, pattern, caseSensitive, provider, priority);
  }

  /**
   * @deprecated
   * @see com.intellij.psi.PsiReferenceContributor
   */
  public void registerReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(null, scope, provider);
  }

  @Deprecated
  public List<PsiReferenceProvider> getProvidersByElement(@NotNull PsiElement element, @NotNull Class clazz) {
    final List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> list = getPairsByElement(element, clazz);
    final ArrayList<PsiReferenceProvider> providers = new ArrayList<PsiReferenceProvider>(list.size());
    for (Trinity<PsiReferenceProvider, ProcessingContext, Double> trinity : list) {
      providers.add(trinity.getFirst());
    }
    return providers;
  }

  @NotNull
  private List<Trinity<PsiReferenceProvider,ProcessingContext,Double>> getPairsByElement(@NotNull PsiElement element, @NotNull Class clazz) {
    assert ReflectionCache.isInstance(element, clazz);

    synchronized (myBindingsMap) {
      if (!myInitialized) {
        for (final PsiReferenceContributor contributor : Extensions.getExtensions(PsiReferenceContributor.EP_NAME)) {
          contributor.registerReferenceProviders(this);
        }
        myInitialized = true;
      }
    }

    MultiMap<Class, Class> knownSupers = myKnownSupers;
    if (knownSupers == null) {
      knownSupers = new MultiMap<Class, Class>();
      Set<Class> allClasses = new THashSet<Class>();
      allClasses.addAll(myBindingsMap.keySet());
      allClasses.addAll(myNamedBindingsMap.keySet());
      for (final Class ancestor : allClasses) {
        for (final Class descendant : allClasses) {
          if (ancestor.isAssignableFrom(descendant)) {
            knownSupers.putValue(descendant, ancestor);
          }
        }
      }
      myKnownSupers = knownSupers;
    }

    List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> ret = null;
    for (final Class aClass : knownSupers.get(clazz)) {
      final SimpleProviderBinding simpleBinding = myBindingsMap.get(aClass);
      final NamedObjectProviderBinding namedBinding = myNamedBindingsMap.get(aClass);
      if (simpleBinding == null && namedBinding == null) continue;

      if (ret == null) ret = new SmartList<Trinity<PsiReferenceProvider, ProcessingContext, Double>>();
      if (simpleBinding != null) {
        simpleBinding.addAcceptableReferenceProviders(element, ret);
      }
      if (namedBinding != null) {
        namedBinding.addAcceptableReferenceProviders(element, ret);
      }
    }
    return ret == null ? Collections.<Trinity<PsiReferenceProvider, ProcessingContext, Double>>emptyList() : ret;
  }

  public static PsiReference[] getReferencesFromProviders(PsiElement context, @NotNull Class clazz){
    assert context.isValid() : "Invalid context: " + context;

    PsiReference[] result = PsiReference.EMPTY_ARRAY;
    final List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> providers = getInstance(context.getProject()).getPairsByElement(context, clazz);
    if (providers.isEmpty()) {
      return result;
    }
    Collections.sort(providers, PRIORITY_COMPARATOR);
    if (LegacyCompletionContributor.DEBUG) {
      System.out.println("ReferenceProvidersRegistry.getReferencesFromProviders");
      System.out.println("providers = " + providers);
    }

    final Double maxPriority = providers.get(0).getThird();
    next: for (Trinity<PsiReferenceProvider, ProcessingContext, Double> trinity : providers) {
      final PsiReference[] refs = trinity.getFirst().getReferencesByElement(context, trinity.getSecond());
      if (trinity.getThird().equals(maxPriority)) {
        result = ArrayUtil.mergeArrays(result, refs, PsiReference.class);
      } else {
        for (PsiReference ref : refs) {
          for (PsiReference reference : result) {
            if (reference.getRangeInElement().contains(ref.getRangeInElement())) {
              continue next;
            }
          }
        }
        result = ArrayUtil.mergeArrays(result, refs, PsiReference.class);
      }
    }
    return result;
  }

}
