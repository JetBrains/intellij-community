// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.StubBuildCachedValuesManager;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * A service used to create and store {@link CachedValue} objects.<p/>
 *
 * By default, cached values are stored in the user data of associated objects implementing {@link UserDataHolder}.
 *
 * @see #createCachedValue(CachedValueProvider, boolean)
 * @see #getCachedValue(PsiElement, CachedValueProvider)
 * @see #getCachedValue(UserDataHolder, CachedValueProvider)
 */
public abstract class CachedValuesManager {
  private static final ThrottledLogger LOG = new ThrottledLogger(Logger.getInstance(CachedValuesManager.class), 5000);

  public static CachedValuesManager getManager(@NotNull Project project) {
    return project.getService(CachedValuesManager.class);
  }

  /**
   * Creates new CachedValue instance with given provider. If the return value is marked as trackable, it's treated as
   * yet another dependency and must comply with its specification.
   * See {@link Result#getDependencyItems()} for the details.
   *
   * @param provider computes values.
   * @param trackValue if value tracking is required. T should be trackable in this case.
   * @return new CachedValue instance.
   */
  public abstract @NotNull <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue);

  public abstract @NotNull <T> CachedValue<T> createCachedValue(
    @NotNull UserDataHolder userDataHolder,
    @NotNull CachedValueProvider<T> provider,
    boolean trackValue
  );

  public abstract @NotNull <T,P> ParameterizedCachedValue<T,P> createParameterizedCachedValue(
    @NotNull ParameterizedCachedValueProvider<T,P> provider,
    boolean trackValue
  );

  protected abstract @NotNull <T,P> ParameterizedCachedValue<T,P> createParameterizedCachedValue(
    @NotNull UserDataHolder userDataHolder,
    @NotNull ParameterizedCachedValueProvider<T,P> provider,
    boolean trackValue
  );

  /**
   * Creates a new CachedValue instance with the given provider.
   */
  public @NotNull <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider) {
    return createCachedValue(provider, false);
  }

  public <T, P> T getParameterizedCachedValue(@NotNull UserDataHolder dataHolder,
                                              @NotNull Key<ParameterizedCachedValue<T, P>> key,
                                              @NotNull ParameterizedCachedValueProvider<T, P> provider,
                                              boolean trackValue,
                                              P parameter) {
    ParameterizedCachedValue<T, P> value;
    if (StubBuildCachedValuesManager.isBuildingStubs()
        && !StubBuildCachedValuesManager.isComputingCachedValue()
        && (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal())) {
      LOG.warn("StubBuildCachedValuesManager should be used during stub building to improve performance");
    }

    if (dataHolder instanceof UserDataHolderEx) {
      UserDataHolderEx dh = (UserDataHolderEx)dataHolder;
      value = dh.getUserData(key);
      if (value == null) {
        trackKeyHolder(dataHolder, key);
        value = createParameterizedCachedValue(dataHolder, provider, trackValue);
        value = dh.putUserDataIfAbsent(key, value);
      }
    }
    else {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (dataHolder) {
        value = dataHolder.getUserData(key);
        if (value == null) {
          trackKeyHolder(dataHolder, key);
          value = createParameterizedCachedValue(dataHolder, provider, trackValue);
          dataHolder.putUserData(key, value);
        }
      }
    }
    return value.getValue(parameter);
  }

  @ApiStatus.Internal
  protected abstract void trackKeyHolder(@NotNull UserDataHolder dataHolder, @NotNull Key<?> key);

  /**
   * Utility method storing created cached values in a {@link UserDataHolder}.
   * The passed cached value provider may only depend on the passed user data holder and longer-living system state (e.g. project/application components/services),
   * see {@link CachedValue} documentation for more details.
   *
   * @param dataHolder holder to store the cached value, e.g. a PsiElement.
   * @param key key to store the cached value.
   * @param provider provider creating the cached value.
   * @param trackValue if value tracking is required (T should be trackable in that case). See {@link #createCachedValue(CachedValueProvider, boolean)} for more details.
   * @return up-to-date value.
   */
  public abstract <T> T getCachedValue(@NotNull UserDataHolder dataHolder,
                                       @NotNull Key<CachedValue<T>> key,
                                       @NotNull CachedValueProvider<T> provider,
                                       boolean trackValue);

  /**
   * Create a cached value with the given provider and non-tracked return value, store it in the first argument's user data. If it's already stored, reuse it.
   * The passed cached value provider may only depend on the passed user data holder and longer-living system state (e.g. project/application components/services),
   * see {@link CachedValue} documentation for more details.
   * @return The cached value
   */
  public <T> T getCachedValue(@NotNull UserDataHolder dataHolder, @NotNull CachedValueProvider<T> provider) {
    return getCachedValue(dataHolder, this.getKeyForClass(provider.getClass()), provider, false);
  }

  /**
   * Create a cached value with the given provider and non-tracked return value, store it in PSI element's user data. If it's already stored, reuse it.
   * The passed cached value provider may only depend on the passed context PSI element and project/application components/services,
   * see {@link CachedValue} documentation for more details.
   * @return The cached value
   */
  public static <T> T getCachedValue(final @NotNull PsiElement context, final @NotNull CachedValueProvider<T> provider) {
    return getCachedValue(context, getKeyForClass(provider.getClass(), globalKeyForProvider), provider);
  }

  /**
   * Create a cached value with the given provider, store it in PSI element's user data. If it's already stored, reuse it.
   * Invalidate on any PSI change in the project.
   * The passed cached value provider may only depend on the passed context PSI element and project/application components/services,
   * see {@link CachedValue} documentation for more details.
   *
   * @return The cached value
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <E extends PsiElement, T> T getProjectPsiDependentCache(@NotNull E context, @NotNull Function<? super E, ? extends T> provider) {
    Key<? extends CachedValue<? extends T>> key = getKeyForClass(provider.getClass(), globalKeyForProvider);
    ParameterizedCachedValue<T, PsiElement> value = (ParameterizedCachedValue<T, PsiElement>)context.getUserData((Key)key);
    if (value != null) {
      return value.getValue(context);
    }

    CachedValuesManager manager = getManager(context.getProject());
    return (T)manager.getParameterizedCachedValue(context, (Key)key, new PsiDependentHandlerProvider<>((Function)provider), false, context);
  }

  private static final class PsiDependentHandlerProvider<T> implements ParameterizedCachedValueProvider<T, PsiElement> {
    private final @NotNull Function<PsiElement, ? extends T> delegate;

    private PsiDependentHandlerProvider(@NotNull Function<PsiElement, ? extends T> delegate) { this.delegate = delegate; }

    @Override
    public Result<T> compute(PsiElement context) {
      T data = delegate.apply(context);
      if (!context.isPhysical()) {
        PsiFile file = context.getContainingFile();
        if (file != null) {
          Result<T> adjusted = Result.create(data, file, PsiModificationTracker.MODIFICATION_COUNT);
          CachedValueProfiler.onResultCreated(adjusted, null);
          return adjusted;
        }
      }

      return Result.createSingleDependency(data, PsiModificationTracker.MODIFICATION_COUNT);
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      PsiDependentHandlerProvider<?> provider = (PsiDependentHandlerProvider<?>)o;
      return Objects.equals(delegate, provider.delegate);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(delegate);
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }

  /**
   * Create a cached value with the given provider and non-tracked return value, store it in PSI element's user data. If it's already stored, reuse it.
   * The passed cached value provider may only depend on the passed context PSI element and project/application components/services,
   * see {@link CachedValue} documentation for more details.
   *
   * @return The cached value
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T> T getCachedValue(@NotNull PsiElement context, @NotNull Key<CachedValue<T>> key, @NotNull CachedValueProvider<T> provider) {
    ParameterizedCachedValue<T, PsiElement> value = (ParameterizedCachedValue<T, PsiElement>)context.getUserData((Key)key);
    if (value != null) {
      return value.getValue(context);
    }

    CachedValuesManager manager = getManager(context.getProject());
    return (T)manager.getParameterizedCachedValue(context, (Key)key, new NonPhysicalPsiHandlerProvider<>(provider), false, context);
  }

  private static final class NonPhysicalPsiHandlerProvider<T> implements ParameterizedCachedValueProvider<T, PsiElement> {
    private final @NotNull CachedValueProvider<T> delegate;

    private NonPhysicalPsiHandlerProvider(@NotNull CachedValueProvider<T> delegate) { this.delegate = delegate; }

    @Override
    public Result<T> compute(PsiElement context) {
      Result<T> result = delegate.compute();
      if (result != null && !context.isPhysical()) {
        PsiFile file = context.getContainingFile();
        if (file != null) {
          Result<T> adjusted = Result.create(result.getValue(), ArrayUtil.append(result.getDependencyItems(), file,
                                                                                 ArrayUtil.OBJECT_ARRAY_FACTORY));
          CachedValueProfiler.onResultCreated(adjusted, result);
          return adjusted;
        }
      }
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      NonPhysicalPsiHandlerProvider<?> provider = (NonPhysicalPsiHandlerProvider<?>)o;
      return Objects.equals(delegate, provider.delegate);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(delegate);
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }

  private static final ConcurrentMap<String, Key<CachedValue<?>>> globalKeyForProvider = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Key<CachedValue<?>>> keyForProvider = new ConcurrentHashMap<>();

  public @NotNull <T> Key<CachedValue<T>> getKeyForClass(@NotNull Class<?> providerClass) {
    return getKeyForClass(providerClass, keyForProvider);
  }

  private static @NotNull <T> Key<CachedValue<T>> getKeyForClass(@NotNull Class<?> providerClass, ConcurrentMap<String, Key<CachedValue<?>>> keyForProvider) {
    String name = providerClass.getName();
    Key<CachedValue<?>> key = keyForProvider.get(name);
    if (key == null) {
      key = ConcurrencyUtil.cacheOrGet(keyForProvider, name, Key.create(name));
    }
    //noinspection unchecked,rawtypes
    return (Key)key;
  }
}
